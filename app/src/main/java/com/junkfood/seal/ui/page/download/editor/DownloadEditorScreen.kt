package com.junkfood.seal.ui.page.download.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.junkfood.seal.R
import com.junkfood.seal.ui.page.downloadv2.configure.Config
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel

/**
 * Full‑screen premium download editor.
 * Displays and allows editing of the download configuration for a given task.
 * UI follows a glass‑morphic dark theme with the Inter font (globally configured).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadEditorScreen(
    taskId: Int,
    onNavigateBack: () -> Unit,
    dialogViewModel: DownloadDialogViewModel
) {
    var config by remember { mutableStateOf(dialogViewModel.getConfigForTask(taskId)) }

    // Glass‑morphic background brush
    val glassBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1E1E1E).copy(alpha = 0.85f),
            Color(0xFF2C2C2C).copy(alpha = 0.70f)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.download_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        dialogViewModel.applyEditorChanges(taskId, config)
                        onNavigateBack()
                    }) {
                        Text(stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.background(glassBrush).blur(12.dp)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(glassBrush)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // TODO: Re‑use existing UI components for format, subtitle, and destination selection.
            FormatSelectionSection(config = config, onConfigChange = { config = it })
            Spacer(Modifier.height(12.dp))
            SubtitleLanguageSection(config = config, onConfigChange = { config = it })
            Spacer(Modifier.height(12.dp))
            DestinationFolderSection(config = config, onConfigChange = { config = it })
        }
    }
}

@Composable
private fun FormatSelectionSection(
    config: Config,
    onConfigChange: (Config) -> Unit
) {
    Text(text = stringResource(R.string.format), style = MaterialTheme.typography.titleMedium)
    // TODO: Implement format selection controls and update config accordingly.
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleLanguageSection(
    config: Config,
    onConfigChange: (Config) -> Unit
) {
    Text(text = stringResource(R.string.subtitle_language), style = MaterialTheme.typography.titleMedium)
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(config.subtitleLanguage ?: "en") }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            readOnly = true,
            value = selected,
            onValueChange = {},
            label = { Text(stringResource(R.string.select_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("en", "es", "fr", "de", "ar").forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang) },
                    onClick = {
                        selected = lang
                        expanded = false
                        onConfigChange(config.copy(subtitleLanguage = lang))
                    }
                )
            }
        }
    }
}

@Composable
private fun DestinationFolderSection(
    config: Config,
    onConfigChange: (Config) -> Unit
) {
    Text(text = stringResource(R.string.download_directory), style = MaterialTheme.typography.titleMedium)
    Text(text = config.downloadPath ?: "/downloads")
}
