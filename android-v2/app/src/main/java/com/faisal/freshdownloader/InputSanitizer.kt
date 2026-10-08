package com.faisal.freshdownloader

/**
 * Pure input normalization used by both UI and downloader engine.
 *
 * Keeping this logic Android-free makes the highest-risk URL edge cases
 * deterministic and unit-testable.
 */
object InputSanitizer {
    fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        return when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.startsWith("://") -> "https$value"
            value.startsWith("//") -> "https:$value"
            value.startsWith("www.", true) ||
                value.startsWith("facebook.com", true) ||
                value.startsWith("instagram.com", true) ||
                value.startsWith("tiktok.com", true) ||
                value.startsWith("vm.tiktok.com", true) ||
                value.startsWith("vt.tiktok.com", true) ||
                value.startsWith("youtube.com", true) ||
                value.startsWith("youtu.be", true) -> "https://$value"
            else -> value
        }
    }

    fun isHttpUrl(raw: String): Boolean {
        val normalized = normalizeUrl(raw)
        return normalized.startsWith("https://", true) || normalized.startsWith("http://", true)
    }

    fun distinctHttpUrls(raw: String): List<String> =
        raw.lineSequence()
            .map(::normalizeUrl)
            .filter(::isHttpUrl)
            .distinct()
            .toList()
}
