package com.faisal.freshdownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Ink = Color(0xFF08101E)
private val InkSoft = Color(0xFF0D1728)
private val CardDark = Color(0xFF111D31)
private val CardLight = Color(0xFF17243B)
private val Cyan = Color(0xFF55DDF7)
private val Blue = Color(0xFF5B7CFF)
private val Purple = Color(0xFF9D6CFF)
private val Success = Color(0xFF59D99A)
private val Danger = Color(0xFFFF6B7A)
private val Muted = Color(0xFF91A1B9)
private val WhiteSoft = Color(0xFFF5F8FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PremiumTheme {
                DownloaderScreen()
            }
        }
    }
}

@Composable
private fun PremiumTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
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
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloaderScreen(vm: DownloaderViewModel = viewModel()) {
    val ui by vm.state
    var tab by remember { mutableIntStateOf(0) }
    var singleUrl by remember { mutableStateOf("") }
    var bulkUrls by remember { mutableStateOf("") }
    var collectionUrl by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(FormatPreset.VIDEO_MP4) }

    val finished = ui.tasks.count { it.status == DownloadStatus.COMPLETE }
    val failed = ui.tasks.count { it.status == DownloadStatus.FAILED }
    val active = ui.tasks.count { it.status == DownloadStatus.DOWNLOADING }

    Scaffold(containerColor = Ink) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF07101E), Color(0xFF0A1426), Color(0xFF08101E))
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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

                item {
                    ModeSelector(selected = tab, onSelect = { tab = it })
                }

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
                        onCollection = { vm.downloadCollection(collectionUrl, preset) }
                    )
                }

                if (ui.tasks.isNotEmpty() || ui.discoveredCount > 0) {
                    item {
                        QueueSummary(
                            total = ui.tasks.size,
                            discovered = ui.discoveredCount,
                            active = active,
                            completed = finished,
                            failed = failed,
                            canClear = !ui.running && (finished > 0 || failed > 0),
                            onClear = vm::clearFinished
                        )
                    }
                }

                if (ui.tasks.isEmpty()) {
                    item { EmptyState(vm.outputPath()) }
                } else {
                    items(ui.tasks, key = { it.id }) { task ->
                        TaskCard(task)
                    }
                }

                item { Spacer(Modifier.height(12.dp)) }
            }
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
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.White.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↓", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Universal Downloader",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            "Fast • private • no paid API",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.76f)
                        )
                    }
                    TextButton(onClick = onUpdateEngine, enabled = !running) {
                        Text("Update", color = if (running) Muted else Color.White)
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDot(running)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            status,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(running: Boolean) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(if (running) Color(0xFFFFC857) else Success)
    )
}

@Composable
private fun PlatformStrip() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PUBLIC PLATFORM SUPPORT", style = MaterialTheme.typography.labelSmall, color = Muted)
            Spacer(Modifier.weight(1f))
            Text("Auto detect", style = MaterialTheme.typography.labelSmall, color = Cyan)
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "YouTube", "Facebook", "Instagram", "TikTok", "RedNote",
                "X", "Reddit", "Vimeo", "Twitch", "SoundCloud", "More"
            ).forEach { PlatformPill(it) }
        }
    }
}

@Composable
private fun PlatformPill(name: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = CardDark,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = WhiteSoft
        )
    }
}

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
            val active = selected == index
            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(14.dp),
                color = if (active) Blue.copy(alpha = 0.9f) else Color.Transparent
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) Color.White else Muted
                    )
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
    onCollection: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = InkSoft),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (tab) {
                            0 -> "Download one video"
                            1 -> "Power bulk queue"
                            else -> "Channel / profile grabber"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when (tab) {
                            0 -> if (singleUrl.isBlank()) "Paste any supported public video URL" else detectPlatform(singleUrl)
                            1 -> "${bulkUrlCount(bulkUrls)} valid links detected"
                            else -> if (collectionUrl.isBlank()) "Discover public videos automatically" else detectPlatform(collectionUrl)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Cyan.copy(alpha = 0.10f)
                ) {
                    Text(
                        when (tab) { 0 -> "1 LINK"; 1 -> "MULTI"; else -> "AUTO" },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Cyan
                    )
                }
            }

            FormatSelector(preset, onPreset)

            when (tab) {
                0 -> {
                    PremiumField(
                        value = singleUrl,
                        onValueChange = onSingleUrl,
                        label = "Video URL",
                        placeholder = "https://...",
                        singleLine = true
                    )
                    PrimaryAction(
                        text = "DOWNLOAD VIDEO",
                        enabled = !running && singleUrl.isNotBlank(),
                        onClick = onSingle
                    )
                }
                1 -> {
                    PremiumField(
                        value = bulkUrls,
                        onValueChange = onBulkUrls,
                        label = "Bulk URLs — one per line",
                        placeholder = "https://...\nhttps://...\nhttps://...",
                        singleLine = false,
                        minLines = 6
                    )
                    PrimaryAction(
                        text = "START ${bulkUrlCount(bulkUrls)} DOWNLOADS",
                        enabled = !running && bulkUrlCount(bulkUrls) > 0,
                        onClick = onBulk
                    )
                }
                else -> {
                    PremiumField(
                        value = collectionUrl,
                        onValueChange = onCollectionUrl,
                        label = "Channel / playlist / public profile URL",
                        placeholder = "Paste channel, playlist or profile link",
                        singleLine = true
                    )
                    PrimaryAction(
                        text = "DISCOVER & DOWNLOAD",
                        enabled = !running && collectionUrl.isNotBlank(),
                        onClick = onCollection
                    )
                    Text(
                        "Collection discovery runs locally through the downloader engine. Availability depends on what each platform exposes publicly.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                }
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
        placeholder = { Text(placeholder, color = Muted.copy(alpha = 0.65f)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = CardDark.copy(alpha = 0.72f),
            unfocusedContainerColor = CardDark.copy(alpha = 0.55f),
            cursorColor = Cyan
        )
    )
}

@Composable
private fun FormatSelector(current: FormatPreset, onSelect: (FormatPreset) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PremiumChip(
            text = "MP4 VIDEO",
            selected = current == FormatPreset.VIDEO_MP4,
            onClick = { onSelect(FormatPreset.VIDEO_MP4) }
        )
        PremiumChip(
            text = "MP3 AUDIO",
            selected = current == FormatPreset.AUDIO_MP3,
            onClick = { onSelect(FormatPreset.AUDIO_MP3) }
        )
    }
}

@Composable
private fun PremiumChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Cyan.copy(alpha = 0.14f) else CardDark,
        border = BorderStroke(1.dp, if (selected) Cyan.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) Cyan else Muted
        )
    }
}

@Composable
private fun PrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Blue,
            contentColor = Color.White,
            disabledContainerColor = CardLight,
            disabledContentColor = Muted
        )
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun QueueSummary(
    total: Int,
    discovered: Int,
    active: Int,
    completed: Int,
    failed: Int,
    canClear: Boolean,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DOWNLOAD QUEUE", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(
                        if (discovered > 0) "$discovered items discovered" else "$total items",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (canClear) TextButton(onClick = onClear) { Text("Clear finished", color = Cyan) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Active", active, Color(0xFFFFC857), Modifier.weight(1f))
                StatPill("Done", completed, Success, Modifier.weight(1f))
                StatPill("Failed", failed, Danger, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = InkSoft
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value.toString(), fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun TaskCard(task: DownloadTask) {
    val accent = when (task.status) {
        DownloadStatus.COMPLETE -> Success
        DownloadStatus.FAILED -> Danger
        DownloadStatus.DOWNLOADING -> Cyan
        else -> Muted
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = InkSoft),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = accent.copy(alpha = 0.11f)
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text(platformInitial(task.platform), fontWeight = FontWeight.Black, color = accent)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.platform,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WhiteSoft,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        statusLabel(task.status),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
                Text(
                    task.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                LinearProgressIndicator(
                    progress = task.progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.07f)
                )
                Text(
                    task.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyState(outputPath: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardDark.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("Ready when you are", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Paste a public link above. Downloads are saved to FreshDownloader inside your Downloads folder.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
            Text(outputPath, style = MaterialTheme.typography.labelSmall, color = Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun bulkUrlCount(raw: String): Int = raw.lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("http://") || it.startsWith("https://") }
    .distinct()
    .count()

private fun platformInitial(platform: String): String = when (platform) {
    "YouTube" -> "YT"
    "Facebook" -> "FB"
    "Instagram" -> "IG"
    "TikTok" -> "TT"
    "RedNote" -> "RN"
    "X / Twitter" -> "X"
    "Reddit" -> "R"
    "Vimeo" -> "V"
    "Twitch" -> "TW"
    "SoundCloud" -> "SC"
    else -> "WEB"
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "QUEUED"
    DownloadStatus.DISCOVERING -> "DISCOVERING"
    DownloadStatus.DOWNLOADING -> "DOWNLOADING"
    DownloadStatus.COMPLETE -> "DONE"
    DownloadStatus.FAILED -> "FAILED"
}
