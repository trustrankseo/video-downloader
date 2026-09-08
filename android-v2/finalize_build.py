from pathlib import Path

# v1.4.2 UI refresh: keep the user-facing version name while bumping the
# installable Android versionCode so this can update over the previous build.
g = Path('app/build.gradle.kts')
s = g.read_text()
for old_code in ('versionCode = 23', 'versionCode = 24', 'versionCode = 25', 'versionCode = 26'):
    s = s.replace(old_code, 'versionCode = 27')
for old_name in ('versionName = "1.3.9"', 'versionName = "1.4.0"', 'versionName = "1.4.1"'):
    s = s.replace(old_name, 'versionName = "1.4.2"')

needle = '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")\n'
if needle not in s:
    raise SystemExit('dependency anchor not found')
extra = ''
if 'com.android.billingclient:billing-ktx' not in s:
    extra += '    implementation("com.android.billingclient:billing-ktx:7.1.1")\n'
if 'com.android.installreferrer:installreferrer' not in s:
    extra += '    implementation("com.android.installreferrer:installreferrer:2.2")\n'
if extra:
    s = s.replace(needle, needle + extra, 1)
g.write_text(s)

p = Path('app/src/main/java/com/faisal/freshdownloader/MainActivity.kt')
t = p.read_text()

# The new AppMenuShell owns the hamburger menu and routes to each tool page.
t = t.replace('private fun DownloaderScreen(vm: DownloaderViewModel = viewModel())', 'fun DownloaderScreen(vm: DownloaderViewModel = viewModel())')
t = t.replace('DownloaderScreen()', 'AppMenuShell()', 1)
t = t.replace('Premium multi-platform downloader', 'Native multi-platform downloader')

# Capture referral deep links even when the activity is already open.
activity_anchor = 'class MainActivity : ComponentActivity() {\n'
if 'override fun onNewIntent(intent: android.content.Intent)' not in t:
    if activity_anchor not in t:
        raise SystemExit('MainActivity anchor not found')
    t = t.replace(
        activity_anchor,
        activity_anchor + '''    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ReferralManager(this).captureReferralUri(intent.data)
    }

''',
        1
    )

# Clipboard helper for compact download-page quick actions.
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

# Remove the large hero/status card and platform strip from the main page.
# Referral, Premium, Device, About, Contact and Reporting now live in the menu.
old = '''            item {
                HeaderCard(
                    running = ui.running,
                    status = ui.statusLine,
                    onUpdateEngine = vm::updateEngine
                )
            }

            item { PlatformStrip() }
            item { ModeSelector(selected = tab, onSelect = { tab = it }) }
'''
new = '''            item { ModeSelector(selected = tab, onSelect = { tab = it }) }
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
    raise SystemExit('clean home anchor not found')
t = t.replace(old, new, 1)

# Retry-all-failed queue action.
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

if 'private fun NativeQuickActions(' not in t:
    t += '''

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

# Activate a pending referral only after one successful download.
v = Path('app/src/main/java/com/faisal/freshdownloader/DownloaderViewModel.kt')
u = v.read_text()
success_old = '''        } else if (result.isSuccess) {
            updateTask(task.id, DownloadStatus.COMPLETE, 1f, "Saved")
        } else {
'''
success_new = '''        } else if (result.isSuccess) {
            ReferralManager(getApplication()).activatePendingReferralAfterSuccessfulDownload()
            updateTask(task.id, DownloadStatus.COMPLETE, 1f, "Saved")
        } else {
'''
if success_old not in u:
    raise SystemExit('referral success hook anchor not found')
u = u.replace(success_old, success_new, 1)
u = u.replace(
    'TikTok profile discovery could not enumerate this account in guest mode. Direct public TikTok video links can still be tried.',
    'TikTok profile discovery is unavailable in the native-only build. Try direct public video links in Single or Bulk mode.'
)
u = u.replace(
    "Instagram did not expose this profile's public posts/reels to signed-out guest mode. Direct public reel links can still be tried.",
    'Instagram profile discovery is unavailable in the native-only build. Try direct public reel/post links in Single or Bulk mode.'
)
v.write_text(u)
