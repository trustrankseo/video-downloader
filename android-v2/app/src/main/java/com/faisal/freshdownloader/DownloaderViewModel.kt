package com.faisal.freshdownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID

class DownloaderViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = DownloaderEngine(app)

    @Volatile
    private var abortRequested = false

    var state = androidx.compose.runtime.mutableStateOf(UiState())
        private set

    fun outputPath(): String = engine.outputPath()

    fun clearFinished() {
        state.value = state.value.copy(
            tasks = state.value.tasks.filterNot {
                it.status == DownloadStatus.COMPLETE ||
                    it.status == DownloadStatus.FAILED ||
                    it.status == DownloadStatus.CANCELLED
            }
        )
    }

    fun stopDownloads() {
        if (!state.value.running) return
        abortRequested = true
        engine.cancelActive()
        state.value = state.value.copy(
            statusLine = "Stopping downloads…",
            tasks = state.value.tasks.map {
                if (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DISCOVERING) {
                    it.copy(status = DownloadStatus.CANCELLED, progress = 0f, message = "Cancelled")
                } else it
            }
        )
    }

    fun downloadSingle(url: String, format: FormatPreset) {
        val clean = url.trim()
        if (clean.isBlank() || state.value.running) return
        abortRequested = false
        val task = DownloadTask(id = UUID.randomUUID().toString(), url = clean)
        state.value = UiState(running = true, statusLine = "Starting…", tasks = listOf(task))
        viewModelScope.launch {
            runOne(task, format)
            state.value = state.value.copy(
                running = false,
                statusLine = if (abortRequested) "Download stopped" else "Finished"
            )
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

        abortRequested = false
        val tasks = urls.map { DownloadTask(id = UUID.randomUUID().toString(), url = it) }
        state.value = UiState(running = true, statusLine = "Bulk queue: ${tasks.size}", tasks = tasks)
        viewModelScope.launch {
            for (task in tasks) {
                if (abortRequested) break
                runOne(task, format)
            }
            if (abortRequested) cancelRemainingQueued()
            state.value = state.value.copy(
                running = false,
                statusLine = if (abortRequested) "Bulk queue stopped" else "Bulk queue finished"
            )
        }
    }

    fun downloadCollection(collectionUrl: String, format: FormatPreset) {
        val clean = collectionUrl.trim()
        if (clean.isBlank() || state.value.running) return
        abortRequested = false
        state.value = UiState(running = true, statusLine = "Discovering collection…")
        viewModelScope.launch {
            val discovered = engine.discoverCollection(clean)

            if (abortRequested) {
                state.value = state.value.copy(running = false, statusLine = "Discovery stopped")
                return@launch
            }

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

            for (task in tasks) {
                if (abortRequested) break
                runOne(task, format)
            }

            if (abortRequested) cancelRemainingQueued()
            state.value = state.value.copy(
                running = false,
                statusLine = if (abortRequested) "Channel/profile download stopped" else "Collection finished"
            )
        }
    }

    fun updateEngine() {
        if (state.value.running) return
        state.value = state.value.copy(running = true, statusLine = "Updating downloader engine…")
        viewModelScope.launch {
            val result = engine.updateEngineStable()
            state.value = state.value.copy(
                running = false,
                statusLine = result.fold({ it }, { "Update failed: ${friendlyError(it)}" })
            )
        }
    }

    private suspend fun runOne(task: DownloadTask, format: FormatPreset) {
        if (abortRequested) {
            updateTask(task.id, DownloadStatus.CANCELLED, 0f, "Cancelled")
            return
        }

        updateTask(task.id, DownloadStatus.DOWNLOADING, 0f, "Starting")
        val result = engine.download(task.url, format) { progress, eta ->
            if (!abortRequested) {
                updateTask(
                    task.id,
                    DownloadStatus.DOWNLOADING,
                    (progress / 100f).coerceIn(0f, 1f),
                    "${progress.toInt()}% • ETA ${eta}s"
                )
            }
        }

        if (abortRequested) {
            updateTask(task.id, DownloadStatus.CANCELLED, 0f, "Stopped by user")
        } else if (result.isSuccess) {
            updateTask(task.id, DownloadStatus.COMPLETE, 1f, "Saved")
        } else {
            updateTask(task.id, DownloadStatus.FAILED, 0f, friendlyError(result.exceptionOrNull()))
        }
    }

    private fun cancelRemainingQueued() {
        state.value = state.value.copy(
            tasks = state.value.tasks.map {
                if (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DISCOVERING) {
                    it.copy(status = DownloadStatus.CANCELLED, progress = 0f, message = "Cancelled")
                } else it
            }
        )
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
            msg.contains("FACEBOOK_PUBLIC_PROFILE_UNAVAILABLE", ignoreCase = true) ->
                "Facebook Page/Profile guest discovery is blocked on this link. Try its Reels/Videos tab or direct public reel/video links."
            msg.contains("unsupported url", ignoreCase = true) && msg.contains("facebook", ignoreCase = true) ->
                "Facebook redirected to a profile/page URL that its extractor cannot enumerate yet."
            msg.contains("cannot parse data", ignoreCase = true) && msg.contains("facebook", ignoreCase = true) ->
                "Facebook changed its public page data. Tap Update Engine, then retry the public video/reel."
            msg.contains("cancel", ignoreCase = true) -> "Cancelled"
            msg.contains("login", ignoreCase = true) || msg.contains("cookies", ignoreCase = true) ->
                "This item requires platform authentication; guest mode cannot access it."
            msg.contains("403", ignoreCase = true) ->
                "Platform blocked the request (403). Try updating the engine or another public URL."
            msg.isBlank() -> "Unknown download error"
            else -> msg.take(180)
        }
    }
}
