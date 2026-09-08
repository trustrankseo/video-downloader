from pathlib import Path

# Native-only Uptodown release metadata.
g = Path('app/build.gradle.kts')
s = g.read_text()
s = s.replace('versionCode = 23', 'versionCode = 25')
s = s.replace('versionCode = 24', 'versionCode = 25')
s = s.replace('versionName = "1.3.9"', 'versionName = "1.4.1"')
s = s.replace('versionName = "1.4.0"', 'versionName = "1.4.1"')

# Keep Play Billing classes compilable for the future Play Store build, but the
# Uptodown UI does not expose the Play purchase flow.
needle = '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")\n'
if 'com.android.billingclient:billing-ktx' not in s:
    if needle not in s:
        raise SystemExit('billing dependency anchor not found')
    s = s.replace(needle, needle + '    implementation("com.android.billingclient:billing-ktx:7.1.1")\n', 1)
g.write_text(s)

p = Path('app/src/main/java/com/faisal/freshdownloader/MainActivity.kt')
t = p.read_text()

# Make native-only positioning explicit in the visible UI.
t = t.replace('Premium multi-platform downloader', 'Native multi-platform downloader')

# Clipboard helper for fully native quick actions.
old = '''    var collectionUrl by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(FormatPreset.VIDEO_MP4) }

    val completed = ui.tasks.count { it.status == DownloadStatus.COMPLETE }
'''
new = '''    var collectionUrl by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(FormatPreset.VIDEO_MP4) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    val completed = ui.tasks.count { it.status == DownloadStatus.COMPLETE }
'''
if old not in t:
    raise SystemExit('DownloaderScreen state anchor not found')
t = t.replace(old, new, 1)

# Add a visible native-mode notice after the platform strip.
old = '''            item { PlatformStrip() }
            item { ModeSelector(selected = tab, onSelect = { tab = it }) }
'''
new = '''            item { PlatformStrip() }
            item { NativeModeNotice() }
            item { ModeSelector(selected = tab, onSelect = { tab = it }) }
            item {
                NativeQuickActions(
                    onPaste = {
                        val pasted = clipboard.getText()?.text?.trim().orEmpty()
                        if (pasted.isNotBlank()) {
                            when (tab) {
                                0 -> singleUrl = pasted
                                1 -> bulkUrls = if (bulkUrls.isBlank()) pasted else bulkUrls.trimEnd() + "\\n" + pasted
                                else -> collectionUrl = pasted
                            }
                        }
                    },
                    onClear = {
                        when (tab) {
                            0 -> singleUrl = ""
                            1 -> bulkUrls = ""
                            else -> collectionUrl = ""
                        }
                    }
                )
            }
'''
if old not in t:
    raise SystemExit('native quick actions anchor not found')
t = t.replace(old, new, 1)

# Add a retry-all-failed native queue action.
old = '''            if (ui.tasks.isEmpty()) {
                item { EmptyState(vm.outputPath()) }
            } else {
                items(ui.tasks, key = { it.id }) { task -> TaskCard(task) }
            }
'''
new = '''            if (!ui.running && failed > 0) {
                item {
                    OutlinedButton(
                        onClick = {
                            val retryUrls = ui.tasks
                                .filter { it.status == DownloadStatus.FAILED }
                                .joinToString("\\n") { it.url }
                            if (retryUrls.isNotBlank()) vm.downloadBulk(retryUrls, preset)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Warning.copy(alpha = 0.6f))
                    ) {
                        Text("RETRY FAILED ($failed)", color = Warning, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (ui.tasks.isEmpty()) {
                item { EmptyState(vm.outputPath()) }
            } else {
                items(ui.tasks, key = { it.id }) { task -> TaskCard(task) }
            }
'''
if old not in t:
    raise SystemExit('retry failed anchor not found')
t = t.replace(old, new, 1)

# Append native-only informational/quick-action composables once.
if 'private fun NativeModeNotice()' not in t:
    t += '''

@Composable
private fun NativeModeNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardDark.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Cyan.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = Success.copy(alpha = 0.14f)
            ) {
                Text(
                    "NATIVE",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    color = Success,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "No embedded browser • direct public-link processing • local download queue",
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun NativeQuickActions(
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onPaste,
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.45f))
        ) {
            Text("PASTE LINK", color = Cyan, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            Text("CLEAR INPUT", color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold)
        }
    }
}
'''

p.write_text(t)

# Friendly native-only profile messaging.
v = Path('app/src/main/java/com/faisal/freshdownloader/DownloaderViewModel.kt')
u = v.read_text()
u = u.replace(
    'TikTok profile discovery could not enumerate this account in guest mode. Direct public TikTok video links can still be tried.',
    'TikTok profile discovery is unavailable in the native-only build. Try direct public video links in Single or Bulk mode.'
)
u = u.replace(
    "Instagram did not expose this profile's public posts/reels to signed-out guest mode. Direct public reel links can still be tried.",
    'Instagram profile discovery is unavailable in the native-only build. Try direct public reel/post links in Single or Bulk mode.'
)
v.write_text(u)
