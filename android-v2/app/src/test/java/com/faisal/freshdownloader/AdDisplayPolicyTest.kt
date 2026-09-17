package com.faisal.freshdownloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdDisplayPolicyTest {
    @Test
    fun firstSuccessfulOperationIsEligible() {
        val policy = AdDisplayPolicy(cooldownMs = 90_000L)
        assertTrue(policy.canShow(nowMs = 100_000L, lastDisplayedAtMs = 0L, operationSucceeded = true, downloadRunning = false))
    }

    @Test
    fun failedOperationIsNotEligible() {
        val policy = AdDisplayPolicy(cooldownMs = 90_000L)
        assertFalse(policy.canShow(nowMs = 100_000L, lastDisplayedAtMs = 0L, operationSucceeded = false, downloadRunning = false))
    }

    @Test
    fun activeDownloadIsNotEligible() {
        val policy = AdDisplayPolicy(cooldownMs = 90_000L)
        assertFalse(policy.canShow(nowMs = 100_000L, lastDisplayedAtMs = 0L, operationSucceeded = true, downloadRunning = true))
    }

    @Test
    fun cooldownBlocksInterstitialUntilNinetySecondsHavePassed() {
        val policy = AdDisplayPolicy(cooldownMs = 90_000L)
        assertFalse(policy.canShow(nowMs = 189_999L, lastDisplayedAtMs = 100_000L, operationSucceeded = true, downloadRunning = false))
        assertTrue(policy.canShow(nowMs = 190_000L, lastDisplayedAtMs = 100_000L, operationSucceeded = true, downloadRunning = false))
    }
}
