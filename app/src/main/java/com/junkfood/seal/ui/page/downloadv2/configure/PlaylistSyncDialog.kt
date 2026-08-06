package com.junkfood.seal.ui.page.downloadv2.configure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.PlaylistVerifier
import com.junkfood.seal.ui.component.SealModalBottomSheet
import com.junkfood.seal.ui.component.makeToast
import com.junkfood.seal.ui.page.settings.format.VideoQuickSettingsDialog
import com.junkfood.seal.ui.page.settings.format.AudioQuickSettingsDialog
import com.junkfood.seal.util.AUDIO_CONVERSION_FORMAT
import com.junkfood.seal.util.AUDIO_CONVERT
import com.junkfood.seal.util.AUDIO_FORMAT
import com.junkfood.seal.util.AUDIO_QUALITY
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.PreferenceStrings
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.PreferenceUtil.updateInt
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

    var url by remember { mutableStateOf(initialUrl) }
    var selectedType by remember { mutableStateOf(0) } // 0: Video, 1: Audio, 2: Subtitle
    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<PlaylistVerifier.ScanResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Quality settings state
    var currentPrefs by remember { mutableStateOf(preferences) }
    var showVideoPresetDialog by remember { mutableStateOf(false) }
    var showAudioPresetDialog by remember { mutableStateOf(false) }
    var showQualitySection by remember { mutableStateOf(false) }

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

            var removeMusic by remember(currentPrefs) { mutableStateOf(currentPrefs.removeMusic) }

            // Content type selection
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نوع المحتوى:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = selectedType == 0,
                    onClick = { selectedType = 0; scanResult = null; showQualitySection = false },
                    label = { Text(stringResource(R.string.video)) }
                )
                FilterChip(
                    selected = selectedType == 1,
                    onClick = { selectedType = 1; scanResult = null; showQualitySection = false },
                    label = { Text(stringResource(R.string.audio)) }
                )
                FilterChip(
                    selected = selectedType == 2,
                    onClick = { selectedType = 2; scanResult = null; showQualitySection = false },
                    label = { Text(stringResource(R.string.subtitle)) }
                )
                FilterChip(
                    selected = removeMusic,
                    onClick = {
                        removeMusic = !removeMusic
                        com.junkfood.seal.util.REMOVE_MUSIC.updateBoolean(removeMusic)
                        currentPrefs = currentPrefs.copy(removeMusic = removeMusic)
                    },
                    label = { Text("🎵 بدون موسيقى") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quality settings toggle & summary
            if (selectedType != 2) {
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
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Subtitle language display
            if (selectedType == 2) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "لغة الترجمة: ${currentPrefs.subtitleLanguage.ifEmpty { "en" }}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Scan button
            val effectivePrefs = remember(selectedType, currentPrefs, removeMusic) {
                currentPrefs.copy(
                    downloadPlaylist = true,
                    extractAudio = selectedType == 1,
                    skipDownload = selectedType == 2,
                    downloadSubtitle = if (selectedType == 2) true else currentPrefs.downloadSubtitle,
                    removeMusic = removeMusic
                )
            }

            Button(
                onClick = {
                    if (url.isBlank()) return@Button
                    isScanning = true
                    errorMessage = null
                    scanResult = null

                    scope.launch {
                        val res = PlaylistVerifier.scanPlaylist(url.trim(), effectivePrefs)
                        isScanning = false
                        res.onSuccess {
                            scanResult = it
                        }.onFailure { th ->
                            errorMessage = th.localizedMessage ?: "حدث خطأ أثناء فحص القائمة"
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
                    Text("جاري فحص المجلد والقائمة...")
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فحص ومقارنة الملفات")
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
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("الموجود بالجهاز", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Text(
                                        text = "${result.foundItems.size} / ${result.totalCount}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("المفقود للتنزيل", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Text(
                                        text = "${result.missingItems.size} / ${result.totalCount}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (result.missingItems.isNotEmpty()) {
                    Text(
                        text = "الملفات المفقودة التي سيتم تنزيلها:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(4.dp)
                    ) {
                        items(result.missingItems) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = String.format("#%03d", item.index),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Download missing items button — uses effectivePrefs with quality settings
                    Button(
                        onClick = {
                            scope.launch {
                                // Update preferences on missing items with current quality settings
                                val itemsWithQuality = result.missingItems.map { item ->
                                    item.copy(preferences = effectivePrefs)
                                }
                                PlaylistVerifier.enqueueMissingItems(itemsWithQuality, downloader)
                                com.junkfood.seal.util.ToastUtil.makeToast("تمت إضافة ${result.missingItems.size} ملف مفقود إلى قائمة التنزيل")
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("استكمال تحميل المفقود فقط (${result.missingItems.size} عنصر)")
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
