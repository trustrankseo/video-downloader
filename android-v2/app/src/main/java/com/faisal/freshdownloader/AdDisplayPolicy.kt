package com.faisal.freshdownloader

class AdDisplayPolicy(
    private val cooldownMs: Long = 90_000L
) {
    fun canShow(
        nowMs: Long,
        lastDisplayedAtMs: Long,
        operationSucceeded: Boolean,
        downloadRunning: Boolean
    ): Boolean {
        if (!operationSucceeded || downloadRunning) return false
        if (lastDisplayedAtMs <= 0L) return true
        return nowMs - lastDisplayedAtMs >= cooldownMs
    }
}
