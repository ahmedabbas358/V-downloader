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
import androidx.compose.material.icons.outlined.Sync
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
                        else -> Icons.Outlined.Info
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (activeFilter) {
                        Filter.All -> "إدارة قائمة التنزيلات الشاملة (الجميع)"
                        Filter.Downloading -> "إدارة التنزيلات الجارية والنشطة"
                        Filter.Canceled -> "إدارة التنزيلات المُلغاة والفاشلة"
                        Filter.Finished -> "إدارة التنزيلات المكتملة"
                        else -> "إدارة التنزيلات"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Section 1: Contextual Actions based on active Tab
        Text(
            text = "إجراءات قسم " + when (activeFilter) {
                Filter.All -> "الجميع"
                Filter.Downloading -> "جار التحميل"
                Filter.Canceled -> "تم الإلغاء"
                Filter.Finished -> "انتهى"
                else -> ""
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        when (activeFilter) {
            Filter.All -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = "تحديد جميع العناصر في القائمة", onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Pause, text = "إيقاف جميع التنزيلات النشطة", onClick = onPauseAll)
                MenuActionItem(icon = Icons.Outlined.PlayArrow, text = "استئناف وإعادة محاولة الكل", onClick = onResumeAll)
                MenuActionItem(icon = Icons.Outlined.DeleteSweep, text = "حذف التنزيلات المكتملة فقط", onClick = onDeleteCompleted)
                MenuActionItem(icon = Icons.Outlined.ErrorOutline, text = "حذف التنزيلات الفاشلة والمُلغاة", onClick = onDeleteFailed)
                MenuActionItem(icon = Icons.Outlined.ClearAll, text = "مسح سجل السجل بالكامل", onClick = onClearHistory)
            }
            Filter.Downloading -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = "تحديد جميع التنزيلات الجارية", onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Pause, text = "إيقاف جميع التنزيلات مؤقتاً", onClick = onPauseAll)
                MenuActionItem(icon = Icons.Outlined.PlayArrow, text = "استئناف جميع التنزيلات", onClick = onResumeAll)
                MenuActionItem(icon = Icons.Outlined.Refresh, text = "إعادة محاولة التنزيلات التعلقة", onClick = onRetryFailed)
                MenuActionItem(icon = Icons.Outlined.Cancel, text = "إلغاء التنزيلات الجارية", onClick = onCancelSelected)
            }
            Filter.Canceled -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = "تحديد العناصر المُلغاة", onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Refresh, text = "إعادة محاولة وتنزيل جميع المُلغاة", onClick = onRetryAll)
                MenuActionItem(icon = Icons.Outlined.DeleteForever, text = "حذف جميع التنزيلات المُلغاة", onClick = onDeleteAll)
            }
            Filter.Finished -> {
                MenuActionItem(icon = Icons.Outlined.SelectAll, text = "تحديد العناصر المكتملة", onClick = onSelectAll)
                MenuActionItem(icon = Icons.Outlined.Refresh, text = "إعادة تنزيل جميع الملفات المكتملة", onClick = onRedownloadAll)
                MenuActionItem(icon = Icons.Outlined.ClearAll, text = "مسح سجل التنزيلات المكتملة", onClick = onDeleteHistory)
                MenuActionItem(icon = Icons.Outlined.DeleteForever, text = "حذف الملفات والسجل بالكامل", onClick = onDeleteFiles)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Section 2: Sort Options
        Text(
            text = "ترتيب العناصر حسب:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        val sortOptions = listOf(
            SortOption.DateNewest to "الأحدث تاريخاً (التصاعدي)",
            SortOption.DateOldest to "الأقدم تاريخاً",
            SortOption.NameAZ to "الاسم (أ - ي)",
            SortOption.NameZA to "الاسم (ي - أ)",
            SortOption.SizeLargest to "الحجم (الأكبر أولاً)",
            SortOption.SizeSmallest to "الحجم (الأصغر أولاً)",
            SortOption.Status to "بحسب حالة التنزيل"
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
            text = "خيارات عرض البطاقات والمعلومات:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        MenuRadioItem(
            text = "عرض شبكي (بطاقات كبيرة)",
            selected = viewOptions.isGridView,
            onClick = { onViewOptionsChange(viewOptions.copy(isGridView = true)) }
        )
        MenuRadioItem(
            text = "عرض قائمة (سطري مدمج)",
            selected = !viewOptions.isGridView,
            onClick = { onViewOptionsChange(viewOptions.copy(isGridView = false)) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "إظهار / إخفاء تفاصيل البطاقات:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )

        MenuSwitchItem(text = "إظهار حجم الملف", checked = viewOptions.showSize, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showSize = it)) })
        MenuSwitchItem(text = "إظهار جودة/دقة المقطع", checked = viewOptions.showQuality, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showQuality = it)) })
        MenuSwitchItem(text = "إظهار تاريخ الإضافة", checked = viewOptions.showDate, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showDate = it)) })
        MenuSwitchItem(text = "إظهار مدة الفيديو", checked = viewOptions.showDuration, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showDuration = it)) })
        MenuSwitchItem(text = "إظهار مصدر/قناة المقطع", checked = viewOptions.showSource, onCheckedChange = { onViewOptionsChange(viewOptions.copy(showSource = it)) })
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
