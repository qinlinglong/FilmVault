package com.filmvault.app.util

import android.content.Context
import android.net.Uri
import com.filmvault.app.di.AppModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

/** 应用私有离线缓存：渐进式媒体直接保存，未加密 HLS 保存清单和分片。 */
object OfflineMediaStore {
    data class CacheEntry(
        val sourceUrl: String,
        val label: String,
        val file: File,
        val detailRoute: String = "",
        val completed: Boolean = true,
        val cacheKey: String = "",
        val referer: String = "",
        val downloaded: Long = 0L,
        val total: Long = 0L,
        val state: String = if (completed) "completed" else "paused",
    ) {
        val bytes: Long get() = file.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: file.length()
        val progress: Float get() = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    private val activeJobs = mutableMapOf<String, Job>()

    suspend fun download(context: Context, sourceUrl: String, referer: String = AppModule.siteSettings.siteUrlNow, label: String = "", cacheKey: String = "", detailRoute: String = "", onProgress: (Long, Long) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        require(sourceUrl.isNotBlank()) { "播放地址为空" }
        cachedFile(context, sourceUrl, cacheKey)?.let { return@withContext it }
        val taskKey = cacheKey.ifBlank { sourceUrl }
        val taskJob = coroutineContext[Job]
        synchronized(activeJobs) { if (taskJob != null) activeJobs[taskKey] = taskJob }
        val directory = File(cacheRoot(context), sourceKey(cacheKey.ifBlank { sourceUrl })).apply { mkdirs() }
        writeMetadata(context, sourceUrl, referer, cacheKey, detailRoute, label, complete = false, state = "downloading", downloaded = 0L, total = 0L)
        var lastPersistAt = 0L
        var lastPersistBytes = 0L
        fun report(downloaded: Long, total: Long) {
            onProgress(downloaded, total)
            val now = System.currentTimeMillis()
            if (downloaded == total || now - lastPersistAt >= 500L || downloaded - lastPersistBytes >= 256L * 1024L) {
                writeMetadata(context, sourceUrl, referer, cacheKey, detailRoute, label, complete = false, state = "downloading", downloaded = downloaded, total = total)
                lastPersistAt = now
                lastPersistBytes = downloaded
            }
        }
        try {
            AppModule.apiClient.openMediaResponse(sourceUrl, referer).use { response ->
                check(response.isSuccessful) { "下载失败：HTTP ${response.code}" }
                val body = response.body ?: error("下载内容为空")
                val contentType = body.contentType()?.toString().orEmpty()
                check(!contentType.contains("text/html", true)) { "播放地址已失效或被站点拒绝" }
                check(!sourceUrl.substringBefore('?').endsWith(".mpd", true) && !contentType.contains("dash", true)) { "DASH 视频暂不支持离线缓存" }
                val hls = sourceUrl.substringBefore('?').endsWith(".m3u8", true) || contentType.contains("mpegurl", true)
                val output = if (hls) downloadHls(sourceUrl, body.string(), directory, referer, ::report) else {
                    val file = File(directory, "media${extensionFor(sourceUrl, contentType)}")
                    val resumeFrom = file.takeIf { it.isFile }?.length() ?: 0L
                    val requestUrl = sourceUrl
                    val responseBody = if (resumeFrom > 0L) {
                        AppModule.apiClient.openMediaResponseWithRange(requestUrl, referer, resumeFrom)
                    } else null
                    if (responseBody != null && responseBody.code !in 200..299) responseBody.close()
                    val total = body.contentLength().coerceAtLeast(0L)
                    val responseForStream = if (resumeFrom > 0L && responseBody?.isSuccessful == true) responseBody else null
                    val actualTotal = if (responseForStream != null) resumeFrom + (responseForStream.body?.contentLength()?.coerceAtLeast(0L) ?: 0L) else total
                    var done = if (responseForStream != null) resumeFrom else 0L
                    if (responseForStream == null && resumeFrom > 0L) file.delete()
                    val streamBody = responseForStream?.body ?: body
                    streamBody.byteStream().use { input -> FileOutputStream(file, responseForStream != null).use { outputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            outputStream.write(buffer, 0, count)
                            done += count
                            report(done, actualTotal)
                        }
                    } }
                    report(done, actualTotal)
                    responseForStream?.close()
                    file
                }
                writeMetadata(context, sourceUrl, referer, cacheKey, detailRoute, label, complete = true, state = "completed", downloaded = outputBytes(output), total = outputBytes(output))
                output
            }
        } catch (error: Throwable) {
            val state = if (error is CancellationException) "paused" else "failed"
            val current = list(context).firstOrNull { it.cacheKey == cacheKey }
            writeMetadata(context, sourceUrl, referer, cacheKey, detailRoute, label, complete = false, state = state, downloaded = current?.bytes ?: 0L, total = current?.total ?: 0L)
            throw error
        } finally {
            synchronized(activeJobs) { if (activeJobs[taskKey] == taskJob) activeJobs.remove(taskKey) }
        }
    }

    fun isActive(cacheKey: String): Boolean = synchronized(activeJobs) { activeJobs[cacheKey]?.isActive == true }
    fun pause(cacheKey: String): Boolean = synchronized(activeJobs) {
        val job = activeJobs.remove(cacheKey)
        job?.cancel()
        job != null
    }

    fun cachedUri(context: Context, sourceUrl: String, cacheKey: String = ""): Uri? = cachedFile(context, sourceUrl, cacheKey)?.let(Uri::fromFile)
    fun isCached(context: Context, cacheKey: String): Boolean = cachedFile(context, "", cacheKey) != null

    fun list(context: Context): List<CacheEntry> = cacheRoot(context).listFiles().orEmpty().mapNotNull { directory ->
        val media = directory.walkTopDown().firstOrNull { it.isFile && it.name != METADATA && it.length() > 0L } ?: return@mapNotNull null
        val properties = Properties()
        File(directory, METADATA).takeIf { it.isFile }?.inputStream()?.use(properties::load)
        CacheEntry(
            sourceUrl = properties.getProperty("url", ""),
            label = properties.getProperty("label", media.name),
            file = media,
            detailRoute = properties.getProperty("detailRoute", ""),
            completed = properties.getProperty("complete", "false") == "true",
            cacheKey = properties.getProperty("cacheKey", ""),
            referer = properties.getProperty("referer", ""),
            downloaded = properties.getProperty("downloaded", "0").toLongOrNull() ?: 0L,
            total = properties.getProperty("total", "0").toLongOrNull() ?: 0L,
            state = properties.getProperty("state", if (properties.getProperty("complete") == "true") "completed" else "paused"),
        )
    }.sortedByDescending { it.file.parentFile?.lastModified() ?: it.file.lastModified() }

    fun delete(entry: CacheEntry): Boolean = entry.file.parentFile?.deleteRecursively() == true
    fun cacheSize(context: Context): Long = cacheRoot(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun cachedFile(context: Context, sourceUrl: String, cacheKey: String = ""): File? {
        val directory = File(cacheRoot(context), sourceKey(cacheKey.ifBlank { sourceUrl }))
        val properties = Properties()
        File(directory, METADATA).takeIf { it.isFile }?.inputStream()?.use(properties::load)
        if (properties.getProperty("complete") != "true") return null
        return directory.listFiles().orEmpty().firstOrNull { it.isFile && it.name != METADATA && it.length() > 0L }
    }

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
        var index = 0
        var downloadedBytes = 0L
        val rewritten = mediaPlaylist.lines().joinToString("\n") { line ->
            val value = line.trim()
            if (value.isEmpty() || value.startsWith("#")) line else {
                val segmentUrl = mediaBase.resolve(value).toString()
                val target = File(segmentDirectory, "segment-${index++}${extensionFor(segmentUrl, "")}")
                if (!target.isFile || target.length() == 0L) {
                    val partial = File(target.parentFile, "${target.name}.part")
                    downloadedBytes += downloadBinary(segmentUrl, partial, referer)
                    check(partial.renameTo(target) || target.isFile) { "保存分片失败" }
                } else {
                    downloadedBytes += target.length()
                }
                onProgress(downloadedBytes, 0L)
                "segments/${target.name}"
            }
        }
        return File(directory, "media.m3u8").apply { writeText(rewritten) }
    }

    private fun fetchText(url: String, referer: String): String = AppModule.apiClient.openMediaResponse(url, referer).use { response ->
        check(response.isSuccessful) { "下载清单失败：HTTP ${response.code}" }
        response.body?.string() ?: error("清单为空")
    }

    private fun downloadBinary(url: String, target: File, referer: String): Long {
        AppModule.apiClient.openMediaResponse(url, referer).use { response ->
            check(response.isSuccessful) { "下载分片失败：HTTP ${response.code}" }
            response.body?.byteStream()?.use { input -> target.outputStream().use { output -> input.copyTo(output) } } ?: error("分片内容为空")
        }
        return target.length()
    }

    private fun sourceKey(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private const val METADATA = "metadata.properties"
    private fun cacheRoot(context: Context): File = File(context.filesDir, "offline-media")
    fun resourceKey(dir: String, id: String, lineId: String, episode: Int): String = "play/$dir/$id/$lineId/$episode"
    private fun writeMetadata(context: Context, sourceUrl: String, referer: String, cacheKey: String, detailRoute: String, label: String, complete: Boolean, state: String, downloaded: Long, total: Long) {
        val properties = Properties().apply {
            setProperty("url", sourceUrl); setProperty("referer", referer); setProperty("cacheKey", cacheKey)
            setProperty("detailRoute", detailRoute); setProperty("label", label); setProperty("complete", complete.toString())
            setProperty("state", state); setProperty("downloaded", downloaded.toString()); setProperty("total", total.toString())
        }
        File(cacheRoot(context), sourceKey(cacheKey.ifBlank { sourceUrl })).apply { mkdirs() }.resolve(METADATA).outputStream().use { output -> properties.store(output, null) }
    }

    private fun outputBytes(file: File): Long = file.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: file.length()

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
