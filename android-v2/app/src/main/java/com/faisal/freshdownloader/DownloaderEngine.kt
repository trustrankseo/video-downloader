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
            "UniversalDownloader"
        ).apply { mkdirs() }
    }

    @Volatile
    private var activeProcessId: String? = null

    fun outputPath(): String = outputDir.absolutePath

    fun cancelActive(): Boolean {
        val processId = activeProcessId ?: return false
        return runCatching {
            YoutubeDL.getInstance().destroyProcessById(processId)
        }.getOrDefault(false)
    }

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
            activeProcessId = processId
            try {
                val response = YoutubeDL.getInstance().execute(
                    request,
                    processId
                ) { progress, eta, _ ->
                    onProgress(progress, eta)
                }
                response.out
            } finally {
                if (activeProcessId == processId) activeProcessId = null
            }
        }
    }

    suspend fun discoverCollection(url: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("--skip-download")
            request.addOption("--no-warnings")
            request.addOption("--print", "%(webpage_url)s")

            val processId = "discover-${UUID.randomUUID()}"
            activeProcessId = processId
            try {
                val response = YoutubeDL.getInstance().execute(request, processId)
                response.out
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("http://") || it.startsWith("https://") }
                    .distinct()
                    .toList()
            } finally {
                if (activeProcessId == processId) activeProcessId = null
            }
        }
    }

    suspend fun updateEngineStable(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            YoutubeDL.getInstance().updateYoutubeDL(context)
            "Downloader engine updated"
        }
    }
}
