package com.filmvault.app.util

import android.content.Context
import android.net.Uri
import com.filmvault.app.di.AppModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

/** 应用私有离线缓存：渐进式媒体直接保存，未加密 HLS 保存清单和分片。 */
object OfflineMediaStore {
    data class CacheEntry(val sourceUrl: String, val label: String, val file: File) {
        val bytes: Long get() = file.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: file.length()
    }

    suspend fun download(context: Context, sourceUrl: String, referer: String = AppModule.siteSettings.siteUrlNow, label: String = "", onProgress: (Long, Long) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        require(sourceUrl.isNotBlank()) { "播放地址为空" }
        cachedFile(context, sourceUrl)?.let { return@withContext it }
        val directory = File(cacheRoot(context), sourceKey(sourceUrl)).apply { mkdirs() }
        try {
            AppModule.apiClient.openMediaResponse(sourceUrl, referer).use { response ->
                check(response.isSuccessful) { "下载失败：HTTP ${response.code}" }
                val body = response.body ?: error("下载内容为空")
                val contentType = body.contentType()?.toString().orEmpty()
                check(!contentType.contains("text/html", true)) { "播放地址已失效或被站点拒绝" }
                check(!sourceUrl.substringBefore('?').endsWith(".mpd", true) && !contentType.contains("dash", true)) { "DASH 视频暂不支持离线缓存" }
                val hls = sourceUrl.substringBefore('?').endsWith(".m3u8", true) || contentType.contains("mpegurl", true)
                val output = if (hls) downloadHls(sourceUrl, body.string(), directory, referer, onProgress) else {
                    val file = File(directory, "media${extensionFor(sourceUrl, contentType)}")
                    val total = body.contentLength().coerceAtLeast(0L)
                    var done = 0L
                    body.byteStream().use { input -> file.outputStream().use { outputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            outputStream.write(buffer, 0, count)
                            done += count
                            onProgress(done, total)
                        }
                    } }
                    onProgress(done, total)
                    file
                }
                if (label.isNotBlank()) writeMetadata(context, sourceUrl, label)
                output
            }
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    fun cachedUri(context: Context, sourceUrl: String): Uri? = cachedFile(context, sourceUrl)?.let(Uri::fromFile)

    fun list(context: Context): List<CacheEntry> = cacheRoot(context).listFiles().orEmpty().mapNotNull { directory ->
        val media = directory.walkTopDown().firstOrNull { it.isFile && it.name != METADATA && it.length() > 0L } ?: return@mapNotNull null
        val properties = Properties()
        File(directory, METADATA).takeIf { it.isFile }?.inputStream()?.use(properties::load)
        CacheEntry(properties.getProperty("url", ""), properties.getProperty("label", media.name), media)
    }.sortedByDescending { it.file.parentFile?.lastModified() ?: it.file.lastModified() }

    fun delete(entry: CacheEntry): Boolean = entry.file.parentFile?.deleteRecursively() == true
    fun cacheSize(context: Context): Long = cacheRoot(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun cachedFile(context: Context, sourceUrl: String): File? = File(cacheRoot(context), sourceKey(sourceUrl)).listFiles().orEmpty().firstOrNull { it.isFile && it.name != METADATA && it.length() > 0L }

    private fun downloadHls(sourceUrl: String, playlist: String, directory: File, referer: String, onProgress: (Long, Long) -> Unit): File {
        check(!playlist.contains("#EXT-X-KEY")) { "加密视频暂不支持离线缓存" }
        var mediaBase = URI(sourceUrl)
        val mediaPlaylist = if (playlist.lines().any { it.startsWith("#EXT-X-STREAM-INF") }) {
            val lines = playlist.lines()
            val variant = lines.mapIndexedNotNull { index, line -> if (line.startsWith("#EXT-X-STREAM-INF")) lines.getOrNull(index + 1)?.trim()?.takeIf { it.isNotEmpty() } else null }.lastOrNull() ?: error("HLS 清单无可用线路")
            val variantUrl = mediaBase.resolve(variant).toString()
            mediaBase = URI(variantUrl)
            fetchText(variantUrl, referer)
        } else playlist
        check(!mediaPlaylist.contains("#EXT-X-KEY")) { "加密视频暂不支持离线缓存" }
        val segmentDirectory = File(directory, "segments").apply { mkdirs() }
        val segmentCount = mediaPlaylist.lines().count { it.trim().isNotEmpty() && !it.trim().startsWith("#") }
        var index = 0
        var completed = 0L
        val rewritten = mediaPlaylist.lines().joinToString("\n") { line ->
            val value = line.trim()
            if (value.isEmpty() || value.startsWith("#")) line else {
                val segmentUrl = mediaBase.resolve(value).toString()
                val target = File(segmentDirectory, "segment-${index++}${extensionFor(segmentUrl, "")}")
                downloadBinary(segmentUrl, target, referer)
                completed++
                onProgress(completed, segmentCount.toLong())
                "segments/${target.name}"
            }
        }
        return File(directory, "media.m3u8").apply { writeText(rewritten) }
    }

    private fun fetchText(url: String, referer: String): String = AppModule.apiClient.openMediaResponse(url, referer).use { response ->
        check(response.isSuccessful) { "下载清单失败：HTTP ${response.code}" }
        response.body?.string() ?: error("清单为空")
    }

    private fun downloadBinary(url: String, target: File, referer: String) {
        AppModule.apiClient.openMediaResponse(url, referer).use { response ->
            check(response.isSuccessful) { "下载分片失败：HTTP ${response.code}" }
            response.body?.byteStream()?.use { input -> target.outputStream().use { output -> input.copyTo(output) } } ?: error("分片内容为空")
        }
    }

    private fun sourceKey(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private const val METADATA = "metadata.properties"
    private fun cacheRoot(context: Context): File = File(context.filesDir, "offline-media")
    private fun writeMetadata(context: Context, sourceUrl: String, label: String) {
        val properties = Properties().apply { setProperty("url", sourceUrl); setProperty("label", label) }
        File(cacheRoot(context), sourceKey(sourceUrl)).apply { mkdirs() }.resolve(METADATA).outputStream().use { output -> properties.store(output, null) }
    }

    private fun extensionFor(url: String, contentType: String): String = when {
        url.substringBefore('?').endsWith(".m3u8", true) || contentType.contains("mpegurl", true) -> ".m3u8"
        url.substringBefore('?').endsWith(".mpd", true) || contentType.contains("dash", true) -> ".mpd"
        contentType.startsWith("video/mp4") -> ".mp4"
        contentType.startsWith("video/webm") -> ".webm"
        contentType.startsWith("video/x-matroska") -> ".mkv"
        contentType.startsWith("video/quicktime") -> ".mov"
        contentType.startsWith("video/x-msvideo") -> ".avi"
        contentType.startsWith("video/mp2t") -> ".ts"
        contentType.startsWith("audio/mpeg") -> ".mp3"
        contentType.startsWith("audio/mp4") -> ".m4a"
        else -> url.substringBefore('?').substringAfterLast('.').lowercase().takeIf { it in setOf("mp4", "m4v", "webm", "mkv", "mov", "avi", "ts", "flv", "mp3", "m4a") }?.let { ".${it}" } ?: ".mp4"
    }
}
