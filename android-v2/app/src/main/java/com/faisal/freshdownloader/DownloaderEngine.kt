package com.faisal.freshdownloader

import android.content.Context
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Native-only download engine.
 *
 * This build intentionally avoids Android WebView/WebKit session extraction.
 * Collection discovery uses the downloader engine and lightweight public HTTP
 * fallbacks only. Direct public links remain available through Single/Bulk mode.
 */
class DownloaderEngine(private val context: Context) {

    private val outputDir: File by lazy {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "UniversalDownloader"
        ).apply { mkdirs() }
    }

    @Volatile private var activeProcessId: String? = null
    @Volatile private var activeConnection: HttpURLConnection? = null
    @Volatile private var cancelRequested = false

    private val browserUserAgent =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    fun outputPath(): String = outputDir.absolutePath

    fun cancelActive(): Boolean {
        cancelRequested = true

        val processId = activeProcessId
        val hadConnection = activeConnection != null
        runCatching { activeConnection?.disconnect() }
        activeConnection = null

        val processStopped = if (processId != null) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)
        } else false

        return processStopped || hadConnection
    }

    suspend fun download(
        url: String,
        format: FormatPreset,
        onProgress: (Float, Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        cancelRequested = false
        runCatching {
            val normalized = normalizeInputUrl(url)
            require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
                "Please enter a valid public http/https URL."
            }

            val targetUrl = if (shouldResolveRedirect(normalized)) {
                runCatching { resolveRedirectUrl(normalized) }.getOrDefault(normalized)
            } else normalized

            if (cancelRequested) throw CancellationException("Cancelled")

            val request = YoutubeDLRequest(targetUrl)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--newline")
            request.addOption("--restrict-filenames")
            request.addOption("--retries", "5")
            request.addOption("--fragment-retries", "5")
            request.addOption("-o", File(outputDir, "%(title)s [%(id)s].%(ext)s").absolutePath)
            applyPublicHeaders(request, targetUrl)

            when (format) {
                FormatPreset.VIDEO_MP4 -> {
                    request.addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b")
                    request.addOption("--merge-output-format", "mp4")
                }
                FormatPreset.AUDIO_MP3 -> {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                }
            }

            val processId = "dl-${UUID.randomUUID()}"
            activeProcessId = processId
            try {
                val response = YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                    onProgress(progress, eta)
                }
                response.out
            } finally {
                if (activeProcessId == processId) activeProcessId = null
            }
        }
    }

    suspend fun discoverCollection(url: String): Result<List<String>> {
        cancelRequested = false
        val normalized = normalizeInputUrl(url)
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Please enter a valid public collection URL."))
        }

        val targetUrl = if (shouldResolveRedirect(normalized)) {
            runCatching { resolveRedirectUrl(normalized) }.getOrDefault(normalized)
        } else normalized

        if (cancelRequested) return cancelledResult()

        val extractorAttempt = withContext(Dispatchers.IO) {
            runCatching { discoverWithYtDlp(targetUrl) }
        }
        if (cancelRequested) return cancelledResult()

        val extracted = extractorAttempt.getOrDefault(emptyList())
        if (extracted.isNotEmpty()) return Result.success(extracted)

        if (isFacebookUrl(targetUrl) || isFacebookUrl(normalized)) {
            val httpFallback = withContext(Dispatchers.IO) {
                runCatching { discoverFacebookPublicProfile(targetUrl) }.getOrDefault(emptyList())
            }
            if (cancelRequested) return cancelledResult()
            if (httpFallback.isNotEmpty()) return Result.success(httpFallback)

            return Result.failure(
                IllegalStateException(
                    "FACEBOOK_PUBLIC_PROFILE_UNAVAILABLE: Facebook did not expose public Page/Profile videos to the native extractor. " +
                        "Direct public reel/video links can still be used in Single or Bulk mode."
                )
            )
        }

        if (isInstagramProfileUrl(targetUrl)) {
            return Result.failure(
                IllegalStateException(
                    "INSTAGRAM_PUBLIC_PROFILE_UNAVAILABLE: This native-only build does not open an embedded sign-in browser. " +
                        "Use direct public Instagram post/reel links in Single or Bulk mode."
                )
            )
        }

        if (isTikTokProfileUrl(targetUrl)) {
            return Result.failure(
                IllegalStateException(
                    "TIKTOK_PUBLIC_PROFILE_UNAVAILABLE: TikTok profile discovery is not available without an embedded browser session. " +
                        "Use direct public TikTok video links in Single or Bulk mode when supported by the extractor."
                )
            )
        }

        if (isInstagramUrl(targetUrl) || isInstagramUrl(normalized)) {
            val detail = firstErrorLine(extractorAttempt.exceptionOrNull())
            return Result.failure(
                IllegalStateException(
                    "INSTAGRAM_ITEM_UNAVAILABLE: Instagram did not expose this public item to the native extractor." +
                        if (detail.isBlank()) "" else " ($detail)"
                )
            )
        }

        if (isTikTokUrl(targetUrl) || isTikTokUrl(normalized)) {
            val detail = firstErrorLine(extractorAttempt.exceptionOrNull())
            return Result.failure(
                IllegalStateException(
                    "TIKTOK_ITEM_UNAVAILABLE: TikTok did not expose this public item to the native extractor." +
                        if (detail.isBlank()) "" else " ($detail)"
                )
            )
        }

        return extractorAttempt
    }

    private fun cancelledResult(): Result<List<String>> =
        Result.failure(CancellationException("Cancelled"))

    private fun discoverWithYtDlp(url: String): List<String> {
        if (cancelRequested) throw CancellationException("Cancelled")

        val request = YoutubeDLRequest(url)
        request.addOption("--flat-playlist")
        request.addOption("--skip-download")
        request.addOption("--no-warnings")
        request.addOption("--print", "%(webpage_url)s")
        applyPublicHeaders(request, url)

        val processId = "discover-${UUID.randomUUID()}"
        activeProcessId = processId
        return try {
            val response = YoutubeDL.getInstance().execute(request, processId)
            response.out
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .distinct()
                .take(500)
                .toList()
        } finally {
            if (activeProcessId == processId) activeProcessId = null
        }
    }

    private fun applyPublicHeaders(request: YoutubeDLRequest, url: String) {
        when {
            isFacebookUrl(url) -> {
                request.addOption("--user-agent", browserUserAgent)
                request.addOption("--referer", "https://www.facebook.com/")
            }
            isInstagramUrl(url) -> {
                request.addOption("--user-agent", browserUserAgent)
                request.addOption("--referer", "https://www.instagram.com/")
            }
            isTikTokUrl(url) -> {
                request.addOption("--user-agent", browserUserAgent)
                request.addOption("--referer", "https://www.tiktok.com/")
            }
        }
    }

    private fun discoverFacebookPublicProfile(url: String): List<String> {
        val clean = normalizeInputUrl(url).substringBefore('#').substringBefore('?').trimEnd('/')
        val candidates = linkedSetOf(
            clean,
            "$clean?sk=reels_tab",
            "$clean/reels/",
            "$clean/videos/",
            clean.replace("www.facebook.com", "m.facebook.com") + "/reels/",
            clean.replace("www.facebook.com", "m.facebook.com") + "/videos/"
        )

        val found = linkedSetOf<String>()
        for (candidate in candidates) {
            if (cancelRequested) break
            val html = runCatching { fetchPublicHtml(candidate) }.getOrNull() ?: continue
            found += extractFacebookVideoUrls(html)
            if (found.size >= 300) break
        }
        return found.take(300)
    }

    private fun extractFacebookVideoUrls(rawHtml: String): List<String> {
        val html = rawHtml
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u003A", ":", ignoreCase = true)
            .replace("\\u003F", "?", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("\\/", "/")
            .replace("&amp;", "&")

        val out = linkedSetOf<String>()
        val absolutePatterns = listOf(
            Regex("https?://(?:www\\.|m\\.)?facebook\\.com/reel/\\d+[^\\\"'<>\\s]*", RegexOption.IGNORE_CASE),
            Regex("https?://(?:www\\.|m\\.)?facebook\\.com/[^\\\"'<>\\s]+/videos/\\d+[^\\\"'<>\\s]*", RegexOption.IGNORE_CASE),
            Regex("https?://(?:www\\.|m\\.)?facebook\\.com/watch/\\?v=\\d+[^\\\"'<>\\s]*", RegexOption.IGNORE_CASE)
        )
        absolutePatterns.forEach { regex ->
            regex.findAll(html).forEach { out += it.value.replace("m.facebook.com", "www.facebook.com") }
        }

        val relativePattern = Regex(
            "href=[\\\"']([^\\\"']*(?:/reel/\\d+|/videos/\\d+|/watch/\\?v=\\d+)[^\\\"']*)[\\\"']",
            RegexOption.IGNORE_CASE
        )
        relativePattern.findAll(html).forEach { match ->
            val href = match.groupValues[1]
            val resolved = runCatching { URL(URL("https://www.facebook.com/"), href).toString() }.getOrNull()
            if (resolved != null) out += resolved
        }

        val videoIdPatterns = listOf(
            Regex("\\\"video_id\\\"\\s*:\\s*\\\"?(\\d{8,})", RegexOption.IGNORE_CASE),
            Regex("\\\"videoId\\\"\\s*:\\s*\\\"?(\\d{8,})", RegexOption.IGNORE_CASE)
        )
        videoIdPatterns.forEach { regex ->
            regex.findAll(html).forEach { match ->
                out += "https://www.facebook.com/watch/?v=${match.groupValues[1]}"
            }
        }
        return out.distinct()
    }

    private fun resolveRedirectUrl(input: String): String {
        var current = normalizeInputUrl(input)
        repeat(6) {
            if (cancelRequested) throw CancellationException("Cancelled")
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", browserUserAgent)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            activeConnection = connection
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return current
                    current = URL(URL(current), location).toString()
                } else {
                    if (isFacebookUrl(current)) {
                        val html = runCatching {
                            connection.inputStream.bufferedReader().use { it.readText().take(600_000) }
                        }.getOrDefault("")
                        val canonical = extractCanonicalFacebookUrl(html)
                        if (!canonical.isNullOrBlank() && canonical != current) current = canonical
                    }
                    return current
                }
            } finally {
                connection.disconnect()
                if (activeConnection === connection) activeConnection = null
            }
        }
        return current
    }

    private fun extractCanonicalFacebookUrl(html: String): String? {
        if (html.isBlank()) return null
        val normalized = html.replace("&amp;", "&").replace("\\/", "/")
        val patterns = listOf(
            Regex("<meta[^>]+property=[\\\"']og:url[\\\"'][^>]+content=[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE),
            Regex("<link[^>]+rel=[\\\"']canonical[\\\"'][^>]+href=[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE),
            Regex("<meta[^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]+property=[\\\"']og:url[\\\"']", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val value = pattern.find(normalized)?.groupValues?.getOrNull(1)
            if (!value.isNullOrBlank() && isFacebookUrl(value)) return value
        }
        return null
    }

    private fun fetchPublicHtml(url: String): String {
        if (cancelRequested) throw CancellationException("Cancelled")
        val connection = (URL(normalizeInputUrl(url)).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", browserUserAgent)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        activeConnection = connection
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return ""
            connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                if (text.length > 5_000_000) text.take(5_000_000) else text
            }
        } finally {
            connection.disconnect()
            if (activeConnection === connection) activeConnection = null
        }
    }

    private fun shouldResolveRedirect(url: String): Boolean {
        val lower = normalizeInputUrl(url).lowercase()
        return (isFacebookUrl(lower) && "/share/" in lower) ||
            "vm.tiktok.com" in lower ||
            "vt.tiktok.com" in lower ||
            "tiktok.com/t/" in lower
    }

    private fun normalizeInputUrl(raw: String): String {
        val value = raw.trim()
        return when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.startsWith("://") -> "https$value"
            value.startsWith("//") -> "https:$value"
            value.startsWith("www.") ||
                value.startsWith("facebook.com", true) ||
                value.startsWith("instagram.com", true) ||
                value.startsWith("tiktok.com", true) ||
                value.startsWith("vm.tiktok.com", true) ||
                value.startsWith("vt.tiktok.com", true) -> "https://$value"
            else -> value
        }
    }

    private fun isFacebookUrl(url: String): Boolean {
        val lower = normalizeInputUrl(url).lowercase()
        return "facebook.com" in lower || "fb.watch" in lower
    }

    private fun isInstagramUrl(url: String): Boolean =
        "instagram.com" in normalizeInputUrl(url).lowercase()

    private fun isTikTokUrl(url: String): Boolean =
        "tiktok.com" in normalizeInputUrl(url).lowercase()

    private fun isInstagramProfileUrl(url: String): Boolean {
        if (!isInstagramUrl(url)) return false
        val path = runCatching { URL(normalizeInputUrl(url)).path.trim('/').lowercase() }.getOrDefault("")
        if (path.isBlank()) return false
        val first = path.substringBefore('/')
        return first !in setOf("reel", "p", "stories", "accounts", "explore", "direct", "tv")
    }

    private fun isTikTokProfileUrl(url: String): Boolean {
        if (!isTikTokUrl(url)) return false
        val path = runCatching { URL(normalizeInputUrl(url)).path.trim('/').lowercase() }.getOrDefault("")
        return path.startsWith("@") && !path.contains("/video/")
    }

    private fun firstErrorLine(t: Throwable?): String = t?.message.orEmpty()
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .take(160)

    suspend fun updateEngineStable(): Result<String> = withContext(Dispatchers.IO) {
        cancelRequested = false
        runCatching {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
            "Downloader engine updated"
        }
    }
}
