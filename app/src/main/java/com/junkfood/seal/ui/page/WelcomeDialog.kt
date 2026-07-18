package com.junkfood.seal.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.ui.component.CheckBoxItem
import com.junkfood.seal.ui.component.PreferenceSingleChoiceItem
import com.junkfood.seal.util.LocaleLanguageCodeMap
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.PreferenceUtil.getInt
import com.junkfood.seal.util.WELCOME_DIALOG
import com.junkfood.seal.util.setLanguage
import com.junkfood.seal.util.toDisplayName
import java.util.Locale

@Composable
fun WelcomeDialog(onClick: () -> Unit) {
    var showWelcomeDialog by rememberSaveable { mutableIntStateOf(WELCOME_DIALOG.getInt()) }
    var disableDialog by remember { mutableStateOf(false) }
    val onDismissRequest = {
        PreferenceUtil.encodeInt(WELCOME_DIALOG, if (disableDialog) 0 else showWelcomeDialog + 1)
        showWelcomeDialog = 0
    }
    if (showWelcomeDialog > 0)
        AlertDialog(
            onDismissRequest = onDismissRequest,
            dismissButton = {
                TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.close)) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClick()
                        onDismissRequest()
                    }
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            title = { Text(stringResource(R.string.user_guide)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    IconDescription(
                        icon = Icons.Outlined.ContentPaste,
                        description = stringResource(R.string.paste_desc),
                    )
                    IconDescription(
                        icon = Icons.Outlined.FileDownload,
                        description = stringResource(R.string.download_desc),
                    )
                    IconDescription(
                        icon = Icons.Outlined.Subscriptions,
                        description = stringResource(R.string.download_history_desc),
                    )
                    IconDescription(
                        icon = Icons.Outlined.Downloading,
                        description = stringResource(R.string.battery_settings_desc),
                    )
                    IconDescription(
                        icon = Icons.Outlined.SettingsSuggest,
                        description = stringResource(R.string.check_download_settings_desc),
                    )
                    if ((showWelcomeDialog > 1))
                        CheckBoxItem(
                            text = stringResource(id = R.string.close_never_show_again),
                            checked = disableDialog,
                            onValueChange = { disableDialog = !disableDialog },
                        )
                }
            },
        )
}

@Composable
fun IconDescription(modifier: Modifier = Modifier, icon: ImageVector, description: String, title: String? = null) {
    Row(
        modifier = modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                modifier = Modifier.padding(12.dp),
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FirstRunGuide(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedLocale by remember { mutableStateOf<Locale?>(null) }
    val languageOptions =
        remember {
            listOf(null, Locale("ar"), Locale("en", "US"), Locale.getDefault())
                .distinctBy { it?.toLanguageTag() ?: "system" }
                .filter { it == null || LocaleLanguageCodeMap.containsKey(it) }
        }

    fun finish() {
        PreferenceUtil.encodeInt(WELCOME_DIALOG, 0)
        onFinished()
    }

    Scaffold { paddingValues ->
        androidx.compose.animation.AnimatedContent(
            targetState = step,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            label = "onboarding_step"
        ) { currentStep ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        modifier = Modifier.widthIn(max = 520.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (currentStep == 0) Icons.Outlined.Translate else Icons.Outlined.Star,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text =
                                if (currentStep == 0) stringResource(R.string.language)
                                else stringResource(R.string.user_guide),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text =
                                if (currentStep == 0) stringResource(R.string.first_run_language_desc)
                                else stringResource(R.string.first_run_guide_desc),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (currentStep == 0) {
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    items(languageOptions.size) { index ->
                        val locale = languageOptions[index]
                        ElevatedCard(
                            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            onClick = {
                                selectedLocale = locale
                                PreferenceUtil.saveLocalePreference(locale)
                                setLanguage(locale)
                            }
                        ) {
                            PreferenceSingleChoiceItem(
                                text = locale.toDisplayName(),
                                selected = selectedLocale == locale,
                            ) {
                                selectedLocale = locale
                                PreferenceUtil.saveLocalePreference(locale)
                                setLanguage(locale)
                            }
                        }
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconDescription(
                                icon = Icons.Outlined.FileDownload,
                                title = "Smart Downloads",
                                description = stringResource(R.string.download_video_desc),
                            )
                            IconDescription(
                                icon = Icons.Outlined.Subscriptions,
                                title = "Playlists & Channels",
                                description = stringResource(R.string.download_playlist_desc),
                            )
                            IconDescription(
                                icon = Icons.Outlined.Translate,
                                title = "Auto Subtitles",
                                description = stringResource(R.string.subtitle_desc),
                            )
                            IconDescription(
                                icon = Icons.Outlined.SettingsSuggest,
                                title = "Powerful Settings",
                                description = stringResource(R.string.check_download_settings_desc),
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (currentStep == 1) {
                            TextButton(onClick = { step = 0 }) {
                                Text(stringResource(R.string.back))
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        Button(
                            onClick = {
                                if (currentStep == 0) step = 1 else finish()
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = stringResource(if (currentStep == 0) R.string.proceed else R.string.start),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
