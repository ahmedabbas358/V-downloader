package com.junkfood.seal.ui.page.downloadv2.configure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.junkfood.seal.database.objects.CommandTemplate
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.TaskFactory
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.PlaylistResult
import com.junkfood.seal.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.junkfood.seal.ui.page.downloadv2.configure.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DownloadDialogViewModel"

class DownloadDialogViewModel(private val downloader: DownloaderV2) : ViewModel() {

    sealed interface SelectionState {
        data object Idle : SelectionState
        data class PlaylistSelection(val result: PlaylistResult) : SelectionState
        data class FormatSelection(val info: VideoInfo) : SelectionState
    }

    sealed interface SheetState {
        data object InputUrl : SheetState
        data class Configure(val urlList: List<String>) : SheetState
        data class Loading(val taskKey: String, val job: Job) : SheetState
        data class Error(val action: Action, val throwable: Throwable) : SheetState
    }

    sealed interface SheetValue {
        data object Expanded : SheetValue
        data object Hidden : SheetValue
    }

    sealed interface Action {
        data object HideSheet : Action
        data class ShowSheet(val urlList: List<String>? = null) : Action
        data class ProceedWithURLs(val urlList: List<String>) : Action
        data object Reset : Action
        data class FetchPlaylist(
            val url: String,
            val preferences: DownloadUtil.DownloadPreferences,
        ) : Action
        data class FetchFormats(
            val url: String,
            val audioOnly: Boolean,
            val preferences: DownloadUtil.DownloadPreferences,
        ) : Action
        data class DownloadWithPreset(
            val urlList: List<String>,
            val preferences: DownloadUtil.DownloadPreferences,
        ) : Action
        data class RunCommand(
            val url: String,
            val template: CommandTemplate,
            val preferences: DownloadUtil.DownloadPreferences,
        ) : Action
        data object Cancel : Action
    }

    private val mSelectionStateFlow = MutableStateFlow<SelectionState>(SelectionState.Idle)
    private val mSheetStateFlow = MutableStateFlow<SheetState>(SheetState.InputUrl)
    private val mSheetValueFlow = MutableStateFlow<SheetValue>(SheetValue.Hidden)

    val selectionStateFlow = mSelectionStateFlow.asStateFlow()
    val sheetStateFlow = mSheetStateFlow.asStateFlow()
    val sheetValueFlow = mSheetValueFlow.asStateFlow()

    // تتبع العمليات الجارية لمنع التكرار وللتنظيف الآمن
    private val activeJobs = mutableMapOf<String, Job>()

    fun postAction(action: Action) {
        when (action) {
            is Action.ProceedWithURLs -> proceedWithUrls(action)
            is Action.FetchFormats -> fetchFormat(action)
            is Action.FetchPlaylist -> fetchPlaylist(action)
            is Action.DownloadWithPreset -> downloadWithPreset(action.urlList, action.preferences)
            is Action.RunCommand -> runCommand(action.url, action.template, action.preferences)
            Action.HideSheet -> hideDialog()
            is Action.ShowSheet -> showDialog(action)
            Action.Cancel -> cancel()
            Action.Reset -> reset()
        }
    }

    // ——— URL helpers ———

    private fun sanitizeUrls(urlList: List<String>): List<String> =
        urlList
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_URLS_PER_BATCH)

    private fun isValidUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    // ——— Actions ———

    private fun proceedWithUrls(action: Action.ProceedWithURLs) {
        val validUrls = sanitizeUrls(action.urlList).filter { isValidUrl(it) }
        if (validUrls.isEmpty()) return
        mSheetStateFlow.update { SheetState.Configure(validUrls) }
    }

    private fun fetchPlaylist(action: Action.FetchPlaylist) {
        val (url, preferences) = action
        val taskKey = "FetchPlaylist_$url"

        // حماية من تكرار الطلبات
        if (activeJobs.containsKey(taskKey)) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                DownloadUtil.getPlaylistOrVideoInfo(
                    playlistURL = url,
                    downloadPreferences = preferences,
                ).onSuccess { info ->
                    when (info) {
                        is PlaylistResult -> {
                            mSelectionStateFlow.update {
                                SelectionState.PlaylistSelection(result = info)
                            }
                        }
                        is VideoInfo -> {
                            mSelectionStateFlow.update {
                                SelectionState.FormatSelection(info = info)
                            }
                        }
                    }
                    dismissSheet() // إخفاء بدون إلغاء (العملية انتهت بنجاح)
                }.onFailure { th ->
                    mSheetStateFlow.update {
                        SheetState.Error(action = action, throwable = th)
                    }
                }
            } catch (th: Throwable) {
                mSheetStateFlow.update {
                    SheetState.Error(action = action, throwable = th)
                }
            } finally {
                activeJobs.remove(taskKey)
            }
        }

        activeJobs[taskKey] = job
        mSheetStateFlow.update { SheetState.Loading(taskKey = taskKey, job = job) }
    }

    private fun fetchFormat(action: Action.FetchFormats) {
        val (url, audioOnly, preferences) = action
        val taskKey = "FetchFormat_$url"

        if (activeJobs.containsKey(taskKey)) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                DownloadUtil.fetchVideoInfoFromUrl(
                    url = url,
                    preferences = preferences.copy(extractAudio = audioOnly),
                    taskKey = taskKey,
                ).onSuccess { info ->
                    mSelectionStateFlow.update { SelectionState.FormatSelection(info = info) }
                    dismissSheet() // إخفاء بدون إلغاء (العملية انتهت بنجاح)
                }.onFailure { th ->
                    mSheetStateFlow.update { SheetState.Error(action = action, throwable = th) }
                }
            } catch (th: Throwable) {
                mSheetStateFlow.update { SheetState.Error(action = action, throwable = th) }
            } finally {
                activeJobs.remove(taskKey)
            }
        }

        activeJobs[taskKey] = job
        mSheetStateFlow.update { SheetState.Loading(taskKey = taskKey, job = job) }
    }

    private fun downloadWithPreset(
        urlList: List<String>,
        preferences: DownloadUtil.DownloadPreferences,
    ) {
        val validUrls = sanitizeUrls(urlList).filter { isValidUrl(it) }
        validUrls.forEach { url ->
            if (preferences.downloadPlaylist) {
                val taskKey = "FetchAndDownload_$url"
                if (activeJobs.containsKey(taskKey)) return@forEach

                val job = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        DownloadUtil.getPlaylistOrVideoInfo(url, preferences)
                            .onSuccess { info ->
                                if (info is PlaylistResult && info.entries != null) {
                                    val indices = info.entries.indices.map { it + 1 }
                                    val tasks = TaskFactory.createWithPlaylistResult(
                                        playlistUrl = url,
                                        indexList = indices,
                                        playlistResult = info,
                                        preferences = preferences
                                    )
                                    tasks.forEach { taskWithState ->
                                        downloader.enqueue(taskWithState)
                                    }
                                } else {
                                    downloader.enqueue(Task(url = url, preferences = preferences))
                                }
                            }
                            .onFailure {
                                downloader.enqueue(Task(url = url, preferences = preferences))
                            }
                    } catch (e: Exception) {
                        downloader.enqueue(Task(url = url, preferences = preferences))
                    } finally {
                        activeJobs.remove(taskKey)
                        if (activeJobs.isEmpty()) {
                            dismissSheet()
                        }
                    }
                }
                activeJobs[taskKey] = job
                mSheetStateFlow.update { SheetState.Loading(taskKey = taskKey, job = job) }
            } else {
                downloader.enqueue(Task(url = url, preferences = preferences))
            }
        }
        if (activeJobs.isEmpty()) {
            dismissSheet()
        }
    }

    private fun runCommand(
        url: String,
        template: CommandTemplate,
        preferences: DownloadUtil.DownloadPreferences,
    ) {
        if (!isValidUrl(url)) return

        val task = Task(
            url = url,
            type = Task.TypeInfo.CustomCommand(template = template),
            preferences = preferences,
        )
        downloader.enqueue(task)
        dismissSheet()
    }

    // ——— Sheet control ———

    /** إخفاء الورقة مع إلغاء أي عملية تحميل جارية (للاستخدام مع زر الرجوع/الإلغاء) */
    private fun hideDialog() {
        val currentState = mSheetStateFlow.value
        if (currentState is SheetState.Loading) {
            cancelLoading(currentState)
        }
        dismissSheet()
    }

    /** إخفاء الورقة وإعادة تعيين الحالة دون إلغاء Jobs (للاستخدام بعد اكتمال العملية) */
    private fun dismissSheet() {
        mSheetValueFlow.update { SheetValue.Hidden }
        mSheetStateFlow.update { SheetState.InputUrl }
    }

    private fun showDialog(action: Action.ShowSheet) {
        val urlList = action.urlList
        if (!urlList.isNullOrEmpty()) {
            mSheetStateFlow.update { SheetState.Configure(sanitizeUrls(urlList)) }
        } else {
            mSheetStateFlow.update { SheetState.InputUrl }
        }
        mSheetValueFlow.update { SheetValue.Expanded }
    }

    private fun cancel(): Boolean {
        val state = mSheetStateFlow.value
        return if (state is SheetState.Loading) {
            cancelLoading(state)
        } else {
            false
        }
    }

    private fun cancelLoading(state: SheetState.Loading): Boolean {
        val destroyed = try {
            YoutubeDL.destroyProcessById(id = state.taskKey)
        } catch (_: Exception) {
            false
        }
        state.job.cancel()
        activeJobs.remove(state.taskKey)
        // إعادة تعيين الحالة فوراً لتجنب بقاء Loading معلقة
        mSheetStateFlow.update { SheetState.InputUrl }
        return destroyed
    }

    private fun reset() {
        // إلغاء جميع العمليات الجارية
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()

        // إعادة تعيين كل الحالات
        mSelectionStateFlow.update { SelectionState.Idle }
        mSheetStateFlow.update { SheetState.InputUrl }
        mSheetValueFlow.update { SheetValue.Hidden }
    }

    // Retrieves the current Config for a given task ID. Currently returns global Config.
    fun getConfigForTask(taskId: Int): Config {
        // In a full implementation, you would extract task‑specific preferences.
        // Here we return the default Config based on current global preferences.
        return Config()
    }

    // Applies edited Config changes and persists them. Updates global preferences.
    fun applyEditorChanges(taskId: Int, newConfig: Config) {
        // Persist changes globally.
        Config.updatePreferences(newValue = newConfig, oldValue = Config())
        // If task‑specific preferences are needed, they can be updated here.
        // For now we simply refresh the UI via the existing state flows if required.
    }

    // ——— Lifecycle ———

    override fun onCleared() {
        super.onCleared()
        activeJobs.values.forEach { job ->
            try {
                job.cancel()
            } catch (_: Exception) {
                // تجاهل أي خطأ أثناء الإلغاء
            }
        }
        activeJobs.clear()
    }

    private companion object {
        const val MAX_URLS_PER_BATCH = 50
    }
}
