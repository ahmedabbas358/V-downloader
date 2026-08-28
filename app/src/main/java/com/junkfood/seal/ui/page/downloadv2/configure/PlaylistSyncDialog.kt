package com.junkfood.seal.ui.page.downloadv2.configure

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.PlaylistVerifier
import com.junkfood.seal.ui.component.SealModalBottomSheet
import com.junkfood.seal.ui.page.settings.format.AudioQuickSettingsDialog
import com.junkfood.seal.ui.page.settings.format.VideoQuickSettingsDialog
import com.junkfood.seal.util.AUDIO_CONVERSION_FORMAT
import com.junkfood.seal.util.AUDIO_CONVERT
import com.junkfood.seal.util.AUDIO_FORMAT
import com.junkfood.seal.util.AUDIO_QUALITY
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.PreferenceStrings
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.PreferenceUtil.updateInt
import com.junkfood.seal.util.ToastUtil
import com.junkfood.seal.util.USE_CUSTOM_AUDIO_PRESET
import com.junkfood.seal.util.VIDEO_FORMAT
import com.junkfood.seal.util.VIDEO_QUALITY
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSyncDialog(
    initialUrl: String = "",
    preferences: DownloadPreferences,
    onDismissRequest: () -> Unit,
) {
    val downloader: DownloaderV2 = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var url by remember { mutableStateOf(initialUrl) }
    var selectedType by remember { mutableIntStateOf(
        when {
            preferences.skipDownload && preferences.downloadSubtitle -> 2
            preferences.extractAudio -> 1
            else -> 0
        }
    ) } // 0: Video, 1: Audio, 2: Subtitle
    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<com.junkfood.seal.download.engine.playlist.PlaylistAuditResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Quality settings state
    var currentPrefs by remember { mutableStateOf(preferences) }
    var showVideoPresetDialog by remember { mutableStateOf(false) }
    var showAudioPresetDialog by remember { mutableStateOf(false) }

    // Directory Override with SAF Picker
    var customFolderPath by remember { mutableStateOf("") }
    var showFolderEditField by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent {
                return super.createIntent(context, input).apply {
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                }
            }
        }
    ) { uri ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val resolvedPath = FileUtil.getRealPath(it)
            if (resolvedPath.isNotBlank()) {
                customFolderPath = resolvedPath
                scanResult = null
            }
        }
    }

    // Results inspection tabs (0 = Missing, 1 = Found)
    var resultTab by remember { mutableIntStateOf(0) }
    val selectedMissingIndices = remember { mutableStateListOf<Int>() }

    SealModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name) + " - مُزامن ذكي",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "مقارنة المجلد بالرابط وتحميل المفقود فقط",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // URL input
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; scanResult = null },
                label = { Text("رابط قائمة التشغيل") },
                placeholder = { Text("https://www.youtube.com/playlist?list=...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Content type selection with HORIZONTAL SCROLLING
            Text(
                text = "نوع المحتوى:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = selectedType == 0,
                    onClick = { selectedType = 0; scanResult = null },
                    label = { Text(stringResource(R.string.video)) }
                )
                FilterChip(
                    selected = selectedType == 1,
                    onClick = { selectedType = 1; scanResult = null },
                    label = { Text(stringResource(R.string.audio)) }
                )
                FilterChip(
                    selected = selectedType == 2,
                    onClick = { selectedType = 2; scanResult = null },
                    label = { Text(stringResource(R.string.subtitle)) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Folder Section with Folder Picker
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (customFolderPath.isNotBlank()) "المجلد المستهدف: $customFolderPath" else "المجلد المستهدف: التلقائي بحسب المحتوى",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "اختيار مجلد من الهاتف",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showFolderEditField = !showFolderEditField }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "كتابة المسار يدوياً",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (showFolderEditField) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customFolderPath,
                            onValueChange = { customFolderPath = it; scanResult = null },
                            label = { Text("مسار المجلد المحلي (اختياري)") },
                            placeholder = { Text("/storage/emulated/0/Download/...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quality settings summary
            if (selectedType != 2) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HighQuality,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "إعدادات الجودة",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = if (selectedType == 0) {
                                    "الدقة: ${PreferenceStrings.getVideoResolutionDesc(currentPrefs.videoResolution)}"
                                } else {
                                    "جودة الصوت: ${PreferenceStrings.getAudioQualityDesc(currentPrefs.audioQuality)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            if (selectedType == 0) showVideoPresetDialog = true
                            else showAudioPresetDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "تعديل الجودة",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Scan button
            val effectivePrefs = remember(selectedType, currentPrefs) {
                currentPrefs.copy(
                    downloadPlaylist = true,
                    extractAudio = selectedType == 1,
                    skipDownload = selectedType == 2,
                    downloadSubtitle = if (selectedType == 2) true else currentPrefs.downloadSubtitle,
                )
            }

            Button(
                onClick = {
                    if (url.isBlank()) return@Button
                    isScanning = true
                    errorMessage = null
                    scanResult = null
                    selectedMissingIndices.clear()

                    scope.launch {
                        val res = PlaylistVerifier.scanPlaylist(
                            playlistUrl = url.trim(),
                            preferences = effectivePrefs,
                            customDirectoryPath = customFolderPath.ifBlank { null }
                        )
                        isScanning = false
                        res.onSuccess {
                            scanResult = it
                            selectedMissingIndices.clear()
                            val autoSelectItems = it.items.filter { item -> 
                                item.state == com.junkfood.seal.download.engine.playlist.AuditState.NOT_DOWNLOADED ||
                                item.state == com.junkfood.seal.download.engine.playlist.AuditState.PARTIAL ||
                                item.state == com.junkfood.seal.download.engine.playlist.AuditState.CORRUPTED
                            }
                            selectedMissingIndices.addAll(autoSelectItems.map { item -> item.index })
                        }.onFailure { th ->
                            errorMessage = th.localizedMessage ?: "حدث خطأ أثناء فحص ومقارنة عناصر المجلد بالقائمة"
                        }
                    }
                },
                enabled = url.isNotBlank() && !isScanning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري فحص وتدقيق المجلد بالقائمة...")
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فحص ومقارنة الملفات في المجلد")
                }
            }

            // Error message
            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Scan results
            scanResult?.let { result ->
                val foundItems = result.items.filter { it.state == com.junkfood.seal.download.engine.playlist.AuditState.DOWNLOADED }
                val missingOrIncompleteItems = result.items.filter { it.state != com.junkfood.seal.download.engine.playlist.AuditState.DOWNLOADED && it.state != com.junkfood.seal.download.engine.playlist.AuditState.UNKNOWN }
                val unknownItems = result.items.filter { it.state == com.junkfood.seal.download.engine.playlist.AuditState.UNKNOWN }

                Spacer(modifier = Modifier.height(16.dp))

                // Directory info + stats
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = result.targetDirectory,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Downloaded", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${result.summary.downloaded} / ${result.totalCount}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Missing", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${missingOrIncompleteItems.size} / ${result.totalCount}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            if (unknownItems.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Unknown", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${unknownItems.size} / ${result.totalCount}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Switcher between Missing and Found
                PrimaryTabRow(
                    selectedTabIndex = resultTab,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = resultTab == 0,
                        onClick = { resultTab = 0 },
                        text = { Text("Missing (${missingOrIncompleteItems.size + unknownItems.size})") }
                    )
                    Tab(
                        selected = resultTab == 1,
                        onClick = { resultTab = 1 },
                        text = { Text("Downloaded (${foundItems.size})") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (resultTab == 0) {
                    // Missing Tab
                    val allMissingItems = missingOrIncompleteItems + unknownItems
                    if (allMissingItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "العناصر المحددة للتنزيل: ${selectedMissingIndices.size} / ${allMissingItems.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    if (selectedMissingIndices.size == allMissingItems.size) {
                                        selectedMissingIndices.clear()
                                    } else {
                                        selectedMissingIndices.clear()
                                        selectedMissingIndices.addAll(allMissingItems.map { it.index })
                                    }
                                }
                            ) {
                                Text(if (selectedMissingIndices.size == allMissingItems.size) "إلغاء تحديد الكل" else "تحديد الكل")
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(4.dp)
                        ) {
                            items(allMissingItems) { item ->
                                val isSelected = selectedMissingIndices.contains(item.index)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedMissingIndices.add(item.index)
                                            else selectedMissingIndices.remove(item.index)
                                        }
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = String.format("#%03d", item.index),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.state != com.junkfood.seal.download.engine.playlist.AuditState.NOT_DOWNLOADED) {
                                            val isUnknown = item.state == com.junkfood.seal.download.engine.playlist.AuditState.UNKNOWN
                                            Text(
                                                text = if (isUnknown) "الحالة: غير مؤكد (UNKNOWN) - يرجى المراجعة" else "الحالة: ${item.state.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUnknown) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Download missing items button
                        Button(
                            onClick = {
                                val toDownload = allMissingItems.filter { selectedMissingIndices.contains(it.index) }
                                if (toDownload.isEmpty()) {
                                    ToastUtil.makeToast("يرجى اختيار عنصر واحد على الأقل للتنزيل")
                                    return@Button
                                }
                                scope.launch {
                                    val itemsWithQuality = toDownload.map { item ->
                                        item.copy(
                                            preferences = effectivePrefs.copy(
                                                commandDirectory = customFolderPath.ifBlank { result.targetDirectory },
                                                subdirectoryPlaylistTitle = false
                                            )
                                        )
                                    }
                                    PlaylistVerifier.enqueueMissingItems(
                                        missingItems = itemsWithQuality,
                                        targetDirectory = customFolderPath.ifBlank { result.targetDirectory },
                                        downloader = downloader
                                    )
                                    ToastUtil.makeToast("تمت إضافة ${toDownload.size} ملف مفقود إلى قائمة التنزيل")
                                    onDismissRequest()
                                }
                            },
                            enabled = selectedMissingIndices.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("استكمال تحميل المحدد فقط (${selectedMissingIndices.size} عنصر)")
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎉 جميع عناصر قائمة التشغيل مكتملة وموجودة بالكامل في المجلد!",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    // Found Tab
                    if (foundItems.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(6.dp)
                        ) {
                            items(foundItems) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = String.format("#%03d", item.index),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (item.matchedFile != null) {
                                        Row(
                                            modifier = Modifier.padding(start = 32.dp, top = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.matchedFile.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            val sizeText = com.junkfood.seal.ui.common.formatters.FileSizeFormatter.format(item.matchedFileSize)
                                            Text(
                                                text = "[$sizeText]",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لم يتم العثور على أي ملفات من هذه القائمة في هذا المجلد بعد.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }

    // Video Quality Dialog
    if (showVideoPresetDialog) {
        var res by remember(currentPrefs) { mutableIntStateOf(currentPrefs.videoResolution) }
        var format by remember(currentPrefs) { mutableIntStateOf(currentPrefs.videoFormat) }

        VideoQuickSettingsDialog(
            videoResolution = res,
            videoFormatPreference = format,
            onResolutionSelect = { res = it },
            onFormatSelect = { format = it },
            onDismissRequest = { showVideoPresetDialog = false },
            onSave = {
                VIDEO_FORMAT.updateInt(format)
                VIDEO_QUALITY.updateInt(res)
                currentPrefs = DownloadPreferences.createFromPreferences()
            },
        )
    }

    // Audio Quality Dialog
    if (showAudioPresetDialog) {
        var quality by remember(currentPrefs) { mutableIntStateOf(currentPrefs.audioQuality) }
        var customPreset by remember(currentPrefs) { mutableStateOf(currentPrefs.useCustomAudioPreset) }
        var conversionFmt by remember(currentPrefs) { mutableIntStateOf(currentPrefs.audioConvertFormat) }
        var convertAudio by remember(currentPrefs) { mutableStateOf(currentPrefs.convertAudio) }
        var preferredFormat by remember(currentPrefs) { mutableIntStateOf(currentPrefs.audioFormat) }

        AudioQuickSettingsDialog(
            modifier = Modifier,
            preferences = currentPrefs,
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
                currentPrefs = DownloadPreferences.createFromPreferences()
            },
        )
    }
}
