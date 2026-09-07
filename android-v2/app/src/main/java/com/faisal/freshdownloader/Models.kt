package com.faisal.freshdownloader

enum class DownloadMode { SINGLE, BULK, COLLECTION }
enum class DownloadStatus { QUEUED, DISCOVERING, DOWNLOADING, COMPLETE, FAILED }
enum class FormatPreset { VIDEO_MP4, AUDIO_MP3 }

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String = url,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val message: String = "Queued"
)

data class UiState(
    val engineReady: Boolean = true,
    val running: Boolean = false,
    val statusLine: String = "Ready",
    val tasks: List<DownloadTask> = emptyList(),
    val discoveredCount: Int = 0
)
