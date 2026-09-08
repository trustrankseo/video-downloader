from pathlib import Path

# v1.4.5: YouTube channel fetch reliability.
# A channel root now discovers Videos + Shorts + Streams instead of only /videos.
# Tracking params such as ?si= are removed from channel share URLs before tab discovery.
g = Path('app/build.gradle.kts')
s = g.read_text()
s = s.replace('versionCode = 28', 'versionCode = 30')
s = s.replace('versionCode = 29', 'versionCode = 30')
s = s.replace('versionName = "1.4.3"', 'versionName = "1.4.5"')
s = s.replace('versionName = "1.4.4"', 'versionName = "1.4.5"')
g.write_text(s)

# Make YouTube channel/handle discovery deterministic and accept flat-playlist IDs.
e = Path('app/src/main/java/com/faisal/freshdownloader/DownloaderEngine.kt')
t = e.read_text()

old = '''        val extractorAttempt = withContext(Dispatchers.IO) {
            runCatching { discoverWithYtDlp(targetUrl) }
        }
'''
new = '''        val discoveryUrls = youtubeCollectionCandidates(targetUrl)
        val extractorAttempt = withContext(Dispatchers.IO) {
            runCatching {
                val combined = linkedSetOf<String>()
                var firstFailure: Throwable? = null
                for (candidate in discoveryUrls) {
                    if (cancelRequested) throw CancellationException("Cancelled")
                    try {
                        combined += discoverWithYtDlp(candidate)
                    } catch (failure: Throwable) {
                        if (firstFailure == null) firstFailure = failure
                    }
                    if (combined.size >= 500) break
                }
                if (combined.isEmpty() && firstFailure != null) throw firstFailure
                combined.take(500)
            }
        }
'''
if old not in t:
    # Older v1.4.4 patch may already have the single discoveryUrl form.
    old = '''        val discoveryUrl = normalizeYouTubeCollectionUrl(targetUrl)
        val extractorAttempt = withContext(Dispatchers.IO) {
            runCatching { discoverWithYtDlp(discoveryUrl) }
        }
'''
if old not in t:
    raise SystemExit('discoverCollection extractor anchor not found')
t = t.replace(old, new, 1)

old = '        request.addOption("--print", "%(webpage_url)s")\n'
new = '        request.addOption("--print", if (isYouTubeUrl(url)) "%(id)s" else "%(webpage_url)s")\n'
if old in t:
    t = t.replace(old, new, 1)

old = '''            response.out
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .distinct()
                .take(500)
                .toList()
'''
new = '''            val youtube = isYouTubeUrl(url)
            val youtubeId = Regex("^[A-Za-z0-9_-]{11}$")
            response.out
                .lineSequence()
                .map { it.trim() }
                .mapNotNull { value ->
                    when {
                        value.startsWith("http://") || value.startsWith("https://") -> value
                        youtube && youtubeId.matches(value) -> "https://www.youtube.com/watch?v=$value"
                        else -> null
                    }
                }
                .distinct()
                .take(500)
                .toList()
'''
if old in t:
    t = t.replace(old, new, 1)

anchor = '''    private fun isFacebookUrl(url: String): Boolean {
'''
helper = '''    private fun isYouTubeUrl(url: String): Boolean {
        val lower = normalizeInputUrl(url).lowercase()
        return "youtube.com" in lower || "youtu.be" in lower
    }

    private fun youtubeCollectionCandidates(url: String): List<String> {
        if (!isYouTubeUrl(url)) return listOf(url)

        val normalized = normalizeInputUrl(url)
        val parsed = runCatching { URL(normalized) }.getOrNull() ?: return listOf(normalized)
        val host = parsed.host.lowercase()
        val path = parsed.path.trim('/')
        val lower = path.lowercase()

        // Playlists are already complete collections and should not be rewritten.
        if (lower == "playlist" || parsed.query.orEmpty().contains("list=")) {
            return listOf(normalized)
        }

        // A normal video/Short URL is a single item, not a channel root.
        if (host == "youtu.be" || lower.startsWith("watch") || lower.startsWith("shorts/")) {
            return listOf(normalized)
        }

        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return listOf(normalized)

        val isChannel = parts.first().startsWith("@") ||
            parts.first().lowercase() in setOf("channel", "c", "user")
        if (!isChannel) return listOf(normalized)

        // Strip share/tracking parameters (for example ?si=...) from channel URLs.
        // Also remove an existing tab suffix so the app can merge all public tabs.
        val baseParts = if (parts.last().lowercase() in setOf("videos", "shorts", "streams", "featured")) {
            parts.dropLast(1)
        } else {
            parts
        }
        val basePath = baseParts.joinToString("/")
        val base = "${parsed.protocol}://${parsed.host}/$basePath"

        return listOf(
            "$base/videos",
            "$base/shorts",
            "$base/streams"
        )
    }

'''
if 'private fun youtubeCollectionCandidates(' not in t:
    if anchor not in t:
        raise SystemExit('YouTube helper insertion anchor not found')
    t = t.replace(anchor, helper + anchor, 1)
else:
    # If an older helper exists, replace the helper block up to isFacebookUrl.
    start = t.index('    private fun isYouTubeUrl(url: String): Boolean {')
    end = t.index(anchor, start)
    t = t[:start] + helper + t[end:]

e.write_text(t)

# Make channel discovery visibly active instead of showing the misleading
# "Ready to download" empty-state card while yt-dlp is still fetching entries.
p = Path('app/src/main/java/com/faisal/freshdownloader/MainActivity.kt')
u = p.read_text()
old = '''            if (ui.tasks.isEmpty()) {
                item { EmptyState(vm.outputPath()) }
            } else {
                items(ui.tasks, key = { it.id }) { task -> TaskCard(task) }
            }
'''
new = '''            if (ui.tasks.isEmpty()) {
                item {
                    if (ui.running) DiscoveryState(ui.statusLine) else EmptyState(vm.outputPath())
                }
            } else {
                items(ui.tasks, key = { it.id }) { task -> TaskCard(task) }
            }
'''
if old in u:
    u = u.replace(old, new, 1)

if 'private fun DiscoveryState(' not in u:
    u += '''

@Composable
private fun DiscoveryState(status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = InkSoft),
        border = BorderStroke(1.dp, Blue.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = Blue
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Fetching channel videos + Shorts…", fontWeight = FontWeight.ExtraBold)
                Text(
                    status.ifBlank { "Reading public channel tabs" },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
'''

p.write_text(u)
