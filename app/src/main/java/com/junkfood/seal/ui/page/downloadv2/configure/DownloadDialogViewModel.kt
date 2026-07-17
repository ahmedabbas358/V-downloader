package com.junkfood.seal.ui.page.downloadv2.configure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.junkfood.seal.database.objects.CommandTemplate
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.PlaylistResult
import com.junkfood.seal.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val mSelectionStateFlow: MutableStateFlow<SelectionState> =
        MutableStateFlow(SelectionState.Idle)
    private val mSheetStateFlow: MutableStateFlow<SheetState> =
        MutableStateFlow(SheetState.InputUrl)
    private val mSheetValueFlow: MutableStateFlow<SheetValue> = MutableStateFlow(SheetValue.Hidden)

    val selectionStateFlow = mSelectionStateFlow.asStateFlow()
    val sheetStateFlow = mSheetStateFlow.asStateFlow()
    val sheetValueFlow = mSheetValueFlow.asStateFlow()

    private val sheetState
        get() = sheetStateFlow.value

    fun postAction(action: Action) {
        with(action) {
            when (this) {
                is Action.ProceedWithURLs -> proceedWithUrls(this)
                is Action.FetchFormats -> fetchFormat(this)
                is Action.FetchPlaylist -> fetchPlaylist(this)
                is Action.DownloadWithPreset -> downloadWithPreset(urlList, preferences)
                is Action.RunCommand -> runCommand(url, template, preferences)
                Action.HideSheet -> hideDialog()
                is Action.ShowSheet -> showDialog(this)
                Action.Cancel -> cancel()
                Action.Reset -> resetSelectionState()
            }
        }
    }

    // Sanitize incoming URL list: trim, drop blanks, de-dup, and cap at a sane limit so a
    // malformed/huge paste can't flood the Configure screen or downstream enqueue calls.
    private fun proceedWithUrls(action: Action.ProceedWithURLs) {
        val validUrls =
            action.urlList
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_URLS_PER_BATCH)

        if (validUrls.isEmpty()) return

        mSheetStateFlow.update { SheetState.Configure(validUrls) }
    }

    private fun fetchPlaylist(action: Action.FetchPlaylist) {
        val (url, preferences) = action

        val job =
            viewModelScope.launch(Dispatchers.IO) {
                DownloadUtil.getPlaylistOrVideoInfo(
                        playlistURL = url,
                        downloadPreferences = preferences,
                    )
                    .onSuccess { info ->
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
                        hideDialog()
                    }
                    .onFailure { th ->
                        mSheetStateFlow.update {
                            SheetState.Error(action = action, throwable = th)
                        }
                    }
            }
        mSheetStateFlow.update { SheetState.Loading(taskKey = "FetchPlaylist_$url", job = job) }
    }

    private fun fetchFormat(action: Action.FetchFormats) {
        val (url, audioOnly, preferences) = action

        val job =
            viewModelScope.launch(Dispatchers.IO) {
                DownloadUtil.fetchVideoInfoFromUrl(
                        url = url,
                        preferences = preferences.copy(extractAudio = audioOnly),
                        taskKey = "FetchFormat_$url",
                    )
                    .onSuccess { info ->
                        mSelectionStateFlow.update {
                            SelectionState.FormatSelection(info = info)
                        }
                        hideDialog()
                    }
                    .onFailure { th ->
                        mSheetStateFlow.update { SheetState.Error(action, throwable = th) }
                    }
            }

        mSheetStateFlow.update { SheetState.Loading(taskKey = "FetchFormat_$url", job = job) }
    }

    // Same sanitation as proceedWithUrls, so a blank/duplicate entry can't slip into a
    // failed Task when downloading directly from a preset.
    private fun downloadWithPreset(
        urlList: List<String>,
        preferences: DownloadUtil.DownloadPreferences,
    ) {
        urlList
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_URLS_PER_BATCH)
            .forEach { downloader.enqueue(Task(url = it, preferences = preferences)) }
        hideDialog()
    }

    // Fix: this previously never called hideDialog(), so the sheet stayed open after
    // enqueuing a custom-command task.
    private fun runCommand(
        url: String,
        template: CommandTemplate,
        preferences: DownloadUtil.DownloadPreferences,
    ) {
        val task =
            Task(
                url = url,
                type = Task.TypeInfo.CustomCommand(template = template),
                preferences = preferences,
            )
        downloader.enqueue(task)
        hideDialog()
    }

    private fun hideDialog() {
        mSheetValueFlow.update { SheetValue.Hidden }
        when (sheetState) {
            is SheetState.Loading -> {
                cancel()
            }

            else -> {}
        }
    }

    private fun showDialog(action: Action.ShowSheet) {
        val urlList = action.urlList
        if (!urlList.isNullOrEmpty()) {
            mSheetStateFlow.update { SheetState.Configure(urlList) }
        } else {
            mSheetStateFlow.update { SheetState.InputUrl }
        }
        mSheetValueFlow.update { SheetValue.Expanded }
    }

    private fun cancel(): Boolean =
        when (val state = sheetState) {
            is SheetState.Loading -> {
                val res = YoutubeDL.destroyProcessById(id = state.taskKey)
                state.job.cancel()
                res
            }
            else -> false
        }

    private fun resetSelectionState() {
        mSelectionStateFlow.update { SelectionState.Idle }
    }

    private companion object {
        const val MAX_URLS_PER_BATCH = 50
    }
}