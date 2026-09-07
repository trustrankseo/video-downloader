package com.faisal.freshdownloader

import android.content.Context
import android.webkit.CookieManager
import java.io.File
import java.net.URL

object SocialCookieJar {
    fun create(context: Context, rootUrl: String): File? {
        val rawCookie = runCatching { CookieManager.getInstance().getCookie(rootUrl) }.getOrNull()
        if (rawCookie.isNullOrBlank()) return null

        val host = runCatching { URL(rootUrl).host.lowercase() }.getOrNull() ?: return null
        val domain = if (host.startsWith("www.")) "." + host.removePrefix("www.") else "." + host
        val safeName = host.replace(Regex("[^a-z0-9.-]"), "_")
        val file = File(context.filesDir, "session-$safeName.cookies.txt")
        val lines = mutableListOf("# Netscape HTTP Cookie File")

        rawCookie.split(';').forEach { part ->
            val pair = part.trim()
            val eq = pair.indexOf('=')
            if (eq <= 0) return@forEach
            val name = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            if (name.isNotBlank()) {
                lines += listOf(domain, "TRUE", "/", "TRUE", "0", name, value).joinToString("\t")
            }
        }
        if (lines.size <= 1) return null

        return runCatching {
            file.writeText(lines.joinToString("\n", postfix = "\n"))
            file
        }.getOrNull()
    }
}
