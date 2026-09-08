from pathlib import Path

# Final release metadata + Google Play Billing dependency.
g = Path('app/build.gradle.kts')
s = g.read_text()
s = s.replace('versionCode = 23', 'versionCode = 24')
s = s.replace('versionName = "1.3.9"', 'versionName = "1.4.0"')
needle = '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")\n'
if 'com.android.billingclient:billing-ktx' not in s:
    if needle not in s:
        raise SystemExit('billing dependency anchor not found')
    s = s.replace(needle, needle + '    implementation("com.android.billingclient:billing-ktx:7.1.1")\n', 1)
g.write_text(s)

# Wire premium entitlement + three free Bulk/Channel trials into the Compose screen.
p = Path('app/src/main/java/com/faisal/freshdownloader/MainActivity.kt')
t = p.read_text()
old = '''    var collectionUrl by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(FormatPreset.VIDEO_MP4) }

    val completed = ui.tasks.count { it.status == DownloadStatus.COMPLETE }
'''
new = '''    var collectionUrl by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(FormatPreset.VIDEO_MP4) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val billing = remember { BillingManager(context) }
    val access = remember { SubscriptionAccess(context) }
    val premium by billing.isPremium
    val premiumPrice by billing.priceText
    val billingStatus by billing.statusText
    var trialsRemaining by remember { mutableIntStateOf(access.trialsRemaining()) }
    var showPaywall by remember { mutableStateOf(false) }

    DisposableEffect(billing) {
        billing.start()
        onDispose { billing.close() }
    }

    if (showPaywall) {
        PremiumRequiredDialog(
            priceText = premiumPrice,
            onDismiss = { showPaywall = false },
            onUpgrade = {
                showPaywall = false
                (context as? android.app.Activity)?.let { billing.purchase(it) }
            },
            onRestore = {
                showPaywall = false
                billing.restore()
            }
        )
    }

    val completed = ui.tasks.count { it.status == DownloadStatus.COMPLETE }
'''
if old not in t:
    raise SystemExit('DownloaderScreen state anchor not found')
t = t.replace(old, new, 1)

old = '''            item { PlatformStrip() }
            item { ModeSelector(selected = tab, onSelect = { tab = it }) }
'''
new = '''            item { PlatformStrip() }
            item {
                SubscriptionCard(
                    premium = premium,
                    trialsRemaining = trialsRemaining,
                    priceText = premiumPrice,
                    status = billingStatus,
                    onUpgrade = { (context as? android.app.Activity)?.let { billing.purchase(it) } },
                    onRestore = billing::restore
                )
            }
            item { ModeSelector(selected = tab, onSelect = { tab = it }) }
'''
if old not in t:
    raise SystemExit('subscription card anchor not found')
t = t.replace(old, new, 1)

old = '''                    onSingle = { vm.downloadSingle(singleUrl, preset) },
                    onBulk = { vm.downloadBulk(bulkUrls, preset) },
                    onCollection = { vm.downloadCollection(collectionUrl, preset) },
                    onStop = vm::stopDownloads
'''
new = '''                    onSingle = { vm.downloadSingle(singleUrl, preset) },
                    onBulk = {
                        if (bulkUrlCount(bulkUrls) > 0) {
                            if (premium) {
                                vm.downloadBulk(bulkUrls, preset)
                            } else if (access.consumeTrial()) {
                                trialsRemaining = access.trialsRemaining()
                                vm.downloadBulk(bulkUrls, preset)
                            } else {
                                showPaywall = true
                            }
                        }
                    },
                    onCollection = {
                        if (collectionUrl.isNotBlank()) {
                            if (premium) {
                                vm.downloadCollection(collectionUrl, preset)
                            } else if (access.consumeTrial()) {
                                trialsRemaining = access.trialsRemaining()
                                vm.downloadCollection(collectionUrl, preset)
                            } else {
                                showPaywall = true
                            }
                        }
                    },
                    onStop = vm::stopDownloads
'''
if old not in t:
    raise SystemExit('premium gate callback anchor not found')
t = t.replace(old, new, 1)
p.write_text(t)

# Make known TikTok media restrictions user-readable instead of exposing parser internals.
v = Path('app/src/main/java/com/faisal/freshdownloader/DownloaderViewModel.kt')
u = v.read_text()
anchor = '''            msg.contains("403", ignoreCase = true) ->
                "Platform blocked the request (403). Try updating the engine or another public URL."
'''
replacement = '''            msg.contains("TIKTOK_MEDIA_URL_NOT_FOUND", ignoreCase = true) ->
                "TikTok did not expose a downloadable media URL to this Android session. Other supported platforms and public URLs can still be used."
            msg.contains("403", ignoreCase = true) ->
                "Platform blocked the request (403). Try updating the engine or another public URL."
'''
if 'TikTok did not expose a downloadable media URL to this Android session' not in u:
    if anchor not in u:
        raise SystemExit('friendly TikTok error anchor not found')
    u = u.replace(anchor, replacement, 1)
v.write_text(u)
