package com.faisal.freshdownloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdDisplayPolicyTest {
    private val policy = AdDisplayPolicy(cooldownMs = 90_000L)

    @Test
    fun successfulCompletedOperationCanShowWhenNoPreviousAd() {
        assertTrue(
            policy.canShow(
                nowMs = 100_000L,
                lastDisplayedAtMs = 0L,
                operationSucceeded = true,
                downloadRunning = false
            )
        )
    }

    @Test
    fun failedOrCancelledOperationCannotShow() {
        assertFalse(policy.canShow(100_000L, 0L, operationSucceeded = false, downloadRunning = false))
    }

    @Test
    fun activeDownloadCannotShow() {
        assertFalse(policy.canShow(100_000L, 0L, operationSucceeded = true, downloadRunning = true))
    }

    @Test
    fun ninetySecondCooldownIsEnforced() {
        assertFalse(policy.canShow(189_999L, 100_000L, operationSucceeded = true, downloadRunning = false))
        assertTrue(policy.canShow(190_000L, 100_000L, operationSucceeded = true, downloadRunning = false))
    }
}
