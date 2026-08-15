package com.junkfood.seal.ui.page.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.PlaylistVerifier
import com.junkfood.seal.ui.common.glassmorphism
import com.junkfood.seal.ui.common.hapticClickable
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.ToastUtil
import com.junkfood.seal.util.makeToast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class PlatformItem(val name: String, val category: String)

val socialPlatforms = listOf(
    // Social
    PlatformItem("TikTok", "Social"),
    PlatformItem("Instagram", "Social"),
    PlatformItem("Facebook", "Social"),
    PlatformItem("X (Twitter)", "Social"),
    PlatformItem("Reddit", "Social"),
    PlatformItem("Pinterest", "Social"),
    PlatformItem("Snapchat", "Social"),
    PlatformItem("LinkedIn", "Social"),
    PlatformItem("Tumblr", "Social"),
    PlatformItem("Mastodon", "Social"),
    PlatformItem("Discord", "Social"),

    // Video
    PlatformItem("YouTube", "Video"),
    PlatformItem("Vimeo", "Video"),
    PlatformItem("Dailymotion", "Video"),
    PlatformItem("Twitch", "Video"),
    PlatformItem("Bilibili", "Video"),
    PlatformItem("Rumble", "Video"),
    PlatformItem("Odysee", "Video"),
    PlatformItem("BitChute", "Video"),
    PlatformItem("PeerTube", "Video"),
    PlatformItem("VK", "Video"),
    PlatformItem("Kwai", "Video"),
    PlatformItem("Likee", "Video"),

    // Audio & Music
    PlatformItem("SoundCloud", "Music"),
    PlatformItem("Bandcamp", "Music"),
    PlatformItem("Mixcloud", "Music"),
    PlatformItem("Spotify", "Music"),
    PlatformItem("Apple Music", "Music"),
    PlatformItem("YouTube Music", "Music"),

    // Education & Knowledge
    PlatformItem("Coursera", "Education"),
    PlatformItem("Udemy", "Education"),
    PlatformItem("Skillshare", "Education"),
    PlatformItem("TED", "Education"),
    PlatformItem("Khan Academy", "Education"),
    PlatformItem("PBS", "Education"),
    PlatformItem("Medium", "Education"),
    PlatformItem("Dev.to", "Education"),
    PlatformItem("Substack", "Education"),
    PlatformItem("GitHub", "Education"),
    PlatformItem("StackOverflow", "Education"),

    // News & Media
    PlatformItem("BBC", "News"),
    PlatformItem("CNN", "News"),
    PlatformItem("Fox News", "News"),
    PlatformItem("Al Jazeera", "News"),
    PlatformItem("Yahoo", "News"),
    PlatformItem("ESPN", "News"),
    PlatformItem("IMDb", "News"),
    PlatformItem("Rotten Tomatoes", "News"),
    PlatformItem("Giphy", "Media"),
    PlatformItem("Imgur", "Media"),
    PlatformItem("9GAG", "Media"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialHubPage(
    modifier: Modifier = Modifier,
    onMenuOpen: () -> Unit,
    dialogViewModel: DownloadDialogViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val downloader: DownloaderV2 = koinInject()

    var selectedMainTab by remember { mutableIntStateOf(0) } // 0 = Playlist Synchronizer, 1 = Universal Extractor

    Scaffold(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedMainTab == 0) "مُزامن القوائم الذكي" else "سوشيال هب ومستخرج الروابط",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuOpen) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Main Navigation Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedMainTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedMainTab == 0,
                    onClick = { selectedMainTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مُزامن القوائم", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedMainTab == 1,
                    onClick = { selectedMainTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("المستخرج والمنصات", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedMainTab == 0) {
                // Tab 0: Embedded Smart Playlist Synchronizer
                EmbeddedPlaylistSyncView(
                    dialogViewModel = dialogViewModel,
                    downloader = downloader
                )
            } else {
                // Tab 1: Universal Deep Extractor & Supported Platforms Directory
                UniversalExtractorView(
                    dialogViewModel = dialogViewModel
                )
            }
        }
    }
}

@Composable
private fun EmbeddedPlaylistSyncView(
    dialogViewModel: DownloadDialogViewModel,
    downloader: DownloaderV2
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var playlistUrl by remember { mutableStateOf("") }
    var selectedType by remember { mutableIntStateOf(0) } // 0 = Video, 1 = Audio, 2 = Subtitle
    var removeMusic by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<PlaylistVerifier.ScanResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            val resolvedPath = FileUtil.getRealPath(it)
            if (resolvedPath.isNotBlank()) {
                customFolderPath = resolvedPath
                scanResult = null
            }
        }
    }

    var resultTab by remember { mutableIntStateOf(0) } // 0 = Missing, 1 = Found
    val selectedMissingIndices = remember { mutableStateListOf<Int>() }

    val basePrefs = remember { DownloadUtil.DownloadPreferences.createFromPreferences() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .glassmorphism()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "فحص واستكمال قوائم التشغيل",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "مقارنة المجلد بالرابط وتحميل المفقود فقط بدقة 100%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // URL Input with paste button
                OutlinedTextField(
                    value = playlistUrl,
                    onValueChange = { playlistUrl = it; scanResult = null },
                    label = { Text("رابط قائمة التشغيل (Playlist URL)") },
                    placeholder = { Text("https://www.youtube.com/playlist?list=...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (playlistUrl.isNotEmpty()) {
                                IconButton(onClick = { playlistUrl = ""; scanResult = null }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "مسح")
                                }
                            }
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                if (clip.isNotBlank()) {
                                    playlistUrl = clip
                                    scanResult = null
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "لصق من الحافظة", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "نوع المحتوى المستهدف للتدقيق:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

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
                        label = { Text("🎬 فيديو") }
                    )
                    FilterChip(
                        selected = selectedType == 1,
                        onClick = { selectedType = 1; scanResult = null },
                        label = { Text("🎵 صوت") }
                    )
                    FilterChip(
                        selected = selectedType == 2,
                        onClick = { selectedType = 2; scanResult = null },
                        label = { Text("📝 ترجمة فقط") }
                    )
                    FilterChip(
                        selected = removeMusic,
                        onClick = {
                            removeMusic = !removeMusic
                            com.junkfood.seal.util.REMOVE_MUSIC.updateBoolean(removeMusic)
                        },
                        label = { Text("🎙️ عزل الموسيقى") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target Folder Selector
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (customFolderPath.isNotBlank()) "المجلد: $customFolderPath" else "المجلد: التلقائي بحسب نوع المحتوى",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "اختيار مجلد من الهاتف", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showFolderEditField = !showFolderEditField }) {
                                Icon(Icons.Default.Edit, contentDescription = "كتابة المسار", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (showFolderEditField) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customFolderPath,
                                onValueChange = { customFolderPath = it; scanResult = null },
                                label = { Text("مسار المجلد المحلي") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val effectivePrefs = remember(selectedType, removeMusic) {
                    basePrefs.copy(
                        downloadPlaylist = true,
                        extractAudio = selectedType == 1,
                        skipDownload = selectedType == 2,
                        downloadSubtitle = if (selectedType == 2) true else basePrefs.downloadSubtitle,
                        removeMusic = removeMusic
                    )
                }

                Button(
                    onClick = {
                        if (playlistUrl.isBlank()) {
                            context.makeToast("يرجى إدخال رابط قائمة التشغيل")
                            return@Button
                        }
                        isScanning = true
                        errorMessage = null
                        scanResult = null
                        selectedMissingIndices.clear()

                        scope.launch {
                            val res = PlaylistVerifier.scanPlaylist(
                                playlistUrl = playlistUrl.trim(),
                                preferences = effectivePrefs,
                                customDirectoryPath = customFolderPath.ifBlank { null }
                            )
                            isScanning = false
                            res.onSuccess {
                                scanResult = it
                                selectedMissingIndices.clear()
                                selectedMissingIndices.addAll(it.missingItems.map { item -> item.index })
                            }.onFailure { th ->
                                errorMessage = th.localizedMessage ?: "حدث خطأ أثناء فحص ومقارنة الملفات"
                            }
                        }
                    },
                    enabled = playlistUrl.isNotBlank() && !isScanning,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("جاري فحص وتدقيق التخزين مع القائمة...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فحص ومقارنة الملفات في التخزين", fontWeight = FontWeight.Bold)
                    }
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        // Scan Results Section
        scanResult?.let { result ->
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = result.targetDirectory,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("الموجود بالجهاز", style = MaterialTheme.typography.labelMedium)
                                }
                                Text(
                                    text = "${result.foundItems.size} / ${result.totalCount}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("المفقود للتنزيل", style = MaterialTheme.typography.labelMedium)
                                }
                                Text(
                                    text = "${result.missingItems.size} / ${result.totalCount}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PrimaryTabRow(
                        selectedTabIndex = resultTab,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    ) {
                        Tab(
                            selected = resultTab == 0,
                            onClick = { resultTab = 0 },
                            text = { Text("المفقود (${result.missingItems.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = resultTab == 1,
                            onClick = { resultTab = 1 },
                            text = { Text("الموجود (${result.foundItems.size})", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (resultTab == 0) {
                        if (result.missingItems.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "المحدد للتنزيل: ${selectedMissingIndices.size} من ${result.missingItems.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = {
                                        if (selectedMissingIndices.size == result.missingItems.size) {
                                            selectedMissingIndices.clear()
                                        } else {
                                            selectedMissingIndices.clear()
                                            selectedMissingIndices.addAll(result.missingItems.map { it.index })
                                        }
                                    }
                                ) {
                                    Text(if (selectedMissingIndices.size == result.missingItems.size) "إلغاء التحديد" else "تحديد الكل")
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(4.dp)
                            ) {
                                items(result.missingItems) { item ->
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
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val toDownload = result.missingItems.filter { selectedMissingIndices.contains(it.index) }
                                    if (toDownload.isEmpty()) {
                                        ToastUtil.makeToast("يرجى تحديد عنصر واحد على الأقل")
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
                                    }
                                },
                                enabled = selectedMissingIndices.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تنزيل العناصر المحددة (${selectedMissingIndices.size} ملف)", fontWeight = FontWeight.Bold)
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
                        if (result.foundItems.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(6.dp)
                            ) {
                                items(result.foundItems) { item ->
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
                                        if (!item.matchedFilePath.isNullOrBlank()) {
                                            Text(
                                                text = item.matchedFilePath.substringAfterLast('/'),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(start = 32.dp, top = 2.dp)
                                            )
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
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun UniversalExtractorView(
    dialogViewModel: DownloadDialogViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var urlInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("All", "Social", "Video", "Music", "Education", "News", "Media")

    val filteredPlatforms = remember(selectedCategory, searchQuery) {
        socialPlatforms.filter { platform ->
            (selectedCategory == "All" || platform.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() || platform.name.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Universal Extractor Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .glassmorphism()
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "المستخرج الذكي الشامل (Deep Extractor)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "الصق أي رابط من أي منصة أو موقع ويب لاستخراج ملفات الفيديو، الصوت، والترجمة بدقة فائقة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/video") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "مسح")
                                }
                            }
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                if (clip.isNotBlank()) {
                                    urlInput = clip
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "لصق من الحافظة", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val trimmedUrl = urlInput.trim()
                        if (trimmedUrl.isNotBlank() && Patterns.WEB_URL.matcher(trimmedUrl).matches()) {
                            dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet(listOf(trimmedUrl)))
                            urlInput = ""
                        } else {
                            context.makeToast("يرجى إدخال رابط صحيح (Valid URL)")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استخراج الوسائط والتحميل", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Platform Directory Search & Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(filteredPlatforms) { platform ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1.1f)
                        .clip(MaterialTheme.shapes.large)
                        .glassmorphism()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .hapticClickable {
                            context.makeToast("الصق رابط من $platform.name في المستخرج أعلاه")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Language,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            platform.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

