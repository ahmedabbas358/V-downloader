package com.junkfood.seal

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.junkfood.seal.ui.common.LocalDarkTheme
import com.junkfood.seal.ui.common.SettingsProvider
import com.junkfood.seal.ui.page.downloadv2.configure.Config
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialog
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.SelectionState
import com.junkfood.seal.ui.page.downloadv2.configure.FormatPage
import com.junkfood.seal.ui.page.downloadv2.configure.PlaylistSelectionPage
import com.junkfood.seal.ui.theme.SealTheme
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.matchUrlFromSharedText
import com.junkfood.seal.util.setLanguage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.getViewModel

private const val TAG = "QuickDownloadActivity"

class QuickDownloadActivity : ComponentActivity() {
    private var sharedUrlCached: String = ""
    private var pendingClipboardRead: Boolean = false
    private var clipboardAttempted: Boolean = false

    private fun readClipboardUrl(): String? {
        val clipboardManager =
            getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager ?: return null
        if (!clipboardManager.hasPrimaryClip()) return null
        val text = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        return matchUrlFromSharedText(text).takeIf { it.isNotBlank() }
    }

    private fun Intent.getSharedURL(): String? {
        val intent = this

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    intent.removeExtra(Intent.EXTRA_TEXT)
                    matchUrlFromSharedText(sharedContent)
                }
            }

            "com.junkfood.seal.action.QUICK_DOWNLOAD" -> {
                pendingClipboardRead = true
                readClipboardUrl()
            }

            else -> {
                null
            }
        }
    }

    private var quickDownloadViewModel: DownloadDialogViewModel? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getSharedURL()?.let { sharedUrlCached = it }

        val launchedFromQuickTile = intent?.action == "com.junkfood.seal.action.QUICK_DOWNLOAD"
        if (sharedUrlCached.isEmpty() && !launchedFromQuickTile) {
            android.widget.Toast.makeText(
                this,
                getString(R.string.paste_fail_msg),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            finish()
            return
        }

        enableEdgeToEdge()

        window.run {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }

        val viewModel: DownloadDialogViewModel = getViewModel()
        quickDownloadViewModel = viewModel
        if (sharedUrlCached.isNotEmpty()) {
            val forceRedownload = intent.getBooleanExtra("force_redownload", false)
            if (forceRedownload) intent.removeExtra("force_redownload")
            viewModel.postAction(Action.ShowSheet(listOf(sharedUrlCached), forceRedownload = forceRedownload))
        } else {
            // Allow Android 10+ window-focus clipboard read and manual paste fallback
            viewModel.postAction(Action.ShowSheet(null))
        }

        setContent {
            SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    var preferences by remember {
                        mutableStateOf(DownloadUtil.DownloadPreferences.createFromPreferences())
                    }

                    val sheetValue = viewModel.sheetValueFlow.collectAsStateWithLifecycle().value

                    val state = viewModel.sheetStateFlow.collectAsStateWithLifecycle().value

                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                    val selectionState =
                        viewModel.selectionStateFlow.collectAsStateWithLifecycle().value

                    var showDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(sheetValue, selectionState) {
                        if (sheetValue == DownloadDialogViewModel.SheetValue.Expanded) {
                            showDialog = true
                        } else if (sheetValue == DownloadDialogViewModel.SheetValue.Hidden) {
                            launch { sheetState.hide() }
                                .invokeOnCompletion {
                                    showDialog = false
                                    if (selectionState == SelectionState.Idle) {
                                        this@QuickDownloadActivity.finish()
                                    }
                                }
                        }
                    }

                    if (showDialog) {
                        DownloadDialog(
                            state = state,
                            sheetState = sheetState,
                            config = Config(),
                            preferences = preferences,
                            onPreferencesUpdate = { preferences = it },
                            onActionPost = { viewModel.postAction(it) },
                        )
                    }

                    when (selectionState) {
                        is SelectionState.FormatSelection ->
                            FormatPage(
                                state = selectionState,
                                onDismissRequest = {
                                    viewModel.postAction(Action.Reset)
                                    this.finish()
                                },
                            )

                        SelectionState.Idle -> {}
                        is SelectionState.PlaylistSelection -> {
                            PlaylistSelectionPage(
                                state = selectionState,
                                onActionPost = { viewModel.postAction(it) },
                                onDismissRequest = {
                                    viewModel.postAction(Action.Reset)
                                    this.finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // On Android 10+ (API 29+), primary clipboard content only becomes readable after window focus
        if (hasFocus && pendingClipboardRead && !clipboardAttempted) {
            clipboardAttempted = true
            pendingClipboardRead = false
            if (sharedUrlCached.isEmpty()) {
                val focusedUrl = readClipboardUrl()
                if (!focusedUrl.isNullOrBlank()) {
                    sharedUrlCached = focusedUrl
                    quickDownloadViewModel?.postAction(Action.ShowSheet(listOf(focusedUrl)))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        clipboardAttempted = false
        val newUrl = intent.getSharedURL()
        if (!newUrl.isNullOrEmpty()) {
            sharedUrlCached = newUrl
            val forceRedownload = intent.getBooleanExtra("force_redownload", false)
            if (forceRedownload) intent.removeExtra("force_redownload")
            quickDownloadViewModel?.postAction(Action.ShowSheet(listOf(newUrl), forceRedownload = forceRedownload))
        } else if (intent.action == "com.junkfood.seal.action.QUICK_DOWNLOAD") {
            sharedUrlCached = ""
            quickDownloadViewModel?.postAction(Action.ShowSheet(null))
        }
    }
}
