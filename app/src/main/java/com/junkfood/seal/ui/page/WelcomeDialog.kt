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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Downloading
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
fun IconDescription(modifier: Modifier = Modifier, icon: ImageVector, description: String) {
    Row(
        modifier = modifier.padding(top = 12.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(modifier = Modifier.size(24.dp), imageVector = icon, contentDescription = null)
        Text(modifier = Modifier.padding(start = 12.dp), text = description)
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(modifier = Modifier.widthIn(max = 520.dp)) {
                    Text(
                        text =
                            if (step == 0) stringResource(R.string.language)
                            else stringResource(R.string.user_guide),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text =
                            if (step == 0) stringResource(R.string.first_run_language_desc)
                            else stringResource(R.string.first_run_guide_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (step == 0) {
                items(languageOptions.size) { index ->
                    val locale = languageOptions[index]
                    ElevatedCard(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
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
                    ElevatedCard(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            IconDescription(
                                icon = Icons.Outlined.FileDownload,
                                description = stringResource(R.string.download_video_desc),
                            )
                            IconDescription(
                                icon = Icons.Outlined.Subscriptions,
                                description = stringResource(R.string.download_playlist_desc),
                            )
                            IconDescription(
                                icon = Icons.Outlined.Translate,
                                description = stringResource(R.string.subtitle_desc),
                            )
                            IconDescription(
                                icon = Icons.Outlined.SettingsSuggest,
                                description = stringResource(R.string.check_download_settings_desc),
                            )
                            IconDescription(
                                icon = Icons.Outlined.Downloading,
                                description = stringResource(R.string.download_queue),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step == 1) {
                        OutlinedButton(onClick = { step = 0 }) {
                            Text(stringResource(R.string.back))
                        }
                        Spacer(Modifier.size(8.dp))
                    }
                    Button(
                        onClick = {
                            if (step == 0) step = 1 else finish()
                        }
                    ) {
                        Text(stringResource(if (step == 0) R.string.proceed else R.string.start))
                    }
                }
            }
        }
    }
}
