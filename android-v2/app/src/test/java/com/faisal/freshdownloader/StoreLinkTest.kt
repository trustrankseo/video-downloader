package com.faisal.freshdownloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreLinkTest {
    @Test
    fun referralStoreUrlAlwaysTargetsUptodown() {
        val url = BuildConfig.UPTODOWN_APP_URL.trim().lowercase()

        assertTrue("Uptodown URL must not be blank", url.isNotBlank())
        assertTrue("Referral install URL must target Uptodown", "uptodown.com" in url)
        assertFalse("Play Store URL must never be used for referrals", "play.google.com" in url)
        assertFalse("market:// links must never be used for referrals", url.startsWith("market://"))
    }
}
