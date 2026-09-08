package com.faisal.freshdownloader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private const val SUPPORT_EMAIL = "sayadzubair0786@gmail.com"

private enum class MenuPage(val title: String, val symbol: String) {
    Downloads("Downloader", "↓"),
    Referral("Refer & Earn", "↗"),
    Premium("Premium", "★"),
    Device("Device & Eligibility", "✓"),
    Engine("App & Engine", "⚙"),
    About("About Me", "i"),
    Contact("Contact Us", "@"),
    Report("Report a Problem", "!")
}

@Composable
fun AppMenuShell(vm: DownloaderViewModel = viewModel()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(MenuPage.Downloads.name) }
    val selected = MenuPage.valueOf(page)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(292.dp),
                drawerContainerColor = Color(0xFF0B1526)
            ) {
                DrawerHeader()
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(8.dp))

                MenuPage.entries.forEach { item ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                item.title,
                                fontWeight = if (item == selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        icon = {
                            Surface(
                                modifier = Modifier.size(30.dp),
                                shape = RoundedCornerShape(9.dp),
                                color = if (item == selected) Color(0xFF5B7CFF).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(item.symbol, fontWeight = FontWeight.Black)
                                }
                            }
                        },
                        selected = item == selected,
                        onClick = {
                            page = item.name
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF5B7CFF).copy(alpha = 0.18f),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.White.copy(alpha = 0.76f),
                            selectedIconColor = Color(0xFF55DDF7),
                            unselectedIconColor = Color.White.copy(alpha = 0.72f)
                        )
                    )
                }

                Spacer(Modifier.weight(1f))
                Text(
                    "Universal Downloader • v${BuildConfig.VERSION_NAME}",
                    modifier = Modifier.padding(18.dp),
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Color(0xFF08101E),
            topBar = {
                Surface(shadowElevation = 4.dp, color = Color(0xFF0C1728)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(60.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        }
                        Text(
                            selected.title,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = Color(0xFF55DDF7).copy(alpha = 0.12f)
                        ) {
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = Color(0xFF55DDF7),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selected) {
                    MenuPage.Downloads -> DownloaderScreen(vm)
                    MenuPage.Referral -> SimplePage { ReferralCard() }
                    MenuPage.Premium -> PremiumPage()
                    MenuPage.Device -> DeviceEligibilityPage()
                    MenuPage.Engine -> EnginePage(vm)
                    MenuPage.About -> AboutPage()
                    MenuPage.Contact -> ContactPage()
                    MenuPage.Report -> ReportProblemPage()
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF153452), Color(0xFF223267), Color(0xFF3A2356))
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("UD", color = Color(0xFF55DDF7), fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Universal Downloader", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        Text("Clean tools. One menu.", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SimplePage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun PremiumPage() {
    val context = LocalContext.current
    val activity = context as? Activity
    val billing = remember { BillingManager(context) }
    val access = remember { SubscriptionAccess(context) }
    var refresh by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        billing.start()
        onDispose { billing.close() }
    }

    val premium by billing.isPremium
    val price by billing.priceText
    val status by billing.statusText
    refresh

    SimplePage {
        Text("Premium Access", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(
            "Single downloads stay free. Bulk and Channel/Profile use your available trials, then Premium.",
            color = Color.White.copy(alpha = 0.68f)
        )
        SubscriptionCard(
            premium = premium,
            trialsRemaining = access.trialsRemaining(),
            priceText = price,
            status = status,
            onUpgrade = { activity?.let { billing.purchase(it) } },
            onRestore = {
                billing.restore()
                refresh++
            }
        )
        InfoTile("Included", "Bulk downloads • Channel/Profile tools • purchase restore")
        InfoTile("Referral bonus", "Successful referral activation can add bonus trial access up to the app limit.")
    }
}

@Composable
private fun DeviceEligibilityPage() {
    val context = LocalContext.current
    val manager = remember { ReferralManager(context) }
    var snap by remember { mutableStateOf(manager.snapshot()) }

    LaunchedEffect(Unit) {
        manager.refreshInstallReferrer()
        kotlinx.coroutines.delay(700)
        snap = manager.snapshot()
    }

    SimplePage {
        Text("Device & Eligibility", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        InfoTile("Install record", "${snap.installId.take(10)}…")
        InfoTile("Install source", snap.installSource)
        InfoTile("Referral status", if (snap.referralActivated) "Activated" else if (snap.pendingReferralCode != null) "Pending" else "Not attached")
        InfoTile("Referral eligibility", if (snap.eligibleForReferral) "Eligible" else "Recorded / not eligible")
        InfoTile("Organic shares", snap.shareCount.toString())
        Text(
            "Privacy-safe record only: the app uses an app-generated install ID. It does not read IMEI, serial number, contacts, microphone, camera or screen recordings.",
            color = Color.White.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EnginePage(vm: DownloaderViewModel) {
    val ui by vm.state
    SimplePage {
        Text("App & Engine", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        InfoTile("App version", BuildConfig.VERSION_NAME)
        InfoTile("Status", ui.statusLine)
        Button(
            onClick = vm::updateEngine,
            enabled = !ui.running,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B7CFF))
        ) {
            Text("UPDATE DOWNLOAD ENGINE", fontWeight = FontWeight.Bold)
        }
        Text(
            "Engine updates only affect supported extractor components. Platform behavior can still change outside the app.",
            color = Color.White.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AboutPage() {
    SimplePage {
        Text("About Me", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111D31),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zubair Abbas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("Developer & creator of Universal Downloader", color = Color(0xFF55DDF7), fontWeight = FontWeight.SemiBold)
                Text(
                    "Universal Downloader is built to keep public-link downloading simple, fast and organized with Single, Bulk and Channel/Profile tools in one app.",
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
        }
        InfoTile("App", "Universal Downloader")
        InfoTile("Version", BuildConfig.VERSION_NAME)
    }
}

@Composable
private fun ContactPage() {
    val context = LocalContext.current
    SimplePage {
        Text("Contact Us", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("Questions, feedback or business inquiries can be sent directly from your email app.", color = Color.White.copy(alpha = 0.68f))
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL?subject=${Uri.encode("Universal Downloader - Contact")}")
                }
                runCatching { context.startActivity(intent) }
                    .onFailure { Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show() }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B7CFF))
        ) {
            Text("EMAIL SUPPORT", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReportProblemPage() {
    val context = LocalContext.current
    val manager = remember { ReferralManager(context) }
    var category by rememberSaveable { mutableStateOf("Download issue") }
    var subject by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    val categories = listOf("Download issue", "Bulk/Channel issue", "Premium issue", "Referral issue", "App crash", "Other")

    SimplePage {
        Text("Report a Problem", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(
            "Send a problem report directly to the developer. App and device details are attached to help diagnose the issue.",
            color = Color.White.copy(alpha = 0.68f)
        )

        Text("Problem type", color = Color.White.copy(alpha = 0.60f), style = MaterialTheme.typography.labelMedium)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            categories.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Short title") },
            singleLine = true
        )
        OutlinedTextField(
            value = details,
            onValueChange = { details = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
            label = { Text("What happened?") },
            minLines = 5
        )

        Button(
            onClick = {
                val snap = manager.snapshot()
                val body = buildString {
                    appendLine("Problem type: $category")
                    appendLine("User title: ${subject.ifBlank { "Not provided" }}")
                    appendLine()
                    appendLine(details.ifBlank { "No additional details provided." })
                    appendLine()
                    appendLine("--- App diagnostics ---")
                    appendLine("App: Universal Downloader ${BuildConfig.VERSION_NAME}")
                    appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Install: ${snap.installId.take(10)}")
                    appendLine("Source: ${snap.installSource}")
                }
                val mailSubject = "Universal Downloader Report - $category"
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse(
                        "mailto:$SUPPORT_EMAIL?subject=${Uri.encode(mailSubject)}&body=${Uri.encode(body)}"
                    )
                }
                runCatching { context.startActivity(intent) }
                    .onFailure { Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show() }
            },
            enabled = details.isNotBlank() || subject.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C6C))
        ) {
            Text("SEND REPORT", fontWeight = FontWeight.ExtraBold)
        }

        Text(
            "The report opens in the user's email app for review before sending. Nothing is silently uploaded in the background.",
            color = Color.White.copy(alpha = 0.48f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun InfoTile(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = Color(0xFF111D31),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label.uppercase(), color = Color.White.copy(alpha = 0.46f), style = MaterialTheme.typography.labelSmall)
            Text(value, color = Color.White.copy(alpha = 0.84f), fontWeight = FontWeight.SemiBold)
        }
    }
}
