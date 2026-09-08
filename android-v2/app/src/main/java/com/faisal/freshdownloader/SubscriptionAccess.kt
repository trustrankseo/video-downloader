package com.faisal.freshdownloader

import android.content.Context

class SubscriptionAccess(context: Context) {
    companion object {
        const val FREE_TRIAL_LIMIT = 3
        const val MAX_REFERRAL_BONUS_TRIALS = 10
        private const val PREFS = "universal_downloader_access"
        private const val KEY_USED = "premium_trials_used"
        private const val KEY_REFERRAL_BONUS = "referral_bonus_trials"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun trialsUsed(): Int = prefs.getInt(KEY_USED, 0).coerceAtLeast(0)

    fun referralBonusTrials(): Int = prefs.getInt(KEY_REFERRAL_BONUS, 0)
        .coerceIn(0, MAX_REFERRAL_BONUS_TRIALS)

    fun totalTrialEntitlement(): Int = FREE_TRIAL_LIMIT + referralBonusTrials()

    fun trialsRemaining(): Int = (totalTrialEntitlement() - trialsUsed()).coerceAtLeast(0)

    fun consumeTrial(): Boolean {
        val used = trialsUsed()
        if (used >= totalTrialEntitlement()) return false
        prefs.edit().putInt(KEY_USED, used + 1).apply()
        return true
    }

    fun addReferralBonusTrial(): Boolean {
        val current = referralBonusTrials()
        if (current >= MAX_REFERRAL_BONUS_TRIALS) return false
        prefs.edit().putInt(KEY_REFERRAL_BONUS, current + 1).apply()
        return true
    }
}
