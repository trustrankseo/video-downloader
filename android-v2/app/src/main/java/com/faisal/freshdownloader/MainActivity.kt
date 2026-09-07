package com.faisal.freshdownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DownloaderScreen()
            }
        }
    }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fresh Downloader", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = vm::updateEngine, enabled = !ui.running) {
                        Text("Update engine")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("No paid API • Public/guest mode", style = MaterialTheme.typography.labelLarge)
            Text("Save folder: ${vm.outputPath()}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))

            TabRow(selectedTabIndex = tab) {
                listOf("Single", "Bulk", "Channel/Profile").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            FormatSelector(preset) { preset = it }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> {
                    OutlinedTextField(
                        value = singleUrl,
                        onValueChange = { singleUrl = it },
                        label = { Text("Public video URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.downloadSingle(singleUrl, preset) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !ui.running && singleUrl.isNotBlank()
                    ) { Text("Download") }
                }
                1 -> {
                    OutlinedTextField(
                        value = bulkUrls,
                        onValueChange = { bulkUrls = it },
                        label = { Text("One public URL per line") },
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.downloadBulk(bulkUrls, preset) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !ui.running && bulkUrls.isNotBlank()
                    ) { Text("Start bulk queue") }
                }
                else -> {
                    OutlinedTextField(
                        value = collectionUrl,
                        onValueChange = { collectionUrl = it },
                        label = { Text("Channel / playlist / public profile URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.downloadCollection(collectionUrl, preset) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !ui.running && collectionUrl.isNotBlank()
                    ) { Text("Discover & download") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No YouTube Data API is used. Collection discovery is local through yt-dlp. Public profile support depends on each platform's current extractor.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui.statusLine, modifier = Modifier.weight(1f))
                if (ui.tasks.any { it.status == DownloadStatus.COMPLETE || it.status == DownloadStatus.FAILED }) {
                    TextButton(onClick = vm::clearFinished, enabled = !ui.running) { Text("Clear") }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ui.tasks, key = { it.id }) { task -> TaskCard(task) }
            }
        }
    }
}

@Composable
private fun FormatSelector(current: FormatPreset, onSelect: (FormatPreset) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current == FormatPreset.VIDEO_MP4,
            onClick = { onSelect(FormatPreset.VIDEO_MP4) },
            label = { Text("MP4 Video") }
        )
        FilterChip(
            selected = current == FormatPreset.AUDIO_MP3,
            onClick = { onSelect(FormatPreset.AUDIO_MP3) },
            label = { Text("MP3 Audio") }
        )
    }
}

@Composable
private fun TaskCard(task: DownloadTask) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(task.url, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = task.progress,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text("${task.status}: ${task.message}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
