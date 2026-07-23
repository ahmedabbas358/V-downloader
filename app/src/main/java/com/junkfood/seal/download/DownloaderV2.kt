package com.junkfood.seal.download

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.junkfood.seal.App
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

    private val snapshotFlow =
        snapshotFlow { taskStateMap.toMap() }

    init {
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

    /** Processes pending tasks, prioritizing downloads. */
    private fun doYourWork() {
        if (
            taskStateMap.countRunning() >=
            PreferenceUtil.getMaxConcurrentDownloads()
        ) {
            return
        }

        taskStateMap
            .entries
            .sortedBy { (_, state) ->
                state.downloadState
            }
            .firstOrNull { (_, state) ->
                state.downloadState == ReadyWithInfo ||
                    state.downloadState == Idle
            }
            ?.let { (task, state) ->
                when (state.downloadState) {
                    Idle -> task.prepare()

                    ReadyWithInfo -> task.download()

                    else -> {
                        throw IllegalStateException(
                            "Unexpected task state: ${state.downloadState}"
                        )
                    }
                }
            }
    }

    private fun Task.prepare() {
        check(downloadState == Idle)

        if (type is TypeInfo.CustomCommand) {
            execute()
        } else {
            fetchInfo()
        }
    }

    private fun Task.fetchInfo() {
        check(downloadState == Idle)

        val task = this
        val taskInfo = task.type

        val isPlaylist = taskInfo is TypeInfo.Playlist && !taskInfo.isFallback
        val isIndividualUrlValid = viewState.url.startsWith("http://", ignoreCase = true) || viewState.url.startsWith("https://", ignoreCase = true)
        val hasIndividualUrl = isIndividualUrlValid && viewState.url != url

        val fetchUrl = if (isPlaylist && hasIndividualUrl) {
            viewState.url
        } else {
            task.url
        }

        val playlistIndex =
            if (isPlaylist && fetchUrl == task.url) {
                (taskInfo as TypeInfo.Playlist).index
            } else {
                null
            }

        scope
            .launch(Dispatchers.Default) {
                DownloadUtil
                    .fetchVideoInfoFromUrl(
                        url = fetchUrl,
                        playlistIndex = playlistIndex,
                        preferences = task.preferences,
                        taskKey = task.id
                    )
                    .onSuccess { videoInfo ->
                        task.info = videoInfo
                        task.downloadState = ReadyWithInfo
                        task.viewState =
                            Task.ViewState.fromVideoInfo(videoInfo)
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }

                        task.downloadState =
                            Error(
                                throwable = throwable,
                                action = FetchInfo
                            )

                        NotificationUtil.notifyError(
                            title = task.viewState.title,
                            textId = R.string.download_error_msg,
                            notificationId = task.notificationId,
                            report = throwable.stackTraceToString()
                        )
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
        check(downloadState == ReadyWithInfo && info != null)

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
                        progressCallback = {
                                progressPercentage,
                                _,
                                text ->

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime > 250L || progressPercentage == 100f) {
                                lastUpdateTime = currentTime
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

                                        NotificationUtil.notifyProgress(
                                            notificationId =
                                                task.notificationId,
                                            progress =
                                                progressPercentage.toInt(),
                                            text = text,
                                            title =
                                                task.viewState.title,
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
                        }
                        pathList
                    }
                    .onSuccess { pathList ->
                        val path =
                            pathList.firstOrNull()

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

                        FileUtil
                            .createIntentForOpeningFile(
                                pathList.firstOrNull()
                            )
                            .run {
                                NotificationUtil
                                    .finishNotification(
                                        task.notificationId,
                                        title =
                                            task.viewState.title,
                                        text = text,
                                        intent =
                                            if (this != null) {
                                                PendingIntent
                                                    .getActivity(
                                                        appContext,
                                                        0,
                                                        this,
                                                        PendingIntent
                                                            .FLAG_IMMUTABLE
                                                    )
                                            } else {
                                                null
                                            }
                                    )
                            }
                    }
                    .onFailure { throwable ->
                        if (
                            throwable
                                is YoutubeDL.CanceledException
                        ) {
                            return@onFailure
                        }

                        if (task.type is TypeInfo.Playlist && !(task.type as TypeInfo.Playlist).isFallback) {
                            val playlistType = task.type as TypeInfo.Playlist
                            val newType = playlistType.copy(isFallback = true)
                            val fallbackTask = task.copy(url = task.viewState.url, type = newType)
                            
                            val oldState = taskStateMap.remove(task)
                            if (oldState != null) {
                                taskStateMap[fallbackTask] = oldState.copy(downloadState = Idle, videoInfo = null)
                                doYourWork()
                            }
                            return@onFailure
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
                            textId = R.string.download_error_msg,
                            notificationId =
                                task.notificationId,
                            report =
                                throwable.stackTraceToString()
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
                throw IllegalStateException(
                    "Task cannot be restarted from state: $downloadState"
                )
            }
        }
    }

    /**
     * Execute a custom command task.
     *
     * @see Task.TypeInfo.CustomCommand
     */
    private fun Task.execute() {
        check(downloadState == Idle)
        check(type is TypeInfo.CustomCommand)

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
                            textId = R.string.download_error_msg,
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
}