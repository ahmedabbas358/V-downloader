package com.junkfood.seal.ui.page.download.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.junkfood.seal.App
import com.junkfood.seal.R
import com.junkfood.seal.ui.page.downloadv2.configure.Config
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.junkfood.seal.util.DownloadType

/**
 * Full-screen download configuration editor.
 * Allows adjusting download type, format selection mode, subtitle language, and destination path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadEditorScreen(
    taskId: Int,
    onNavigateBack: () -> Unit,
    dialogViewModel: DownloadDialogViewModel,
) {
    var config by remember { mutableStateOf(dialogViewModel.getConfigForTask(taskId)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.download_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            dialogViewModel.applyEditorChanges(taskId, config)
                            onNavigateBack()
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormatSelectionSection(config = config, onConfigChange = { config = it })
            SubtitleLanguageSection(config = config, onConfigChange = { config = it })
            DestinationFolderSection(config = config, onConfigChange = { config = it })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatSelectionSection(
    config: Config,
    onConfigChange: (Config) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.download_type),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                config.typeEntries.forEach { type ->
                    val label = when (type) {
                        DownloadType.Audio -> stringResource(R.string.audio)
                        DownloadType.Video -> stringResource(R.string.video)
                        DownloadType.Command -> stringResource(R.string.commands)
                        DownloadType.Playlist -> stringResource(R.string.playlist)
                        DownloadType.Subtitle -> stringResource(R.string.subtitle)
                    }
                    FilterChip(
                        selected = config.downloadType == type,
                        onClick = { onConfigChange(config.copy(downloadType = type)) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.format_selection),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = config.useFormatSelection,
                    onCheckedChange = { onConfigChange(config.copy(useFormatSelection = it)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleLanguageSection(
    config: Config,
    onConfigChange: (Config) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = config.subtitleLanguage.orEmpty().ifBlank { "ar" }
    val languages = listOf("ar", "en", "fr", "es", "de", "tr", "ja")

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.subtitle_language),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = selected,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.select_language)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                expanded = false
                                onConfigChange(config.copy(subtitleLanguage = lang))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationFolderSection(
    config: Config,
    onConfigChange: (Config) -> Unit,
) {
    val defaultPath = App.videoDownloadDir
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.download_directory),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = config.downloadPath ?: defaultPath,
                onValueChange = { onConfigChange(config.copy(downloadPath = it)) },
                label = { Text(stringResource(R.string.custom_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
