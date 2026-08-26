package com.junkfood.seal.ui.page.downloadv2.configure

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junkfood.seal.R
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.TaskFactory
import com.junkfood.seal.ui.component.ClearButton
import com.junkfood.seal.ui.component.ConfirmButton
import com.junkfood.seal.ui.component.DismissButton
import com.junkfood.seal.ui.component.FormatItem
import com.junkfood.seal.ui.component.FormatSubtitle
import com.junkfood.seal.ui.component.FormatVideoPreview
import com.junkfood.seal.ui.component.PreferenceInfo
import com.junkfood.seal.ui.component.SealDialog
import com.junkfood.seal.ui.component.SealSearchBar
import com.junkfood.seal.ui.component.SuggestedFormatItem
import com.junkfood.seal.ui.component.TextButtonWithIcon
import com.junkfood.seal.ui.component.VideoFilterChip
import com.junkfood.seal.ui.page.download.VideoClipDialog
import com.junkfood.seal.ui.page.download.VideoSelectionSlider
import com.junkfood.seal.ui.page.settings.general.DialogCheckBoxItem
import com.junkfood.seal.ui.theme.SealTheme
import com.junkfood.seal.ui.theme.generateLabelColor
import com.junkfood.seal.util.EXTRACT_AUDIO
import com.junkfood.seal.util.Format
import com.junkfood.seal.util.MERGE_MULTI_AUDIO_STREAM
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.junkfood.seal.util.PreferenceUtil.getInt
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateString
import com.junkfood.seal.util.SUBTITLE
import com.junkfood.seal.util.SUBTITLE_LANGUAGE
import com.junkfood.seal.util.SubtitleFormat
import com.junkfood.seal.util.VIDEO_CLIP
import com.junkfood.seal.util.VideoClip
import com.junkfood.seal.util.VideoInfo
import com.junkfood.seal.util.toHttpsUrl
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val TAG = "FormatPage"

private data class FormatConfig(
    val formatList: List<Format>,
    val videoClips: List<VideoClip>,
    val splitByChapter: Boolean,
    val newTitle: String,
    val selectedSubtitles: List<String>,
    val selectedAutoCaptions: List<String> = emptyList(),
    val skipDownload: Boolean = false,
    val subtitleFormat: Int = com.junkfood.seal.util.CONVERT_SUBTITLE.getInt(),
    val embedSubtitle: Boolean = com.junkfood.seal.util.EMBED_SUBTITLE.getBoolean(),
)

@Composable
fun FormatPage(
    modifier: Modifier = Modifier,
    videoInfo: VideoInfo,
    playlistTasks: List<com.junkfood.seal.download.TaskFactory.TaskWithState>? = null,
    audioOnly: Boolean = com.junkfood.seal.util.PreferenceUtil.getDownloadType() == com.junkfood.seal.util.DownloadType.Audio || com.junkfood.seal.util.EXTRACT_AUDIO.getBoolean(),
    isSubtitleOnly: Boolean = com.junkfood.seal.util.PreferenceUtil.getDownloadType() == com.junkfood.seal.util.DownloadType.Subtitle,
    downloader: DownloaderV2 = koinInject(),
    onNavigateBack: () -> Unit = {},
) {
    if (videoInfo.formats.isNullOrEmpty() && !isSubtitleOnly) return
    val mergeAudioStream = MERGE_MULTI_AUDIO_STREAM.getBoolean()
    val subtitleLanguageRegex = SUBTITLE_LANGUAGE.getString()
    val initialSelectedSubtitles =
        videoInfo
            .run { subtitles.keys + automaticCaptions.keys }
            .filterWithRegex(subtitleLanguageRegex)

    val isAudioSelected = audioOnly
    val isSubOnly = isSubtitleOnly

    FormatPageImpl(
        modifier = modifier,
        videoInfo = videoInfo,
        onNavigateBack = onNavigateBack,
        audioOnly = isAudioSelected,
        isSubtitleOnly = isSubOnly,
        mergeAudioStream = !isAudioSelected && mergeAudioStream,
        selectedSubtitleCodes = initialSelectedSubtitles,
        isClippingAvailable = !isAudioSelected && !isSubOnly && VIDEO_CLIP.getBoolean() && (videoInfo.duration ?: .0) >= 0,
    ) { config ->
        with(config) {

            val audioOnlyFormats = formatList.filter { it.isAudioOnly() }
            val videoFormats = formatList.filter { it.containsVideo() }
            val isAudioOnlyPlaylist = isAudioSelected || (audioOnlyFormats.isNotEmpty() && videoFormats.isEmpty())
            val mergeAudioStreamPlaylist = audioOnlyFormats.size > 1
            val hasVideoOnlyFormat = videoFormats.any { it.isVideoOnly() || !it.containsAudio() }
            val vId = videoFormats.joinToString(separator = "+") { it.formatId.toString() }
            val aId = audioOnlyFormats.joinToString(separator = "+") { it.formatId.toString() }
            val maxSelectedHeight = videoFormats.mapNotNull { it.height?.toInt() }.maxOrNull()
            
            val fallbackSpec = when {
                isAudioOnlyPlaylist -> "bestaudio/best"
                maxSelectedHeight != null && maxSelectedHeight > 0 -> "bestvideo[height<=$maxSelectedHeight]+bestaudio/bestvideo+bestaudio/best"
                else -> "bestvideo+bestaudio/best"
            }

            val playlistFormatId = when {
                isAudioOnlyPlaylist -> "bestaudio/best"
                maxSelectedHeight != null && maxSelectedHeight > 0 -> "bestvideo[height<=$maxSelectedHeight]+bestaudio/bestvideo+bestaudio/best"
                else -> fallbackSpec
            }

            if (playlistTasks != null) {
                val hasSelectedSubs = selectedSubtitles.isNotEmpty() || selectedAutoCaptions.isNotEmpty()
                val chosenSubs = (selectedSubtitles + selectedAutoCaptions).distinct()
                val subLangString = if (chosenSubs.isNotEmpty()) chosenSubs.joinToString(",") else ""

                playlistTasks.forEach { taskWithState ->
                    val isSubOnly = skipDownload || isSubtitleOnly || taskWithState.task.preferences.skipDownload
                    val currentPlaylistType = taskWithState.task.type as? com.junkfood.seal.download.Task.TypeInfo.Playlist
                    val resolvedTitle = currentPlaylistType?.playlistTitle?.ifBlank { null }
                        ?: videoInfo.playlist?.ifBlank { null }
                        ?: videoInfo.playlistTitle?.ifBlank { null }
                        ?: ""
                    val updatedType = if (currentPlaylistType != null) {
                        currentPlaylistType.copy(playlistTitle = if (currentPlaylistType.playlistTitle.isBlank()) resolvedTitle else currentPlaylistType.playlistTitle)
                    } else {
                        taskWithState.task.type
                    }
                    val updatedTask = taskWithState.task.copy(
                        type = updatedType,
                        preferences = taskWithState.task.preferences.copy(
                            skipDownload = isSubOnly,
                            formatIdString = if (isSubOnly) "" else playlistFormatId,
                            extractAudio = if (isSubOnly) false else (taskWithState.task.preferences.extractAudio || isAudioOnlyPlaylist),
                            mergeAudioStream = if (isSubOnly) false else mergeAudioStreamPlaylist,
                            downloadSubtitle = if (isSubOnly) true else (hasSelectedSubs || taskWithState.task.preferences.downloadSubtitle),
                            convertSubtitle = subtitleFormat,
                            embedSubtitle = if (isSubOnly || isAudioOnlyPlaylist) false else embedSubtitle,
                            autoSubtitle = true,
                            autoTranslatedSubtitles = true,
                            subtitleLanguage = if (subLangString.isNotEmpty()) subLangString else taskWithState.task.preferences.subtitleLanguage,
                            splitByChapter = if (isSubOnly) false else splitByChapter,
                            newTitle = newTitle,
                            playlistNumbering = taskWithState.task.preferences.playlistNumbering || isSubOnly || com.junkfood.seal.util.PLAYLIST_NUMBERING.getBoolean(),
                        )
                    )
                    downloader.enqueue(taskWithState.copy(task = updatedTask))
                }
            } else {
                val createdTaskWithState = com.junkfood.seal.download.TaskFactory.createWithConfigurations(
                    videoInfo = videoInfo,
                    formatList = formatList,
                    videoClips = videoClips,
                    splitByChapter = splitByChapter,
                    newTitle = newTitle,
                    selectedSubtitles = selectedSubtitles,
                    selectedAutoCaptions = selectedAutoCaptions,
                    skipDownload = skipDownload,
                    subtitleFormat = subtitleFormat,
                    embedSubtitle = embedSubtitle,
                )
                val finalTask = if (isAudioSelected && !skipDownload) {
                    createdTaskWithState.copy(
                        task = createdTaskWithState.task.copy(
                            preferences = createdTaskWithState.task.preferences.copy(
                                extractAudio = true
                            )
                        )
                    )
                } else {
                    createdTaskWithState
                }
                downloader.enqueue(finalTask)
            }

            onNavigateBack()
        }
    }
}

private const val NOT_SELECTED = -1

@Preview
@Composable
fun FormatPagePreview() {
    val captionsMap =
        mapOf(
            "en-en" to listOf(SubtitleFormat(ext = "", url = "", name = "English from English")),
            "ja-en" to listOf(SubtitleFormat(ext = "", url = "", name = "Japanese from English")),
            "zh-Hans-en" to
                listOf(
                    SubtitleFormat(ext = "", url = "", name = "Chinese (Simplified) from English")
                ),
            "zh-Hant-en" to
                listOf(
                    SubtitleFormat(ext = "", url = "", name = "Chinese (Traditional) from English")
                ),
        )

    val subMap = buildMap {
        put("en", listOf(SubtitleFormat(ext = "ass", url = "", name = "English")))
        put("ja", listOf(SubtitleFormat(ext = "ass", url = "", name = "Japanese")))
    }
    val videoInfo =
        VideoInfo(
            formats =
                buildList {
                    repeat(7) { add(Format(formatId = "$it")) }
                    repeat(7) { add(Format(formatId = "$it", vcodec = "avc1", acodec = "none")) }
                    repeat(7) {
                        add(
                            Format(
                                formatId = "$it",
                                acodec = "aac",
                                vcodec = "none",
                                format = "251 - audio only (medium)",
                                fileSizeApprox = 2000000.0,
                                tbr = 128.0,
                            )
                        )
                    }
                },
            subtitles = subMap,
            automaticCaptions = captionsMap,
            requestedFormats =
                buildList {
                    add(
                        Format(
                            formatId = "616",
                            format = "616 - 1920x1080 (Premium)",
                            acodec = "none",
                            vcodec = "vp09.00.40.08",
                            ext = "webm",
                        )
                    )
                    add(
                        Format(
                            formatId = "251",
                            format = "251 - audio only (medium)",
                            acodec = "opus",
                            vcodec = "none",
                            ext = "webm",
                        )
                    )
                },
        )
    SealTheme {
        FormatPageImpl(
            videoInfo = videoInfo,
            isClippingAvailable = true,
            mergeAudioStream = true,
            selectedSubtitleCodes = setOf("en", "ja-en"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatPageImpl(
    modifier: Modifier = Modifier,
    videoInfo: VideoInfo = VideoInfo(),
    audioOnly: Boolean = false,
    isSubtitleOnly: Boolean = false,
    mergeAudioStream: Boolean = false,
    isClippingAvailable: Boolean = false,
    selectedSubtitleCodes: Set<String>,
    onNavigateBack: () -> Unit = {},
    onDownloadPressed: (FormatConfig) -> Unit = { _ -> },
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    if (videoInfo.formats.orEmpty().isEmpty() && !isSubtitleOnly) return
    val videoFormats = remember(videoInfo.formats) {
        videoInfo.formats.orEmpty()
            .filter { it.containsVideo() && !it.formatId.isNullOrEmpty() && !it.formatId.startsWith("sb") }
            .distinctBy { it.formatId }
            .sortedWith(
                compareByDescending<Format> { it.height ?: 0 }
                    .thenByDescending { it.fps ?: 0.0 }
                    .thenByDescending { it.tbr ?: it.vbr ?: it.abr ?: 0.0 }
            )
    }
    val audioOnlyFormats = remember(videoInfo.formats) {
        videoInfo.formats.orEmpty()
            .filter { it.isAudioOnly() && it.containsAudio() && !it.formatId.isNullOrEmpty() }
            .distinctBy { it.formatId }
            .sortedByDescending { it.tbr ?: it.abr ?: 0.0 }
    }

    val duration = videoInfo.duration ?: 0.0

    var videoItemLimit by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var audioOnlyItemLimit by remember { mutableIntStateOf(Int.MAX_VALUE) }

    val isSuggestedFormatAvailable =
        !videoInfo.requestedFormats.isNullOrEmpty() || !videoInfo.requestedDownloads.isNullOrEmpty()

    var isSuggestedFormatSelected by remember {
        mutableStateOf(isSuggestedFormatAvailable && !audioOnly && !isSubtitleOnly)
    }

    var selectedVideoFormat by remember { mutableIntStateOf(NOT_SELECTED) }
    val selectedAudioOnlyFormats = remember {
        mutableStateListOf<Int>().apply {
            if (audioOnly && audioOnlyFormats.isNotEmpty()) {
                add(0)
            }
        }
    }
    val context = LocalContext.current

    val uriHandler = LocalUriHandler.current
    val hapticFeedback = LocalHapticFeedback.current

    fun String?.share() =
        this?.let {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            context.startActivity(
                Intent.createChooser(
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, it)
                    },
                    null,
                ),
                null,
            )
        }

    var isClippingVideo by remember { mutableStateOf(false) }
    var isSplittingVideo by remember { mutableStateOf(false) }
    val isSplitByChapterAvailable = !videoInfo.chapters.isNullOrEmpty()

    val videoDurationRange = 0f..(videoInfo.duration?.toFloat() ?: 0f)
    var showVideoClipDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSubtitleSelectionDialog by remember { mutableStateOf(false) }

    var videoClipDuration by remember { mutableStateOf(videoDurationRange) }
    var videoTitle by remember { mutableStateOf("") }
    
    var subtitleFormat by remember { mutableIntStateOf(com.junkfood.seal.util.CONVERT_SUBTITLE.getInt()) }

    val manualSubtitleMap: Map<String, List<SubtitleFormat>> = videoInfo.subtitles
    val autoCaptionMap: Map<String, List<SubtitleFormat>> = videoInfo.automaticCaptions
    val suggestedSubtitleMap: Map<String, List<SubtitleFormat>> = remember(manualSubtitleMap, autoCaptionMap) {
        val combined = mutableMapOf<String, List<SubtitleFormat>>()
        combined.putAll(manualSubtitleMap)
        for ((code, formats) in autoCaptionMap) {
            if (!combined.containsKey(code)) {
                combined[code] = formats
            }
        }
        combined
    }
    val totalSubtitlesCount = manualSubtitleMap.size + autoCaptionMap.size

    LaunchedEffect(isClippingVideo) {
        delay(200)
        videoClipDuration = videoDurationRange
    }

    val lazyGridState = rememberLazyGridState()

    val formatList: List<Format> by remember {
        derivedStateOf {
            mutableListOf<Format>().apply {
                if (isSuggestedFormatSelected) {
                    videoInfo.requestedFormats?.let { addAll(it) }
                        ?: videoInfo.requestedDownloads?.forEach {
                            it.requestedFormats?.let { addAll(it) }
                        }
                } else {
                    videoFormats.getOrNull(selectedVideoFormat)?.let { add(it) }
                    selectedAudioOnlyFormats.forEach { index ->
                        add(audioOnlyFormats.elementAt(index))
                    }
                }
            }
        }
    }

    val isFabExpanded by remember { derivedStateOf { lazyGridState.firstVisibleItemIndex > 0 } }

    val selectedSubtitles = remember {
        mutableStateListOf<String>().apply {
            addAll(selectedSubtitleCodes.filter { manualSubtitleMap.containsKey(it) })
        }
    }

    val selectedAutoCaptions = remember {
        mutableStateListOf<String>().apply {
            addAll(selectedSubtitleCodes.filter { !manualSubtitleMap.containsKey(it) && autoCaptionMap.containsKey(it) })
        }
    }

    var embedSubtitleState by remember { mutableStateOf(com.junkfood.seal.util.EMBED_SUBTITLE.getBoolean()) }

    val isAnyDialogShown = showSubtitleSelectionDialog || showVideoClipDialog || showRenameDialog

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.format_selection),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    )
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.close))
                    }
                },
            )
        },
        floatingActionButton = {
            val isFormatSelected =
                isSuggestedFormatSelected ||
                    formatList.isNotEmpty() ||
                    isSubtitleOnly ||
                    selectedSubtitles.isNotEmpty() ||
                    selectedAutoCaptions.isNotEmpty()
            if (isFormatSelected && !isAnyDialogShown) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onDownloadPressed(
                            FormatConfig(
                                formatList = formatList,
                                videoClips =
                                    if (isClippingVideo) listOf(VideoClip(videoClipDuration))
                                    else emptyList(),
                                splitByChapter = isSplittingVideo,
                                newTitle = videoTitle,
                                selectedSubtitles = selectedSubtitles,
                                selectedAutoCaptions = selectedAutoCaptions,
                                skipDownload = isSubtitleOnly,
                                subtitleFormat = subtitleFormat,
                                embedSubtitle = embedSubtitleState,
                            )
                        )
                    },
                    modifier = Modifier.padding(12.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    text = { Text(stringResource(R.string.start_download)) },
                    expanded = isFabExpanded,
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { paddingValues ->
        LazyVerticalGrid(
            modifier = Modifier.padding(paddingValues),
            state = lazyGridState,
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FormatVideoPreview(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = videoTitle.ifEmpty { videoInfo.title ?: "" },
                        author = videoInfo.uploader ?: videoInfo.channel ?: videoInfo.uploaderId ?: videoInfo.extractor ?: stringResource(id = R.string.unknown),
                        thumbnailUrl = videoInfo.thumbnail.toHttpsUrl(),
                        durationMs = videoInfo.duration?.times(1000)?.toLong(),
                        isClippingVideo = isClippingVideo,
                        isSplittingVideo = isSplittingVideo,
                        isClippingAvailable = isClippingAvailable,
                        isSplitByChapterAvailable = isSplitByChapterAvailable,
                        onClippingToggled = { isClippingVideo = !isClippingVideo },
                        onSplittingToggled = { isSplittingVideo = !isSplittingVideo },
                        onRename = { showRenameDialog = true },
                        onOpenThumbnail = { uriHandler.openUri(videoInfo.thumbnail.toHttpsUrl()) },
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                var shouldUpdateClipDuration by remember { mutableStateOf(false) }

                Column {
                    AnimatedVisibility(visible = isClippingVideo) {
                        Column {
                            val state =
                                remember(isClippingVideo, showVideoClipDialog) {
                                    RangeSliderState(
                                        activeRangeStart = videoClipDuration.start,
                                        activeRangeEnd = videoClipDuration.endInclusive,
                                        valueRange = videoDurationRange,
                                        onValueChangeFinished = { shouldUpdateClipDuration = true },
                                    )
                                }
                            DisposableEffect(shouldUpdateClipDuration) {
                                videoClipDuration = state.activeRangeStart..state.activeRangeEnd
                                onDispose { shouldUpdateClipDuration = false }
                            }

                            VideoSelectionSlider(
                                modifier = Modifier.fillMaxWidth(),
                                state = state,
                                onDiscard = { isClippingVideo = false },
                                onDurationClick = { showVideoClipDialog = true },
                            )
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }

                    AnimatedVisibility(visible = isSplittingVideo) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            id = R.string.split_video_msg,
                                            videoInfo.chapters?.size ?: 0,
                                        ),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                TextButtonWithIcon(
                                    onClick = { isSplittingVideo = false },
                                    icon = Icons.Outlined.Delete,
                                    text = stringResource(id = R.string.discard),
                                    contentColor = MaterialTheme.colorScheme.error,
                                )
                            }
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                }
            }

            if (totalSubtitlesCount > 0 || isSubtitleOnly) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 12.dp).padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = stringResource(id = R.string.subtitle_language),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )

                            ClickableTextAction(
                                visible = true,
                                text =
                                    stringResource(
                                        id = R.string.show_all_items,
                                        totalSubtitlesCount,
                                    ),
                            ) {
                                showSubtitleSelectionDialog = true
                            }
                        }

                        LazyRow(modifier = Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((code, formats) in suggestedSubtitleMap) {
                                item(key = code) {
                                    val isManual = manualSubtitleMap.containsKey(code)
                                    val isChecked = if (isManual) selectedSubtitles.contains(code) else selectedAutoCaptions.contains(code)
                                    VideoFilterChip(
                                        selected = isChecked,
                                        onClick = {
                                            if (isManual) {
                                                if (selectedSubtitles.contains(code)) selectedSubtitles.remove(code)
                                                else selectedSubtitles.add(code)
                                            } else {
                                                if (selectedAutoCaptions.contains(code)) selectedAutoCaptions.remove(code)
                                                else selectedAutoCaptions.add(code)
                                            }
                                        },
                                        label = formats.firstOrNull()?.run { name ?: protocol ?: code } ?: code,
                                    )
                                }
                            }
                        }
                        
                        if (!isSubtitleOnly && !audioOnly) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = stringResource(id = R.string.embed_subtitles),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                androidx.compose.material3.Switch(
                                    checked = embedSubtitleState,
                                    onCheckedChange = { embedSubtitleState = it },
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(id = R.string.convert_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            var subtitleFormatExpanded by remember { mutableStateOf(false) }
                            androidx.compose.foundation.layout.Box {
                                TextButton(onClick = { subtitleFormatExpanded = true }) {
                                    Text(com.junkfood.seal.util.PreferenceStrings.getSubtitleConversionFormat(subtitleFormat))
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = subtitleFormatExpanded,
                                    onDismissRequest = { subtitleFormatExpanded = false }
                                ) {
                                    listOf(0, 1, 2, 3, 4).forEach { format ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(com.junkfood.seal.util.PreferenceStrings.getSubtitleConversionFormat(format)) },
                                            onClick = {
                                                subtitleFormat = format
                                                subtitleFormatExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isSuggestedFormatAvailable && !isSubtitleOnly && !audioOnly) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.padding(top = 12.dp, bottom = 4.dp).padding(horizontal = 12.dp),
                    ) {
                        FormatSubtitle(text = stringResource(R.string.suggested))
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val onClick = {
                        isSuggestedFormatSelected = true
                        selectedAudioOnlyFormats.clear()
                        selectedVideoFormat = NOT_SELECTED
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SuggestedFormatItem(
                            modifier = Modifier.weight(1f),
                            videoInfo = videoInfo,
                            selected = isSuggestedFormatSelected,
                            onClick = onClick,
                        )
                    }
                }
            }

            val showAudioSection = audioOnlyFormats.isNotEmpty() && !isSubtitleOnly && (audioOnly || mergeAudioStream)

            if (showAudioSection) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp).padding(horizontal = 12.dp),
                    ) {
                        FormatSubtitle(
                            text = stringResource(R.string.audio),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        )

                        ClickableTextAction(
                            visible = audioOnlyItemLimit < audioOnlyFormats.size,
                            text = stringResource(R.string.show_all_items, audioOnlyFormats.size),
                        ) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            audioOnlyItemLimit = Int.MAX_VALUE
                        }
                    }
                }

                itemsIndexed(
                    audioOnlyFormats.subList(
                        fromIndex = 0,
                        toIndex = min(audioOnlyItemLimit, audioOnlyFormats.size),
                    )
                ) { index, formatInfo ->
                    FormatItem(
                        formatInfo = formatInfo,
                        duration = duration,
                        selected = selectedAudioOnlyFormats.contains(index),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        outlineColor = MaterialTheme.colorScheme.secondary,
                        onLongClick = { formatInfo.url.share() },
                    ) {
                        if (selectedAudioOnlyFormats.contains(index)) {
                            selectedAudioOnlyFormats.remove(index)
                        } else {
                            if (!mergeAudioStream) {
                                selectedAudioOnlyFormats.clear()
                            }
                            isSuggestedFormatSelected = false
                            selectedAudioOnlyFormats.add(index)
                        }
                    }
                }
            }

            if (videoFormats.isNotEmpty() && !isSubtitleOnly && !audioOnly) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp).padding(horizontal = 12.dp),
                    ) {
                        FormatSubtitle(
                            text = stringResource(R.string.video),
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        )
                        ClickableTextAction(
                            visible = videoItemLimit < videoFormats.size,
                            text = stringResource(R.string.show_all_items, videoFormats.size),
                        ) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            videoItemLimit = Int.MAX_VALUE
                        }
                    }
                }
                itemsIndexed(
                    videoFormats.subList(0, min(videoItemLimit, videoFormats.size))
                ) { index, formatInfo ->
                    FormatItem(
                        formatInfo = formatInfo,
                        duration = duration,
                        selected = selectedVideoFormat == index,
                        onLongClick = { formatInfo.url.share() },
                    ) {
                        selectedVideoFormat =
                            if (selectedVideoFormat == index) NOT_SELECTED
                            else {
                                isSuggestedFormatSelected = false
                                index
                            }
                    }
                }
            }

            if (showAudioSection && videoFormats.isNotEmpty() && !isSubtitleOnly && !audioOnly)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PreferenceInfo(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        text = stringResource(R.string.abs_hint),
                        applyPaddings = false,
                    )
                }
            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
    }
    if (showVideoClipDialog)
        VideoClipDialog(
            onDismissRequest = { showVideoClipDialog = false },
            initialValue = videoClipDuration,
            valueRange = videoDurationRange,
            onConfirm = { videoClipDuration = it },
        )

    if (showRenameDialog)
        RenameDialog(
            initialValue = videoTitle.ifEmpty { videoInfo.title ?: "" },
            onDismissRequest = { showRenameDialog = false },
        ) {
            videoTitle = it
        }
    if (showSubtitleSelectionDialog)
        SubtitleSelectionDialog(
            suggestedSubtitles = manualSubtitleMap,
            autoCaptions = autoCaptionMap,
            selectedSubtitles = selectedSubtitles,
            selectedAutoCaptions = selectedAutoCaptions,
            onDismissRequest = { showSubtitleSelectionDialog = false },
            onConfirm = { subs, autoSubs ->
                selectedSubtitles.run {
                    clear()
                    addAll(subs)
                }
                selectedAutoCaptions.run {
                    clear()
                    addAll(autoSubs)
                }

                showSubtitleSelectionDialog = false
            },
        )
}

@Composable
private fun RenameDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var filename by remember { mutableStateOf(initialValue) }
    SealDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfirmButton {
                onConfirm(filename)
                onDismissRequest()
            }
        },
        dismissButton = { DismissButton { onDismissRequest() } },
        title = { Text(text = stringResource(id = R.string.rename)) },
        icon = { Icon(imageVector = Icons.Outlined.Edit, contentDescription = null) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text(text = stringResource(id = R.string.title)) },
                    trailingIcon = { if (filename == initialValue) ClearButton { filename = "" } },
                )
            }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VideoClipDialog(
    modifier: Modifier = Modifier,
    initialValue: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    onDismissRequest: () -> Unit,
    onConfirm: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    var range by remember { mutableStateOf(initialValue) }
    SealDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfirmButton {
                onConfirm(range)
                onDismissRequest()
            }
        },
        dismissButton = { DismissButton { onDismissRequest() } },
        title = { Text(text = stringResource(id = R.string.video_clip)) },
        icon = { Icon(imageVector = Icons.Outlined.ContentCut, contentDescription = null) },
        text = {
            Column {
                VideoSelectionSlider(
                    modifier = Modifier.fillMaxWidth(),
                    state =
                        remember {
                            RangeSliderState(
                                activeRangeStart = range.start,
                                activeRangeEnd = range.endInclusive,
                                valueRange = valueRange,
                                onValueChangeFinished = {},
                            )
                        },
                    onDiscard = {},
                    onDurationClick = {},
                )
            }
        },
    )
}

private fun getLocalizedSubtitleName(code: String, formatName: String?): String {
    val cleanCode = code.substringBefore("-")
    val locale = try {
        val loc = java.util.Locale.forLanguageTag(code)
        if (loc.displayName.isNotBlank()) loc else java.util.Locale.forLanguageTag(cleanCode)
    } catch (e: Exception) {
        null
    }
    val localName = locale?.getDisplayName(java.util.Locale.getDefault())?.replaceFirstChar { it.uppercase() }
    return when {
        !localName.isNullOrBlank() && !formatName.isNullOrBlank() && !localName.equals(formatName, ignoreCase = true) ->
            "$localName ($formatName)"
        !localName.isNullOrBlank() -> localName
        !formatName.isNullOrBlank() -> formatName
        else -> code
    }
}

private fun (Map<String, List<SubtitleFormat>>).filterWithSearchText(
    searchText: String
): Map<String, List<SubtitleFormat>> {
    return this.filter { (code, formats) ->
        if (searchText.isBlank()) return@filter true
        val formatName = formats.firstOrNull()?.name.orEmpty()
        val localized = getLocalizedSubtitleName(code, formatName)
        code.contains(searchText, ignoreCase = true) ||
            formatName.contains(searchText, ignoreCase = true) ||
            localized.contains(searchText, ignoreCase = true)
    }
}

private fun Map<String, List<SubtitleFormat>>.sortedWithSelection(
    selectedKeys: List<String>
): Map<String, List<SubtitleFormat>> {
    return this.toList()
        .sortedWith { entry1, entry2 ->
            when {
                entry1.first in selectedKeys && entry2.first in selectedKeys ->
                    entry1.compareTo(entry2) // Both in selectedKeys - equal priority
                entry1.first in selectedKeys -> -1 // str1 has priority
                entry2.first in selectedKeys -> 1 // str2 has priority
                else -> entry1.compareTo(entry2)
            }
        }
        .toMap()
}

/**
 * Prioritizes comparison of subtitle names (via `getSubtitleName()`) if available, otherwise
 * compares the `key` portion of the pairs.
 *
 * Examples: `zh` (Chinese) should be greater than `en` (English) according to their names
 */
private fun (Pair<String, List<SubtitleFormat>>).compareTo(
    other: (Pair<String, List<SubtitleFormat>>)
): Int {
    val (key, list) = this
    val (otherKey, otherList) = other

    val name = list.getSubtitleName()
    val otherName = otherList.getSubtitleName()

    return if (name != null && otherName != null) {
        name.compareTo(otherName)
    } else {
        key.compareTo(otherKey)
    }
}

private fun (List<SubtitleFormat>).getSubtitleName(): String? = firstOrNull()?.name

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtitleSelectionDialog(
    suggestedSubtitles: Map<String, List<SubtitleFormat>>,
    autoCaptions: Map<String, List<SubtitleFormat>>,
    selectedSubtitles: List<String>,
    selectedAutoCaptions: List<String> = emptyList(),
    onDismissRequest: () -> Unit = {},
    onConfirm: (subs: List<String>, autoSubs: List<String>) -> Unit = { _, _ -> },
) {
    var searchText by remember { mutableStateOf("") }
    val selectedSubtitlesState = remember {
        mutableStateListOf<String>().apply { addAll(selectedSubtitles) }
    }
    val selectedAutoCaptionsState = remember { 
        mutableStateListOf<String>().apply { addAll(selectedAutoCaptions) }
    }

    val suggestedSubtitlesFiltered =
        suggestedSubtitles.filterWithSearchText(searchText).sortedWithSelection(selectedSubtitlesState)
    val autoCaptionsFiltered =
        autoCaptions.filterWithSearchText(searchText).sortedWithSelection(selectedAutoCaptionsState)

    SealDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        onDismissRequest = onDismissRequest,
        confirmButton = { ConfirmButton { onConfirm(selectedSubtitlesState, selectedAutoCaptionsState) } },
        dismissButton = { DismissButton { onDismissRequest() } },
        title = { Text(text = stringResource(id = R.string.subtitle_language)) },
        icon = { Icon(imageVector = Icons.Outlined.Subtitles, contentDescription = null) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (autoCaptions.size + suggestedSubtitles.size > 4) {
                    SealSearchBar(
                        text = searchText,
                        placeholderText = stringResource(R.string.search_in_subtitles),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        searchText = it
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            selectedSubtitlesState.clear()
                            selectedSubtitlesState.addAll(suggestedSubtitles.keys)
                            selectedAutoCaptionsState.clear()
                            selectedAutoCaptionsState.addAll(autoCaptions.keys)
                        }
                    ) {
                        Text(stringResource(R.string.select_all))
                    }
                    TextButton(
                        onClick = {
                            selectedSubtitlesState.clear()
                            selectedAutoCaptionsState.clear()
                        }
                    ) {
                        Text(stringResource(R.string.deselect_all))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (suggestedSubtitlesFiltered.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(id = R.string.manual_subtitles),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        for ((code, formats) in suggestedSubtitlesFiltered) {
                            item(key = "manual_$code") {
                                DialogCheckBoxItem(
                                    modifier = Modifier.animateItem(),
                                    checked = selectedSubtitlesState.contains(code),
                                    onValueChange = {
                                        if (selectedSubtitlesState.contains(code)) {
                                            selectedSubtitlesState.remove(code)
                                        } else {
                                            selectedSubtitlesState.add(code)
                                        }
                                    },
                                    text = getLocalizedSubtitleName(code, formats.firstOrNull()?.name ?: formats.firstOrNull()?.protocol ?: code),
                                )
                            }
                        }
                    }

                    if (autoCaptionsFiltered.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(id = R.string.auto_subtitle),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        for ((code, formats) in autoCaptionsFiltered) {
                            item(key = "auto_$code") {
                                DialogCheckBoxItem(
                                    modifier = Modifier.animateItem(),
                                    checked = selectedAutoCaptionsState.contains(code),
                                    onValueChange = {
                                        if (selectedAutoCaptionsState.contains(code)) {
                                            selectedAutoCaptionsState.remove(code)
                                        } else {
                                            selectedAutoCaptionsState.add(code)
                                        }
                                    },
                                    text = getLocalizedSubtitleName(code, formats.firstOrNull()?.name ?: formats.firstOrNull()?.protocol ?: code),
                                )
                            }
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            }
        },
    )
}

@Preview
@Composable
private fun SubtitleSelectionDialogPreview() {
    val captionsMap =
        mapOf(
            "en-en" to listOf(SubtitleFormat(ext = "", url = "", name = "English from English")),
            "ja-en" to listOf(SubtitleFormat(ext = "", url = "", name = "Japanese from English")),
            "zh-Hans-en" to
                listOf(
                    SubtitleFormat(ext = "", url = "", name = "Chinese (Simplified) from English")
                ),
            "zh-Hant-en" to
                listOf(
                    SubtitleFormat(ext = "", url = "", name = "Chinese (Traditional) from English")
                ),
        )

    val subMap = buildMap {
        put("en", listOf(SubtitleFormat(ext = "ass", url = "", name = "English")))
        put("ja", listOf(SubtitleFormat(ext = "ass", url = "", name = "Japanese")))
    }

    SealTheme {
        SubtitleSelectionDialog(
            suggestedSubtitles = subMap,
            autoCaptions = captionsMap,
            selectedSubtitles = listOf(),
        )
    }
}

@Composable
private fun ClickableTextAction(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedVisibility(visible = visible, exit = fadeOut(animationSpec = spring())) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier =
                modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp, horizontal = 12.dp),
        )
    }
}

fun <T : Collection<String>> T.filterWithRegex(subtitleLanguageRegex: String): Set<String> {
    return com.junkfood.seal.download.engine.subtitle.discovery.LanguageMatcher.matchLanguageCodes(this, subtitleLanguageRegex)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Preview
fun UpdateSubtitleLanguageDialog(
    modifier: Modifier = Modifier,
    languages: Set<String> = setOf("en", "ja"),
    onDismissRequest: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.update_subtitle_languages),
                textAlign = TextAlign.Center,
            )
        },
        icon = { Icon(imageVector = Icons.Filled.Subtitles, contentDescription = null) },
        text = {
            Column {
                Text(text = stringResource(R.string.update_language_msg))

                Spacer(modifier = Modifier.height(24.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    languages.forEach {
                        Row(modifier = Modifier, verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier.padding(end = 8.dp)
                                        .size(16.dp)
                                        .background(
                                            color = it.hashCode().generateLabelColor(),
                                            shape = CircleShape,
                                        )
                                        .clearAndSetSemantics {}
                            ) {}
                            Text(
                                text = it,
                                modifier = Modifier,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(text = stringResource(id = R.string.okay)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.no_thanks))
            }
        },
    )
}
