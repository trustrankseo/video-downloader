package com.faisal.freshdownloader

import android.content.Context

class SubscriptionAccess(context: Context) {
    companion object {
        const val FREE_TRIAL_LIMIT = 3
        private const val PREFS = "universal_downloader_access"
        private const val KEY_USED = "premium_trials_used"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun trialsUsed(): Int = prefs.getInt(KEY_USED, 0).coerceIn(0, FREE_TRIAL_LIMIT)

    fun trialsRemaining(): Int = (FREE_TRIAL_LIMIT - trialsUsed()).coerceAtLeast(0)

    fun consumeTrial(): Boolean {
        val used = trialsUsed()
        if (used >= FREE_TRIAL_LIMIT) return false
        prefs.edit().putInt(KEY_USED, used + 1).apply()
        return true
    }
}
