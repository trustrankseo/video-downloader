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
    val value = url.lowercase()
    return when {
        "youtube.com" in value || "youtu.be" in value -> "YouTube"
        "facebook.com" in value || "fb.watch" in value -> "Facebook"
        "instagram.com" in value -> "Instagram"
        "tiktok.com" in value -> "TikTok"
        "twitter.com" in value || "x.com" in value -> "X / Twitter"
        "reddit.com" in value || "redd.it" in value -> "Reddit"
        "vimeo.com" in value -> "Vimeo"
        "dailymotion.com" in value || "dai.ly" in value -> "Dailymotion"
        "twitch.tv" in value -> "Twitch"
        "soundcloud.com" in value -> "SoundCloud"
        "rednote.com" in value || "xiaohongshu.com" in value || "xhslink.com" in value -> "RedNote"
        "pinterest.com" in value || "pin.it" in value -> "Pinterest"
        "bilibili.com" in value || "b23.tv" in value -> "Bilibili"
        else -> "Web"
    }
}
