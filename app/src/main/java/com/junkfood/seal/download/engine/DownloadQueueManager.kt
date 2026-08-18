package com.junkfood.seal.download.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.junkfood.seal.App
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.MainActivity
import com.junkfood.seal.R
import com.junkfood.seal.database.objects.DownloadOperation
import com.junkfood.seal.download.PlaylistVerifier
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.Task.DownloadState
import com.junkfood.seal.download.Task.DownloadState.Canceled
import com.junkfood.seal.download.Task.DownloadState.Completed
import com.junkfood.seal.download.Task.DownloadState.Error
import com.junkfood.seal.download.Task.DownloadState.FetchingInfo
import com.junkfood.seal.download.Task.DownloadState.Idle
import com.junkfood.seal.download.Task.DownloadState.ReadyWithInfo
import com.junkfood.seal.download.Task.DownloadState.Running
import com.junkfood.seal.download.Task.RestartableAction.Download
import com.junkfood.seal.download.Task.RestartableAction.FetchInfo
import com.junkfood.seal.download.Task.TypeInfo
import com.junkfood.seal.download.engine.resilience.FileCollisionResolver
import com.junkfood.seal.download.engine.subtitle.SubtitleManager
import com.junkfood.seal.util.DatabaseUtil
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.NotificationUtil
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DownloadQueueManager
 *
 * Manages the task lifecycle, queue scheduling, priority ordering, concurrent limits,
 * network connectivity auto-recovery, and state persistence for the download subsystem.
 */
@OptIn(FlowPreview::class)
class DownloadQueueManager(
    private val appContext: Context = context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val TAG = "DownloadQueueManager"

    val taskStateMap: SnapshotStateMap<Task, Task.State> = mutableStateMapOf()

    private val retryCountMap = mutableMapOf<String, Int>()
    private val priorityTaskIds = mutableSetOf<String>()

    private val snapshotFlow =
        snapshotFlow { taskStateMap.toMap() }
            .sample(500)

    init {
        // 1. Network Connectivity Listener
        try {
            App.connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG, "Network connection restored. Processing pending download queue...")
                        scope.launch(Dispatchers.Default) { processQueue() }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not register network callback", e)
        }

        // 2. Foreground Service Controller
        // snapshotFlow must be collected on the Main thread (Compose requirement).
        // App.startService() also posts to Main internally, but we still collect on Main
        // to avoid any thread-hop timing issues with the Compose snapshot system.
        scope.launch(Dispatchers.Main) {
            snapshotFlow
                .onEach { withContext(Dispatchers.Default) { processQueue() } }
                .map { it.hasActiveTasks() }
                .distinctUntilChanged()
                .collect { hasActive ->
                    if (hasActive && NotificationUtil.areNotificationsEnabled()) {
                        App.startService()
                    } else {
                        App.stopService()
                    }
                }
        }

        // 3. Backup and State Restoration
        scope.launch(Dispatchers.IO) {
            restoreQueueFromBackup()

            snapshotFlow
                .map { it.trimTaskHistory() }
                .distinctUntilChanged()
                .collect { taskMap ->
                    PreferenceUtil.encodeTaskListBackup(taskMap)
                }
        }
    }

    fun prioritize(task: Task) {
        priorityTaskIds.add(task.id)
        processQueue()
    }

    fun enqueue(task: Task, state: Task.State? = null) {
        taskStateMap[task] = state ?: Task.State(
            downloadState = Idle,
            videoInfo = null,
            viewState = Task.ViewState(url = task.url, title = task.url)
        )
        processQueue()
    }

    fun remove(task: Task): Boolean {
        return taskStateMap.remove(task) != null
    }

    fun cancel(task: Task, isPaused: Boolean = false): Boolean {
        val currentState = taskStateMap[task]?.downloadState ?: return false

        when (currentState) {
            is DownloadState.Cancelable -> {
                YoutubeDL.destroyProcessById(currentState.taskId)
                currentState.job.cancel()
                val progress = (currentState as? Running)?.progress
                NotificationUtil.cancelNotification(task.id.hashCode())

                taskStateMap[task] = taskStateMap[task]!!.copy(
                    downloadState = Canceled(
                        action = currentState.action,
                        progress = progress,
                        isPaused = isPaused
                    )
                )
                return true
            }
            Idle -> {
                taskStateMap[task] = taskStateMap[task]!!.copy(
                    downloadState = Canceled(action = FetchInfo, isPaused = isPaused)
                )
                return true
            }
            ReadyWithInfo -> {
                taskStateMap[task] = taskStateMap[task]!!.copy(
                    downloadState = Canceled(action = Download, isPaused = isPaused)
                )
                return true
            }
            else -> return false
        }
    }

    fun restart(task: Task) {
        val state = taskStateMap[task] ?: return
        val currentDownloadState = state.downloadState
        if (currentDownloadState is DownloadState.Restartable) {
            val nextState = when (currentDownloadState.action) {
                Download -> ReadyWithInfo
                FetchInfo -> Idle
            }
            taskStateMap[task] = state.copy(downloadState = nextState)
            processQueue()
        }
    }

    /**
     * Processes pending tasks, observing concurrency limits, priorities, and playlist sequence.
     */
    fun processQueue() {
        val maxConcurrent = PreferenceUtil.getMaxConcurrentDownloads().coerceAtLeast(1)

        val runningSubtitleCount = taskStateMap.count { (task, state) ->
            (state.downloadState is Running || state.downloadState is FetchingInfo) &&
            task.preferences.skipDownload && task.preferences.downloadSubtitle
        }

        while (taskStateMap.countRunning() < maxConcurrent) {
            val pendingEntry = taskStateMap.entries
                .sortedWith(
                    compareBy<Map.Entry<Task, Task.State>> {
                        if (priorityTaskIds.contains(it.key.id)) 0 else 1
                    }.thenBy {
                        if (it.value.downloadState == ReadyWithInfo) 0 else 1
                    }.thenBy {
                        (it.key.type as? TypeInfo.Playlist)?.index ?: Int.MAX_VALUE
                    }.thenBy {
                        it.key.timeCreated
                    }
                )
                .firstOrNull { (task, state) ->
                    val isPending = state.downloadState == ReadyWithInfo || state.downloadState == Idle
                    val isSubtitle = task.preferences.skipDownload && task.preferences.downloadSubtitle
                    if (isPending) {
                        if (isSubtitle && runningSubtitleCount > 0) false else true
                    } else {
                        false
                    }
                } ?: break

            val (task, state) = pendingEntry
            when (state.downloadState) {
                Idle -> prepareTask(task)
                ReadyWithInfo -> startDownloadTask(task)
                else -> break
            }
        }
    }

    private fun prepareTask(task: Task) {
        val state = taskStateMap[task] ?: return
        if (state.downloadState != Idle) return

        if (task.type is TypeInfo.CustomCommand) {
            executeCustomCommandTask(task)
        } else {
            taskStateMap[task] = state.copy(downloadState = FetchingInfo(taskId = task.id))
            fetchInfoForTask(task)
        }
    }

    private fun fetchInfoForTask(task: Task) {
        val job = scope.launch(Dispatchers.Default) {
            val result = DownloadTaskExecutor.fetchVideoInfo(task, appContext)

            result.onSuccess { videoInfo ->
                val current = taskStateMap[task] ?: return@onSuccess
                taskStateMap[task] = current.copy(
                    info = videoInfo,
                    downloadState = ReadyWithInfo,
                    viewState = Task.ViewState.fromVideoInfo(videoInfo, task.preferences)
                )
                processQueue()
            }.onFailure { throwable ->
                if (throwable is YoutubeDL.CanceledException) return@onFailure

                val isRateLimited = SubtitleManager.isRateLimitOrBotError(throwable)
                val retries = (retryCountMap[task.id] ?: 0) + 1
                retryCountMap[task.id] = retries

                if (retries <= 2) {
                    val backoffDelay = SubtitleManager.getRetryBackoffDelayMs(retries, isRateLimited)
                    Log.w(TAG, "Task ${task.id} info fetch failed ($retries/2). Retrying after ${backoffDelay}ms...")
                    delay(backoffDelay)
                    val current = taskStateMap[task]
                    if (current != null) {
                        taskStateMap[task] = current.copy(downloadState = Idle)
                        processQueue()
                        return@onFailure
                    }
                }

                // Safe fallback for playlist items if all retries failed
                if (task.type is TypeInfo.Playlist && task.url.isNotBlank()) {
                    val playlistType = task.type as TypeInfo.Playlist
                    val cleanTitle = taskStateMap[task]?.viewState?.title
                        ?.removePrefix("[Subtitle] ")?.ifBlank { "Track_${playlistType.index}" }
                        ?: "Track_${playlistType.index}"
                    val fallbackId = FileCollisionResolver.extractVideoId(task.url, fallbackId = task.id)
                    val fallbackInfo = VideoInfo(
                        id = fallbackId,
                        title = cleanTitle,
                        webpageUrl = task.url,
                        originalUrl = task.url,
                        uploader = playlistType.playlistTitle,
                        extractor = "Youtube",
                        extractorKey = "Youtube"
                    )
                    val current = taskStateMap[task] ?: return@onFailure
                    taskStateMap[task] = current.copy(
                        info = fallbackInfo,
                        downloadState = ReadyWithInfo
                    )
                    processQueue()
                    return@onFailure
                }

                val current = taskStateMap[task] ?: return@onFailure
                taskStateMap[task] = current.copy(
                    downloadState = Error(throwable = throwable, action = FetchInfo)
                )
                NotificationUtil.notifyError(
                    title = current.viewState.title,
                    textId = R.string.download_error_professional,
                    notificationId = task.id.hashCode(),
                    report = throwable.stackTraceToString(),
                    taskId = task.id
                )

                // Cooldown and proceed to next task in queue
                val cooldown = SubtitleManager.getAntiBanDelayMs(isRateLimited)
                delay(cooldown)
                processQueue()
            }
        }

        val current = taskStateMap[task] ?: return
        taskStateMap[task] = current.copy(
            downloadState = FetchingInfo(job = job, taskId = task.id)
        )
    }

    private fun startDownloadTask(task: Task) {
        val state = taskStateMap[task] ?: return
        if (state.downloadState != ReadyWithInfo || state.videoInfo == null) return

        val job = scope.launch(Dispatchers.Default) {
            val result = DownloadTaskExecutor.executeDownload(
                task = task,
                videoInfo = state.videoInfo!!, // guarded: null check is on line 351
                onProgressUpdate = { progress, progressText ->
                    val current = taskStateMap[task] ?: return@executeDownload
                    if (current.downloadState is Running) {
                        taskStateMap[task] = current.copy(
                            downloadState = (current.downloadState as Running).copy(
                                progress = progress,
                                progressText = progressText
                            )
                        )
                        NotificationUtil.notifyProgress(
                            notificationId = task.id.hashCode(),
                            progress = (progress * 100).toInt(),
                            text = progressText,
                            title = current.viewState.title,
                            taskId = task.id
                        )
                    }
                },
                appContext = appContext,
            )

            result.onSuccess { pathList ->
                val primaryPath = pathList.firstOrNull()
                if (primaryPath != null) {
                    val actualLen = java.io.File(primaryPath).length().toDouble()
                    if (actualLen > 0) {
                        val current = taskStateMap[task]
                        if (current != null) {
                            taskStateMap[task] = current.copy(
                                viewState = current.viewState.copy(fileSizeApprox = actualLen)
                            )
                        }
                    }
                }

                val current = taskStateMap[task] ?: return@onSuccess
                taskStateMap[task] = current.copy(downloadState = Completed(primaryPath))

                DatabaseUtil.insertDownloadOperation(
                    DownloadOperation(
                        url = task.url,
                        title = current.viewState.title,
                        status = "Completed",
                        timestamp = System.currentTimeMillis(),
                        filePath = primaryPath,
                        playlistIndex = (task.type as? TypeInfo.Playlist)?.index
                    )
                )

                val notifText = appContext.getString(
                    if (pathList.isEmpty()) R.string.status_completed else R.string.download_finish_notification
                )
                val openIntent = FileUtil.createIntentForOpeningFile(primaryPath)
                val notifPendingIntent = if (openIntent != null) {
                    PendingIntent.getActivity(appContext, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
                } else {
                    val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    PendingIntent.getActivity(appContext, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                }

                NotificationUtil.finishNotification(
                    notificationId = task.id.hashCode(),
                    title = current.viewState.title,
                    text = notifText,
                    filePath = primaryPath,
                    intent = notifPendingIntent
                )

                checkPlaylistCompletion(task)

                // If this is a subtitle playlist task, apply polite pacing delay before the next item to prevent YouTube ban
                val isSubtitlePlaylist = task.preferences.skipDownload && task.type is TypeInfo.Playlist
                if (isSubtitlePlaylist) {
                    delay(SubtitleManager.getAntiBanDelayMs(isRateLimited = false))
                }

                processQueue()
            }.onFailure { throwable ->
                if (throwable is YoutubeDL.CanceledException) return@onFailure

                val isRateLimited = SubtitleManager.isRateLimitOrBotError(throwable)
                val retries = (retryCountMap[task.id] ?: 0) + 1
                retryCountMap[task.id] = retries

                // 2 internal retries with backoff delay
                if (retries <= 2 && (throwable !is IllegalStateException || throwable.message?.contains("FFmpeg", ignoreCase = true) != true)) {
                    val backoffDelay = SubtitleManager.getRetryBackoffDelayMs(retries, isRateLimited)
                    Log.w(TAG, "Task ${task.id} download failed ($retries/2). Retrying after ${backoffDelay}ms: ${throwable.message}")
                    delay(backoffDelay)

                    val current = taskStateMap[task]
                    if (current != null) {
                        taskStateMap[task] = current.copy(downloadState = ReadyWithInfo)
                        processQueue()
                        return@onFailure
                    }
                }

                // If this is a playlist item, attempt fallback task if available
                if (task.type is TypeInfo.Playlist && !(task.type as TypeInfo.Playlist).isFallback) {
                    val playlistType = task.type as TypeInfo.Playlist
                    val fallbackUrl = taskStateMap[task]?.viewState?.url?.ifEmpty { task.url } ?: task.url
                    val fallbackTask = task.copy(
                        url = fallbackUrl,
                        type = playlistType.copy(isFallback = true),
                        preferences = task.preferences.copy(downloadPlaylist = false)
                    )
                    val oldState = taskStateMap.remove(task)
                    if (oldState != null && oldState.videoInfo != null) {
                        taskStateMap[fallbackTask] = oldState.copy(downloadState = ReadyWithInfo)
                        processQueue()
                        return@onFailure
                    }
                }

                // Permanent failure for this item: Mark as Error and gracefully advance to the next item
                val current = taskStateMap[task] ?: return@onFailure
                taskStateMap[task] = current.copy(
                    downloadState = Error(throwable = throwable, action = Download)
                )

                DatabaseUtil.insertDownloadOperation(
                    DownloadOperation(
                        url = task.url,
                        title = current.viewState.title,
                        status = "Error",
                        errorMessage = throwable.message,
                        timestamp = System.currentTimeMillis(),
                        playlistIndex = (task.type as? TypeInfo.Playlist)?.index
                    )
                )

                NotificationUtil.notifyError(
                    title = current.viewState.title,
                    textId = R.string.download_error_professional,
                    notificationId = task.id.hashCode(),
                    report = throwable.stackTraceToString(),
                    taskId = task.id
                )

                checkPlaylistCompletion(task)

                // Anti-ban cooling-off delay before processing the next item in the playlist
                val cooldownDelay = SubtitleManager.getAntiBanDelayMs(isRateLimited)
                Log.d(TAG, "Item ${task.id} failed after retries. Applying anti-ban cooldown (${cooldownDelay}ms) before next item...")
                delay(cooldownDelay)

                processQueue()
            }
        }

        taskStateMap[task] = state.copy(
            downloadState = Running(job = job, taskId = task.id)
        )
    }

    private fun executeCustomCommandTask(task: Task) {
        val template = (task.type as? TypeInfo.CustomCommand)?.template ?: return
        val state = taskStateMap[task] ?: return

        val job = scope.launch(Dispatchers.Default) {
            val result = DownloadTaskExecutor.executeCustomCommand(
                task = task,
                template = template,
                onProgressUpdate = { progress, progressText ->
                    val current = taskStateMap[task] ?: return@executeCustomCommand
                    if (current.downloadState is Running) {
                        taskStateMap[task] = current.copy(
                            downloadState = (current.downloadState as Running).copy(
                                progress = progress,
                                progressText = progressText
                            )
                        )
                        NotificationUtil.makeNotificationForCustomCommand(
                            notificationId = task.id.hashCode(),
                            taskId = task.id,
                            progress = (progress * 100).toInt(),
                            templateName = template.name,
                            taskUrl = task.url,
                            text = progressText
                        )
                    }
                },
                appContext = appContext
            )

            result.onSuccess {
                val current = taskStateMap[task] ?: return@onSuccess
                taskStateMap[task] = current.copy(downloadState = Completed(null))
                NotificationUtil.finishNotification(
                    notificationId = task.id.hashCode(),
                    title = current.viewState.title,
                    text = appContext.getString(R.string.status_completed),
                    intent = null
                )
                processQueue()
            }.onFailure { throwable ->
                if (throwable is YoutubeDL.CanceledException) return@onFailure
                val current = taskStateMap[task] ?: return@onFailure
                taskStateMap[task] = current.copy(
                    downloadState = Error(throwable = throwable, action = Download)
                )
                NotificationUtil.notifyError(
                    title = current.viewState.title,
                    textId = R.string.download_error_professional,
                    notificationId = task.id.hashCode(),
                    report = throwable.stackTraceToString()
                )
                processQueue()
            }
        }

        taskStateMap[task] = state.copy(
            downloadState = Running(job = job, taskId = task.id)
        )
    }

    private fun checkPlaylistCompletion(completedTask: Task) {
        val playlistType = completedTask.type as? TypeInfo.Playlist ?: return
        val playlistUrl = playlistType.playlistUrl
        if (playlistUrl.isEmpty()) return

        val playlistTasks = taskStateMap.keys.filter {
            val type = it.type as? TypeInfo.Playlist ?: return@filter false
            type.playlistUrl == playlistUrl
        }
        if (playlistTasks.isEmpty()) return

        val allFinished = playlistTasks.all { task ->
            val state = taskStateMap[task]?.downloadState
            state is Completed || state is Error || state is Canceled
        }

        if (allFinished) {
            val verificationItems = playlistTasks.mapNotNull { task ->
                val type = task.type as? TypeInfo.Playlist ?: return@mapNotNull null
                val viewState = taskStateMap[task]?.viewState
                PlaylistVerifier.VerificationItem(
                    index = type.index,
                    title = viewState?.title ?: task.url,
                    url = viewState?.url ?: task.url,
                    playlistUrl = type.playlistUrl,
                    playlistTitle = type.playlistTitle,
                    preferences = task.preferences
                )
            }.sortedBy { it.index }

            if (verificationItems.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    PlaylistVerifier.verifyAndRetryPlaylist(verificationItems)
                }
            }
        }
    }

    private fun restoreQueueFromBackup() {
        val taskList = PreferenceUtil.decodeTaskListBackup().mapValues { (_, state) ->
            val previousState = state.downloadState
            val downloadState = when (previousState) {
                is FetchingInfo, Idle -> Canceled(action = FetchInfo)
                is Running -> Canceled(action = Download, progress = previousState.progress)
                ReadyWithInfo -> Canceled(action = Download, progress = null)
                else -> previousState
            }
            state.copy(downloadState = downloadState)
        }
        taskList.forEach { (task, state) ->
            taskStateMap[task] = state
        }
    }

    private fun Map<Task, Task.State>.countRunning(): Int {
        return count { (_, state) ->
            state.downloadState is Running || state.downloadState is FetchingInfo
        }
    }

    /**
     * Returns true if there are any tasks that are currently active or waiting to be processed.
     * Used to decide whether the foreground service should be alive.
     * Includes Running, FetchingInfo, Idle (waiting to fetch), and ReadyWithInfo (waiting to download).
     */
    private fun Map<Task, Task.State>.hasActiveTasks(): Boolean {
        return any { (_, state) ->
            when (state.downloadState) {
                is Running, is FetchingInfo, Idle, ReadyWithInfo -> true
                else -> false
            }
        }
    }

    private fun Map<Task, Task.State>.trimTaskHistory(maxSize: Int = 100): Map<Task, Task.State> {
        return entries
            .sortedWith(
                compareByDescending<Map.Entry<Task, Task.State>> {
                    it.value.downloadState is Running || it.value.downloadState is FetchingInfo
                }.thenByDescending {
                    it.key.timeCreated
                }
            )
            .take(maxSize)
            .associate { it.toPair() }
    }

    private fun Task.State.copy(
        info: VideoInfo? = this.videoInfo,
        downloadState: DownloadState = this.downloadState,
        viewState: Task.ViewState = this.viewState,
    ): Task.State = Task.State(
        downloadState = downloadState,
        videoInfo = info,
        viewState = viewState,
    )
}
