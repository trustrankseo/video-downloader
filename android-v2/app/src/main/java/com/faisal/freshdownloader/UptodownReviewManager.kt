package com.faisal.freshdownloader

import android.content.Context
import android.content.Intent
import android.net.Uri

class UptodownReviewManager(context: Context) {
    companion object {
        private const val PREFS = "uptodown_review_prompt"
        private const val KEY_SUCCESS_COUNT = "success_count"
        private const val KEY_NEVER_ASK = "never_ask"
        private const val KEY_REVIEWED_OR_OPENED = "reviewed_or_opened"
        private const val KEY_SNOOZE_UNTIL = "snooze_until"
        private const val MIN_SUCCESSFUL_DOWNLOADS = 3
        private const val SNOOZE_MS = 7L * 24L * 60L * 60L * 1000L
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordSuccessfulDownload() {
        val current = prefs.getInt(KEY_SUCCESS_COUNT, 0).coerceAtLeast(0)
        prefs.edit().putInt(KEY_SUCCESS_COUNT, current + 1).apply()
    }

    fun shouldPrompt(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (BuildConfig.UPTODOWN_APP_URL.isBlank()) return false
        if (prefs.getBoolean(KEY_NEVER_ASK, false)) return false
        if (prefs.getBoolean(KEY_REVIEWED_OR_OPENED, false)) return false
        if (prefs.getInt(KEY_SUCCESS_COUNT, 0) < MIN_SUCCESSFUL_DOWNLOADS) return false
        return nowMs >= prefs.getLong(KEY_SNOOZE_UNTIL, 0L)
    }

    fun remindLater(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_SNOOZE_UNTIL, nowMs + SNOOZE_MS).apply()
    }

    fun neverAskAgain() {
        prefs.edit().putBoolean(KEY_NEVER_ASK, true).apply()
    }

    fun openUptodownReview(context: Context): Boolean {
        val url = BuildConfig.UPTODOWN_APP_URL.trim()
        if (url.isBlank()) return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            prefs.edit().putBoolean(KEY_REVIEWED_OR_OPENED, true).apply()
            true
        }.getOrDefault(false)
    }
}
