package com.junkfood.seal.download

import androidx.annotation.CheckResult
import com.junkfood.seal.download.Task.DownloadState.Idle
import com.junkfood.seal.download.Task.DownloadState.ReadyWithInfo
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.PreferenceUtil.getInt
import com.junkfood.seal.util.Format
import com.junkfood.seal.util.PlaylistResult
import com.junkfood.seal.util.VideoClip
import com.junkfood.seal.util.VideoInfo
import kotlin.math.roundToInt

object TaskFactory {
    /**
     * @return A [TaskWithState] with extra configurations made by user in the custom format
     *   selection page
     */
    @CheckResult
    fun createWithConfigurations(
        videoInfo: VideoInfo,
        formatList: List<Format>,
        videoClips: List<VideoClip>,
        splitByChapter: Boolean,
        newTitle: String,
        selectedSubtitles: List<String>,
        selectedAutoCaptions: List<String>,
        skipDownload: Boolean = false,
        subtitleFormat: Int = com.junkfood.seal.util.CONVERT_SUBTITLE.getInt(),
    ): TaskWithState {
        val fileSize = if (skipDownload) 0.0 else {
            formatList.fold(.0) { acc, format ->
                acc + (format.fileSize ?: format.fileSizeApprox ?: .0)
            }
        }

        val info =
            videoInfo
                .run { if (fileSize != .0) copy(fileSize = fileSize) else this }
                .run { if (newTitle.isNotEmpty()) copy(title = newTitle) else this }

        val audioOnlyFormats = formatList.filter { it.isAudioOnly() }
        val videoFormats = formatList.filter { it.containsVideo() }
        val audioOnly = audioOnlyFormats.isNotEmpty() && videoFormats.isEmpty()
        val mergeAudioStream = audioOnlyFormats.size > 1
        val formatId = formatList.joinToString(separator = "+") { it.formatId.toString() }

        val subtitleLanguage =
            (selectedSubtitles + selectedAutoCaptions).joinToString(separator = ",")
        val hasSelectedSubs = subtitleLanguage.isNotEmpty()

        val preferences =
            DownloadPreferences.createFromPreferences()
                .run {
                    copy(
                        formatIdString = if (skipDownload) "" else formatId,
                        videoClips = if (skipDownload) emptyList() else videoClips,
                        splitByChapter = if (skipDownload) false else splitByChapter,
                        newTitle = newTitle,
                        mergeAudioStream = if (skipDownload) false else mergeAudioStream,
                        extractAudio = if (skipDownload) false else (extractAudio || audioOnly),
                        skipDownload = skipDownload,
                        downloadSubtitle = downloadSubtitle || skipDownload || hasSelectedSubs,
                        convertSubtitle = subtitleFormat,
                        autoSubtitle = if (hasSelectedSubs) selectedAutoCaptions.isNotEmpty() else (autoSubtitle || skipDownload),
                        subtitleLanguage = if (hasSelectedSubs) subtitleLanguage else this.subtitleLanguage,
                    )
                }

        val task = Task(url = info.originalUrl.toString(), preferences = preferences)
        val state =
            Task.State(
                downloadState = ReadyWithInfo,
                videoInfo = info,
                viewState =
                    Task.ViewState.fromVideoInfo(info = info, preferences = preferences)
                        .copy(videoFormats = videoFormats, audioOnlyFormats = audioOnlyFormats),
            )

        return TaskWithState(task, state)
    }

    /** @return List of [TaskWithState]s created from playlist items */
    @CheckResult
    fun createWithPlaylistResult(
        playlistUrl: String,
        indexList: List<Int>,
        playlistResult: PlaylistResult,
        preferences: DownloadPreferences,
    ): List<TaskWithState> {
        checkNotNull(playlistResult.entries)
        val indexEntryMap = indexList.associateWith { index -> playlistResult.entries[index - 1] }

        val sanitizedPreferences = if (preferences.skipDownload) {
            preferences.copy(
                formatIdString = "",
                extractAudio = false,
                mergeAudioStream = false,
                splitByChapter = false,
                videoClips = emptyList(),
                downloadSubtitle = true,
                autoSubtitle = preferences.autoSubtitle || preferences.subtitleLanguage.isNotEmpty(),
                subtitleLanguage = preferences.subtitleLanguage,
                autoTranslatedSubtitles = preferences.autoTranslatedSubtitles,
                convertSubtitle = preferences.convertSubtitle,
                playlistNumbering = preferences.playlistNumbering,
            )
        } else preferences

        val taskList =
            indexEntryMap.map { (index, entry) ->
                val entryUrlRaw = entry.url.orEmpty()
                val itemUrl = when {
                    entryUrlRaw.startsWith("http://", ignoreCase = true) || entryUrlRaw.startsWith("https://", ignoreCase = true) -> entryUrlRaw
                    !entry.id.isNullOrEmpty() -> "https://www.youtube.com/watch?v=${entry.id}"
                    else -> ""
                }
                val isSubOnly = sanitizedPreferences.skipDownload && sanitizedPreferences.downloadSubtitle
                val baseTitle = entry.title ?: "${playlistResult.title} - $index"
                val viewState =
                    Task.ViewState(
                        url = itemUrl,
                        title = if (isSubOnly) "[Subtitle] $baseTitle" else baseTitle,
                        duration = if (isSubOnly) 0 else (entry.duration?.roundToInt() ?: 0),
                        uploader = entry.uploader ?: entry.channel ?: playlistResult.channel ?: "",
                        thumbnailUrl = (entry.thumbnails?.lastOrNull()?.url) ?: "",
                        isSubOnly = isSubOnly,
                    )
                val task = Task(
                    url = itemUrl.ifEmpty { playlistUrl }, 
                    preferences = sanitizedPreferences, 
                    type = Task.TypeInfo.Playlist(
                        index = index,
                        playlistTitle = playlistResult.title ?: "",
                        playlistUrl = playlistUrl
                    )
                )
                val state =
                    Task.State(downloadState = Idle, videoInfo = null, viewState = viewState)
                TaskWithState(task, state)
            }

        return taskList
    }

    data class TaskWithState(val task: Task, val state: Task.State)
}
