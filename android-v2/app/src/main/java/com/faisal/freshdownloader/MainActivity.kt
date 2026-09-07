package com.faisal.freshdownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF08101E)
private val InkSoft = Color(0xFF0D1728)
private val CardDark = Color(0xFF111D31)
private val Cyan = Color(0xFF55DDF7)
private val Blue = Color(0xFF5B7CFF)
private val Purple = Color(0xFF9D6CFF)
private val Success = Color(0xFF59D99A)
private val Danger = Color(0xFFFF5C6C)
private val Warning = Color(0xFFFFC857)
private val Muted = Color(0xFF91A1B9)
private val WhiteSoft = Color(0xFFF5F8FF)

private data class PlatformBrand(val name: String, val mark: String, val color: Color)

private val platformBrands = listOf(
    PlatformBrand("YouTube", "▶", Color(0xFFFF0033)),
    PlatformBrand("Facebook", "f", Color(0xFF1877F2)),
    PlatformBrand("Instagram", "◎", Color(0xFFE4405F)),
    PlatformBrand("TikTok", "♪", Color(0xFF25F4EE)),
    PlatformBrand("RedNote", "小", Color(0xFFFF2442)),
    PlatformBrand("X / Twitter", "X", Color(0xFF16181C)),
    PlatformBrand("Reddit", "r", Color(0xFFFF4500)),
    PlatformBrand("Vimeo", "v", Color(0xFF1AB7EA)),
    PlatformBrand("Twitch", "T", Color(0xFF9146FF)),
    PlatformBrand("SoundCloud", "≋", Color(0xFFFF5500)),
    PlatformBrand("Pinterest", "P", Color(0xFFE60023)),
    PlatformBrand("Bilibili", "B", Color(0xFF00A1D6)),
    PlatformBrand("Dailymotion", "d", Color(0xFF00AAFF))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PremiumTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                if (showSplash) {
                    AnimatedSplash { showSplash = false }
                } else {
                    DownloaderScreen()
                }
            }
        }
    }
}

@Composable
private fun PremiumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Purple,
            tertiary = Blue,
            background = Ink,
            surface = InkSoft,
            surfaceVariant = CardDark,
            onPrimary = Ink,
            onBackground = WhiteSoft,
            onSurface = WhiteSoft,
            onSurfaceVariant = Muted,
            error = Danger
        ),
        content = content
    )
}

@Composable
private fun AnimatedSplash(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.62f) }
    val alpha = remember { Animatable(0f) }
    val glow = remember { Animatable(0.12f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { scale.animateTo(1f, tween(760, easing = FastOutSlowInEasing)) }
            launch { alpha.animateTo(1f, tween(620)) }
            launch { glow.animateTo(0.34f, tween(900)) }
        }
        delay(900)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF040812), Color(0xFF091327), Color(0xFF090B1C))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(142.dp)
                    .drawBehind {
                        drawCircle(Purple.copy(alpha = glow.value), radius = size.minDimension * 0.66f)
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_universal_logo),
                    contentDescription = "Universal Downloader logo",
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            this.alpha = alpha.value
                        }
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Universal Downloader",
                color = Color.White.copy(alpha = alpha.value),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "One app • many platforms",
                color = Muted.copy(alpha = alpha.value),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(180.dp).clip(RoundedCornerShape(99.dp)),
                color = Cyan,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}

@Composable
private fun DownloaderScreen(vm: DownloaderViewModel = viewModel()) {
    val ui by vm.state
    var tab by remember { mutableIntStateOf(0) }
    var singleUrl by remember { mutableStateOf("") }
    var bulkUrls by remember { mutableStateOf("") }
    var collectionUrl by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(FormatPreset.VIDEO_MP4) }

    val completed = ui.tasks.count { it.status == DownloadStatus.COMPLETE }
    val failed = ui.tasks.count { it.status == DownloadStatus.FAILED }
    val cancelled = ui.tasks.count { it.status == DownloadStatus.CANCELLED }
    val active = ui.tasks.count { it.status == DownloadStatus.DOWNLOADING }

    Scaffold(containerColor = Ink) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF07101E), Color(0xFF0A1426), Color(0xFF08101E))
                    )
                )
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeaderCard(
                    running = ui.running,
                    status = ui.statusLine,
                    onUpdateEngine = vm::updateEngine
                )
            }

            item { PlatformStrip() }
            item { ModeSelector(selected = tab, onSelect = { tab = it }) }

            item {
                DownloadComposer(
                    tab = tab,
                    singleUrl = singleUrl,
                    onSingleUrl = { singleUrl = it },
                    bulkUrls = bulkUrls,
                    onBulkUrls = { bulkUrls = it },
                    collectionUrl = collectionUrl,
                    onCollectionUrl = { collectionUrl = it },
                    preset = preset,
                    onPreset = { preset = it },
                    running = ui.running,
                    onSingle = { vm.downloadSingle(singleUrl, preset) },
                    onBulk = { vm.downloadBulk(bulkUrls, preset) },
                    onCollection = { vm.downloadCollection(collectionUrl, preset) },
                    onStop = vm::stopDownloads
                )
            }

            if (ui.tasks.isNotEmpty() || ui.discoveredCount > 0) {
                item {
                    QueueSummary(
                        total = ui.tasks.size,
                        discovered = ui.discoveredCount,
                        active = active,
                        completed = completed,
                        failed = failed,
                        cancelled = cancelled,
                        canClear = !ui.running && (completed > 0 || failed > 0 || cancelled > 0),
                        onClear = vm::clearFinished
                    )
                }
            }

            if (ui.tasks.isEmpty()) {
                item { EmptyState(vm.outputPath()) }
            } else {
                items(ui.tasks, key = { it.id }) { task -> TaskCard(task) }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun HeaderCard(running: Boolean, status: String, onUpdateEngine: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF183E62), Color(0xFF22356E), Color(0xFF4A286B))
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_universal_logo),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Universal Downloader", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text("Premium multi-platform downloader", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onUpdateEngine, enabled = !running) {
                        Text("Update", color = if (running) Muted else Color.White)
                    }
                }

                Surface(color = Color.Black.copy(alpha = 0.18f), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (running) Warning else Success)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformStrip() {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SUPPORTED PUBLIC PLATFORMS", style = MaterialTheme.typography.labelSmall, color = Muted)
            Spacer(Modifier.weight(1f))
            Text("Auto detect", style = MaterialTheme.typography.labelSmall, color = Cyan)
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            platformBrands.forEach { PlatformPill(it) }
        }
    }
}

@Composable
private fun PlatformPill(brand: PlatformBrand) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardDark,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlatformMark(brand)
            Spacer(Modifier.width(7.dp))
            Text(brand.name, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PlatformMark(brand: PlatformBrand, size: Int = 27) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(brand.color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            brand.mark,
            color = if (brand.name == "TikTok") Ink else Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private fun brandFor(platform: String): PlatformBrand =
    platformBrands.firstOrNull { platform.startsWith(it.name.substringBefore(" /"), ignoreCase = true) }
        ?: PlatformBrand("Web", "↗", Blue)

@Composable
private fun ModeSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardDark)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("Single", "Bulk", "Channel").forEachIndexed { index, label ->
            val selectedNow = selected == index
            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(14.dp),
                color = if (selectedNow) Blue.copy(alpha = 0.92f) else Color.Transparent
            ) {
                Box(Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontWeight = if (selectedNow) FontWeight.Bold else FontWeight.Medium, color = if (selectedNow) Color.White else Muted)
                }
            }
        }
    }
}

@Composable
private fun DownloadComposer(
    tab: Int,
    singleUrl: String,
    onSingleUrl: (String) -> Unit,
    bulkUrls: String,
    onBulkUrls: (String) -> Unit,
    collectionUrl: String,
    onCollectionUrl: (String) -> Unit,
    preset: FormatPreset,
    onPreset: (FormatPreset) -> Unit,
    running: Boolean,
    onSingle: () -> Unit,
    onBulk: () -> Unit,
    onCollection: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = InkSoft),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Column {
                Text(
                    when (tab) {
                        0 -> "Single video download"
                        1 -> "Bulk download queue"
                        else -> "Channel / playlist / profile"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (tab) {
                        0 -> if (singleUrl.isBlank()) "Paste a supported public video link" else detectPlatform(singleUrl)
                        1 -> "${bulkUrlCount(bulkUrls)} valid links detected"
                        else -> if (collectionUrl.isBlank()) "Discover public videos automatically" else detectPlatform(collectionUrl)
                    },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            FormatSelector(preset, onPreset)

            when (tab) {
                0 -> PremiumField(singleUrl, onSingleUrl, "Video URL", "https://...", true)
                1 -> PremiumField(bulkUrls, onBulkUrls, "Bulk URLs — one per line", "https://...\nhttps://...", false, 6)
                else -> PremiumField(collectionUrl, onCollectionUrl, "Channel / playlist / profile URL", "Paste public collection link", true)
            }

            if (running) {
                StopAction(onStop)
            } else {
                when (tab) {
                    0 -> PrimaryAction("DOWNLOAD VIDEO", singleUrl.isNotBlank(), onSingle)
                    1 -> PrimaryAction("START ${bulkUrlCount(bulkUrls)} DOWNLOADS", bulkUrlCount(bulkUrls) > 0, onBulk)
                    else -> PrimaryAction("DISCOVER & DOWNLOAD", collectionUrl.isNotBlank(), onCollection)
                }
            }

            if (tab == 2) {
                Text(
                    "Public collection discovery depends on what each platform exposes without account authentication.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun PremiumField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Muted.copy(alpha = 0.62f)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = CardDark.copy(alpha = 0.72f),
            unfocusedContainerColor = CardDark.copy(alpha = 0.56f),
            cursorColor = Cyan
        )
    )
}

@Composable
private fun FormatSelector(current: FormatPreset, onSelect: (FormatPreset) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormatChip("MP4 VIDEO", current == FormatPreset.VIDEO_MP4) { onSelect(FormatPreset.VIDEO_MP4) }
        FormatChip("MP3 AUDIO", current == FormatPreset.AUDIO_MP3) { onSelect(FormatPreset.AUDIO_MP3) }
    }
}

@Composable
private fun FormatChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Cyan.copy(alpha = 0.14f) else CardDark,
        border = BorderStroke(1.dp, if (selected) Cyan.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.08f))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.labelMedium, color = if (selected) Cyan else Muted)
    }
}

@Composable
private fun PrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White)
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StopAction(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White)
    ) {
        Text("■  STOP / ABORT DOWNLOADS", fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun QueueSummary(
    total: Int,
    discovered: Int,
    active: Int,
    completed: Int,
    failed: Int,
    cancelled: Int,
    canClear: Boolean,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DOWNLOAD QUEUE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (canClear) TextButton(onClick = onClear) { Text("Clear") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Total", total.toString(), Blue)
                if (discovered > 0) StatChip("Found", discovered.toString(), Purple)
                StatChip("Active", active.toString(), Warning)
                StatChip("Done", completed.toString(), Success)
            }
            if (failed > 0 || cancelled > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (failed > 0) StatChip("Failed", failed.toString(), Danger)
                    if (cancelled > 0) StatChip("Cancelled", cancelled.toString(), Muted)
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f)) {
        Text("$label $value", modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TaskCard(task: DownloadTask) {
    val brand = brandFor(task.platform)
    val statusColor = when (task.status) {
        DownloadStatus.COMPLETE -> Success
        DownloadStatus.FAILED -> Danger
        DownloadStatus.CANCELLED -> Muted
        DownloadStatus.DOWNLOADING -> Cyan
        else -> Warning
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformMark(brand, 32)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.platform, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(task.url, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Text(task.status.name, color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }

            LinearProgressIndicator(
                progress = task.progress,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp)),
                color = statusColor,
                trackColor = Color.White.copy(alpha = 0.07f)
            )
            Text(task.message, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyState(outputPath: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = InkSoft),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ready to download", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text("Files save to $outputPath", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun bulkUrlCount(raw: String): Int = raw.lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("http://") || it.startsWith("https://") }
    .distinct()
    .count()
