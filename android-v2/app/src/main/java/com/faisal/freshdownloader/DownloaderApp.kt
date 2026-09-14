package com.faisal.freshdownloader

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class DownloaderApp : Application() {
    @Volatile
    private var mediaEngineInitialized = false

    /** Initializes native media components only after the user accepts the privacy policy. */
    fun initializeMediaEngine(): Result<Unit> = synchronized(this) {
        if (mediaEngineInitialized) return@synchronized Result.success(Unit)
        runCatching {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            mediaEngineInitialized = true
        }
    }
}
