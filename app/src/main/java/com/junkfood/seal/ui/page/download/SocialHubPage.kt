package com.junkfood.seal.ui.page.download

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.ui.common.glassmorphism
import com.junkfood.seal.ui.common.hapticClickable
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.junkfood.seal.util.makeToast

val socialPlatforms = listOf(
    "TikTok", "Instagram", "Facebook", "X (Twitter)", "Reddit", "Pinterest", "Snapchat",
    "Vimeo", "Dailymotion", "Twitch", "Bilibili", "VK", "LinkedIn", "Tumblr", "Flickr",
    "SoundCloud", "Bandcamp", "Mixcloud", "Spotify (Audio)", "Apple Music", "YouTube Music",
    "Kwai", "Likee", "ShareChat", "Rizzle", "Rumble", "Odysee", "BitChute", "PeerTube",
    "Mastodon", "Patreon", "OnlyFans", "Fansly", "Substack", "Medium", "Dev.to", "GitHub",
    "StackOverflow", "Coursera", "Udemy", "Skillshare", "TED", "Khan Academy", "PBS",
    "BBC", "CNN", "Fox News", "Al Jazeera", "Yahoo", "MSN", "AOL", "ESPN", "Bleacher Report",
    "IMDb", "Rotten Tomatoes", "Giphy", "Tenor", "Imgur", "9GAG", "iFunny", "Discord", "Telegram"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialHubPage(
    modifier: Modifier = Modifier,
    onMenuOpen: () -> Unit,
    dialogViewModel: DownloadDialogViewModel
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        topBar = {
            TopAppBar(
                modifier = Modifier,
                title = { Text("Social Hub", fontWeight = FontWeight.Bold) },
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
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Universal Deep Extractor", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Paste any unknown website link here. The deep extractor will scan the source code to sniff media files (Video/Audio/Images) with high precision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://example.com/video") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet(listOf(urlInput)))
                                urlInput = ""
                            } else {
                                context.makeToast("Please enter a valid URL")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extract Media")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Supported Platforms (${socialPlatforms.size}+)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(socialPlatforms) { platform ->
                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable {
                                context.makeToast("Enter a $platform link in the extractor above")
                            },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(platform, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
