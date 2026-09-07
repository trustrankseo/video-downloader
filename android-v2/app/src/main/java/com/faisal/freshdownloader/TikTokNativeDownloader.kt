package com.faisal.freshdownloader

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.CancellationException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * TikTok MP4 fallback that does not depend on yt-dlp browser impersonation.
 * It reuses the user's existing WebView session, reads TikTok's public page data,
 * resolves a direct media URL, and saves the MP4 with Android networking.
 */
class TikTokNativeDownloader(
    private val context: Context,
    private val outputDir: File,
    private val userAgent: String,
    private val isCancelled: () -> Boolean,
    private val onConnection: (HttpURLConnection?) -> Unit
) {
    fun download(videoUrl: String, onProgress: (Float, Long) -> Unit): String {
        if (isCancelled()) throw CancellationException("Cancelled")

        val page = fetchText(videoUrl)
        if (isCancelled()) throw CancellationException("Cancelled")

        val mediaUrl = extractMediaUrl(page)
            ?: throw IllegalStateException(
                "TIKTOK_MEDIA_URL_NOT_FOUND: TikTok page loaded, but no playable MP4 address was present in the returned page data."
            )

        val videoId = Regex("/video/(\\d+)", RegexOption.IGNORE_CASE)
            .find(videoUrl)?.groupValues?.getOrNull(1)
            ?: System.currentTimeMillis().toString()

        val finalFile = File(outputDir, "TikTok [$videoId].mp4")
        val partFile = File(outputDir, "TikTok [$videoId].mp4.part")
        if (partFile.exists()) partFile.delete()

        val connection = open(mediaUrl).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", videoUrl)
            setRequestProperty("Accept", "*/*")
            applyCookies(this, videoUrl)
        }
        onConnection(connection)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("TIKTOK_MEDIA_HTTP_$code: TikTok media server rejected the download request.")
            }

            val total = connection.contentLengthLong.coerceAtLeast(-1L)
            var copied = 0L
            connection.inputStream.use { input ->
                partFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        if (isCancelled()) throw CancellationException("Cancelled")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val progress = ((copied.toDouble() / total.toDouble()) * 100.0)
                                .coerceIn(0.0, 100.0)
                                .toFloat()
                            onProgress(progress, 0L)
                        }
                    }
                }
            }

            if (isCancelled()) throw CancellationException("Cancelled")
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            onProgress(100f, 0L)
            return finalFile.absolutePath
        } catch (e: Throwable) {
            if (partFile.exists()) partFile.delete()
            throw e
        } finally {
            connection.disconnect()
            onConnection(null)
        }
    }

    private fun fetchText(videoUrl: String): String {
        val connection = open(videoUrl).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", "https://www.tiktok.com/")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            applyCookies(this, videoUrl)
        }
        onConnection(connection)
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("TIKTOK_PAGE_HTTP_$code: TikTok rejected the video page request.")
            }
            connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                if (text.length > 12_000_000) text.take(12_000_000) else text
            }
        } finally {
            connection.disconnect()
            onConnection(null)
        }
    }

    private fun extractMediaUrl(raw: String): String? {
        if (raw.isBlank()) return null
        val html = raw
            .replace("&amp;", "&")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("\\u003A", ":", ignoreCase = true)
            .replace("\\u003D", "=", ignoreCase = true)
            .replace("\\/", "/")

        // TikTok currently uses more than one hydration schema. In newer web payloads
        // playAddr/downloadAddr may be arrays instead of a single string.
        val patterns = listOf(
            Regex("\\\"playAddr\\\"\\s*:\\s*\\[\\s*\\\"(https?://[^\\\"]+)\\\"", RegexOption.IGNORE_CASE),
            Regex("\\\"downloadAddr\\\"\\s*:\\s*\\[\\s*\\\"(https?://[^\\\"]+)\\\"", RegexOption.IGNORE_CASE),
            Regex("\\\"playAddr\\\"\\s*:\\s*\\\"(https?://[^\\\"]+)\\\"", RegexOption.IGNORE_CASE),
            Regex("\\\"downloadAddr\\\"\\s*:\\s*\\\"(https?://[^\\\"]+)\\\"", RegexOption.IGNORE_CASE),
            Regex("\\\"play_addr\\\"[\\s\\S]{0,2000}?\\\"url_list\\\"\\s*:\\s*\\[\\s*\\\"(https?://[^\\\"]+)\\\"", RegexOption.IGNORE_CASE),
            Regex("\\\"playAddr\\\"[\\s\\S]{0,2000}?\\\"urlList\\\"\\s*:\\s*\\[\\s*\\\"(https?://[^\\\"]+)\\\"", RegexOption.IGNORE_CASE),
            Regex("\\\"PlayAddr\\\"[\\s\\S]{0,2000}?\\\"UrlList\\\"\\s*:\\s*\\[\\s*\\\"(https?://[^\\\"]+)\\\"", RegexOption.IGNORE_CASE),
            Regex("\\\"playAddr\\\"[\\s\\S]{0,2000}?\\\"src\\\"\\s*:\\s*\\\"(https?://[^\\\"]+)\\\"", RegexOption.IGNORE_CASE),
            Regex("<video[^>]+src=[\\\"'](https?://[^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE),
            Regex("(https?://[^\\\"'<>\\s]+(?:mime_type=video_mp4|mime_type=video%2Fmp4)[^\\\"'<>\\s]*)", RegexOption.IGNORE_CASE),
            Regex("(https?://[^\\\"'<>\\s]*(?:tiktokcdn|byteoversea|ibytedtos)[^\\\"'<>\\s]*/video/[^\\\"'<>\\s]+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val candidate = pattern.find(html)?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank()) {
                val decoded = decodeUrl(candidate)
                if (decoded.startsWith("http://") || decoded.startsWith("https://")) return decoded
            }
        }
        return null
    }

    private fun decodeUrl(value: String): String = value
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003D", "=", ignoreCase = true)
        .replace("\\/", "/")
        .replace("&amp;", "&")

    private fun applyCookies(connection: HttpURLConnection, pageUrl: String) {
        val manager = CookieManager.getInstance()
        val cookie = runCatching {
            manager.getCookie(pageUrl) ?: manager.getCookie("https://www.tiktok.com/")
        }.getOrNull()
        if (!cookie.isNullOrBlank()) connection.setRequestProperty("Cookie", cookie)
    }

    private fun open(url: String): HttpURLConnection = URL(url).openConnection() as HttpURLConnection
}
