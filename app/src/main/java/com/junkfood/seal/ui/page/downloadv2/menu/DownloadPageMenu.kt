package com.junkfood.seal.ui.page.downloadv2.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.ui.page.downloadv2.Filter

enum class SortOption {
    DateNewest,
    DateOldest,
    NameAZ,
    NameZA,
    SizeLargest,
    SizeSmallest,
    Status
}

data class ViewOptionsState(
    val isGridView: Boolean = true,
    val showSize: Boolean = true,
    val showQuality: Boolean = true,
    val showDate: Boolean = true,
    val showDuration: Boolean = true,
    val showSource: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPageMenuSheet(
    activeFilter: Filter,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    viewOptions: ViewOptionsState,
    onViewOptionsChange: (ViewOptionsState) -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteCompleted: () -> Unit,
    onDeleteFailed: () -> Unit,
    onClearHistory: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onCancelSelected: () -> Unit,
    onRetryAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onRedownloadAll: () -> Unit,
    onDeleteHistory: () -> Unit,
    onDeleteFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        // Tab Header Badge
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (activeFilter) {
                        Filter.All -> Icons.AutoMirrored.Outlined.List
                        Filter.Downloading -> Icons.Outlined.Refresh
                        Filter.Canceled -> Icons.Outlined.ErrorOutline
                        Filter.Finished -> Icons.Outlined.DeleteSweep
                        Filter.Video -> Icons.Outlined.VideoFile
                        Filter.Audio -> Icons.Outlined.AudioFile
                        Filter.Subtitle -> Icons.Outlined.Subtitles
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (activeFilter) {
                        Filter.All -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_filter_desc_all)
                        Filter.Downloading -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_filter_desc_downloading)
                        Filter.Canceled -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_filter_desc_canceled)
                        Filter.Finished -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_filter_desc_finished)
                        Filter.Video -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_filter_desc_video)
                        Filter.Audio -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_filter_desc_audio)
                        Filter.Subtitle -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_filter_desc_subtitle)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Section 1: Contextual Actions based on active Tab
        val sectionName = when (activeFilter) {
            Filter.All -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.filter_all)
            Filter.Downloading -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.filter_downloading)
            Filter.Canceled -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.filter_canceled)
            Filter.Finished -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.filter_finished)
            Filter.Video -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.video_filter)
            Filter.Audio -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.audio_filter)
            Filter.Subtitle -> androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.subtitle_filter)
        }
        Text(
            text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_section_actions, sectionName),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        when (activeFilter) {
            Filter.All -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_select_all_list), onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Pause, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_pause_active), onClick = onPauseAll)
                MenuActionItem(icon = Icons.Outlined.PlayArrow, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_resume_and_retry_all), onClick = onResumeAll)
                MenuActionItem(icon = Icons.Outlined.DeleteSweep, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_delete_completed), onClick = onDeleteCompleted)
                MenuActionItem(icon = Icons.Outlined.ErrorOutline, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_delete_failed), onClick = onDeleteFailed)
                MenuActionItem(icon = Icons.Outlined.ClearAll, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_clear_all_history), onClick = onClearHistory)
            }
            Filter.Downloading -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_select_active), onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Pause, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_pause_all), onClick = onPauseAll)
                MenuActionItem(icon = Icons.Outlined.PlayArrow, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_resume_all), onClick = onResumeAll)
                MenuActionItem(icon = Icons.Outlined.Refresh, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_retry_paused), onClick = onRetryFailed)
                MenuActionItem(icon = Icons.Outlined.Cancel, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_cancel_active), onClick = onCancelSelected)
            }
            Filter.Canceled -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_select_canceled), onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Refresh, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_retry_all_canceled), onClick = onRetryAll)
                MenuActionItem(icon = Icons.Outlined.DeleteForever, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_delete_all_canceled), onClick = onDeleteAll)
            }
            Filter.Finished, Filter.Video, Filter.Audio, Filter.Subtitle -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_select_visible), onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Refresh, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_redownload_visible), onClick = onRedownloadAll)
                MenuActionItem(icon = Icons.Outlined.ClearAll, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_clear_visible_history), onClick = onDeleteHistory)
                MenuActionItem(icon = Icons.Outlined.DeleteForever, text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.menu_delete_visible_files), onClick = onDeleteFiles)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Section 2: Sort Options
        Text(
            text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.sort_items_by),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        val sortOptions = listOf(
            SortOption.DateNewest to androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.sort_date_newest),
            SortOption.DateOldest to androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.sort_date_oldest),
            SortOption.NameAZ to androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.sort_name_az),
            SortOption.NameZA to androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.sort_name_za),
            SortOption.SizeLargest to androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.sort_size_largest),
            SortOption.SizeSmallest to androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.sort_size_smallest),
            SortOption.Status to androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.sort_by_status)
        )
        sortOptions.forEach { (option, label) ->
            MenuRadioItem(
                text = label,
                selected = sortOption == option,
                onClick = { onSortOptionChange(option) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Section 3: View & Display Options
        Text(
            text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.view_card_options),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        MenuRadioItem(
            text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.view_grid_cards),
            selected = viewOptions.isGridView,
            onClick = { onViewOptionsChange(viewOptions.copy(isGridView = true)) }
        )
        MenuRadioItem(
            text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.view_compact_list),
            selected = !viewOptions.isGridView,
            onClick = { onViewOptionsChange(viewOptions.copy(isGridView = false)) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.show_hide_card_details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )

        MenuSwitchItem(text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.show_file_size), checked = viewOptions.showSize, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showSize = it)) })
        MenuSwitchItem(text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.show_quality), checked = viewOptions.showQuality, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showQuality = it)) })
        MenuSwitchItem(text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.show_date_added), checked = viewOptions.showDate, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showDate = it)) })
        MenuSwitchItem(text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.show_video_duration), checked = viewOptions.showDuration, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showDuration = it)) })
        MenuSwitchItem(text = androidx.compose.ui.res.stringResource(com.junkfood.seal.R.string.show_source_channel), checked = viewOptions.showSource, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showSource = it)) })
    }
}

@Composable
private fun MenuActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MenuRadioItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MenuSwitchItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}
