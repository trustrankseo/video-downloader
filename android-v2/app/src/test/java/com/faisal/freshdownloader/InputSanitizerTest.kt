package com.faisal.freshdownloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSanitizerTest {
    @Test
    fun preservesHttpsUrl() {
        assertEquals("https://example.com/video", InputSanitizer.normalizeUrl(" https://example.com/video "))
    }

    @Test
    fun preservesHttpUrl() {
        assertEquals("http://example.com/video", InputSanitizer.normalizeUrl("http://example.com/video"))
    }

    @Test
    fun addsSchemeToWwwUrl() {
        assertEquals("https://www.example.com/video", InputSanitizer.normalizeUrl("www.example.com/video"))
    }

    @Test
    fun addsSchemeToKnownPlatformHost() {
        assertEquals("https://youtu.be/abcdefghijk", InputSanitizer.normalizeUrl("youtu.be/abcdefghijk"))
    }

    @Test
    fun expandsProtocolRelativeUrl() {
        assertEquals("https://example.com/video", InputSanitizer.normalizeUrl("//example.com/video"))
    }

    @Test
    fun repairsMissingSchemePrefix() {
        assertEquals("https://example.com/video", InputSanitizer.normalizeUrl("://example.com/video"))
    }

    @Test
    fun rejectsPlainTextAsHttpUrl() {
        assertFalse(InputSanitizer.isHttpUrl("not a url"))
    }

    @Test
    fun acceptsNormalizedPlatformUrl() {
        assertTrue(InputSanitizer.isHttpUrl("instagram.com/p/abc"))
    }

    @Test
    fun bulkInputNormalizesDeduplicatesAndDropsInvalidLines() {
        val raw = """
            youtube.com/watch?v=123
            https://youtube.com/watch?v=123
            not-a-url
            https://example.com/a
            https://example.com/a
        """.trimIndent()

        assertEquals(
            listOf(
                "https://youtube.com/watch?v=123",
                "https://example.com/a"
            ),
            InputSanitizer.distinctHttpUrls(raw)
        )
    }

    @Test
    fun bulkInputIgnoresBlankLines() {
        assertEquals(
            listOf("https://example.com/a"),
            InputSanitizer.distinctHttpUrls("\n  \nhttps://example.com/a\n")
        )
    }
}
