package com.filmvault.app.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.filmvault.app.di.AppModule
import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * 播放直链的应用私有离线缓存。
 *
 * MP4 等渐进式媒体直接保存；未加密 HLS 会把媒体清单和分片保存到同一缓存目录，
 * 生成可由 ExoPlayer 离线读取的本地 m3u8。缓存不写入公共存储，也不改变站点数据。
 */
object OfflineMediaStore {
    suspend fun download(context: Context, sourceUrl: String, referer: String = AppModule.siteSettings.siteUrlNow): File = withContext(Dispatchers.IO) {
        require(sourceUrl.isNotBlank()) { "播放地址为空" }
        val key = sourceKey(sourceUrl)
        val existing = cachedFile(context, sourceUrl)
        if (existing != null) return@withContext existing

        val directory = File(context.filesDir, "offline-media/$key").apply { mkdirs() }
        try {
            val response = AppModule.apiClient.openMediaResponse(sourceUrl, referer)
            response.use {
                check(it.isSuccessful) { "下载失败：HTTP ${it.code}" }
                val body = it.body ?: error("下载内容为空")
                val contentType = body.contentType()?.toString().orEmpty()
                check(!contentType.contains("text/html", true)) { "播放地址已失效或被站点拒绝" }
                check(!sourceUrl.substringBefore('?').endsWith(".mpd", true) && !contentType.contains("dash", true)) {
                    "DASH 视频暂不支持离线缓存"
                }
                val looksLikeHls = sourceUrl.substringBefore('?').endsWith(".m3u8", true) ||
                    contentType.contains("mpegurl", true)
                if (looksLikeHls) {
                    downloadHls(sourceUrl, body.string(), directory, referer)
                } else {
                    val output = File(directory, "media${extensionFor(sourceUrl, contentType)}")
                    body.byteStream().use { input -> output.outputStream().use { outputStream -> input.copyTo(outputStream) } }
                    output
                }
            }
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    fun cachedUri(context: Context, sourceUrl: String): Uri? = cachedFile(context, sourceUrl)?.let(Uri::fromFile)

    private fun cachedFile(context: Context, sourceUrl: String): File? {
        val directory = File(context.filesDir, "offline-media/${sourceKey(sourceUrl)}")
        return directory.listFiles()?.firstOrNull { it.isFile && it.length() > 0L }
    }

    private fun downloadHls(sourceUrl: String, playlist: String, directory: File, referer: String): File {
        check(!playlist.contains("#EXT-X-KEY")) { "加密视频暂不支持离线缓存" }
        var mediaBase = URI(sourceUrl)
        val mediaPlaylist = if (playlist.lines().any { it.startsWith("#EXT-X-STREAM-INF") }) {
            val variant = playlist.lines().mapIndexedNotNull { index, line ->
                if (line.startsWith("#EXT-X-STREAM-INF")) playlist.lines().getOrNull(index + 1)?.trim()?.takeIf { it.isNotEmpty() } else null
            }.lastOrNull() ?: error("HLS 清单无可用线路")
            val variantUrl = mediaBase.resolve(variant).toString()
            mediaBase = URI(variantUrl)
            fetchText(variantUrl, referer)
        } else playlist
        check(!mediaPlaylist.contains("#EXT-X-KEY")) { "加密视频暂不支持离线缓存" }

        val segmentDirectory = File(directory, "segments").apply { mkdirs() }
        var segmentIndex = 0
        val rewritten = mediaPlaylist.lines().joinToString("\n") { line ->
            val value = line.trim()
            when {
                value.isEmpty() || value.startsWith("#") -> line
                else -> {
                    val segmentUrl = mediaBase.resolve(value).toString()
                    val segmentFile = File(segmentDirectory, "segment-${segmentIndex++}${extensionFor(segmentUrl, "")}")
                    downloadBinary(segmentUrl, segmentFile, referer)
                    "segments/${segmentFile.name}"
                }
            }
        }
        return File(directory, "media.m3u8").apply { writeText(rewritten) }
    }

    private fun fetchText(url: String, referer: String): String {
        AppModule.apiClient.openMediaResponse(url, referer).use { response ->
            check(response.isSuccessful) { "下载清单失败：HTTP ${response.code}" }
            return response.body?.string() ?: error("清单为空")
        }
    }

    private fun downloadBinary(url: String, target: File, referer: String) {
        AppModule.apiClient.openMediaResponse(url, referer).use { response ->
            check(response.isSuccessful) { "下载分片失败：HTTP ${response.code}" }
            response.body?.byteStream()?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                ?: error("分片内容为空")
        }
    }

    private fun sourceKey(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

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
        else -> url.substringBefore('?').substringAfterLast('.', "")
            .lowercase()
            .takeIf { it in setOf("mp4", "m4v", "webm", "mkv", "mov", "avi", "ts", "flv", "mp3", "m4a") }
            ?.let { ".${it}" }
            ?: ".mp4"
    }
}
