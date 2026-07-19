package com.junkfood.seal.ui.page.downloadv2.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
            .padding(bottom = 24.dp)
    ) {
        // Management Options based on current section
        Text(
            text = stringResource(R.string.management),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        when (activeFilter) {
            Filter.All -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = stringResource(R.string.select_all), onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Delete, text = stringResource(R.string.delete_selected), onClick = onDeleteSelected)
                MenuActionItem(icon = Icons.Outlined.DeleteSweep, text = stringResource(R.string.delete_completed), onClick = onDeleteCompleted)
                MenuActionItem(icon = Icons.Outlined.ErrorOutline, text = stringResource(R.string.delete_failed), onClick = onDeleteFailed)
                MenuActionItem(icon = Icons.Outlined.ClearAll, text = stringResource(R.string.clear_history), onClick = onClearHistory)
            }
            Filter.Downloading -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = stringResource(R.string.select_all), onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Pause, text = stringResource(R.string.pause_all), onClick = onPauseAll)
                MenuActionItem(icon = Icons.Outlined.PlayArrow, text = stringResource(R.string.resume_all), onClick = onResumeAll)
                MenuActionItem(icon = Icons.Outlined.Refresh, text = stringResource(R.string.retry_failed), onClick = onRetryFailed)
                MenuActionItem(icon = Icons.Outlined.Cancel, text = stringResource(R.string.cancel_selected), onClick = onCancelSelected)
            }
            Filter.Canceled -> {
                MenuActionItem(icon = Icons.Outlined.Refresh, text = stringResource(R.string.retry_all), onClick = onRetryAll)
                MenuActionItem(icon = Icons.Outlined.DeleteForever, text = stringResource(R.string.delete_all), onClick = onDeleteAll)
            }
            Filter.Finished -> {
                MenuActionItem(icon = Icons.Outlined.Refresh, text = stringResource(R.string.redownload_all), onClick = onRedownloadAll)
                MenuActionItem(icon = Icons.Outlined.ClearAll, text = stringResource(R.string.delete_history), onClick = onDeleteHistory)
                MenuActionItem(icon = Icons.Outlined.DeleteForever, text = stringResource(R.string.delete_files), onClick = onDeleteFiles)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Sort Options
        Text(
            text = stringResource(R.string.sort_by),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        val sortOptions = listOf(
            SortOption.DateNewest to stringResource(R.string.date_added_newest),
            SortOption.DateOldest to stringResource(R.string.date_added_oldest),
            SortOption.NameAZ to stringResource(R.string.name_a_z),
            SortOption.NameZA to stringResource(R.string.name_z_a),
            SortOption.SizeLargest to stringResource(R.string.size_largest),
            SortOption.SizeSmallest to stringResource(R.string.size_smallest),
            SortOption.Status to stringResource(R.string.status)
        )
        sortOptions.forEach { (option, label) ->
            MenuRadioItem(
                text = label,
                selected = sortOption == option,
                onClick = { onSortOptionChange(option) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // View Options
        Text(
            text = stringResource(R.string.view_options),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        
        MenuRadioItem(
            text = stringResource(R.string.list_view),
            selected = !viewOptions.isGridView,
            onClick = { onViewOptionsChange(viewOptions.copy(isGridView = false)) }
        )
        MenuRadioItem(
            text = stringResource(R.string.card_view),
            selected = viewOptions.isGridView,
            onClick = { onViewOptionsChange(viewOptions.copy(isGridView = true)) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.show_hide),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        MenuSwitchItem(text = stringResource(R.string.size), checked = viewOptions.showSize, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showSize = it)) })
        MenuSwitchItem(text = stringResource(R.string.quality), checked = viewOptions.showQuality, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showQuality = it)) })
        MenuSwitchItem(text = stringResource(R.string.date), checked = viewOptions.showDate, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showDate = it)) })
        MenuSwitchItem(text = stringResource(R.string.duration), checked = viewOptions.showDuration, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showDuration = it)) })
        MenuSwitchItem(text = stringResource(R.string.source), checked = viewOptions.showSource, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showSource = it)) })
    }
}

@Composable
private fun MenuActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MenuRadioItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}
