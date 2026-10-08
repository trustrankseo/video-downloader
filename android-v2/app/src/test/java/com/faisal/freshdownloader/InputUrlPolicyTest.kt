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
}
