@file:OptIn(ExperimentalMaterial3Api::class)

package com.junkfood.seal.ui.page.download

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.junkfood.seal.App.Companion.applicationScope
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.R
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.TaskFactory
import com.junkfood.seal.util.CUSTOM_COMMAND
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.FORMAT_SELECTION
import com.junkfood.seal.util.PLAYLIST
import com.junkfood.seal.util.PlaylistResult
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.junkfood.seal.util.ToastUtil
import com.junkfood.seal.util.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Refactoring for introducing multitasking and download queue management
class HomePageViewModel(private val downloaderV2: DownloaderV2) : ViewModel() {

    private val mutableViewStateFlow = MutableStateFlow(ViewState())
    val viewStateFlow = mutableViewStateFlow.asStateFlow()

    val videoInfoFlow = MutableStateFlow(VideoInfo())

    data class ViewState(
        val showPlaylistSelectionDialog: Boolean = false,
        val url: String = "",
        val showFormatSelectionPage: Boolean = false,
        val isUrlSharingTriggered: Boolean = false,
        val isFetchingInfo: Boolean = false,
        val playlistResult: PlaylistResult? = null
    )

    fun updateUrl(url: String, isUrlSharingTriggered: Boolean = false) =
        mutableViewStateFlow.update {
            it.copy(url = url, isUrlSharingTriggered = isUrlSharingTriggered)
        }

    fun startDownloadVideo() {
        val url = viewStateFlow.value.url
        if (url.isBlank()) {
            ToastUtil.makeToast(context.getString(R.string.url_empty))
            return
        }
        if (PLAYLIST.getBoolean()) {
            viewModelScope.launch(Dispatchers.IO) { parsePlaylistInfo(url) }
            return
        }

        if (FORMAT_SELECTION.getBoolean()) {
            viewModelScope.launch(Dispatchers.IO) { fetchInfoForFormatSelection(url) }
            return
        }

        val task = Task(url = url, preferences = DownloadUtil.DownloadPreferences.createFromPreferences())
        downloaderV2.enqueue(task)
    }

    private fun fetchInfoForFormatSelection(url: String) {
        mutableViewStateFlow.update { it.copy(isFetchingInfo = true) }
        DownloadUtil.fetchVideoInfoFromUrl(url = url)
            .onSuccess { showFormatSelectionPageOrDownload(it) }
            .onFailure {
                ToastUtil.makeToast(it.message ?: "Error fetching info")
            }
        mutableViewStateFlow.update { it.copy(isFetchingInfo = false) }
    }

    private fun parsePlaylistInfo(url: String) {
        mutableViewStateFlow.update { it.copy(isFetchingInfo = true) }
        DownloadUtil.getPlaylistOrVideoInfo(url)
            .onSuccess { info ->
                mutableViewStateFlow.update { it.copy(isFetchingInfo = false) }
                when (info) {
                    is PlaylistResult -> {
                        showPlaylistPage(info)
                    }

                    is VideoInfo -> {
                        if (FORMAT_SELECTION.getBoolean()) {
                            showFormatSelectionPageOrDownload(info)
                        } else {
                            val task = Task(url = url, preferences = DownloadUtil.DownloadPreferences.createFromPreferences())
                            downloaderV2.enqueue(task)
                        }
                    }
                }
            }
            .onFailure {
                mutableViewStateFlow.update { it.copy(isFetchingInfo = false) }
                ToastUtil.makeToast(it.message ?: "Error fetching playlist info")
            }
    }

    private fun showPlaylistPage(playlistResult: PlaylistResult) {
        mutableViewStateFlow.update { it.copy(showPlaylistSelectionDialog = true, playlistResult = playlistResult) }
    }

    private fun showFormatSelectionPageOrDownload(info: VideoInfo) {
        if (info.format.isNullOrEmpty()) {
            val task = Task(url = info.originalUrl.toString(), preferences = DownloadUtil.DownloadPreferences.createFromPreferences())
            downloaderV2.enqueue(task)
        } else {
            videoInfoFlow.update { info }
            mutableViewStateFlow.update { it.copy(showFormatSelectionPage = true) }
        }
    }

    fun hidePlaylistDialog() {
        mutableViewStateFlow.update { it.copy(showPlaylistSelectionDialog = false) }
    }

    fun hideFormatPage() {
        mutableViewStateFlow.update { it.copy(showFormatSelectionPage = false) }
    }

    fun onShareIntentConsumed() {
        mutableViewStateFlow.update { it.copy(isUrlSharingTriggered = false) }
    }

    companion object {
        private const val TAG = "DownloadViewModel"
    }
}
