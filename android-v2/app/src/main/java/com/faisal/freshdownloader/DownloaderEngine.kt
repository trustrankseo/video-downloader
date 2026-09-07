package com.faisal.freshdownloader

import android.content.Context
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DownloaderEngine(private val context: Context) {

    private val outputDir: File by lazy {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "FreshDownloader"
        ).apply { mkdirs() }
    }

    fun outputPath(): String = outputDir.absolutePath

    suspend fun download(
        url: String,
        format: FormatPreset,
        onProgress: (Float, Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--newline")
            request.addOption("--restrict-filenames")
            request.addOption("--retries", "5")
            request.addOption("--fragment-retries", "5")
            request.addOption("-o", File(outputDir, "%(title)s [%(id)s].%(ext)s").absolutePath)

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
            val response = YoutubeDL.getInstance().execute(
                request,
                processId
            ) { progress, eta, _ ->
                onProgress(progress, eta)
            }
            response.out
        }
    }

    /**
     * Discover entries from any collection URL supported by yt-dlp: YouTube
     * channel/playlist and public profile/feed URLs on supported sites.
     * No paid API, cookies, or account credentials are supplied.
     */
    suspend fun discoverCollection(url: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("--skip-download")
            request.addOption("--no-warnings")
            request.addOption("--print", "%(webpage_url)s")

            val response = YoutubeDL.getInstance().execute(request)
            response.out
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .distinct()
                .toList()
        }
    }

    suspend fun updateEngineStable(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            YoutubeDL.getInstance().updateYoutubeDL(context)
            "yt-dlp updated"
        }
    }
}
