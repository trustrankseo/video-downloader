package com.faisal.freshdownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

class DownloaderViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = DownloaderEngine(app)
    private val reviewManager = UptodownReviewManager(app)

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
            statusLine = "Stopping all active downloads…",
            tasks = state.value.tasks.map {
                if (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DISCOVERING) {
                    it.copy(status = DownloadStatus.CANCELLED, progress = 0f, message = "Cancelled")
                } else it
            }
        )
    }

    fun downloadSingle(url: String, format: FormatPreset) {
        val clean = InputUrlPolicy.normalize(url)
        if (!InputUrlPolicy.isValidWebUrl(clean) || state.value.running) return

        InputUrlPolicy.paidDrmBlockReason(clean)?.let { reason ->
            val task = DownloadTask(
                id = UUID.randomUUID().toString(),
                url = clean,
                format = format,
                status = DownloadStatus.FAILED,
                message = reason
            )
            state.value = UiState(
                running = false,
                statusLine = reason,
                tasks = listOf(task)
            )
            return
        }

        if (InputUrlPolicy.isLikelyCollectionUrl(clean)) {
            val task = DownloadTask(
                id = UUID.randomUUID().toString(),
                url = clean,
                format = format,
                status = DownloadStatus.FAILED,
                message = "Channel/profile/playlist link detected. Use the Channel tab."
            )
            state.value = UiState(
                running = false,
                statusLine = "Use Channel mode for channel/profile/playlist links.",
                tasks = listOf(task)
            )
            return
        }

        abortRequested = false
        engine.resetCancellation()
        val task = DownloadTask(id = UUID.randomUUID().toString(), url = clean, format = format)
        state.value = UiState(running = true, statusLine = "Starting download…", tasks = listOf(task))
        viewModelScope.launch {
            runTaskGroup(listOf(task), 1, "Download")
        }
    }

    fun downloadBulk(raw: String, format: FormatPreset, threads: Int = 3) {
        if (state.value.running) return
        val normalized = raw.lineSequence()
            .map { InputUrlPolicy.normalize(it) }
            .filter { InputUrlPolicy.isValidWebUrl(it) }
            .distinct()
            .toList()
        val blockedCount = normalized.count { InputUrlPolicy.isBlockedPaidDrmUrl(it) }
        val urls = normalized.filter { InputUrlPolicy.isAllowedDownloadUrl(it) }

        if (urls.isEmpty()) {
            if (blockedCount > 0) {
                state.value = UiState(
                    running = false,
                    statusLine = "$blockedCount paid/subscription/DRM link(s) blocked by policy."
                )
            }
            return
        }

        abortRequested = false
        engine.resetCancellation()
        val tasks = urls.map {
            DownloadTask(id = UUID.randomUUID().toString(), url = it, format = format)
        }
        val workers = threads.coerceIn(1, 8)
        state.value = UiState(
            running = true,
            statusLine = buildString {
                append("Bulk queue: ${tasks.size} items • $workers threads")
                if (blockedCount > 0) append(" • $blockedCount paid/DRM blocked")
            },
            tasks = tasks
        )
        viewModelScope.launch {
            runTaskGroup(tasks, workers, "Bulk queue")
        }
    }

    fun downloadCollection(collectionUrl: String, format: FormatPreset, threads: Int = 3) {
        val clean = InputUrlPolicy.normalize(collectionUrl)
        if (!InputUrlPolicy.isValidWebUrl(clean) || state.value.running) return

        InputUrlPolicy.paidDrmBlockReason(clean)?.let { reason ->
            state.value = UiState(running = false, statusLine = reason)
            return
        }

        abortRequested = false
        engine.resetCancellation()
        val workers = threads.coerceIn(1, 8)
        state.value = UiState(running = true, statusLine = "Fetching channel/profile items…")

        viewModelScope.launch {
            DownloadKeepAliveService.start(getApplication())
            try {
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

                val tasks = urls.map {
                    DownloadTask(id = UUID.randomUUID().toString(), url = it, format = format)
                }
                state.value = UiState(
                    running = true,
                    statusLine = "Found ${tasks.size} items • downloading with $workers threads",
                    tasks = tasks,
                    discoveredCount = tasks.size
                )

                runTaskGroup(tasks, workers, "Channel/profile", serviceAlreadyRunning = true)
            } finally {
                DownloadKeepAliveService.stop(getApplication())
            }
        }
    }

    fun retryTask(taskId: String, threads: Int = 1) {
        if (state.value.running) return
        val task = state.value.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status != DownloadStatus.FAILED && task.status != DownloadStatus.CANCELLED) return

        abortRequested = false
        engine.resetCancellation()
        val reset = task.copy(status = DownloadStatus.QUEUED, progress = 0f, message = "Queued for retry")
        replaceTask(reset)
        state.value = state.value.copy(running = true, statusLine = "Retrying item…")

        viewModelScope.launch {
            runTaskGroup(listOf(reset), threads.coerceIn(1, 8), "Retry")
        }
    }

    fun retryAll(threads: Int = 3) {
        if (state.value.running) return
        val retryableIds = state.value.tasks
            .filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
            .map { it.id }
            .toSet()
        if (retryableIds.isEmpty()) return

        abortRequested = false
        engine.resetCancellation()
        val resetTasks = state.value.tasks.map {
            if (it.id in retryableIds) {
                it.copy(status = DownloadStatus.QUEUED, progress = 0f, message = "Queued for retry")
            } else it
        }
        state.value = state.value.copy(
            running = true,
            statusLine = "Retrying ${retryableIds.size} items…",
            tasks = resetTasks
        )
        val targets = resetTasks.filter { it.id in retryableIds }

        viewModelScope.launch {
            runTaskGroup(targets, threads.coerceIn(1, 8), "Retry all")
        }
    }

    fun consumeReviewEvent(eventId: Long) {
        if (eventId != 0L && state.value.reviewEventId == eventId) {
            state.value = state.value.copy(reviewEventId = 0L)
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

    private suspend fun runTaskGroup(
        tasks: List<DownloadTask>,
        threads: Int,
        label: String,
        serviceAlreadyRunning: Boolean = false
    ) {
        if (tasks.isEmpty()) {
            state.value = state.value.copy(running = false)
            return
        }

        val ids = tasks.map { it.id }.toSet()
        val workers = threads.coerceIn(1, 8)
        if (!serviceAlreadyRunning) DownloadKeepAliveService.start(getApplication())

        try {
            coroutineScope {
                val semaphore = Semaphore(workers)
                tasks.map { task ->
                    async {
                        semaphore.withPermit {
                            if (abortRequested) {
                                updateTask(task.id, DownloadStatus.CANCELLED, 0f, "Cancelled")
                            } else {
                                runOne(task)
                            }
                        }
                    }
                }.awaitAll()
            }

            if (abortRequested) cancelRemainingQueued()

            val succeededNow = state.value.tasks.any {
                it.id in ids && it.status == DownloadStatus.COMPLETE
            }
            val failedNow = state.value.tasks.count {
                it.id in ids && it.status == DownloadStatus.FAILED
            }
            val cancelledNow = state.value.tasks.count {
                it.id in ids && it.status == DownloadStatus.CANCELLED
            }

            state.value = state.value.copy(
                running = false,
                statusLine = when {
                    abortRequested -> "$label stopped • $cancelledNow cancelled"
                    failedNow > 0 -> "$label finished • $failedNow failed • tap Retry"
                    else -> "$label finished"
                },
                reviewEventId = if (succeededNow && reviewManager.shouldPrompt()) System.nanoTime() else 0L
            )
        } finally {
            if (!serviceAlreadyRunning) DownloadKeepAliveService.stop(getApplication())
        }
    }

    private suspend fun runOne(task: DownloadTask) {
        if (abortRequested) {
            updateTask(task.id, DownloadStatus.CANCELLED, 0f, "Cancelled")
            return
        }

        updateTask(task.id, DownloadStatus.DOWNLOADING, 0f, "Fetching media info…")
        val result = engine.download(task.url, task.format) { progress, eta ->
            if (!abortRequested) {
                val safeProgress = (progress / 100f).coerceIn(0f, 1f)
                val message = if (progress <= 0f) {
                    "Fetching media info…"
                } else {
                    "${progress.toInt()}% • ETA ${eta}s"
                }
                updateTask(task.id, DownloadStatus.DOWNLOADING, safeProgress, message)
            }
        }

        if (abortRequested) {
            updateTask(task.id, DownloadStatus.CANCELLED, 0f, "Stopped by user")
        } else if (result.isSuccess) {
            ReferralManager(getApplication()).activatePendingReferralAfterSuccessfulDownload()
            reviewManager.recordSuccessfulDownload()
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

    @Synchronized
    private fun replaceTask(task: DownloadTask) {
        state.value = state.value.copy(
            tasks = state.value.tasks.map { if (it.id == task.id) task else it }
        )
    }

    @Synchronized
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
            msg.contains("PAID_DRM_BLOCKED", ignoreCase = true) ->
                "Paid, subscription, rental, purchase, or DRM-protected media is intentionally blocked."
            msg.contains("channel/profile/playlist", ignoreCase = true) ->
                "This is a channel/profile/playlist link. Use Channel mode."
            msg.contains("FACEBOOK_PUBLIC_PROFILE_UNAVAILABLE", ignoreCase = true) ->
                "Facebook Page/Profile is not exposing public Reels/Videos to guest mode. Direct public reel/video links can still work."
            msg.contains("INSTAGRAM_PUBLIC_PROFILE_UNAVAILABLE", ignoreCase = true) ->
                "Instagram profile discovery is unavailable in the native-only build. Try direct public reel/post links in Single or Bulk mode."
            msg.contains("PUBLIC_PROFILE_UNAVAILABLE", ignoreCase = true) ->
                "This public profile cannot be enumerated in guest mode. Try a direct public media link in Single or Bulk mode."
            msg.contains("secondary user id", ignoreCase = true) ->
                "The public-profile extractor could not resolve this account in guest mode."
            msg.contains("PUBLIC_ITEM_UNAVAILABLE", ignoreCase = true) ->
                "The source did not expose this public item to the download engine."
            msg.contains("unsupported url", ignoreCase = true) && msg.contains("facebook", ignoreCase = true) ->
                "Facebook redirected to a profile/page URL that its extractor cannot enumerate yet."
            msg.contains("cannot parse data", ignoreCase = true) && msg.contains("facebook", ignoreCase = true) ->
                "Facebook changed its public page data. Tap Update Engine, then retry the public video/reel."
            msg.contains("cancel", ignoreCase = true) -> "Cancelled"
            msg.contains("login", ignoreCase = true) || msg.contains("cookies", ignoreCase = true) ->
                "This item requires platform authentication; guest mode cannot access it."
            msg.contains("403", ignoreCase = true) ->
                "Platform blocked the request (403). Try updating the engine or another public URL."
            msg.contains("MEDIA_INFO_TIMEOUT", ignoreCase = true) ->
                "YouTube media info timed out. The extractor/client challenge stalled. Tap Retry."
            msg.contains("timed out", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) ->
                "The media server timed out. Tap Retry."
            msg.isBlank() -> "Unknown download error"
            else -> msg.take(180)
        }
    }
}
