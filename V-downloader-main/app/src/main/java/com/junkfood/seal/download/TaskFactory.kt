package com.junkfood.seal.download

import androidx.annotation.CheckResult
import com.junkfood.seal.download.Task.DownloadState.Idle
import com.junkfood.seal.download.Task.DownloadState.ReadyWithInfo
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
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
    ): TaskWithState {
        val fileSize =
            formatList.fold(.0) { acc, format ->
                acc + (format.fileSize ?: format.fileSizeApprox ?: .0)
            }

        val info =
            videoInfo
                .run { if (fileSize != .0) copy(fileSize = fileSize) else this }
                .run { if (newTitle.isNotEmpty()) copy(title = newTitle) else this }

        val audioOnlyFormats = formatList.filter { it.isAudioOnly() }
        val videoFormats = formatList.filter { it.containsVideo() }
        val audioOnly = audioOnlyFormats.isNotEmpty() && videoFormats.isEmpty()
        val mergeAudioStream = audioOnlyFormats.size > 1
        val baseFormatId = formatList.joinToString(separator = "+") { it.formatId.toString() }
        
        val formatId = if (videoFormats.size == 1 && audioOnlyFormats.isEmpty() && videoFormats[0].isVideoOnly()) {
            "$baseFormatId+ba/best"
        } else {
            baseFormatId
        }

        val subtitleLanguage =
            (selectedSubtitles + selectedAutoCaptions).joinToString(separator = ",")

        val preferences =
            DownloadPreferences.createFromPreferences()
                .run {
                    copy(
                        formatIdString = formatId,
                        videoClips = videoClips,
                        splitByChapter = splitByChapter,
                        newTitle = newTitle,
                        mergeAudioStream = mergeAudioStream,
                        extractAudio = extractAudio || audioOnly,
                        skipDownload = skipDownload,
                    )
                }
                .run {
                    copy(
                        downloadSubtitle = subtitleLanguage.isNotEmpty(),
                        autoSubtitle = selectedAutoCaptions.isNotEmpty(),
                        subtitleLanguage = subtitleLanguage,
                    )
                }

        val task = Task(url = info.originalUrl.toString(), preferences = preferences)
        val state =
            Task.State(
                downloadState = ReadyWithInfo,
                videoInfo = info,
                viewState =
                    Task.ViewState.fromVideoInfo(info = info)
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
        val entries = playlistResult.entries
        val playlistTitle = playlistResult.title ?: ""

        // Fix: safe index mapping — entries are 0-based, playlist indexes are 1-based
        // Also ensure downloadPlaylist = true and subdirectoryPlaylistTitle = true
        val playlistPreferences = preferences.copy(
            downloadPlaylist = true,
            playlistNumbering = true,
            subdirectoryPlaylistTitle = playlistTitle.isNotEmpty(),
        )

        val taskList = indexList.mapNotNull { index ->
            // Safe bounds check to avoid IndexOutOfBoundsException
            val entry = entries.getOrNull(index - 1) ?: return@mapNotNull null
            val entryUrl = entry.url ?: playlistUrl

            val viewState = Task.ViewState(
                url = entryUrl,
                title = entry.title?.let { "$index. $it" } ?: "${playlistTitle.ifEmpty { "Playlist" }} - $index",
                duration = entry.duration?.roundToInt() ?: 0,
                uploader = entry.uploader ?: entry.channel ?: playlistResult.channel ?: playlistResult.uploader ?: "",
                thumbnailUrl = entry.thumbnails?.lastOrNull()?.url ?: "",
            )

            val dummyInfo = VideoInfo(
                id = entry.id ?: "",
                title = entry.title ?: "",
                webpageUrl = entryUrl,
                originalUrl = entryUrl,
                uploader = entry.uploader ?: entry.channel ?: playlistResult.channel ?: playlistResult.uploader ?: "",
                thumbnail = entry.thumbnails?.lastOrNull()?.url ?: "",
                extractor = playlistResult.extractorKey ?: "generic",
                extractorKey = playlistResult.extractorKey ?: "generic",
                duration = entry.duration,
            )

            val task = Task(
                url = entryUrl,
                preferences = playlistPreferences,
                type = Task.TypeInfo.Playlist(
                    index = index,
                    playlistTitle = playlistTitle,
                    playlistUrl = playlistUrl,
                    isFallback = entryUrl != playlistUrl,
                )
            )
            val state = Task.State(downloadState = ReadyWithInfo, videoInfo = dummyInfo, viewState = viewState)
            TaskWithState(task, state)
        }

        return taskList
    }

    data class TaskWithState(val task: Task, val state: Task.State)
}
