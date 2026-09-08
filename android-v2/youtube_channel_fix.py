from pathlib import Path

# v1.4.4: YouTube channel fetch reliability + visible discovery state.
g = Path('app/build.gradle.kts')
s = g.read_text()
s = s.replace('versionCode = 28', 'versionCode = 29')
s = s.replace('versionName = "1.4.3"', 'versionName = "1.4.4"')
g.write_text(s)

# Make YouTube channel/handle discovery deterministic and accept flat-playlist IDs.
e = Path('app/src/main/java/com/faisal/freshdownloader/DownloaderEngine.kt')
t = e.read_text()

old = '''        val extractorAttempt = withContext(Dispatchers.IO) {
            runCatching { discoverWithYtDlp(targetUrl) }
        }
'''
new = '''        val discoveryUrl = normalizeYouTubeCollectionUrl(targetUrl)
        val extractorAttempt = withContext(Dispatchers.IO) {
            runCatching { discoverWithYtDlp(discoveryUrl) }
        }
'''
if old not in t:
    raise SystemExit('discoverCollection extractor anchor not found')
t = t.replace(old, new, 1)

old = '        request.addOption("--print", "%(webpage_url)s")\n'
new = '        request.addOption("--print", if (isYouTubeUrl(url)) "%(id)s" else "%(webpage_url)s")\n'
if old not in t:
    raise SystemExit('yt-dlp print anchor not found')
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
if old not in t:
    raise SystemExit('yt-dlp output parser anchor not found')
t = t.replace(old, new, 1)

anchor = '''    private fun isFacebookUrl(url: String): Boolean {
'''
helper = '''    private fun isYouTubeUrl(url: String): Boolean {
        val lower = normalizeInputUrl(url).lowercase()
        return "youtube.com" in lower || "youtu.be" in lower
    }

    private fun normalizeYouTubeCollectionUrl(url: String): String {
        if (!isYouTubeUrl(url)) return url
        val clean = normalizeInputUrl(url).trimEnd('/')
        val parsed = runCatching { URL(clean) }.getOrNull() ?: return clean
        if (!parsed.query.isNullOrBlank()) return clean

        val path = parsed.path.trim('/')
        if (path.isBlank()) return clean
        val lower = path.lowercase()
        if (lower.endsWith("/videos") || lower.endsWith("/shorts") || lower.endsWith("/streams")) return clean

        val isChannelRoot = path.startsWith("@") ||
            lower.startsWith("channel/") ||
            lower.startsWith("c/") ||
            lower.startsWith("user/")
        return if (isChannelRoot) "$clean/videos" else clean
    }

'''
if helper not in t:
    if anchor not in t:
        raise SystemExit('YouTube helper insertion anchor not found')
    t = t.replace(anchor, helper + anchor, 1)

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
if old not in u:
    raise SystemExit('empty/discovery UI anchor not found')
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
                Text("Fetching channel videos…", fontWeight = FontWeight.ExtraBold)
                Text(
                    status.ifBlank { "Reading public video list" },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
'''

p.write_text(u)
