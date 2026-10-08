package com.faisal.freshdownloader

import java.net.URI

object InputUrlPolicy {
    fun normalize(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""

        return when {
            value.startsWith("https://", ignoreCase = true) ||
                value.startsWith("http://", ignoreCase = true) -> value
            value.startsWith("://") -> "https$value"
            value.startsWith("//") -> "https:$value"
            value.startsWith("www.", ignoreCase = true) ||
                value.startsWith("facebook.com", ignoreCase = true) ||
                value.startsWith("fb.watch", ignoreCase = true) ||
                value.startsWith("instagram.com", ignoreCase = true) ||
                value.startsWith("youtube.com", ignoreCase = true) ||
                value.startsWith("youtu.be", ignoreCase = true) ||
                value.startsWith("tiktok.com", ignoreCase = true) ||
                value.startsWith("vm.tiktok.com", ignoreCase = true) ||
                value.startsWith("vt.tiktok.com", ignoreCase = true) -> "https://$value"
            BARE_DOMAIN.matches(value) -> "https://$value"
            else -> value
        }
    }

    fun isValidWebUrl(raw: String): Boolean {
        val normalized = normalize(raw)
        return runCatching {
            val uri = URI(normalized)
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
                !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    private val BARE_DOMAIN =
        Regex("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(?::\\d{1,5})?(?:/[^\\s]*)?$")
}
