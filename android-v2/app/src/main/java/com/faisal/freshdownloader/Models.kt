package com.faisal.freshdownloader

enum class DownloadMode { SINGLE, BULK, COLLECTION }
enum class DownloadStatus { QUEUED, DISCOVERING, DOWNLOADING, COMPLETE, FAILED, CANCELLED }
enum class FormatPreset { VIDEO_MP4, AUDIO_MP3 }

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String = url,
    val platform: String = detectPlatform(url),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val message: String = "Queued"
)

data class UiState(
    val engineReady: Boolean = true,
    val running: Boolean = false,
    val statusLine: String = "Ready for public links",
    val tasks: List<DownloadTask> = emptyList(),
    val discoveredCount: Int = 0
)

fun detectPlatform(url: String): String {
    return if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
        "Public media"
    } else {
        "Supported link"
    }
}
