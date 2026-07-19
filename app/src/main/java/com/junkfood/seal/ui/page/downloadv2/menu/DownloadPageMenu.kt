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
            text = "Management",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        when (activeFilter) {
            Filter.All -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = "Select All", onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Delete, text = "Delete Selected", onClick = onDeleteSelected)
                MenuActionItem(icon = Icons.Outlined.DeleteSweep, text = "Delete Completed", onClick = onDeleteCompleted)
                MenuActionItem(icon = Icons.Outlined.ErrorOutline, text = "Delete Failed", onClick = onDeleteFailed)
                MenuActionItem(icon = Icons.Outlined.ClearAll, text = "Clear History", onClick = onClearHistory)
            }
            Filter.Downloading -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = "Select All", onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Pause, text = "Pause All", onClick = onPauseAll)
                MenuActionItem(icon = Icons.Outlined.PlayArrow, text = "Resume All", onClick = onResumeAll)
                MenuActionItem(icon = Icons.Outlined.Refresh, text = "Retry Failed", onClick = onRetryFailed)
                MenuActionItem(icon = Icons.Outlined.Cancel, text = "Cancel Selected", onClick = onCancelSelected)
            }
            Filter.Canceled -> {
                MenuActionItem(icon = Icons.Outlined.Refresh, text = "Retry All", onClick = onRetryAll)
                MenuActionItem(icon = Icons.Outlined.DeleteForever, text = "Delete All", onClick = onDeleteAll)
            }
            Filter.Finished -> {
                MenuActionItem(icon = Icons.Outlined.Refresh, text = "Redownload All", onClick = onRedownloadAll)
                MenuActionItem(icon = Icons.Outlined.ClearAll, text = "Delete History", onClick = onDeleteHistory)
                MenuActionItem(icon = Icons.Outlined.DeleteForever, text = "Delete Files", onClick = onDeleteFiles)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Sort Options
        Text(
            text = "Sort By",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        val sortOptions = listOf(
            SortOption.DateNewest to "Date Added (Newest)",
            SortOption.DateOldest to "Date Added (Oldest)",
            SortOption.NameAZ to "Name (A-Z)",
            SortOption.NameZA to "Name (Z-A)",
            SortOption.SizeLargest to "Size (Largest)",
            SortOption.SizeSmallest to "Size (Smallest)",
            SortOption.Status to "Status"
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
            text = "View Options",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        
        MenuRadioItem(
            text = "List View",
            selected = !viewOptions.isGridView,
            onClick = { onViewOptionsChange(viewOptions.copy(isGridView = false)) }
        )
        MenuRadioItem(
            text = "Card View",
            selected = viewOptions.isGridView,
            onClick = { onViewOptionsChange(viewOptions.copy(isGridView = true)) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Show / Hide",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        MenuSwitchItem(text = "Size", checked = viewOptions.showSize, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showSize = it)) })
        MenuSwitchItem(text = "Quality", checked = viewOptions.showQuality, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showQuality = it)) })
        MenuSwitchItem(text = "Date", checked = viewOptions.showDate, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showDate = it)) })
        MenuSwitchItem(text = "Duration", checked = viewOptions.showDuration, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showDuration = it)) })
        MenuSwitchItem(text = "Source", checked = viewOptions.showSource, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showSource = it)) })
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
