package com.faisal.freshdownloader

import java.net.URI

object InputUrlPolicy {
    private const val PAID_DRM_MESSAGE =
        "Paid, subscription, rental, purchase, or DRM-protected media services are not supported."

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

    fun paidDrmBlockReason(raw: String): String? {
        val normalized = normalize(raw)
        if (!isValidWebUrl(normalized)) return null

        return runCatching {
            val uri = URI(normalized)
            val host = uri.host.orEmpty().lowercase().trimEnd('.').removePrefix("www.")
            val path = uri.path.orEmpty().lowercase()

            when {
                PAID_DRM_HOSTS.any { hostMatches(host, it) } -> PAID_DRM_MESSAGE
                isAmazonPaidVideoPath(host, path) -> PAID_DRM_MESSAGE
                hostMatches(host, "vimeo.com") && path.startsWith("/ondemand/") -> PAID_DRM_MESSAGE
                hostMatches(host, "play.google.com") &&
                    (path.startsWith("/store/movies") || path.startsWith("/store/tv")) -> PAID_DRM_MESSAGE
                else -> null
            }
        }.getOrNull()
    }

    fun isBlockedPaidDrmUrl(raw: String): Boolean = paidDrmBlockReason(raw) != null

    fun isAllowedDownloadUrl(raw: String): Boolean =
        isValidWebUrl(raw) && !isBlockedPaidDrmUrl(raw)

    fun isLikelyCollectionUrl(raw: String): Boolean {
        val normalized = normalize(raw)
        return runCatching {
            val uri = URI(normalized)
            val host = uri.host.orEmpty().lowercase().removePrefix("www.")
            val path = uri.path.orEmpty().trim('/').lowercase()
            val first = path.substringBefore('/')

            when {
                host == "youtube.com" || host.endsWith(".youtube.com") -> {
                    path == "playlist" ||
                        first.startsWith("@") ||
                        first in setOf("channel", "c", "user")
                }
                host == "instagram.com" || host.endsWith(".instagram.com") -> {
                    path.isNotBlank() &&
                        !path.contains('/') &&
                        first !in setOf("reel", "p", "stories", "explore", "tv")
                }
                host == "tiktok.com" || host.endsWith(".tiktok.com") -> {
                    first.startsWith("@") && !path.contains("/video/")
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun hostMatches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

    private fun isAmazonPaidVideoPath(host: String, path: String): Boolean {
        val amazonHost = AMAZON_STORE_HOSTS.any { hostMatches(host, it) }
        if (!amazonHost) return false

        return path.startsWith("/gp/video") ||
            path.startsWith("/video/detail") ||
            path.contains("/primevideo") ||
            path.contains("/amazon-video")
    }

    private val PAID_DRM_HOSTS = setOf(
        "netflix.com",
        "disneyplus.com",
        "hulu.com",
        "max.com",
        "hbomax.com",
        "primevideo.com",
        "tv.apple.com",
        "peacocktv.com",
        "paramountplus.com",
        "discoveryplus.com",
        "crunchyroll.com",
        "dazn.com",
        "fubo.tv",
        "sling.com",
        "starz.com",
        "showtime.com",
        "britbox.com",
        "mubi.com",
        "criterionchannel.com",
        "amcplus.com",
        "shudder.com",
        "nowtv.com",
        "skyshowtime.com",
        "canalplus.com",
        "stan.com.au",
        "binge.com.au",
        "foxtel.com.au",
        "viaplay.com",
        "rakuten.tv",
        "curiositystream.com",
        "nebula.tv",
        "dropout.tv",
        "moviesanywhere.com",
        "athome.fandango.com",
        "vudu.com",
        "spotify.com",
        "music.apple.com",
        "tidal.com",
        "deezer.com",
        "audible.com",
        "patreon.com",
        "onlyfans.com"
    )

    private val AMAZON_STORE_HOSTS = setOf(
        "amazon.com",
        "amazon.co.uk",
        "amazon.de",
        "amazon.fr",
        "amazon.it",
        "amazon.es",
        "amazon.ca",
        "amazon.com.au",
        "amazon.co.jp",
        "amazon.in",
        "amazon.com.br",
        "amazon.com.mx"
    )

    private val BARE_DOMAIN =
        Regex("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(?::\\d{1,5})?(?:/[^\\s]*)?$")
}
