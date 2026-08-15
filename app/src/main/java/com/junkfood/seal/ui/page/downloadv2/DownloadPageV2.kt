package com.junkfood.seal.ui.page.downloadv2

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.junkfood.seal.R
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.Task.DownloadState.Canceled
import com.junkfood.seal.download.Task.DownloadState.Completed
import com.junkfood.seal.download.Task.DownloadState.Error
import com.junkfood.seal.download.Task.DownloadState.FetchingInfo
import com.junkfood.seal.download.Task.DownloadState.Idle
import com.junkfood.seal.download.Task.DownloadState.ReadyWithInfo
import com.junkfood.seal.download.Task.DownloadState.Running
import com.junkfood.seal.ui.common.HapticFeedback.slightHapticFeedback
import com.junkfood.seal.ui.common.LocalDarkTheme
import com.junkfood.seal.ui.common.LocalFixedColorRoles
import com.junkfood.seal.ui.common.LocalWindowWidthState
import com.junkfood.seal.ui.component.SealModalBottomSheet
import com.junkfood.seal.ui.component.SelectionGroupDefaults
import com.junkfood.seal.ui.component.SelectionGroupItem
import com.junkfood.seal.ui.component.SelectionGroupRow
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.junkfood.seal.ui.page.downloadv2.configure.Config
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialog
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.junkfood.seal.ui.page.downloadv2.configure.FormatPage
import com.junkfood.seal.ui.page.downloadv2.configure.PlaylistSelectionPage
import com.junkfood.seal.ui.page.downloadv2.configure.PlaylistSyncDialog
import com.junkfood.seal.ui.page.downloadv2.configure.PreferencesMock
import com.junkfood.seal.ui.page.downloadv2.menu.DownloadPageMenuSheet
import com.junkfood.seal.ui.page.downloadv2.menu.SortOption
import com.junkfood.seal.ui.page.downloadv2.menu.ViewOptionsState
import com.junkfood.seal.ui.svg.DynamicColorImageVectors
import com.junkfood.seal.ui.svg.drawablevectors.download
import com.junkfood.seal.ui.theme.SealTheme
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.getErrorReport
import com.junkfood.seal.util.makeToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TAG = "DownloadPageV2"

/*
 * FIX: "Unresolved reference 'selected'" / "Unresolved reference 'retry'"
 *
 * SelectionHeader() referenced R.string.selected and R.string.retry, but those
 * string resource entries don't exist in res/values/strings.xml (or weren't
 * added yet), so aapt never generated those R.string fields for the compiler
 * to resolve. Rather than leaving the build broken, SelectionHeader below uses
 * plain literal strings for these two labels.
 *
 * To restore proper localization, add these two entries to
 * res/values/strings.xml (and any values-xx/strings.xml translations you keep)
 * and then switch the two literals back to stringResource(R.string.selected)
 * and stringResource(R.string.retry):
 *
 *   <string name="selected">selected</string>
 *   <string name="retry">Retry</string>
 */

enum class Filter {
    All,
    Downloading,
    Finished,
    Video,
    Audio,
    Subtitle,
    Canceled;

    @Composable
    @ReadOnlyComposable
    fun label(): String =
        when (this) {
            All -> stringResource(R.string.all)
            Downloading -> stringResource(R.string.status_downloading)
            Finished -> stringResource(R.string.status_completed)
            Video -> stringResource(R.string.video)
            Audio -> stringResource(R.string.audio)
            Subtitle -> stringResource(R.string.subtitle)
            Canceled -> stringResource(R.string.status_canceled)
        }

    fun predict(entry: Pair<Task, Task.State>): Boolean {
        if (this == All) return true
        val (task, state) = entry
        val downloadState = state.downloadState
        return when (this) {
            Downloading -> {
                downloadState is FetchingInfo || downloadState is Idle || downloadState is ReadyWithInfo || downloadState is Running
            }
            Finished -> {
                downloadState is Completed
            }
            Video -> {
                !task.preferences.extractAudio && !(task.preferences.skipDownload && task.preferences.downloadSubtitle)
            }
            Audio -> {
                task.preferences.extractAudio && !task.preferences.skipDownload
            }
            Subtitle -> {
                task.preferences.skipDownload && task.preferences.downloadSubtitle || state.viewState.isSubOnly
            }
            Canceled -> {
                downloadState is Error || downloadState is Task.DownloadState.Canceled
            }
        }
    }
}

sealed interface UiAction {
    data class OpenFile(val filePath: String?) : UiAction

    data class ShareFile(val filePath: String?) : UiAction

    data class OpenThumbnailURL(val url: String) : UiAction

    data object CopyVideoURL : UiAction

    data class OpenVideoURL(val url: String) : UiAction

    data object Cancel : UiAction

    data object Pause : UiAction

    data object Delete : UiAction

    data object Resume : UiAction

    data class CopyErrorReport(val throwable: Throwable) : UiAction

    data class AdjustSubtitle(val filePath: String) : UiAction

    data object Prioritize : UiAction

    data class PreviewMedia(val filePath: String) : UiAction

    data class TrimMedia(val filePath: String) : UiAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPageV2(
    modifier: Modifier = Modifier,
    onMenuOpen: (() -> Unit) = {},
    dialogViewModel: DownloadDialogViewModel,
    downloader: DownloaderV2 = koinInject(),
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    DownloadPageImplV2(
        modifier = modifier,
        taskDownloadStateMap = downloader.getTaskStateMap(),
        downloadCallback = {
            view.slightHapticFeedback()
            dialogViewModel.postAction(Action.ShowSheet())
        },
        onMenuOpen = onMenuOpen,
    ) { task, action ->
        view.slightHapticFeedback()
        when (action) {
            UiAction.Cancel -> downloader.cancel(task)
            UiAction.Pause -> downloader.pause(task)
            UiAction.Delete -> downloader.remove(task)
            UiAction.Resume -> downloader.restart(task)
            UiAction.Prioritize -> {
                downloader.prioritize(task)
                context.makeToast("تم رفع أولوية التنزيل إلى القمة")
            }
            is UiAction.CopyErrorReport -> {
                clipboardManager.setText(
                    AnnotatedString(getErrorReport(action.throwable, task.url))
                )
                context.makeToast(R.string.error_copied)
            }
            UiAction.CopyVideoURL -> {
                clipboardManager.setText(AnnotatedString(task.url))
                context.makeToast(R.string.link_copied)
            }
            is UiAction.OpenFile -> {
                action.filePath?.let {
                    FileUtil.openFile(path = it) { context.makeToast(R.string.file_unavailable) }
                }
            }
            is UiAction.OpenThumbnailURL -> {
                uriHandler.openUri(action.url)
            }
            is UiAction.OpenVideoURL -> {
                uriHandler.openUri(action.url)
            }
            is UiAction.ShareFile -> {
                val shareTitle = context.getString(R.string.share)
                FileUtil.createIntentForSharingFile(action.filePath)?.let {
                    context.startActivity(Intent.createChooser(it, shareTitle))
                }
            }
            else -> {}
        }
    }

}

@Composable
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        top = calculateTopPadding() + other.calculateTopPadding(),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
        start =
            calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
        end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
    )
}

private const val HeaderSpacingDp = 28

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPageImplV2(
    modifier: Modifier = Modifier,
    taskDownloadStateMap: SnapshotStateMap<Task, Task.State>,
    downloadCallback: () -> Unit = {},
    onMenuOpen: (() -> Unit) = {},
    onActionPost: (Task, UiAction) -> Unit,
) {
    var activeFilter by remember { mutableStateOf(Filter.All) }
    var sortOption by remember { mutableStateOf(SortOption.DateNewest) }
    var viewOptions by remember { mutableStateOf(ViewOptionsState()) }
    var isMenuSheetOpen by remember { mutableStateOf(false) }
    var showPlaylistSyncDialog by remember { mutableStateOf(false) }
    var selectedTasks by remember { mutableStateOf<Set<Task>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var adjustingSubtitleFile by remember { mutableStateOf<java.io.File?>(null) }
    var previewMediaFile by remember { mutableStateOf<java.io.File?>(null) }
    var trimMediaFile by remember { mutableStateOf<java.io.File?>(null) }
    val isSelectionMode = selectedTasks.isNotEmpty()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val filteredMap by
        remember(activeFilter, sortOption, taskDownloadStateMap, searchQuery) {
            derivedStateOf {
                val filtered = taskDownloadStateMap.filter {
                    val matchesFilter = activeFilter.predict(it.toPair())
                    val matchesSearch = searchQuery.isBlank() ||
                            it.second.viewState.title.contains(searchQuery, ignoreCase = true) ||
                            it.second.viewState.uploader.contains(searchQuery, ignoreCase = true) ||
                            it.first.url.contains(searchQuery, ignoreCase = true)
                    matchesFilter && matchesSearch
                }
                filtered.toList().sortedWith(Comparator { a, b ->
                    when (sortOption) {
                        SortOption.DateNewest -> b.first.timeCreated.compareTo(a.first.timeCreated)
                        SortOption.DateOldest -> a.first.timeCreated.compareTo(b.first.timeCreated)
                        SortOption.NameAZ -> a.second.viewState.title.compareTo(b.second.viewState.title, ignoreCase = true)
                        SortOption.NameZA -> b.second.viewState.title.compareTo(a.second.viewState.title, ignoreCase = true)
                        SortOption.SizeLargest -> b.second.viewState.fileSizeApprox.compareTo(a.second.viewState.fileSizeApprox)
                        SortOption.SizeSmallest -> a.second.viewState.fileSizeApprox.compareTo(b.second.viewState.fileSizeApprox)
                        SortOption.Status -> {
                            val stateA = a.second.downloadState::class.java.simpleName
                            val stateB = b.second.downloadState::class.java.simpleName
                            stateA.compareTo(stateB)
                        }
                    }
                })
            }
        }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    val view = LocalView.current

    fun showActionSheet(task: Task) {
        view.slightHapticFeedback()
        scope.launch {
            selectedTask = task
            delay(50)
            sheetState.show()
        }
    }

    fun toggleSelection(task: Task) {
        selectedTasks = if (selectedTasks.contains(task)) {
            selectedTasks - task
        } else {
            selectedTasks + task
        }
    }

    LaunchedEffect(selectedTask, taskDownloadStateMap.size) {
        if (!taskDownloadStateMap.contains(selectedTask)) {
            selectedTask = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        containerColor = Color.Transparent,
        floatingActionButtonPosition = if (isSelectionMode) androidx.compose.material3.FabPosition.Center else androidx.compose.material3.FabPosition.End,
        floatingActionButton = {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    SelectionBottomBar(
                        selectedCount = selectedTasks.size,
                        onPauseSelected = {
                            selectedTasks.forEach { onActionPost(it, UiAction.Pause) }
                            selectedTasks = emptySet()
                        },
                        onResumeSelected = {
                            selectedTasks.filter { taskDownloadStateMap[it]?.downloadState is Task.DownloadState.Restartable }
                                .forEach { onActionPost(it, UiAction.Resume) }
                            selectedTasks = emptySet()
                        },
                        onCancelSelected = {
                            selectedTasks.forEach { onActionPost(it, UiAction.Cancel) }
                            selectedTasks = emptySet()
                        },
                        onDeleteSelected = {
                            selectedTasks.forEach { onActionPost(it, UiAction.Delete) }
                            selectedTasks = emptySet()
                        },
                        onSelectAll = {
                            selectedTasks = if (selectedTasks.size == filteredMap.size) emptySet() else filteredMap.map { it.first }.toSet()
                        },
                        modifier = Modifier
                    )
                }
            } else {
                FABs(
                    modifier = Modifier,
                    downloadCallback = downloadCallback,
                    syncCallback = { showPlaylistSyncDialog = true },
                )
            }
        },
    ) { windowInsetsPadding ->
        val lazyListState = rememberLazyGridState()
        val windowWidthSizeClass = LocalWindowWidthState.current
        val spacerHeight =
            with(LocalDensity.current) {
                if (windowWidthSizeClass != WindowWidthSizeClass.Compact) 0f
                else HeaderSpacingDp.dp.toPx()
            }
        var headerOffset by remember { mutableFloatStateOf(spacerHeight) }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .then(
                        if (windowWidthSizeClass != WindowWidthSizeClass.Compact) Modifier
                        else
                            Modifier.nestedScroll(
                                connection =
                                    TopBarNestedScrollConnection(
                                        maxOffset = spacerHeight,
                                        flingAnimationSpec = rememberSplineBasedDecay(),
                                        offset = { headerOffset },
                                        onOffsetUpdate = { headerOffset = it },
                                    )
                            )
                    )
        ) {
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(with(LocalDensity.current) { headerOffset.toDp() }))
                    if (isSelectionMode) {
                        SelectionHeader(
                            selectedCount = selectedTasks.size,
                            onClearSelection = { selectedTasks = emptySet() },
                            onSelectAll = {
                                selectedTasks = if (selectedTasks.size == filteredMap.size) emptySet() else filteredMap.map { it.first }.toSet()
                            },
                            onDeleteSelected = {
                                selectedTasks.forEach { onActionPost(it, UiAction.Delete) }
                                selectedTasks = emptySet()
                            },
                            onResumeSelected = {
                                selectedTasks.filter { taskDownloadStateMap[it]?.downloadState is Task.DownloadState.Restartable }
                                    .forEach { onActionPost(it, UiAction.Resume) }
                                selectedTasks = emptySet()
                            },
                            onCancelSelected = {
                                selectedTasks.forEach { onActionPost(it, UiAction.Cancel) }
                                selectedTasks = emptySet()
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    } else {
                        Header(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            isSearching = isSearching,
                            onToggleSearch = { isSearching = it },
                            onMenuOpen = onMenuOpen
                        )
                        SelectionGroupRow(
                            modifier =
                                Modifier.horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp)
                        ) {
                            Filter.entries.forEach { filter ->
                                SelectionGroupItem(
                                    colors =
                                        SelectionGroupDefaults.colors(
                                            activeContainerColor =
                                                LocalFixedColorRoles.current.tertiaryFixed,
                                            activeContentColor =
                                                LocalFixedColorRoles.current.onTertiaryFixed,
                                        ),
                                    selected = activeFilter == filter,
                                    onClick = {
                                        if (activeFilter == filter) {
                                            scope.launch { lazyListState.animateScrollToItem(0) }
                                            scope.launch {
                                                val initialValue = headerOffset
                                                AnimationState(initialValue = initialValue).animateTo(
                                                    spacerHeight
                                                ) {
                                                    headerOffset = value
                                                }
                                            }
                                        } else {
                                            activeFilter = filter
                                        }
                                    },
                                ) {
                                    Text(filter.label())
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (headerOffset <= 0.1f && spacerHeight > 0f) {
                        HorizontalDivider(thickness = Dp.Hairline)
                    }
                }

                LazyVerticalGrid(
                    modifier = Modifier,
                    state = lazyListState,
                    columns = GridCells.Adaptive(240.dp),
                    contentPadding =
                        windowInsetsPadding +
                            PaddingValues(start = 20.dp, end = 20.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (filteredMap.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val videoCount =
                                filteredMap.count {
                                    !it.second.viewState.videoFormats.isNullOrEmpty() &&
                                        !it.second.viewState.title.startsWith("[Subtitle]")
                                }
                            SubHeader(
                                modifier = Modifier,
                                videoCount = videoCount,
                                audioCount = filteredMap.size - videoCount,
                                isGridView = viewOptions.isGridView,
                                onToggleView = { viewOptions = viewOptions.copy(isGridView = !viewOptions.isGridView) },
                                onShowMenu = { isMenuSheetOpen = true },
                            )
                        }
                    }

                    if (viewOptions.isGridView) {
                        itemsIndexed(
                            items = filteredMap,
                            key = { _, (task, _) -> task.id },
                        ) { _, item ->
                            val task = item.first
                            val state = item.second
                            with(state.viewState) {
                                VideoCardV2(
                                    modifier = Modifier.padding(bottom = 20.dp),
                                    viewState = this,
                                    isSelected = selectedTasks.contains(task),
                                    showSize = viewOptions.showSize,
                                    showDuration = viewOptions.showDuration,
                                    showSource = viewOptions.showSource,
                                    actionButton = {
                                        ActionButton(
                                            modifier = Modifier,
                                            downloadState = state.downloadState,
                                        ) {
                                            onActionPost(task, it)

                                        }
                                    },
                                    stateIndicator = {
                                        CardStateIndicator(
                                            modifier = Modifier,
                                            downloadState = state.downloadState,
                                        )
                                    },
                                    onLongClick = {
                                        view.slightHapticFeedback()
                                        toggleSelection(task)
                                    },
                                    onButtonClick = {
                                        if (isSelectionMode) toggleSelection(task) else showActionSheet(task)
                                    },
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = filteredMap,
                            key = { _, (task, _) -> task.id },
                            span = { _, _ -> GridItemSpan(maxLineSpan) },
                        ) { _, item ->
                            val task = item.first
                            val state = item.second
                            VideoListItem(
                                modifier = Modifier.padding(bottom = 16.dp),
                                viewState = state.viewState,
                                isSelected = selectedTasks.contains(task),
                                showSize = viewOptions.showSize,
                                showDuration = viewOptions.showDuration,
                                showSource = viewOptions.showSource,
                                stateIndicator = {
                                    ListItemStateText(
                                        modifier = Modifier.padding(top = 3.dp),
                                        downloadState = state.downloadState,
                                    )
                                },
                                onLongClick = {
                                    view.slightHapticFeedback()
                                    toggleSelection(task)
                                },
                                onButtonClick = {
                                    if (isSelectionMode) toggleSelection(task) else showActionSheet(task)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (filteredMap.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                DownloadQueuePlaceholder(
                    modifier =
                        Modifier.fillMaxHeight(0.4f).widthIn(max = 360.dp).align(Alignment.Center)
                )
            }
        }
    }

    if (isMenuSheetOpen) {
        SealModalBottomSheet(
            sheetState = menuSheetState,
            contentPadding = PaddingValues(),
            onDismissRequest = {
                scope.launch { menuSheetState.hide() }.invokeOnCompletion { isMenuSheetOpen = false }
            }
        ) {
            val closeMenu = { scope.launch { menuSheetState.hide() }.invokeOnCompletion { isMenuSheetOpen = false } }
            DownloadPageMenuSheet(
                activeFilter = activeFilter,
                sortOption = sortOption,
                onSortOptionChange = { sortOption = it },
                viewOptions = viewOptions,
                onViewOptionsChange = { viewOptions = it },
                onSelectAll = {
                    selectedTasks = filteredMap.map { it.first }.toSet()
                    closeMenu()
                },
                onDeleteSelected = {
                    selectedTasks.forEach { onActionPost(it, UiAction.Delete) }
                    selectedTasks = emptySet()
                    closeMenu()
                },
                onDeleteCompleted = {
                    filteredMap.filter { it.second.downloadState is Completed }.forEach { onActionPost(it.first, UiAction.Delete) }
                    closeMenu()
                },
                onDeleteFailed = {
                    filteredMap.filter { it.second.downloadState is Error || it.second.downloadState is Canceled }.forEach { onActionPost(it.first, UiAction.Delete) }
                    closeMenu()
                },
                onClearHistory = {
                    filteredMap.forEach { onActionPost(it.first, UiAction.Delete) }
                    closeMenu()
                },
                onPauseAll = {
                    filteredMap.forEach { onActionPost(it.first, UiAction.Pause) }
                    closeMenu()
                },
                onResumeAll = {
                    filteredMap.filter { it.second.downloadState is Task.DownloadState.Restartable }.forEach { onActionPost(it.first, UiAction.Resume) }
                    closeMenu()
                },
                onRetryFailed = {
                    filteredMap.filter { it.second.downloadState is Task.DownloadState.Restartable }.forEach { onActionPost(it.first, UiAction.Resume) }
                    closeMenu()
                },
                onCancelSelected = {
                    selectedTasks.forEach { onActionPost(it, UiAction.Cancel) }
                    selectedTasks = emptySet()
                    closeMenu()
                },
                onRetryAll = {
                    filteredMap.filter { it.second.downloadState is Task.DownloadState.Restartable }.forEach { onActionPost(it.first, UiAction.Resume) }
                    closeMenu()
                },
                onDeleteAll = {
                    filteredMap.forEach { onActionPost(it.first, UiAction.Delete) }
                    closeMenu()
                },
                onRedownloadAll = {
                    filteredMap.filter { it.second.downloadState is Task.DownloadState.Restartable }.forEach { onActionPost(it.first, UiAction.Resume) }
                    closeMenu()
                },
                onDeleteHistory = {
                    filteredMap.forEach { onActionPost(it.first, UiAction.Delete) }
                    closeMenu()
                },
                onDeleteFiles = {
                    // Requires additional access, this is simplified for now
                    filteredMap.forEach { onActionPost(it.first, UiAction.Delete) }
                    closeMenu()
                }
            )
        }
    }

    if (showPlaylistSyncDialog) {
        PlaylistSyncDialog(
            initialUrl = "",
            preferences = DownloadUtil.DownloadPreferences.createFromPreferences(),
            onDismissRequest = { showPlaylistSyncDialog = false }
        )
    }

    if (selectedTask != null) {
        val task = selectedTask!!
        val (downloadState, _, viewState) = taskDownloadStateMap[task] ?: return
        SealModalBottomSheet(
            sheetState = sheetState,
            contentPadding = PaddingValues(),
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { selectedTask = null }
            },
        ) {
            SheetContent(
                task = task,
                downloadState = downloadState,
                viewState = viewState,
                onDismissRequest = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedTask = null }
                },
                onActionPost = { t, a ->
                    when (a) {
                        is UiAction.AdjustSubtitle -> adjustingSubtitleFile = java.io.File(a.filePath)
                        is UiAction.PreviewMedia -> previewMediaFile = java.io.File(a.filePath)
                        is UiAction.TrimMedia -> trimMediaFile = java.io.File(a.filePath)
                        else -> onActionPost(t, a)
                    }
                },
            )
        }
    }

    if (adjustingSubtitleFile != null) {
        com.junkfood.seal.ui.component.SubtitleAdjustDialog(
            file = adjustingSubtitleFile!!,
            onDismissRequest = { adjustingSubtitleFile = null }
        )
    }

    if (previewMediaFile != null) {
        com.junkfood.seal.ui.component.MediaPreviewDialog(
            file = previewMediaFile!!,
            onDismissRequest = { previewMediaFile = null }
        )
    }

    if (trimMediaFile != null) {
        com.junkfood.seal.ui.component.MediaTrimmerDialog(
            file = trimMediaFile!!,
            onDismissRequest = { trimMediaFile = null }
        )
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onResumeSelected: () -> Unit,
    onCancelSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClearSelection) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cancel),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "تم تحديد $selectedCount عناصر",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSelectAll) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.List,
                contentDescription = stringResource(R.string.select_all),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onResumeSelected) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Refresh,
                contentDescription = "إعادة التنزيل",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onDeleteSelected) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    onPauseSelected: () -> Unit,
    onResumeSelected: () -> Unit,
    onCancelSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSelectAll)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("تحديد الكل", style = MaterialTheme.typography.labelSmall)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPauseSelected)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Pause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("إيقاف", style = MaterialTheme.typography.labelSmall)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onResumeSelected)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("استئناف", style = MaterialTheme.typography.labelSmall)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCancelSelected)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("إلغاء", style = MaterialTheme.typography.labelSmall)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDeleteSelected)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("حذف", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun Header(
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    isSearching: Boolean = false,
    onToggleSearch: (Boolean) -> Unit = {},
    onMenuOpen: () -> Unit = {}
) {
    val windowWidthSizeClass = LocalWindowWidthState.current
    when (windowWidthSizeClass) {
        WindowWidthSizeClass.Expanded -> {
            HeaderExpanded(
                modifier = modifier,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                isSearching = isSearching,
                onToggleSearch = onToggleSearch
            )
        }
        else -> {
            HeaderCompact(
                modifier = modifier,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                isSearching = isSearching,
                onToggleSearch = onToggleSearch,
                onMenuOpen = onMenuOpen
            )
        }
    }
}

@Composable
private fun HeaderCompact(
    modifier: Modifier = Modifier,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onMenuOpen: () -> Unit
) {
    Row(modifier = modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isSearching) {
            IconButton(onClick = { onToggleSearch(false); onSearchQueryChange("") }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "إغلاق البحث",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            androidx.compose.material3.TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("بحث بالعنوان أو القناة...", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "مسح",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            IconButton(onClick = onMenuOpen) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = stringResource(R.string.show_navigation_drawer),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(R.string.download_queue),
                color = MaterialTheme.colorScheme.onSurface,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onToggleSearch(true) }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Search,
                    contentDescription = "بحث",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeaderExpanded(
    modifier: Modifier = Modifier,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    onToggleSearch: (Boolean) -> Unit
) {
    Row(modifier = modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            stringResource(R.string.download_queue),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        if (isSearching) {
            androidx.compose.material3.TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("بحث...") },
                singleLine = true,
                modifier = Modifier.width(280.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            IconButton(onClick = { onToggleSearch(false); onSearchQueryChange("") }) {
                Icon(Icons.Outlined.Close, contentDescription = "Close search")
            }
        } else {
            IconButton(onClick = { onToggleSearch(true) }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Search,
                    contentDescription = "بحث",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
fun FABs(
    modifier: Modifier = Modifier,
    downloadCallback: () -> Unit = {},
    syncCallback: () -> Unit = {},
) {
    val expanded = LocalWindowWidthState.current != WindowWidthSizeClass.Compact
    Column(modifier = modifier.padding(6.dp), horizontalAlignment = Alignment.End) {
        SmallFloatingActionButton(
            onClick = syncCallback,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = "مُزامن قوائم التشغيل الذكي"
            )
        }
        FloatingActionButton(
            onClick = downloadCallback,
            content = {
                if (expanded) {
                    Row(
                        modifier = Modifier.widthIn(min = 80.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.download))
                    }
                } else {
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = stringResource(R.string.download),
                    )
                }
            },
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
@Preview
private fun DownloadQueuePlaceholder(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        ConstraintLayout {
            val (image, text) = createRefs()
            val showImage =
                with(LocalDensity.current) {
                    this@BoxWithConstraints.constraints.maxHeight >= 240.dp.toPx()
                }
            if (showImage) {
                Image(
                    painter = rememberVectorPainter(image = DynamicColorImageVectors.download()),
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxHeight(0.5f).widthIn(max = 240.dp).constrainAs(image) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        },
                )
            } else {
                Spacer(Modifier.height(72.dp).constrainAs(image) { top.linkTo(parent.top) })
            }
            Column(
                modifier = Modifier.constrainAs(text) { top.linkTo(image.bottom, margin = 36.dp) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.you_ll_find_your_downloads_here),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.download_hint),
                    modifier = Modifier.padding(top = 4.dp).padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SubHeader(
    modifier: Modifier = Modifier,
    containerColor: Color =
        MaterialTheme.colorScheme.run {
            if (LocalDarkTheme.current.isDarkTheme()) surfaceContainer else surfaceContainerLowest
        },
    videoCount: Int = 0,
    audioCount: Int = 0,
    isGridView: Boolean = true,
    onToggleView: () -> Unit,
    onShowMenu: () -> Unit,
) {
    val text = buildString {
        if (videoCount > 0) {
            append(pluralStringResource(R.plurals.video_count, videoCount).format(videoCount))
            if (audioCount > 0) {
                append(", ")
            }
        }
        if (audioCount > 0) {
            append(pluralStringResource(R.plurals.audio_count, audioCount).format(audioCount))
        }
    }

    Row(
        modifier = modifier.padding(top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        FilledIconButton(
            onClick = onToggleView,
            modifier = Modifier.clearAndSetSemantics {}.size(32.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor),
        ) {
            Icon(
                imageVector =
                    if (isGridView) Icons.AutoMirrored.Outlined.List else Icons.Outlined.GridView,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.width(4.dp))

        FilledIconButton(
            onClick = onShowMenu,
            modifier = Modifier.clearAndSetSemantics {}.size(32.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

internal class DownloadPageV2Test {
    private val mockDownloader =
        object : DownloaderV2 {
            private val map = mutableStateMapOf<Task, Task.State>()

            init {
                val viewState =
                    Task.ViewState(title = "Sample title", uploader = "dummy video uploader")
                val list =
                    listOf(
                        Task.State(Idle, null, viewState),
                        Task.State(Canceled(Task.RestartableAction.Download), null, viewState),
                        Task.State(Completed(null), null, viewState),
                    )
                map.run {
                    repeat(9) {
                        put(Task(url = "$it", preferences = PreferencesMock), list[it % 3])
                    }
                }
                val scope = CoroutineScope(SupervisorJob())

                scope.launch(Dispatchers.Default) {
                    while (true) {
                        delay(1000)
                        val newEntries =
                            map.toMap().map { (task, state) ->
                                val newDownloadState =
                                    when (state.downloadState) {
                                        is Canceled -> Idle
                                        is Completed -> Idle
                                        is Error -> Idle
                                        is FetchingInfo -> ReadyWithInfo
                                        Idle -> FetchingInfo(Job(), task.id)
                                        ReadyWithInfo -> Running(Job(), task.id)
                                        is Running -> {
                                            val preState: Running = state.downloadState
                                            if (preState.progress >= 1f) Completed(null)
                                            else preState.copy(progress = preState.progress + 0.1f)
                                        }
                                    }
                                task to state.copy(downloadState = newDownloadState)
                            }
                        Snapshot.withMutableSnapshot {
                            newEntries.forEach { (task, state) ->
                                delay(100)
                                map[task] = state
                            }
                        }
                    }
                }
            }

            override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
                return map
            }

            override fun cancel(task: Task): Boolean {
                return false
            }

            override fun pause(task: Task): Boolean {
                return false
            }

            override fun restart(task: Task) {}

            override fun enqueue(task: Task) {}

            override fun enqueue(task: Task, state: Task.State) {}

            override fun remove(task: Task): Boolean {
                return true
            }
        }

    @Composable
    @Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
    @Preview(name = "Tablet", device = "spec:width=600dp,height=800dp,dpi=240")
    private fun Preview() {

        val downloader: DownloaderV2 = mockDownloader
        SealTheme {
            Column() {
                DownloadPageImplV2(
                    taskDownloadStateMap = downloader.getTaskStateMap(),
                    onActionPost = { task, state -> },
                    onMenuOpen = {},
                )
            }
        }
    }
}
