package com.junkfood.seal.ui.page.downloadv2.configure

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NewLabel
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.junkfood.seal.App
import com.junkfood.seal.R
import com.junkfood.seal.ui.common.HapticFeedback.longPressHapticFeedback
import com.junkfood.seal.ui.common.motion.materialSharedAxisX
import com.junkfood.seal.ui.component.ButtonChip
import com.junkfood.seal.ui.component.DrawerSheetSubtitle
import com.junkfood.seal.ui.component.OutlinedButtonWithIcon
import com.junkfood.seal.ui.component.SealModalBottomSheet
import com.junkfood.seal.ui.component.SealModalBottomSheetM2Variant
import com.junkfood.seal.ui.component.SingleChoiceChip
import com.junkfood.seal.ui.component.SingleChoiceSegmentedButton
import com.junkfood.seal.ui.component.VideoFilterChip
import com.junkfood.seal.ui.page.command.TemplatePickerDialog
import com.junkfood.seal.ui.page.downloadv2.configure.ActionButton.Download
import com.junkfood.seal.ui.page.downloadv2.configure.ActionButton.FetchInfo
import com.junkfood.seal.ui.page.downloadv2.configure.ActionButton.StartTask
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.SelectionState
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetState.Configure
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetState.Error
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetState.InputUrl
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetState.Loading
import com.junkfood.seal.ui.page.settings.command.CommandTemplateDialog
import com.junkfood.seal.ui.page.settings.format.AudioQuickSettingsDialog
import com.junkfood.seal.ui.page.settings.format.VideoQuickSettingsDialog
import com.junkfood.seal.ui.page.settings.network.CookiesQuickSettingsDialog
import com.junkfood.seal.ui.theme.SealTheme
import com.junkfood.seal.util.AUDIO_CONVERSION_FORMAT
import com.junkfood.seal.util.AUDIO_CONVERT
import com.junkfood.seal.util.AUDIO_FORMAT
import com.junkfood.seal.util.AUDIO_QUALITY
import com.junkfood.seal.util.COOKIES
import com.junkfood.seal.util.CUSTOM_COMMAND
import com.junkfood.seal.util.DatabaseUtil
import com.junkfood.seal.util.DownloadType
import com.junkfood.seal.util.DownloadType.Audio
import com.junkfood.seal.util.DownloadType.Command
import com.junkfood.seal.util.DownloadType.Playlist
import com.junkfood.seal.util.DownloadType.Video
import com.junkfood.seal.util.DownloadType.entries
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.EXTRACT_AUDIO
import com.junkfood.seal.util.FORMAT_SELECTION
import com.junkfood.seal.util.PreferenceStrings
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.VideoInfo
import com.junkfood.seal.util.SubtitleFormat
import com.junkfood.seal.util.PreferenceUtil.updateInt
import com.junkfood.seal.util.PreferenceUtil.updateString
import com.junkfood.seal.util.SUBTITLE
import com.junkfood.seal.util.SUBTITLE_LANGUAGE
import com.junkfood.seal.util.TEMPLATE_ID
import com.junkfood.seal.util.THUMBNAIL
import com.junkfood.seal.util.ToastUtil
import com.junkfood.seal.util.USE_CUSTOM_AUDIO_PRESET
import com.junkfood.seal.util.VIDEO_FORMAT
import com.junkfood.seal.util.VIDEO_QUALITY
import com.junkfood.seal.download.TaskFactory
import kotlinx.coroutines.launch

@Composable
private fun DownloadType.label(): String =
    stringResource(
        when (this) {
            Audio -> R.string.audio
            Video -> R.string.video
            Command -> R.string.commands
            Playlist -> R.string.playlist
            DownloadType.Subtitle -> R.string.subtitle
        }
    )

val PreferencesMock = DownloadUtil.DownloadPreferences.EMPTY

data class Config(
    val downloadType: DownloadType? = PreferenceUtil.getDownloadType() ?: Video,
    val typeEntries: List<DownloadType> =
        when (CUSTOM_COMMAND.getBoolean()) {
            true -> DownloadType.entries
            false -> DownloadType.entries - Command
        },
    val useFormatSelection: Boolean = FORMAT_SELECTION.getBoolean(),
    val savedLinks: Set<String> = PreferenceUtil.getSavedLinks(),
    val subtitleLanguage: String? = null,
    val downloadPath: String? = null,
) {
    companion object {
        fun updatePreferences(newValue: Config, oldValue: Config) {
            with(newValue) {
                if (downloadType != oldValue.downloadType) {
                    downloadType?.let { PreferenceUtil.updateDownloadType(it) }
                }
                if (useFormatSelection != oldValue.useFormatSelection) {
                    FORMAT_SELECTION.updateBoolean(useFormatSelection)
                }
                if (savedLinks != oldValue.savedLinks) {
                    PreferenceUtil.updateSavedLinks(savedLinks)
                }
                if (subtitleLanguage != oldValue.subtitleLanguage) {
                    subtitleLanguage?.let { SUBTITLE_LANGUAGE.updateString(it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDialog(
    modifier: Modifier = Modifier,
    config: Config,
    sheetState: SheetState,
    preferences: DownloadUtil.DownloadPreferences,
    onPreferencesUpdate: (DownloadUtil.DownloadPreferences) -> Unit,
    state: DownloadDialogViewModel.SheetState = InputUrl,
    onActionPost: (Action) -> Unit = {},
) {
    var showVideoPresetDialog by remember { mutableStateOf(false) }
    var showAudioPresetDialog by remember { mutableStateOf(false) }

    SealModalBottomSheet(
        sheetState = sheetState,
        contentPadding = PaddingValues(),
        onDismissRequest = { onActionPost(Action.HideSheet) },
    ) {
        DownloadDialogContent(
            modifier = modifier,
            state = state,
            config = config,
            preferences = preferences,
            onPreferencesUpdate = onPreferencesUpdate,
            onPresetEdit = { type ->
                when (type) {
                    Audio -> showAudioPresetDialog = true
                    Video -> showVideoPresetDialog = true
                    else -> {}
                }
            },
            onActionPost = onActionPost,
        )
    }

    if (showVideoPresetDialog) {
        var res by remember(preferences) { mutableIntStateOf(preferences.videoResolution) }
        var format by remember(preferences) { mutableIntStateOf(preferences.videoFormat) }

        VideoQuickSettingsDialog(
            videoResolution = res,
            videoFormatPreference = format,
            onResolutionSelect = { res = it },
            onFormatSelect = { format = it },
            onDismissRequest = { showVideoPresetDialog = false },
            onSave = {
                VIDEO_FORMAT.updateInt(format)
                VIDEO_QUALITY.updateInt(res)
                onPreferencesUpdate(DownloadUtil.DownloadPreferences.createFromPreferences())
            },
        )
    }

    if (showAudioPresetDialog) {
        var quality by remember(preferences) { mutableIntStateOf(preferences.audioQuality) }
        var customPreset by
            remember(preferences) { mutableStateOf(preferences.useCustomAudioPreset) }
        var conversionFmt by
            remember(preferences) { mutableIntStateOf(preferences.audioConvertFormat) }
        var convertAudio by remember(preferences) { mutableStateOf(preferences.convertAudio) }
        var preferredFormat by remember(preferences) { mutableIntStateOf(preferences.audioFormat) }

        AudioQuickSettingsDialog(
            modifier = Modifier,
            preferences = preferences,
            audioQuality = quality,
            onQualitySelect = { quality = it },
            useCustomAudioPreset = customPreset,
            onCustomPresetToggle = { customPreset = it },
            convertAudio = convertAudio,
            onConvertToggled = { convertAudio = it },
            conversionFormat = conversionFmt,
            onConversionSelect = { conversionFmt = it },
            preferredFormat = preferredFormat,
            onPreferredSelect = { preferredFormat = it },
            onDismissRequest = { showAudioPresetDialog = false },
            onSave = {
                AUDIO_QUALITY.updateInt(quality)
                USE_CUSTOM_AUDIO_PRESET.updateBoolean(customPreset)
                AUDIO_CONVERSION_FORMAT.updateInt(conversionFmt)
                AUDIO_CONVERT.updateBoolean(convertAudio)
                AUDIO_FORMAT.updateInt(preferredFormat)
                onPreferencesUpdate(DownloadUtil.DownloadPreferences.createFromPreferences())
            },
        )
    }
}

@Composable
private fun ErrorPage(modifier: Modifier = Modifier, state: Error, onActionPost: (Action) -> Unit) {
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val url =
        state.action.run {
            when (this) {
                is Action.FetchFormats -> url
                is Action.FetchPlaylist -> url
                else -> {
                    throw IllegalArgumentException()
                }
            }
        }
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.fetch_info_error_msg),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = state.throwable.message.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(vertical = 16.dp, horizontal = 20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            maxLines = 20,
            overflow = TextOverflow.Clip,
        )

        Row(modifier = Modifier) {
            FilledTonalButton(onClick = { onActionPost(state.action) }) { Text("Retry") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    view.longPressHapticFeedback()
                    clipboardManager.setText(
                        AnnotatedString(
                            App.getVersionReport() + "\nURL: ${url}\n${state.throwable.message}"
                        )
                    )
                    ToastUtil.makeToast(R.string.error_copied)
                }
            ) {
                Text(stringResource(R.string.copy_error_report))
            }
        }
    }
}

@Composable
private fun DownloadDialogContent(
    modifier: Modifier = Modifier,
    state: DownloadDialogViewModel.SheetState,
    config: Config,
    preferences: DownloadUtil.DownloadPreferences,
    onPreferencesUpdate: (DownloadUtil.DownloadPreferences) -> Unit,
    onPresetEdit: (DownloadType?) -> Unit,
    onActionPost: (Action) -> Unit,
) {
    AnimatedContent(
        modifier = modifier,
        targetState = state,
        label = "DownloadDialogContent",
        transitionSpec = {
            materialSharedAxisX(initialOffsetX = { it / 6 }, targetOffsetX = { -it / 6 })
        },
    ) { state ->
        when (state) {
            is Configure -> {
                check(state.urlList.isNotEmpty())
                if (state.urlList.size == 1) {
                    ConfigurePage(
                        url = state.urlList.first(),
                        config = config,
                        preferences = preferences,
                        onPreferencesUpdate = onPreferencesUpdate,
                        onPresetEdit = onPresetEdit,
                        onConfigSave = {
                            Config.updatePreferences(newValue = it, oldValue = config)
                        },
                        settingChips = {
                            AdditionalSettings(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                isQuickDownload = false,
                                preference = preferences,
                                selectedType = config.downloadType,
                                useFormatSelection = config.useFormatSelection,
                                onPreferenceUpdate = {
                                    onPreferencesUpdate(
                                        DownloadUtil.DownloadPreferences.createFromPreferences()
                                    )
                                },
                            )
                        },
                        onActionPost = { onActionPost(it) },
                    )
                } else {
                    // FIX: ConfigurePagePlaylistVariant's trailing lambda parameter is
                    // onDownload: (DownloadType, Boolean) -> Unit  (a Function2).
                    // The previous code wrote a parameter-less lambda that referenced
                    // an unbound `it`, which the compiler resolves as Function0<Unit>,
                    // causing: "Argument type mismatch: actual type is
                    // kotlin.Function0<Unit> but expected kotlin.Function2<...>".
                    // Declaring both parameters explicitly (ignoring the unused second
                    // one with `_`) matches the expected function type.
                    ConfigurePagePlaylistVariant(
                        initialDownloadType = config.downloadType ?: Video,
                        preferences = preferences,
                        onPreferencesUpdate = onPreferencesUpdate,
                        onPresetEdit = onPresetEdit,
                        onDismissRequest = { onActionPost(Action.HideSheet) },
                    ) { selectedDownloadType, useFormatSelection ->
                        if (useFormatSelection || selectedDownloadType == DownloadType.Subtitle) {
                            val firstUrl = state.urlList.firstOrNull()
                            if (firstUrl != null) {
                                val updatedPrefs = preferences.copy(
                                    extractAudio = selectedDownloadType == Audio,
                                    skipDownload = selectedDownloadType == DownloadType.Subtitle,
                                    downloadSubtitle = if (selectedDownloadType == DownloadType.Subtitle) true else preferences.downloadSubtitle,
                                    autoSubtitle = if (selectedDownloadType == DownloadType.Subtitle) true else preferences.autoSubtitle,
                                    autoTranslatedSubtitles = if (selectedDownloadType == DownloadType.Subtitle) true else preferences.autoTranslatedSubtitles,
                                )
                                onActionPost(
                                    Action.FetchPlaylistSubtitleFormats(
                                        firstVideoUrl = firstUrl,
                                        preferences = updatedPrefs,
                                        playlistTasks = state.urlList.mapIndexed { index, itemUrl ->
                                            val task = com.junkfood.seal.download.Task(
                                                url = itemUrl,
                                                preferences = updatedPrefs,
                                                type = com.junkfood.seal.download.Task.TypeInfo.Playlist(
                                                    index = index + 1,
                                                    playlistTitle = "",
                                                    playlistUrl = itemUrl,
                                                )
                                            )
                                            val itemState = com.junkfood.seal.download.Task.State(
                                                downloadState = com.junkfood.seal.download.Task.DownloadState.Idle,
                                                videoInfo = null,
                                                viewState = com.junkfood.seal.download.Task.ViewState(
                                                    url = itemUrl,
                                                    title = "",
                                                    duration = 0,
                                                    uploader = "",
                                                    thumbnailUrl = "",
                                                )
                                            )
                                            TaskFactory.TaskWithState(task, itemState)
                                        }
                                    )
                                )
                            }
                        } else {
                            onActionPost(
                                Action.DownloadWithPreset(
                                    urlList = state.urlList,
                                    preferences = preferences.copy(
                                        extractAudio = selectedDownloadType == Audio,
                                        skipDownload = selectedDownloadType == DownloadType.Subtitle,
                                        downloadSubtitle = if (selectedDownloadType == DownloadType.Subtitle) true else preferences.downloadSubtitle,
                                        autoSubtitle = if (selectedDownloadType == DownloadType.Subtitle) true else preferences.autoSubtitle,
                                        autoTranslatedSubtitles = if (selectedDownloadType == DownloadType.Subtitle) true else preferences.autoTranslatedSubtitles,
                                    ),
                                )
                            )
                        }
                    }
                }
            }

            is Error -> {
                ErrorPage(state = state, onActionPost = onActionPost)
            }

            is Loading -> {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 120.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            InputUrl -> {
                InputUrlPage(
                    config = config,
                    onConfigUpdate = { Config.updatePreferences(newValue = it, oldValue = config) },
                    onActionPost = onActionPost,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ErrorPreview() {
    SealModalBottomSheet(
        onDismissRequest = {},
        sheetState =
            with(LocalDensity.current) {
                SheetState(
                    initialValue = SheetValue.Expanded,
                    skipPartiallyExpanded = true,
                    velocityThreshold = { 56.dp.toPx() },
                    positionalThreshold = { 125.dp.toPx() },
                )
            },
    ) {
        ErrorPage(
            state =
                Error(
                    action =
                        Action.FetchFormats(
                            url = "",
                            audioOnly = true,
                            preferences = PreferencesMock,
                        ),
                    throwable = Exception("Not good"),
                ),
            onActionPost = {},
        )
    }
}

@Composable
fun FormatPage(
    modifier: Modifier = Modifier,
    state: SelectionState.FormatSelection,
    onDismissRequest: () -> Unit,
) {
    val sheetState =
        androidx.compose.material.rememberModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            skipHalfExpanded = true,
        )

    LaunchedEffect(state) { sheetState.show() }
    val scope = rememberCoroutineScope()
    BackHandler { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() } }

    SealModalBottomSheetM2Variant(sheetState = sheetState, sheetGesturesEnabled = false) {
        FormatPage(
            modifier = modifier,
            videoInfo = state.info,
            playlistTasks = state.playlistTasks,
            onNavigateBack = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigurePagePreview() {
    SealTheme() {
        SealModalBottomSheet(
            sheetState =
                with(LocalDensity.current) {
                    SheetState(
                        initialValue = SheetValue.Expanded,
                        skipPartiallyExpanded = true,
                        velocityThreshold = { 56.dp.toPx() },
                        positionalThreshold = { 125.dp.toPx() },
                    )
                },
            onDismissRequest = {},
            contentPadding = PaddingValues(),
        ) {
            ConfigurePage(
                config =
                    Config(
                        downloadType = Audio,
                        useFormatSelection = true,
                        typeEntries = entries - Command,
                    ),
                preferences = PreferencesMock,
                onPreferencesUpdate = {},
                onConfigSave = {},
                settingChips = {},
            ) {}
        }
    }
}

@Composable
private fun ConfigurePage(
    modifier: Modifier = Modifier,
    url: String = "",
    config: Config,
    preferences: DownloadUtil.DownloadPreferences,
    onPreferencesUpdate: (DownloadUtil.DownloadPreferences) -> Unit,
    settingChips: @Composable () -> Unit,
    onPresetEdit: (DownloadType?) -> Unit = {},
    onConfigSave: (Config) -> Unit,
    onActionPost: (Action) -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Fix: rememberSaveable للحفاظ على الحالة عند تغيير الإعدادات أو اللغة
    var selectedType by rememberSaveable(config.downloadType) {
        mutableStateOf(config.downloadType)
    }
    var useFormatSelection by rememberSaveable(config.useFormatSelection) {
        mutableStateOf(config.useFormatSelection)
    }

    // Fix: التأكد من أن selectedType ليس null قبل السماح بالمتابعة
    val canProceed = selectedType != null && selectedType in config.typeEntries

    var showTemplateSelectionDialog by remember { mutableStateOf(false) }
    var showTemplateCreatorDialog by remember { mutableStateOf(false) }
    var showTemplateEditorDialog by remember { mutableStateOf(false) }
    val template by
        remember(showTemplateCreatorDialog, showTemplateSelectionDialog, showTemplateEditorDialog) {
            mutableStateOf(PreferenceUtil.getTemplate())
        }

    // Fix: ترتيب المعدلات الصحيح + fillMaxWidth لتجنب تمدد غير متوقع
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Header(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    title = stringResource(R.string.settings_before_download),
                    icon = Icons.Outlined.DoneAll,
                )
                DrawerSheetSubtitle(text = stringResource(id = R.string.download_type))
                DownloadTypeSelectionGroup(
                    typeEntries = config.typeEntries,
                    selectedType = selectedType,
                    onSelect = {
                        selectedType = it
                        EXTRACT_AUDIO.updateBoolean(it == Audio)
                    },
                )
                Column(modifier = Modifier.animateContentSize()) {
                    if (selectedType != Command) {
                        DrawerSheetSubtitle(
                            text = stringResource(id = R.string.format_selection),
                            modifier = Modifier,
                        )
                        Preset(
                            modifier = Modifier,
                            preference = preferences,
                            selected = !useFormatSelection,
                            downloadType = selectedType,
                            onClick = { useFormatSelection = false },
                            showEditIcon = !useFormatSelection,
                            onEdit = { onPresetEdit(selectedType) },
                        )
                        Custom(
                            selected = useFormatSelection,
                            enabled = true,
                            downloadType = selectedType,
                            onClick = { useFormatSelection = true },
                        )
                    } else {
                        if (showTemplateSelectionDialog) {
                            TemplatePickerDialog { showTemplateSelectionDialog = false }
                        }
                        if (showTemplateCreatorDialog) {
                            CommandTemplateDialog(
                                onDismissRequest = { showTemplateCreatorDialog = false },
                                confirmationCallback = { scope.launch { TEMPLATE_ID.updateInt(it) } },
                            )
                        }
                        if (showTemplateEditorDialog) {
                            CommandTemplateDialog(
                                commandTemplate = template,
                                onDismissRequest = { showTemplateEditorDialog = false },
                            )
                        }
                        DrawerSheetSubtitle(
                            text = stringResource(id = R.string.template_selection),
                            modifier = Modifier,
                        )
                        LazyRow(modifier = Modifier) {
                            item {
                                ButtonChip(
                                    icon = Icons.Outlined.Code,
                                    label = template.name,
                                    onClick = { showTemplateSelectionDialog = true },
                                )
                            }
                            item {
                                ButtonChip(
                                    icon = Icons.Outlined.NewLabel,
                                    label = stringResource(id = R.string.new_template),
                                    onClick = { showTemplateCreatorDialog = true },
                                )
                            }
                            item {
                                ButtonChip(
                                    icon = Icons.Outlined.Edit,
                                    label = stringResource(id = R.string.edit_template, template.name),
                                    onClick = { showTemplateEditorDialog = true },
                                )
                            }
                        }
                    }
                }
            }
            var expanded by remember { mutableStateOf(false) }
            ExpandableTitle(expanded = expanded, onClick = { expanded = true }) { settingChips() }
        }

        Spacer(Modifier.height(8.dp))
        ActionButtons(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
            canProceed = canProceed,
            selectedType = selectedType,
            useFormatSelection = useFormatSelection,
            onCancel = { onActionPost(Action.HideSheet) },
            onDownload = {
                onConfigSave(
                    config.copy(
                        useFormatSelection = useFormatSelection,
                        downloadType = selectedType,
                    )
                )
                val isPlaylist = selectedType == Playlist || url.contains("list=", ignoreCase = true)
                onActionPost(
                    Action.DownloadWithPreset(
                        urlList = listOf(url),
                        preferences = preferences.copy(
                            downloadPlaylist = isPlaylist,
                            extractAudio = selectedType == Audio,
                            skipDownload = selectedType == DownloadType.Subtitle,
                            downloadSubtitle = if (selectedType == DownloadType.Subtitle) true else preferences.downloadSubtitle,
                            autoSubtitle = if (selectedType == DownloadType.Subtitle) true else preferences.autoSubtitle,
                            autoTranslatedSubtitles = if (selectedType == DownloadType.Subtitle) true else preferences.autoTranslatedSubtitles,
                        ),
                    )
                )
            },
            onFetchInfo = {
                onConfigSave(
                    config.copy(
                        useFormatSelection = useFormatSelection,
                        downloadType = selectedType,
                    )
                )
                val isPlaylistUrl = selectedType == Playlist || (selectedType != Video && selectedType != Audio && selectedType != DownloadType.Subtitle && url.contains("list=", ignoreCase = true))
                if (isPlaylistUrl) {
                    onActionPost(
                        Action.FetchPlaylist(
                            url = url,
                            preferences = preferences.copy(
                                downloadPlaylist = true,
                                extractAudio = selectedType == Audio,
                                skipDownload = selectedType == DownloadType.Subtitle,
                                downloadSubtitle = if (selectedType == DownloadType.Subtitle) true else preferences.downloadSubtitle,
                                autoSubtitle = if (selectedType == DownloadType.Subtitle) true else preferences.autoSubtitle,
                                autoTranslatedSubtitles = if (selectedType == DownloadType.Subtitle) true else preferences.autoTranslatedSubtitles,
                            )
                        )
                    )
                } else {
                    onActionPost(
                        Action.FetchFormats(
                            url = url,
                            audioOnly = selectedType == Audio,
                            preferences = preferences.copy(
                                extractAudio = selectedType == Audio,
                                skipDownload = selectedType == DownloadType.Subtitle,
                                downloadSubtitle = if (selectedType == DownloadType.Subtitle) true else preferences.downloadSubtitle,
                                autoSubtitle = if (selectedType == DownloadType.Subtitle) true else preferences.autoSubtitle,
                                autoTranslatedSubtitles = if (selectedType == DownloadType.Subtitle) true else preferences.autoTranslatedSubtitles,
                            ),
                        )
                    )
                }
            },
            onTaskStart = {
                onConfigSave(
                    config.copy(
                        useFormatSelection = useFormatSelection,
                        downloadType = selectedType,
                    )
                )
                onActionPost(
                    Action.RunCommand(url = url, template = template, preferences = preferences)
                )
            },
        )
    }
}

@Composable
fun ConfigurePagePlaylistVariant(
    modifier: Modifier = Modifier,
    initialDownloadType: DownloadType,
    preferences: DownloadUtil.DownloadPreferences,
    onPreferencesUpdate: (DownloadUtil.DownloadPreferences) -> Unit,
    onPresetEdit: (DownloadType?) -> Unit = {},
    onDismissRequest: () -> Unit,
    onDownload: (DownloadType, Boolean) -> Unit,
) {
    var selectedType by remember(initialDownloadType) { mutableStateOf(initialDownloadType) }
    var useFormatSelection by remember { mutableStateOf(false) }

    // Fix: ترتيب المعدلات الصحيح + fillMaxWidth
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Header(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    title = stringResource(R.string.settings_before_download),
                    icon = Icons.Outlined.DoneAll,
                )
                DrawerSheetSubtitle(text = stringResource(id = R.string.download_type))
                DownloadTypeSelectionGroup(
                    typeEntries = listOf(Video, Audio, DownloadType.Subtitle),
                    selectedType = selectedType,
                    onSelect = { selectedType = it },
                )
                DrawerSheetSubtitle(
                    text = stringResource(id = R.string.format_selection),
                    modifier = Modifier,
                )
                Preset(
                    modifier = Modifier,
                    preference = preferences,
                    selected = !useFormatSelection,
                    downloadType = selectedType,
                    onClick = { useFormatSelection = false },
                    showEditIcon = !useFormatSelection,
                    onEdit = { onPresetEdit(selectedType) },
                )
                Custom(
                    selected = useFormatSelection,
                    enabled = true,
                    downloadType = selectedType,
                    onClick = { useFormatSelection = true },
                )
            }
            var expanded by remember { mutableStateOf(false) }
            ExpandableTitle(expanded = expanded, onClick = { expanded = true }) {
                AdditionalSettings(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    isQuickDownload = false,
                    isPlaylist = true,
                    preference = preferences,
                    selectedType = selectedType,
                    useFormatSelection = useFormatSelection,
                    onPreferenceUpdate = {
                        onPreferencesUpdate(DownloadUtil.DownloadPreferences.createFromPreferences())
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        ActionButtons(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
            canProceed = true,
            selectedType = selectedType,
            useFormatSelection = useFormatSelection,
            onCancel = onDismissRequest,
            onDownload = {
                onDownload(selectedType, useFormatSelection)
                onDismissRequest()
            },
            onFetchInfo = {
                onDownload(selectedType, useFormatSelection)
            },
            onTaskStart = {
                onDownload(selectedType, useFormatSelection)
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun AdditionalSettings(
    modifier: Modifier = Modifier,
    isQuickDownload: Boolean,
    isPlaylist: Boolean = false,
    selectedType: DownloadType?,
    preference: DownloadUtil.DownloadPreferences,
    videoInfo: VideoInfo? = null,
    useFormatSelection: Boolean = false,
    onNavigateToCookieGeneratorPage: (String) -> Unit = {},
    onPreferenceUpdate: () -> Unit,
) {
    val cookiesProfiles by DatabaseUtil.getCookiesFlow().collectAsStateWithLifecycle(emptyList())
    var showCookiesDialog by rememberSaveable { mutableStateOf(false) }

    with(preference) {
        Column(modifier = modifier) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                if (cookiesProfiles.isNotEmpty()) {
                    VideoFilterChip(
                        selected = preference.cookies,
                        onClick = {
                            if (isQuickDownload) {
                                COOKIES.updateBoolean(!cookies)
                                onPreferenceUpdate()
                            } else {
                                showCookiesDialog = true
                            }
                        },
                        label = stringResource(id = R.string.cookies),
                    )
                }

                if (selectedType != DownloadType.Subtitle) {
                    VideoFilterChip(
                        selected = downloadSubtitle,
                        enabled = selectedType != Command,
                        onClick = {
                            SUBTITLE.updateBoolean(!downloadSubtitle)
                            onPreferenceUpdate()
                        },
                        label = stringResource(id = R.string.download_subtitles),
                    )
                }
                VideoFilterChip(
                    selected = createThumbnail,
                    enabled = selectedType != Command,
                    onClick = {
                        THUMBNAIL.updateBoolean(!createThumbnail)
                        onPreferenceUpdate()
                    },
                    label = stringResource(R.string.create_thumbnail),
                )
                if (!isPlaylist && (selectedType == Video || selectedType == Audio)) {
                    val removeMusic = preference.removeMusic
                    VideoFilterChip(
                        selected = removeMusic,
                        enabled = true,
                        onClick = {
                            com.junkfood.seal.util.REMOVE_MUSIC.updateBoolean(!removeMusic)
                            onPreferenceUpdate()
                        },
                        label = "عزل الصوت بالذكاء الاصطناعي (AI Voice)",
                    )
                }
            }

            if (downloadSubtitle && selectedType != Command && !useFormatSelection) {
                Spacer(modifier = Modifier.height(8.dp))
                SubtitleLanguageSelector(
                    preference = preference,
                    videoInfo = videoInfo,
                    selected = false,
                    onLanguageChange = { newLang ->
                        SUBTITLE_LANGUAGE.updateString(newLang)
                        onPreferenceUpdate()
                    }
                )
            }
        }

        if (showCookiesDialog && cookiesProfiles.isNotEmpty()) {
            CookiesQuickSettingsDialog(
                onDismissRequest = { showCookiesDialog = false },
                onConfirm = {},
                cookieProfiles = cookiesProfiles,
                onCookieProfileClicked = { onNavigateToCookieGeneratorPage(it.url) },
                isCookiesEnabled = cookies,
                onCookiesToggled = {
                    COOKIES.updateBoolean(!cookies)
                    onPreferenceUpdate()
                },
            )
        }
    }
}

@Composable
fun ExpandableTitle(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = Dp.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
        Column(
            modifier =
                modifier
                    .clickable(
                        onClick = onClick,
                        onClickLabel = stringResource(R.string.show_more_actions),
                    )
                    .padding(top = 12.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = stringResource(R.string.additional_settings),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(32.dp))
            }
            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceItem(
    modifier: Modifier = Modifier,
    title: String,
    desc: String,
    selected: Boolean,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val corner by
        animateDpAsState(
            if (selected) 28.dp else 16.dp,
            animationSpec = tween(120),
            label = "corner",
        )
    val color by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
            animationSpec = tween(120),
            label = "color",
        )

    Surface(
        selected = selected,
        onClick = onClick,
        color = color,
        shape = RoundedCornerShape(corner),
        modifier = modifier.padding(vertical = 4.dp).run { if (!enabled) alpha(0.32f) else this },
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.invoke()
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 32.dp),
                )
            }
            action?.invoke()
        }
    }
}

@Composable
internal fun Header(modifier: Modifier = Modifier, icon: ImageVector, title: String) {
    Column(modifier = modifier) {
        Icon(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            imageVector = icon,
            contentDescription = null,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier =
                Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp, bottom = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DownloadTypeSelectionGroup(
    modifier: Modifier = Modifier,
    typeEntries: List<DownloadType>,
    selectedType: DownloadType?,
    onSelect: (DownloadType) -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(typeEntries) { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onSelect(type) },
                label = {
                    Text(
                        text = type.label(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = if (selectedType == type) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun Preset(
    modifier: Modifier = Modifier,
    preference: DownloadUtil.DownloadPreferences,
    downloadType: DownloadType?,
    selected: Boolean,
    showEditIcon: Boolean,
    onEdit: () -> Unit,
    onClick: () -> Unit,
) {
    val description =
        when (downloadType) {
            Audio -> PreferenceStrings.getAudioPresetText(preference)
            Video -> PreferenceStrings.getVideoPresetText(preference)
            DownloadType.Subtitle -> PreferenceStrings.getSubtitlePresetText(preference)
            Command -> stringResource(R.string.custom_command)
            Playlist -> stringResource(R.string.preset_format_selection_desc)
            else -> ""
        }

    SingleChoiceItem(
        modifier = modifier,
        title = stringResource(R.string.preset),
        desc = description,
        icon = {
            Icon(
                imageVector = if (selected) Icons.Filled.SettingsSuggest else Icons.Outlined.SettingsSuggest,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        selected = selected,
        action = if (showEditIcon) {
            {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else null,
        onClick = onClick,
    )
}

@Composable
private fun Custom(
    modifier: Modifier = Modifier,
    selected: Boolean,
    enabled: Boolean = true,
    downloadType: DownloadType? = null,
    onClick: () -> Unit,
) {
    val icon = when (downloadType) {
        Audio -> if (selected) Icons.Filled.AudioFile else Icons.Outlined.AudioFile
        DownloadType.Subtitle -> if (selected) Icons.Filled.Subtitles else Icons.Outlined.Subtitles
        else -> if (selected) Icons.Filled.VideoFile else Icons.Outlined.VideoFile
    }

    val desc = when (downloadType) {
        Audio -> "تخصيص تنسيقات وجودة الصوت المتاحة (MP3, M4A, Opus, FLAC, WAV)"
        DownloadType.Subtitle -> "اختيار لغات وتنسيقات الترجمة المتاحة (SRT, VTT, ASS)"
        else -> stringResource(R.string.custom_format_selection_desc)
    }

    SingleChoiceItem(
        modifier = modifier,
        title = stringResource(R.string.custom),
        desc = desc,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        selected = selected,
        enabled = enabled,
        onClick = onClick,
    )
}

private enum class ActionButton {
    FetchInfo,
    Download,
    StartTask,
}

@Composable
private fun ActionButton.Icon() {
    Icon(
        imageVector =
            when (this) {
                FetchInfo -> Icons.AutoMirrored.Filled.ArrowForward
                Download -> Icons.Outlined.FileDownload
                StartTask -> Icons.Filled.DownloadDone
            },
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun ActionButton.Label() {
    Text(
        stringResource(
            when (this) {
                FetchInfo -> R.string.proceed
                Download -> R.string.download
                StartTask -> R.string.start
            }
        ),
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun ActionButtons(
    modifier: Modifier = Modifier,
    canProceed: Boolean,
    selectedType: DownloadType?,
    useFormatSelection: Boolean,
    onCancel: () -> Unit,
    onFetchInfo: () -> Unit,
    onDownload: () -> Unit,
    onTaskStart: () -> Unit,
) {
    val action =
        if (selectedType == Command) {
            StartTask
        } else if (selectedType == Playlist || useFormatSelection) {
            FetchInfo
        } else {
            Download
        }

    val state = rememberLazyListState()
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.End,
        state = state,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            OutlinedButtonWithIcon(
                modifier = Modifier.padding(horizontal = 12.dp),
                onClick = onCancel,
                icon = Icons.Outlined.Cancel,
                text = stringResource(R.string.cancel),
            )
        }
        item {
            Button(
                modifier = Modifier,
                onClick = {
                    when (action) {
                        FetchInfo -> onFetchInfo()
                        Download -> onDownload()
                        StartTask -> onTaskStart()
                    }
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = canProceed,
            ) {
                AnimatedContent(
                    targetState = action,
                    label = "",
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 90))).togetherWith(
                            fadeOut(animationSpec = tween(90))
                        )
                    },
                ) { action ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        action.Icon()
                        action.Label()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleLanguageSelector(
    modifier: Modifier = Modifier,
    preference: DownloadUtil.DownloadPreferences,
    videoInfo: VideoInfo? = null,
    selected: Boolean = false,
    onLanguageChange: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    val currentLang = preference.subtitleLanguage.ifEmpty { stringResource(R.string.all_languages) }

    SingleChoiceItem(
        modifier = modifier,
        title = stringResource(R.string.subtitle_language_selection),
        desc = currentLang,
        icon = { Icon(Icons.Outlined.Subtitles, null) },
        selected = selected,
        onClick = { showDialog = true },
    )

    if (showDialog) {
        val availableSubs = videoInfo?.subtitles ?: emptyMap()
        val availableAutoCaptions = videoInfo?.automaticCaptions ?: emptyMap()
        val hasFetchedSubs = availableSubs.isNotEmpty() || availableAutoCaptions.isNotEmpty()

        var selectedCodes by remember {
            mutableStateOf(
                preference.subtitleLanguage.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            )
        }
        var customText by remember { mutableStateOf(preference.subtitleLanguage) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.subtitle_language_selection)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (hasFetchedSubs) {
                        Text(
                            text = stringResource(R.string.suggested),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            for ((code, formats) in availableSubs) {
                                val name = formats.firstOrNull()?.name ?: code
                                val isSelected = selectedCodes.contains(code)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCodes = if (isSelected) selectedCodes - code else selectedCodes + code
                                        customText = selectedCodes.joinToString(",")
                                    },
                                    label = { Text("$name [$code]") }
                                )
                            }
                        }

                        if (availableAutoCaptions.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.auto_generated_subtitles),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                for ((code, formats) in availableAutoCaptions) {
                                    val name = formats.firstOrNull()?.name ?: "$code (auto)"
                                    val isSelected = selectedCodes.contains(code)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCodes = if (isSelected) selectedCodes - code else selectedCodes + code
                                            customText = selectedCodes.joinToString(",")
                                        },
                                        label = { Text("$name [$code]") }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Text(
                            text = stringResource(R.string.select_language),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            val presets = listOf("ar" to "العربية", "en" to "English", "fr" to "Français", "es" to "Español", "" to "الكل (All)")
                            presets.forEach { (code, label) ->
                                val isSelected = if (code.isEmpty()) selectedCodes.isEmpty() else selectedCodes.contains(code)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (code.isEmpty()) {
                                            selectedCodes = emptySet()
                                            customText = ""
                                        } else {
                                            selectedCodes = if (isSelected) selectedCodes - code else selectedCodes + code
                                            customText = selectedCodes.joinToString(",")
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customText,
                        onValueChange = {
                            customText = it
                            selectedCodes = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet()
                        },
                        label = { Text(stringResource(R.string.select_language)) },
                        placeholder = { Text("ar,en,fr...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalLang = customText.trim()
                    onLanguageChange(finalLang)
                    showDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
