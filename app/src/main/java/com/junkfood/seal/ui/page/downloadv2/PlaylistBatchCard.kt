package com.junkfood.seal.ui.page.downloadv2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junkfood.seal.R
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.Task.DownloadState.Canceled
import com.junkfood.seal.download.Task.DownloadState.Completed
import com.junkfood.seal.download.Task.DownloadState.Error
import com.junkfood.seal.download.Task.DownloadState.FetchingInfo
import com.junkfood.seal.download.Task.DownloadState.Idle
import com.junkfood.seal.download.Task.DownloadState.ReadyWithInfo
import com.junkfood.seal.download.Task.DownloadState.Running
import com.junkfood.seal.download.Task.TypeInfo
import com.junkfood.seal.download.engine.builder.OutputTemplateBuilder
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.PreferenceUtil
import java.io.File

enum class PlaylistBatchType {
    VIDEO,
    AUDIO,
    SUBTITLE,
}

@Composable
fun PlaylistBatchCard(
    modifier: Modifier = Modifier,
    playlistTitle: String,
    batchType: PlaylistBatchType = PlaylistBatchType.VIDEO,
    tasks: List<Pair<Task, Task.State>>,
    onActionPost: (Task, UiAction) -> Unit,
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val cleanPlaylistTitle = playlistTitle
        .removePrefix("[Subtitles] ")
        .removePrefix("[Subtitle] ")
        .replace(Regex("^#\\d+\\s*"), "")
        .trim()
        .ifBlank { stringResource(R.string.playlist) }

    val totalCount = tasks.size
    val completedCount = tasks.count { it.second.downloadState is Completed }
    val errorCount = tasks.count { it.second.downloadState is Error }
    val canceledCount = tasks.count { it.second.downloadState is Canceled }
    val activeTaskPair = tasks.firstOrNull { 
        it.second.downloadState is Running || it.second.downloadState is FetchingInfo 
    }
    val isAllCompleted = completedCount == totalCount && totalCount > 0
    val isRunning = activeTaskPair != null

    val targetProgress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "batchProgress"
    )

    val (badgeIcon: ImageVector, badgeLabel: String, containerColor: Color, contentColor: Color) = when (batchType) {
        PlaylistBatchType.SUBTITLE -> Quadruple(
            Icons.Outlined.Subtitles,
            stringResource(R.string.subtitle_filter),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        PlaylistBatchType.AUDIO -> Quadruple(
            Icons.Outlined.MusicNote,
            stringResource(R.string.extract_audio),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        PlaylistBatchType.VIDEO -> Quadruple(
            Icons.Outlined.VideoLibrary,
            stringResource(R.string.video),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header Row ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = containerColor,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.playlist),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " • $badgeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = cleanPlaylistTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = CircleShape,
                    color = when {
                        isAllCompleted -> MaterialTheme.colorScheme.primaryContainer
                        isRunning -> MaterialTheme.colorScheme.secondaryContainer
                        errorCount > 0 -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = when {
                                isAllCompleted -> "مكتمل ✓"
                                isRunning -> "جاري التنزيل..."
                                canceledCount > 0 -> "متوقف"
                                errorCount > 0 -> "فشل $errorCount"
                                else -> "في الانتظار"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                isAllCompleted -> MaterialTheme.colorScheme.onPrimaryContainer
                                isRunning -> MaterialTheme.colorScheme.onSecondaryContainer
                                errorCount > 0 -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Progress Bar & Counters ─────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تم تنزيل $completedCount من $totalCount مقطع",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(targetProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            // ── Active Operation Sub-card ───────────────────────────────────────────
            if (activeTaskPair != null) {
                val activeTask = activeTaskPair.first
                val activeState = activeTaskPair.second
                val playlistIndex = (activeTask.type as? TypeInfo.Playlist)?.index ?: 0
                val rawActiveTitle = activeState.viewState.title
                    .removePrefix("[Subtitles] ")
                    .removePrefix("[Subtitle] ")
                    .replace(Regex("^#\\d+\\s*"), "")
                    .trim()
                val activeTitle = rawActiveTitle.ifBlank { "مقطع $playlistIndex" }
                val progressText = (activeState.downloadState as? Running)?.progressText.orEmpty()

                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "العملية الجارية: $activeTitle",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (progressText.isNotBlank()) {
                                Text(
                                    text = progressText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Controls Row ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Expand / Collapse details button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isExpanded) "إخفاء التفاصيل" else "عرض قائمة المقاطع ($totalCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Batch Actions
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Open folder button
                    FilledTonalIconButton(
                        onClick = {
                            val completedFilePath = tasks.firstNotNullOfOrNull { (_, state) ->
                                (state.downloadState as? Completed)?.filePath?.takeIf { File(it).exists() }
                            }
                            val isAudio = batchType == PlaylistBatchType.AUDIO
                            val isSub = batchType == PlaylistBatchType.SUBTITLE
                            val dirToOpen = if (completedFilePath != null) {
                                File(completedFilePath).parentFile ?: (if (isAudio) File(com.junkfood.seal.App.audioDownloadDir) else File(com.junkfood.seal.App.videoDownloadDir))
                            } else {
                                val firstTask = tasks.firstOrNull()?.first
                                val isAudioDownload = isAudio
                                val basePath = if (firstTask != null) {
                                    OutputTemplateBuilder.resolveBaseDirectory(firstTask.preferences, isAudioDownload)
                                } else {
                                    if (isAudio) com.junkfood.seal.App.audioDownloadDir else com.junkfood.seal.App.videoDownloadDir
                                }
                                val cleanFolder = FileUtil.cleanFileName(cleanPlaylistTitle).ifBlank { "Playlist" }
                                val folderName = if (isSub) "[Subtitles] $cleanFolder" else cleanFolder
                                val target = File(basePath, folderName)
                                val altTarget = if (isSub) File(basePath, cleanFolder) else File(basePath, "[Subtitles] $cleanFolder")
                                when {
                                    target.exists() -> target
                                    altTarget.exists() -> altTarget
                                    else -> target
                                }
                            }
                            if (!dirToOpen.exists()) dirToOpen.mkdirs()
                            FileUtil.openDirectory(dirToOpen.absolutePath)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = "فتح مجلد القائمة",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isRunning) {
                        FilledTonalIconButton(
                            onClick = {
                                tasks.forEach { (task, state) ->
                                    if (state.downloadState is Running || state.downloadState is FetchingInfo || state.downloadState is Idle || state.downloadState is ReadyWithInfo) {
                                        onActionPost(task, UiAction.Pause)
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Pause,
                                contentDescription = "إيقاف مؤقت للكل",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (!isAllCompleted) {
                        FilledTonalIconButton(
                            onClick = {
                                tasks.forEach { (task, state) ->
                                    if (state.downloadState is Task.DownloadState.Restartable) {
                                        onActionPost(task, UiAction.Resume)
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "استئناف الكل",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (!isAllCompleted) {
                        FilledTonalIconButton(
                            onClick = {
                                tasks.forEach { (task, state) ->
                                    if (state.downloadState !is Completed) {
                                        onActionPost(task, UiAction.Cancel)
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cancel,
                                contentDescription = "إلغاء التنزيل",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── Expanded Individual Items List ──────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tasks.forEachIndexed { index, (task, state) ->
                        val itemIndex = (task.type as? TypeInfo.Playlist)?.index ?: (index + 1)
                        val itemTitle = state.viewState.title
                            .removePrefix("[Subtitles] ")
                            .removePrefix("[Subtitle] ")
                            .replace(Regex("^#\\d+\\s*"), "")
                            .trim()
                            .ifBlank { "مقطع $itemIndex" }
                        val isItemDone = state.downloadState is Completed
                        val isItemRunning = state.downloadState is Running || state.downloadState is FetchingInfo
                        val isItemError = state.downloadState is Error
                        val completedPath = (state.downloadState as? Completed)?.filePath

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isItemRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                isItemDone -> MaterialTheme.colorScheme.surfaceContainerLowest
                                isItemError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isItemDone && !completedPath.isNullOrBlank()) {
                                        Modifier.clickable {
                                            onActionPost(task, UiAction.OpenFile(completedPath))
                                        }
                                    } else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "%02d".format(itemIndex),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.width(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = itemTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                when {
                                    isItemDone -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "تم التنزيل",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            if (!completedPath.isNullOrBlank()) {
                                                Spacer(Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Outlined.PlayCircleOutline,
                                                    contentDescription = "فتح المقطع",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                    isItemRunning -> CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    isItemError -> Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = "خطأ",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Outlined.HourglassEmpty,
                                        contentDescription = "انتظار",
                                        tint = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
