package com.faisal.freshdownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID

class DownloaderViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = DownloaderEngine(app)

    var state = androidx.compose.runtime.mutableStateOf(UiState())
        private set

    fun outputPath(): String = engine.outputPath()

    fun clearFinished() {
        state.value = state.value.copy(
            tasks = state.value.tasks.filterNot {
                it.status == DownloadStatus.COMPLETE || it.status == DownloadStatus.FAILED
            }
        )
    }

    fun downloadSingle(url: String, format: FormatPreset) {
        val clean = url.trim()
        if (clean.isBlank() || state.value.running) return
        val task = DownloadTask(id = UUID.randomUUID().toString(), url = clean)
        state.value = UiState(running = true, statusLine = "Starting…", tasks = listOf(task))
        viewModelScope.launch {
            runOne(task, format)
            state.value = state.value.copy(running = false, statusLine = "Finished")
        }
    }

    fun downloadBulk(raw: String, format: FormatPreset) {
        if (state.value.running) return
        val urls = raw.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .toList()
        if (urls.isEmpty()) return

        val tasks = urls.map { DownloadTask(id = UUID.randomUUID().toString(), url = it) }
        state.value = UiState(running = true, statusLine = "Bulk queue: ${tasks.size}", tasks = tasks)
        viewModelScope.launch {
            // Sequential by design: stability first and lower rate-limit pressure.
            for (task in tasks) runOne(task, format)
            state.value = state.value.copy(running = false, statusLine = "Bulk queue finished")
        }
    }

    fun downloadCollection(collectionUrl: String, format: FormatPreset) {
        val clean = collectionUrl.trim()
        if (clean.isBlank() || state.value.running) return
        state.value = UiState(running = true, statusLine = "Discovering collection…")
        viewModelScope.launch {
            val discovered = engine.discoverCollection(clean)
            if (discovered.isFailure) {
                state.value = UiState(
                    running = false,
                    statusLine = "Discovery failed: ${friendlyError(discovered.exceptionOrNull())}"
                )
                return@launch
            }

            val urls = discovered.getOrDefault(emptyList())
            if (urls.isEmpty()) {
                state.value = UiState(
                    running = false,
                    statusLine = "No public items found. The platform may require login or block profile extraction."
                )
                return@launch
            }

            val tasks = urls.map { DownloadTask(id = UUID.randomUUID().toString(), url = it) }
            state.value = UiState(
                running = true,
                statusLine = "Found ${tasks.size} items. Downloading…",
                tasks = tasks,
                discoveredCount = tasks.size
            )
            for (task in tasks) runOne(task, format)
            state.value = state.value.copy(running = false, statusLine = "Collection finished")
        }
    }

    fun updateEngine() {
        if (state.value.running) return
        state.value = state.value.copy(running = true, statusLine = "Updating yt-dlp…")
        viewModelScope.launch {
            val result = engine.updateEngineStable()
            state.value = state.value.copy(
                running = false,
                statusLine = result.fold({ it }, { "Update failed: ${friendlyError(it)}" })
            )
        }
    }

    private suspend fun runOne(task: DownloadTask, format: FormatPreset) {
        updateTask(task.id, DownloadStatus.DOWNLOADING, 0f, "Starting")
        val result = engine.download(task.url, format) { progress, eta ->
            updateTask(
                task.id,
                DownloadStatus.DOWNLOADING,
                (progress / 100f).coerceIn(0f, 1f),
                "${progress.toInt()}% • ETA ${eta}s"
            )
        }
        if (result.isSuccess) {
            updateTask(task.id, DownloadStatus.COMPLETE, 1f, "Saved")
        } else {
            updateTask(task.id, DownloadStatus.FAILED, 0f, friendlyError(result.exceptionOrNull()))
        }
    }

    private fun updateTask(id: String, status: DownloadStatus, progress: Float, message: String) {
        state.value = state.value.copy(
            tasks = state.value.tasks.map {
                if (it.id == id) it.copy(status = status, progress = progress, message = message) else it
            }
        )
    }

    private fun friendlyError(t: Throwable?): String {
        val msg = t?.message.orEmpty()
        return when {
            msg.contains("login", ignoreCase = true) || msg.contains("cookies", ignoreCase = true) ->
                "This item requires platform authentication; guest mode cannot access it."
            msg.contains("403", ignoreCase = true) ->
                "Platform blocked the request (403). Try updating the engine or another public URL."
            msg.isBlank() -> "Unknown download error"
            else -> msg.take(180)
        }
    }
}
