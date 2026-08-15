package com.junkfood.seal.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.junkfood.seal.App
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.junkfood.seal.R
import com.junkfood.seal.database.objects.DownloadOperation
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private const val TAG = "DownloaderV2"

/*
 * ---------------------------------------------------------------------------------------
 * Fix for: "Unresolved reference: audioDownloadDir, videoDownloadDir"
 * "Unresolved reference: SUBTITLE_REGEX, THUMBNAIL_REGEX"
 *
 * The refactor referenced symbols (audioDownloadDir / videoDownloadDir / SUBTITLE_REGEX /
 * THUMBNAIL_REGEX) that no longer exist as globals and are not exposed by FileUtil in this
 * codebase snapshot. Rather than requiring an edit to FileUtil.kt (which we don't have in
 * scope here), these are declared locally and privately in this file so DownloaderV2.kt
 * compiles standalone. If FileUtil later grows real getAudioDownloadDir()/getVideoDownloadDir()
 * accessors or SUBTITLE_REGEX/THUMBNAIL_REGEX constants, these local declarations can simply
 * be removed and the two call sites below switched back to FileUtil.* without any other change.
 * ---------------------------------------------------------------------------------------
 */
private const val SUBTITLE_REGEX = "(?i)\\.(srt|vtt|ass|ssa|sub)$"
private const val THUMBNAIL_REGEX = "(?i)\\.(jpe?g|png|webp|bmp)$"

private fun getAudioDownloadDir(context: Context): String =
    App.audioDownloadDir

private fun getVideoDownloadDir(context: Context): String =
    App.videoDownloadDir

interface DownloaderV2 {
    fun getTaskStateMap(): SnapshotStateMap<Task, Task.State>

    fun cancel(task: Task): Boolean

    fun cancel(taskId: String): Boolean {
        return getTaskStateMap()
            .keys
            .find { it.id == taskId }
            ?.let { cancel(it) }
            ?: false
    }

    fun pause(taskId: String): Boolean {
        return getTaskStateMap()
            .keys
            .find { it.id == taskId }
            ?.let { pause(it) }
            ?: false
    }

    fun restart(task: Task)

    fun pause(task: Task): Boolean

    /** Enqueue a [Task] with an empty [Task.State]. */
    fun enqueue(task: Task)

    fun enqueue(task: Task, state: Task.State)

    fun enqueue(taskWithState: TaskFactory.TaskWithState) {
        val (task, state) = taskWithState
        enqueue(task, state)
    }

    fun remove(task: Task): Boolean

    fun prioritize(task: Task)
}

internal object FakeDownloaderV2 : DownloaderV2 {
    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return mutableStateMapOf()
    }

    override fun cancel(task: Task): Boolean = false

    override fun pause(task: Task): Boolean = false

    override fun restart(task: Task) = Unit

    override fun enqueue(task: Task) = Unit

    override fun enqueue(task: Task, state: Task.State) = Unit

    override fun remove(task: Task): Boolean = true

    override fun prioritize(task: Task) = Unit
}

/**
 * TODO:
 * - Notification
 * - Custom commands
 * - States for ViewModels
 */
@OptIn(FlowPreview::class)
class DownloaderV2Impl(
    private val appContext: Context
) : DownloaderV2, KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val taskStateMap =
        mutableStateMapOf<Task, Task.State>()

    private val retryCountMap = mutableMapOf<String, Int>()

    private val priorityTaskIds = mutableSetOf<String>()

    private val subtitleMutex = kotlinx.coroutines.sync.Mutex()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val snapshotFlow =
        snapshotFlow { taskStateMap.toMap() }
            .kotlinx.coroutines.flow.sample(500)

    override fun prioritize(task: Task) {
        priorityTaskIds.add(task.id)
        doYourWork()
    }

    init {
        try {
            App.connectivityManager.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    Log.d(TAG, "Network connection active. Processing queue…")
                    scope.launch(Dispatchers.Default) { doYourWork() }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Could not register network callback", e)
        }

        scope.launch(Dispatchers.Default) {
            snapshotFlow
                .onEach { doYourWork() }
                .map { it.countRunning() }
                .distinctUntilChanged()
                .collect { runningCount ->
                    if (
                        runningCount > 0 &&
                        NotificationUtil.areNotificationsEnabled()
                    ) {
                        App.startService()
                    } else {
                        App.stopService()
                    }
                }
        }

        scope.launch(Dispatchers.IO) {
            // Don't write before we read.
            enqueueFromBackup()

            snapshotFlow
                .map { it.trimTaskHistory() }
                .distinctUntilChanged()
                .collect { taskMap ->
                    taskMap.forEach {
                        Log.d(TAG, it.value.viewState.title)
                    }

                    PreferenceUtil.encodeTaskListBackup(taskMap)
                }
        }
    }

    private fun enqueueFromBackup() {
        val taskList =
            PreferenceUtil
                .decodeTaskListBackup()
                .mapValues { (_, state) ->
                    val previousState = state.downloadState

                    val downloadState =
                        when (previousState) {
                            is FetchingInfo,
                            Idle -> {
                                Canceled(action = FetchInfo)
                            }

                            is Running -> {
                                Canceled(
                                    action = Download,
                                    progress = previousState.progress
                                )
                            }

                            ReadyWithInfo -> {
                                Canceled(
                                    action = Download,
                                    progress = null
                                )
                            }

                            else -> previousState
                        }

                    state.copy(downloadState = downloadState)
                }

        taskList.forEach(::enqueue)
    }

    private fun Map<Task, Task.State>.countRunning(): Int {
        return count { (_, state) ->
            state.downloadState is Running ||
                state.downloadState is FetchingInfo
        }
    }

    private fun Map<Task, Task.State>.trimTaskHistory(
        maxSize: Int = 100
    ): Map<Task, Task.State> {
        return entries
            .sortedWith(
                compareByDescending<Map.Entry<Task, Task.State>> {
                    it.value.downloadState is Running ||
                        it.value.downloadState is FetchingInfo
                }.thenByDescending {
                    it.key.timeCreated
                }
            )
            .take(maxSize)
            .associate { it.toPair() }
    }

    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return taskStateMap
    }

    override fun enqueue(task: Task) {
        taskStateMap +=
            task to
                Task.State(
                    downloadState = Idle,
                    videoInfo = null,
                    viewState =
                        Task.ViewState(
                            url = task.url,
                            title = task.url
                        )
                )
    }

    override fun enqueue(
        task: Task,
        state: Task.State
    ) {
        taskStateMap += task to state
    }

    /**
     * The caller is responsible for stopping the [task]
     * before removing it.
     *
     * @return true if the task was removed.
     */
    override fun remove(task: Task): Boolean {
        if (taskStateMap.containsKey(task)) {
            taskStateMap.remove(task)
            return true
        }

        return false
    }

    override fun cancel(task: Task): Boolean {
        return task.cancelImpl(isPaused = false)
    }

    override fun pause(task: Task): Boolean {
        return task.cancelImpl(isPaused = true)
    }

    override fun restart(task: Task) {
        task.restartImpl()
    }

    private var Task.state: Task.State
        get() = taskStateMap[this]!!
        set(value) {
            taskStateMap[this] = value
        }

    private var Task.downloadState: DownloadState
        get() = state.downloadState
        set(value) {
            val previousState = state
            taskStateMap[this] =
                previousState.copy(downloadState = value)
        }

    private var Task.info: VideoInfo?
        get() = state.videoInfo
        set(value) {
            val previousState = state
            taskStateMap[this] =
                previousState.copy(videoInfo = value)
        }

    private var Task.viewState: Task.ViewState
        get() = state.viewState
        set(value) {
            val previousState = state
            taskStateMap[this] =
                previousState.copy(viewState = value)
        }

    private val Task.notificationId: Int
        get() = id.hashCode()

    /** Processes pending tasks, prioritizing downloads in strict playlist order. */
    private fun doYourWork() {
        val maxConcurrent = PreferenceUtil.getMaxConcurrentDownloads().coerceAtLeast(1)
        
        val runningSubtitleCount = taskStateMap.count { (task, state) ->
            (state.downloadState is Running || state.downloadState is FetchingInfo) &&
                task.preferences.skipDownload && task.preferences.downloadSubtitle
        }

        while (taskStateMap.countRunning() < maxConcurrent) {
            val pendingEntry = taskStateMap
                .entries
                .sortedWith(
                    compareBy<Map.Entry<Task, Task.State>> {
                        // High priority task moved to top
                        if (priorityTaskIds.contains(it.key.id)) 0 else 1
                    }.thenBy {
                        // Prioritize ReadyWithInfo over Idle
                        if (it.value.downloadState == ReadyWithInfo) 0 else 1
                    }.thenBy {
                        // Strict numerical playlist order (#001, #002, #003...)
                        (it.key.type as? TypeInfo.Playlist)?.index ?: Int.MAX_VALUE
                    }.thenBy {
                        it.key.timeCreated
                    }
                )
                .firstOrNull { (task, state) ->
                    val isPending = state.downloadState == ReadyWithInfo || state.downloadState == Idle
                    val isSubtitle = task.preferences.skipDownload && task.preferences.downloadSubtitle
                    if (isPending) {
                        // For subtitles: ensure strictly 1 active subtitle task at a time (sequential execution)
                        if (isSubtitle && (runningSubtitleCount > 0 || isSubtitleOnlyQueue())) {
                            false
                        } else {
                            true
                        }
                    } else {
                        false
                    }
                } ?: break

            val (task, state) = pendingEntry
            when (state.downloadState) {
                Idle -> task.prepare()

                ReadyWithInfo -> task.download()

                else -> break
            }
        }
    }

    private fun isSubtitleOnlyQueue(): Boolean {
        return taskStateMap.any { (task, state) ->
            (state.downloadState is Running || state.downloadState is FetchingInfo) &&
                task.preferences.skipDownload && task.preferences.downloadSubtitle
        }
    }

    private fun Task.prepare() {
        if (downloadState != Idle) return

        // Auto-skip if the file already exists on disk
        val existingPath = checkExistingFile(this)
        if (existingPath != null) {
            Log.d(TAG, "File already exists on disk, auto-completing: $existingPath")
            downloadState = Completed(existingPath)

            val text = appContext.getString(R.string.status_completed)
            FileUtil.createIntentForOpeningFile(existingPath)
                .run {
                    NotificationUtil.finishNotification(
                        notificationId,
                        title = viewState.title,
                        text = text,
                        intent = if (this != null) {
                            PendingIntent.getActivity(
                                appContext, 0, this, PendingIntent.FLAG_IMMUTABLE
                            )
                        } else null
                    )
                }
            return
        }

        if (type is TypeInfo.CustomCommand) {
            execute()
        } else {
            fetchInfo()
        }
    }

    private fun Task.fetchInfo() {
        if (downloadState != Idle) return

        val task = this
        val taskInfo = task.type

        val isPlaylist = taskInfo is TypeInfo.Playlist && !taskInfo.isFallback
        val isIndividualUrlValid = viewState.url.startsWith("http://", ignoreCase = true) || viewState.url.startsWith("https://", ignoreCase = true)

        val fetchUrl = if (isPlaylist && isIndividualUrlValid) {
            viewState.url
        } else {
            task.url
        }

        val isDirectVideoUrl = fetchUrl.contains("watch?v=", ignoreCase = true) || fetchUrl.contains("youtu.be/", ignoreCase = true) || fetchUrl.contains("/shorts/", ignoreCase = true)

        val playlistIndex =
            if (isPlaylist && !isDirectVideoUrl) {
                (taskInfo as TypeInfo.Playlist).index
            } else {
                null
            }

        scope
            .launch(Dispatchers.Default) {
                val isSubtitlePlaylist = task.preferences.skipDownload && isPlaylist
                var acquiredSubtitleLock = false
                try {
                    if (isSubtitlePlaylist) {
                        subtitleMutex.lock()
                        acquiredSubtitleLock = true
                        kotlinx.coroutines.delay(100L)
                    }
                    var retryCount = 0
                    val maxRetries = 3
                    var result: Result<VideoInfo>? = null
                    
                    while (retryCount <= maxRetries) {
                        if (retryCount > 0) {
                            kotlinx.coroutines.delay((1000L * retryCount).coerceAtMost(3000L))
                        }
                        
                        val fetchRes = DownloadUtil
                            .fetchVideoInfoFromUrl(
                                url = fetchUrl,
                                playlistIndex = playlistIndex,
                                preferences = task.preferences,
                                taskKey = task.id
                            )
                            
                        if (fetchRes.isSuccess) {
                            result = fetchRes
                            break
                        } else {
                            val th = fetchRes.exceptionOrNull()
                            if (th is YoutubeDL.CanceledException) {
                                result = fetchRes
                                break
                            }
                            // If we hit the max retries, break and report the error
                            if (retryCount == maxRetries) {
                                result = fetchRes
                                break
                            }
                            retryCount++
                        }
                    }
                    
                    result!!
                        .onSuccess { videoInfo ->
                            task.info = videoInfo
                            task.downloadState = ReadyWithInfo
                            task.viewState =
                                Task.ViewState.fromVideoInfo(videoInfo, task.preferences)
                        }
                        .onFailure { throwable ->
                            if (throwable is YoutubeDL.CanceledException) {
                                return@onFailure
                            }

                            if (isPlaylist && fetchUrl.isNotBlank()) {
                                val playlistType = taskInfo as TypeInfo.Playlist
                                val cleanTitle = task.viewState.title.removePrefix("[Subtitle] ").ifBlank { "Track_${playlistType.index}" }
                                val extractedId = when {
                                    fetchUrl.contains("v=") -> fetchUrl.substringAfter("v=").substringBefore("&").substringBefore("?")
                                    fetchUrl.contains("youtu.be/") -> fetchUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                                    else -> task.id
                                }
                                val fallbackInfo = VideoInfo(
                                    id = extractedId,
                                    title = cleanTitle,
                                    webpageUrl = fetchUrl,
                                    originalUrl = fetchUrl,
                                    uploader = playlistType.playlistTitle,
                                    extractor = "Youtube",
                                    extractorKey = "Youtube"
                                )
                                task.info = fallbackInfo
                                task.downloadState = ReadyWithInfo
                                return@onFailure
                            }

                            task.downloadState =
                                Error(
                                    throwable = throwable,
                                    action = FetchInfo
                                )

                            NotificationUtil.notifyError(
                                title = task.viewState.title,
                                textId = R.string.download_error_professional,
                                notificationId = task.notificationId,
                                report = throwable.stackTraceToString()
                            )
                        }
                } finally {
                    if (acquiredSubtitleLock) {
                        subtitleMutex.unlock()
                    }
                }
            }
            .also { job ->
                task.downloadState =
                    FetchingInfo(
                        job = job,
                        taskId = task.id
                    )
            }
    }

    private fun Task.download() {
        if (downloadState != ReadyWithInfo || info == null) return

        // Keep an explicit reference because this Task is used
        // inside nested Result callbacks.
        val task = this

        if (task.type is TypeInfo.CustomCommand) {
            task.execute()
            return
        }

        scope
            .launch(Dispatchers.Default) {
                val playlistItem =
                    (task.type as? TypeInfo.Playlist)?.index ?: 0

                val sourcePlaylistUrl =
                    if (playlistItem != 0) {
                        (task.type as? TypeInfo.Playlist)?.playlistUrl ?: ""
                    } else {
                        ""
                    }

                val isSubtitlePlaylist = task.preferences.skipDownload && task.type is TypeInfo.Playlist
                var acquiredSubtitleLock = false

                try {
                    if (isSubtitlePlaylist) {
                        subtitleMutex.lock()
                        acquiredSubtitleLock = true
                        kotlinx.coroutines.delay(100L)
                    }

                    var lastUpdateTime = 0L
                    DownloadUtil
                        .downloadVideo(
                            videoInfo = task.info,
                            playlistUrl = sourcePlaylistUrl,
                            playlistItem = playlistItem,
                            taskId = task.id,
                            downloadPreferences = task.preferences,
                            skipDownload = task.preferences.skipDownload,
                            isFallback = (task.type as? TypeInfo.Playlist)?.isFallback ?: false,
                            fallbackPlaylistTitle = (task.type as? TypeInfo.Playlist)?.playlistTitle ?: "",
                            progressCallback = { progressPercentage, _, text ->
                                val isMerging = text.contains("Merger", ignoreCase = true) ||
                                        text.contains("ffmpeg", ignoreCase = true) ||
                                        text.contains("Postprocessor", ignoreCase = true) ||
                                        text.contains("Merging", ignoreCase = true) ||
                                        text.contains("ExtractAudio", ignoreCase = true)

                                val effectiveProgress = when {
                                    isMerging -> 0.95f
                                    else -> (progressPercentage / 100f).coerceIn(0f, 0.99f)
                                }
                                val effectiveText = if (isMerging) "جاري دمج الصوت والفيديو..." else text

                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastUpdateTime > 250L || isMerging) {
                                    lastUpdateTime = currentTime

                                    when (val previousState = task.downloadState) {
                                        is Running -> {
                                            task.downloadState = previousState.copy(
                                                progress = effectiveProgress,
                                                progressText = effectiveText
                                            )

                                            NotificationUtil.notifyProgress(
                                                notificationId = task.notificationId,
                                                progress = (effectiveProgress * 100).toInt(),
                                                text = effectiveText,
                                                title = task.viewState.title,
                                                taskId = task.id
                                            )
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        )
                        .mapCatching { pathList ->
                            if (!task.preferences.skipDownload) {
                                if (pathList.isEmpty()) {
                                    throw Exception("No files were downloaded. yt-dlp may have skipped or failed.")
                                }
                                val firstFile = java.io.File(pathList.first())
                                if (!firstFile.exists() || firstFile.length() == 0L) {
                                    throw Exception("Downloaded file is empty or does not exist (0 bytes).")
                                }
                            } else {
                                if (pathList.isEmpty()) {
                                    throw Exception("لم يتم العثور على ملف ترجمة متاح لهذا المقطع للتحميل (No subtitle file was downloaded)")
                                }
                            }

                            if (task.preferences.removeMusic && !task.preferences.skipDownload && pathList.isNotEmpty()) {
                                when (val previousState = task.downloadState) {
                                    is Running -> {
                                        task.downloadState = previousState.copy(
                                            progress = 0.96f,
                                            progressText = "جاري عزل الصوت وإزالة الموسيقى..."
                                        )
                                        NotificationUtil.notifyProgress(
                                            notificationId = task.notificationId,
                                            progress = 96,
                                            text = "جاري عزل الصوت وإزالة الموسيقى...",
                                            title = task.viewState.title,
                                            taskId = task.id
                                        )
                                    }
                                    else -> Unit
                                }
                                com.junkfood.seal.util.MusicRemovalEngine.processFiles(
                                    filePaths = pathList,
                                    isAudioOnly = DownloadUtil.isAudioOnlyDownload(task.preferences, task.info ?: VideoInfo(id = task.id)),
                                    onProgress = { percent, msg ->
                                        when (val previousState = task.downloadState) {
                                            is Running -> {
                                                val scaledProgress = 0.95f + (percent / 100f) * 0.04f
                                                task.downloadState = previousState.copy(
                                                    progress = scaledProgress,
                                                    progressText = msg
                                                )
                                                NotificationUtil.notifyProgress(
                                                    notificationId = task.notificationId,
                                                    progress = (scaledProgress * 100).toInt(),
                                                    text = msg,
                                                    title = task.viewState.title,
                                                    taskId = task.id
                                                )
                                            }
                                            else -> Unit
                                        }
                                    }
                                )
                            } else {
                                pathList
                            }
                        }
                        .onSuccess { pathList ->
                            val path =
                                pathList.firstOrNull()

                            if (path != null) {
                                val actualLen = java.io.File(path).length().toDouble()
                                if (actualLen > 0) {
                                    task.viewState = task.viewState.copy(fileSizeApprox = actualLen)
                                }
                            }

                            task.downloadState =
                                Completed(path)

                            com.junkfood.seal.util.DatabaseUtil
                                .insertDownloadOperation(
                                    DownloadOperation(
                                        url = task.url,
                                        title =
                                            task.viewState.title,
                                        status = "Completed",
                                        timestamp =
                                            System.currentTimeMillis(),
                                        filePath = path,
                                        playlistIndex =
                                            (
                                                task.type
                                                    as? TypeInfo.Playlist
                                            )?.index
                                    )
                                )

                            val text =
                                appContext.getString(
                                    if (pathList.isEmpty()) {
                                        R.string.status_completed
                                    } else {
                                        R.string
                                            .download_finish_notification
                                    }
                                )

                            val openFileIntent = FileUtil
                                .createIntentForOpeningFile(
                                    pathList.firstOrNull()
                                )
                            val notifPendingIntent = if (openFileIntent != null) {
                                PendingIntent.getActivity(
                                    appContext, 0, openFileIntent,
                                    PendingIntent.FLAG_IMMUTABLE
                                )
                            } else {
                                // Fallback: open main activity when file intent fails
                                val launchIntent = Intent(appContext, com.junkfood.seal.MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                PendingIntent.getActivity(
                                    appContext, 0, launchIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                            }
                            NotificationUtil.finishNotification(
                                notificationId = task.notificationId,
                                title = task.viewState.title,
                                text = text,
                                filePath = path,
                                intent = notifPendingIntent
                            )
                        }
                        .onFailure { throwable ->
                            if (
                                throwable
                                    is YoutubeDL.CanceledException
                            ) {
                                return@onFailure
                            }

                            if (throwable is IllegalStateException && throwable.message?.contains("FFmpeg", ignoreCase = true) == true) {
                                // Skip retries for missing FFmpeg
                            } else {
                                val retries = (retryCountMap[task.id] ?: 0) + 1
                                retryCountMap[task.id] = retries

                                if (retries < 3) {
                                    val oldState = taskStateMap[task]
                                    if (oldState != null) {
                                        taskStateMap[task] = oldState.copy(downloadState = Idle)
                                        doYourWork()
                                        return@onFailure
                                    }
                                }
                            }

                            if (task.type is TypeInfo.Playlist && !(task.type as TypeInfo.Playlist).isFallback) {
                                val playlistType = task.type as TypeInfo.Playlist
                                val fallbackUrl = task.viewState.url.ifEmpty { task.url }
                                val newType = playlistType.copy(isFallback = true)
                                val fallbackTask = task.copy(
                                    url = fallbackUrl,
                                    type = newType,
                                    preferences = task.preferences.copy(downloadPlaylist = false)
                                )

                                val oldState = taskStateMap.remove(task)
                                if (oldState != null) {
                                    taskStateMap[fallbackTask] = oldState.copy(downloadState = Idle, videoInfo = null)
                                    doYourWork()
                                    return@onFailure
                                }
                            }

                            task.downloadState =
                                Error(
                                    throwable = throwable,
                                    action = Download
                                )

                            com.junkfood.seal.util.DatabaseUtil
                                .insertDownloadOperation(
                                    DownloadOperation(
                                        url = task.url,
                                        title =
                                            task.viewState.title,
                                        status = "Error",
                                        errorMessage =
                                            throwable.message,
                                        timestamp =
                                            System.currentTimeMillis(),
                                        playlistIndex =
                                            (
                                                task.type
                                                    as? TypeInfo.Playlist
                                            )?.index
                                    )
                                )

                            NotificationUtil.notifyError(
                                title = task.viewState.title,
                                textId = R.string.download_error_professional,
                                notificationId = task.notificationId,
                                report = throwable.stackTraceToString(),
                                taskId = task.id
                            )
                        }
                } finally {
                    if (acquiredSubtitleLock) {
                        subtitleMutex.unlock()
                    }
                    checkPlaylistCompletion(task)
                }
            }
            .also { job ->
                task.downloadState =
                    Running(
                        job = job,
                        taskId = task.id
                    )
            }
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
            state is Completed || state is Error || state is Task.DownloadState.Canceled
        }

        if (allFinished) {
            val verificationItems = playlistTasks.mapNotNull { task ->
                val type = task.type as? TypeInfo.Playlist ?: return@mapNotNull null
                PlaylistVerifier.VerificationItem(
                    index = type.index,
                    title = task.viewState.title,
                    url = task.viewState.url,
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

    private fun Task.cancelImpl(
        isPaused: Boolean = false
    ): Boolean {
        when (val previousState = downloadState) {
            is DownloadState.Cancelable -> {
                YoutubeDL.destroyProcessById(
                    previousState.taskId
                )

                previousState.job.cancel()

                val progress =
                    if (previousState is Running) {
                        previousState.progress
                    } else {
                        null
                    }

                NotificationUtil.cancelNotification(
                    notificationId
                )

                downloadState =
                    DownloadState.Canceled(
                        action = previousState.action,
                        progress = progress,
                        isPaused = isPaused
                    )

                return true
            }

            Idle -> {
                downloadState =
                    DownloadState.Canceled(
                        action = FetchInfo,
                        isPaused = isPaused
                    )
            }

            ReadyWithInfo -> {
                downloadState =
                    DownloadState.Canceled(
                        action = Download,
                        isPaused = isPaused
                    )
            }

            else -> {
                return false
            }
        }

        return true
    }

    private fun Task.restartImpl() {
        when (val previousState = downloadState) {
            is DownloadState.Restartable -> {
                downloadState =
                    when (previousState.action) {
                        Download -> ReadyWithInfo
                        FetchInfo -> Idle
                    }
            }

            else -> {
                Log.w(TAG, "Task cannot be restarted from state: $downloadState")
            }
        }
    }

    /**
     * Execute a custom command task.
     *
     * @see Task.TypeInfo.CustomCommand
     */
    private fun Task.execute() {
        if (downloadState != Idle || type !is TypeInfo.CustomCommand) return

        val task = this
        val template = type.template

        scope
            .launch {
                DownloadUtil
                    .executeCustomCommandTask(
                        task.url,
                        task.id,
                        template,
                        task.preferences
                    ) {
                            progressPercentage,
                            _,
                            text ->

                        val progress =
                            progressPercentage / 100f

                        when (
                            val previousState =
                                task.downloadState
                        ) {
                            is Running -> {
                                task.downloadState =
                                    previousState.copy(
                                        progress = progress,
                                        progressText = text
                                    )

                                NotificationUtil
                                    .makeNotificationForCustomCommand(
                                        notificationId =
                                            task.notificationId,
                                        taskId = task.id,
                                        progress =
                                            progressPercentage.toInt(),
                                        templateName =
                                            template.name,
                                        taskUrl = task.url,
                                        text = text
                                    )
                            }

                            else -> Unit
                        }
                    }
                    .onFailure { throwable ->
                        if (
                            throwable
                                is YoutubeDL.CanceledException
                        ) {
                            return@onFailure
                        }

                        task.downloadState =
                            Error(
                                throwable = throwable,
                                action = Download
                            )

                        NotificationUtil.notifyError(
                            title = task.viewState.title,
                            textId = R.string.download_error_professional,
                            notificationId =
                                task.notificationId,
                            report =
                                throwable.stackTraceToString()
                        )
                    }
                    .onSuccess {
                        task.downloadState =
                            Completed(null)

                        val text =
                            appContext.getString(
                                R.string.status_completed
                            )

                        NotificationUtil.finishNotification(
                            notificationId =
                                task.notificationId,
                            title =
                                task.viewState.title,
                            text = text,
                            intent = null
                        )
                    }
            }
            .also { job ->
                task.downloadState =
                    Running(
                        job = job,
                        taskId = task.id
                    )
            }
    }

    private fun checkExistingFile(task: Task): String? {
        val preferences = task.preferences
        val isSubtitleOnly = preferences.skipDownload && preferences.downloadSubtitle
        val playlistType = task.type as? TypeInfo.Playlist
        val cleanPlaylistName = FileUtil.cleanFileName(playlistType?.playlistTitle.orEmpty())

        val baseDir = if (preferences.extractAudio) {
            if (preferences.privateDirectory) appContext.filesDir.absolutePath else getAudioDownloadDir(appContext)
        } else {
            if (preferences.privateDirectory) appContext.filesDir.absolutePath else getVideoDownloadDir(appContext)
        }

        val candidateDirs = mutableListOf<java.io.File>()
        if (isSubtitleOnly && cleanPlaylistName.isNotEmpty()) {
            candidateDirs.add(java.io.File(baseDir, "[Subtitles] $cleanPlaylistName"))
        } else if (preferences.subdirectoryPlaylistTitle && cleanPlaylistName.isNotEmpty()) {
            candidateDirs.add(java.io.File(baseDir, cleanPlaylistName))
        }
        candidateDirs.add(java.io.File(baseDir))

        val rawUrl = task.url.ifEmpty { task.viewState.url }
        val extractedId = when {
            rawUrl.contains("v=") -> rawUrl.substringAfter("v=").substringBefore("&").substringBefore("?")
            rawUrl.contains("youtu.be/") -> rawUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            rawUrl.contains("/shorts/") -> rawUrl.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
            else -> ""
        }.ifEmpty { task.info?.id.orEmpty() }

        val rawTitle = task.viewState.title.removePrefix("[Subtitle] ").replace(Regex("^#\\d+\\s*"), "").trim()
        val isUrlTitle = rawTitle.startsWith("http://", ignoreCase = true) || rawTitle.startsWith("https://", ignoreCase = true)
        val cleanTitleStr = if (!isUrlTitle) FileUtil.cleanFileName(rawTitle) else ""

        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.isDirectory) continue

            val files = dir.walkTopDown().maxDepth(2).filter { file ->
                file.isFile &&
                !file.name.endsWith(".part", ignoreCase = true) &&
                !file.name.endsWith(".ytdl", ignoreCase = true) &&
                !file.name.endsWith(".tmp", ignoreCase = true) &&
                file.length() > (if (isSubtitleOnly) 10L else 1024L)
            }.toList()

            for (file in files) {
                val fileName = file.name
                val isSubFile = fileName.contains(Regex(SUBTITLE_REGEX))

                if (isSubtitleOnly && !isSubFile) continue
                if (!isSubtitleOnly && isSubFile) continue
                if (!isSubtitleOnly && fileName.contains(Regex(THUMBNAIL_REGEX))) continue

                // 1. Exact Video ID match (minimum 5 chars)
                if (extractedId.length >= 5 && fileName.contains(extractedId)) {
                    return file.absolutePath
                }

                // 2. Strict cleaned title match (only if not a raw URL)
                if (cleanTitleStr.length >= 6 && fileName.contains(cleanTitleStr)) {
                    return file.absolutePath
                }
            }
        }

        return null
    }
}
