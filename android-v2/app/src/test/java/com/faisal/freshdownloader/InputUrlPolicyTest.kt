package com.faisal.freshdownloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputUrlPolicyTest {
    @Test fun keepsHttpsUrl() =
        assertEquals("https://example.com/v/1", InputUrlPolicy.normalize(" https://example.com/v/1 "))

    @Test fun addsHttpsToWwwUrl() =
        assertEquals("https://www.example.com/v", InputUrlPolicy.normalize("www.example.com/v"))

    @Test fun addsHttpsToKnownMediaHosts() {
        assertEquals("https://youtu.be/abcdefghijk", InputUrlPolicy.normalize("youtu.be/abcdefghijk"))
        assertEquals("https://facebook.com/reel/123", InputUrlPolicy.normalize("facebook.com/reel/123"))
        assertEquals("https://vm.tiktok.com/abc", InputUrlPolicy.normalize("vm.tiktok.com/abc"))
    }

    @Test fun addsHttpsToBareDomain() =
        assertEquals("https://example.com/path", InputUrlPolicy.normalize("example.com/path"))

    @Test fun acceptsHttpAndHttps() {
        assertTrue(InputUrlPolicy.isValidWebUrl("http://example.com/a"))
        assertTrue(InputUrlPolicy.isValidWebUrl("https://example.com/a"))
    }

    @Test fun acceptsNormalizedBareLinks() {
        assertTrue(InputUrlPolicy.isValidWebUrl("youtu.be/abcdefghijk"))
        assertTrue(InputUrlPolicy.isValidWebUrl("example.com/video"))
    }

    @Test fun rejectsUnsupportedSchemesAndGarbage() {
        assertFalse(InputUrlPolicy.isValidWebUrl("ftp://example.com/file"))
        assertFalse(InputUrlPolicy.isValidWebUrl("javascript:alert(1)"))
        assertFalse(InputUrlPolicy.isValidWebUrl("not a url"))
        assertFalse(InputUrlPolicy.isValidWebUrl(""))
    }

    @Test fun rejectsUrlsWithoutHost() {
        assertFalse(InputUrlPolicy.isValidWebUrl("https:///missing-host"))
        assertFalse(InputUrlPolicy.isValidWebUrl("http://"))
    }

    @Test fun blocksKnownPaidDrmSubscriptionServices() {
        val blocked = listOf(
            "https://netflix.com/title/123",
            "https://www.disneyplus.com/video/abc",
            "https://primevideo.com/detail/xyz",
            "https://tv.apple.com/movie/example",
            "https://spotify.com/track/123",
            "https://patreon.com/posts/paid-video"
        )

        blocked.forEach { url ->
            assertTrue("Expected paid/DRM URL to be blocked: $url", InputUrlPolicy.isBlockedPaidDrmUrl(url))
            assertFalse("Blocked URL must not be allowed: $url", InputUrlPolicy.isAllowedDownloadUrl(url))
            assertTrue(InputUrlPolicy.paidDrmBlockReason(url)?.contains("DRM", ignoreCase = true) == true)
        }
    }

    @Test fun blocksPaidPathsWithoutBlockingWholeGeneralDomains() {
        assertTrue(InputUrlPolicy.isBlockedPaidDrmUrl("https://amazon.com/gp/video/detail/B123"))
        assertTrue(InputUrlPolicy.isBlockedPaidDrmUrl("https://amazon.co.uk/Prime-Video/movie/123"))
        assertTrue(InputUrlPolicy.isBlockedPaidDrmUrl("https://vimeo.com/ondemand/movie123"))
        assertTrue(InputUrlPolicy.isBlockedPaidDrmUrl("https://play.google.com/store/movies/details/Movie?id=123"))

        assertFalse(InputUrlPolicy.isBlockedPaidDrmUrl("https://amazon.com/dp/B000TEST"))
        assertFalse(InputUrlPolicy.isBlockedPaidDrmUrl("https://vimeo.com/123456789"))
    }

    @Test fun keepsPublicDownloaderCoreAllowed() {
        val allowed = listOf(
            "https://youtube.com/watch?v=abcdefghijk",
            "https://youtube.com/@creator",
            "https://facebook.com/reel/123",
            "https://instagram.com/reel/ABC123/",
            "https://tiktok.com/@creator/video/123456",
            "https://example.com/public-video.mp4"
        )

        allowed.forEach { url ->
            assertTrue("Expected public URL to remain valid: $url", InputUrlPolicy.isValidWebUrl(url))
            assertFalse("Public URL must not be paid/DRM blocked: $url", InputUrlPolicy.isBlockedPaidDrmUrl(url))
            assertTrue("Public URL must remain allowed: $url", InputUrlPolicy.isAllowedDownloadUrl(url))
        }
    }

    @Test fun blocksPaidServiceSubdomainsToo() {
        assertTrue(InputUrlPolicy.isBlockedPaidDrmUrl("https://watch.netflix.com/title/123"))
        assertTrue(InputUrlPolicy.isBlockedPaidDrmUrl("https://app.hulu.com/watch/123"))
    }

    @Test fun identifiesCollectionLinksWithoutBlockingDirectMedia() {
        assertTrue(InputUrlPolicy.isLikelyCollectionUrl("https://youtube.com/@babyrainbowhi"))
        assertTrue(InputUrlPolicy.isLikelyCollectionUrl("https://youtube.com/channel/UC123"))
        assertTrue(InputUrlPolicy.isLikelyCollectionUrl("https://youtube.com/playlist?list=PL123"))
        assertTrue(InputUrlPolicy.isLikelyCollectionUrl("https://tiktok.com/@creator"))
        assertFalse(InputUrlPolicy.isLikelyCollectionUrl("https://youtube.com/watch?v=abcdefghijk"))
        assertFalse(InputUrlPolicy.isLikelyCollectionUrl("https://youtube.com/shorts/abcdefghijk"))
        assertFalse(InputUrlPolicy.isLikelyCollectionUrl("https://tiktok.com/@creator/video/123456"))
        assertFalse(InputUrlPolicy.isLikelyCollectionUrl("https://instagram.com/reel/ABC123/"))
    }
}
