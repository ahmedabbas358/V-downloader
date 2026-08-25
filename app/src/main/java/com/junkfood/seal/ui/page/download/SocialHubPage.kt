package com.junkfood.seal.ui.page.download

import android.util.Patterns
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.ui.common.hapticClickable
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.junkfood.seal.util.makeToast

data class PlatformItem(val name: String, val category: String)
data class CategoryItem(val key: String, @androidx.annotation.StringRes val labelRes: Int)

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

val platformCategories = listOf(
    CategoryItem("All", R.string.cat_all),
    CategoryItem("Social", R.string.cat_social),
    CategoryItem("Video", R.string.cat_video),
    CategoryItem("Music", R.string.cat_music),
    CategoryItem("Education", R.string.cat_education),
    CategoryItem("News", R.string.cat_news),
    CategoryItem("Media", R.string.cat_media),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialHubPage(
    modifier: Modifier = Modifier,
    onMenuOpen: () -> Unit,
    dialogViewModel: DownloadDialogViewModel
) {
    Scaffold(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.social_hub_title),
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
            UniversalExtractorView(
                dialogViewModel = dialogViewModel
            )
        }
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.deep_extractor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.deep_extractor_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/video") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                if (clip.isNotBlank()) {
                                    val clean = com.junkfood.seal.util.findURLsFromString(clip, firstMatchOnly = true).firstOrNull() ?: clip
                                    urlInput = clean
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val trimmedUrl = urlInput.trim()
                        val detectedUrls = com.junkfood.seal.util.SocialMediaUrlNormalizer.extractAndNormalizeUrls(trimmedUrl, firstMatchOnly = true)
                        val targetUrl = detectedUrls.firstOrNull() ?: com.junkfood.seal.util.SocialMediaUrlNormalizer.normalizeUrl(trimmedUrl)
                        if (targetUrl.isNotBlank() && (Patterns.WEB_URL.matcher(targetUrl).matches() || targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
                            dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet(listOf(targetUrl)))
                            urlInput = ""
                        } else {
                            context.makeToast(context.getString(R.string.valid_url_prompt))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.extract_and_download), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Platform Directory Search & Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            platformCategories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat.key,
                    onClick = { selectedCategory = cat.key },
                    label = { Text(stringResource(cat.labelRes), fontWeight = if (selectedCategory == cat.key) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(filteredPlatforms) { platform ->
                Surface(
                    modifier = Modifier
                        .aspectRatio(1.1f)
                        .hapticClickable {
                            val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                            val detected = com.junkfood.seal.util.findURLsFromString(clip, firstMatchOnly = true).firstOrNull() ?: clip
                            if (detected.isNotBlank() && Patterns.WEB_URL.matcher(detected).matches()) {
                                urlInput = detected
                                context.makeToast(context.getString(R.string.pasted_from_clipboard, detected))
                            } else {
                                context.makeToast(context.getString(R.string.paste_platform_prompt, platform.name))
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            platform.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
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
