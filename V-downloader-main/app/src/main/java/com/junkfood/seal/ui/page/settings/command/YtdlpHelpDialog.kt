package com.junkfood.seal.ui.page.settings.command

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R

@Composable
fun YtdlpHelpDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "yt-dlp Command Arguments", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "-f, --format: Video format code, e.g. -f 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best'", style = MaterialTheme.typography.bodyMedium)
                Text(text = "--merge-output-format: If a merge is required, output to given container format. e.g. --merge-output-format mkv", style = MaterialTheme.typography.bodyMedium)
                Text(text = "-x, --extract-audio: Convert video files to audio-only files", style = MaterialTheme.typography.bodyMedium)
                Text(text = "--audio-format: Format of audio extraction. e.g. --audio-format mp3", style = MaterialTheme.typography.bodyMedium)
                Text(text = "--audio-quality: Audio quality, 0 (best) to 9 (worst). e.g. --audio-quality 0", style = MaterialTheme.typography.bodyMedium)
                Text(text = "--embed-subs: Embed subtitles in the video", style = MaterialTheme.typography.bodyMedium)
                Text(text = "--embed-metadata: Embed metadata to the video file", style = MaterialTheme.typography.bodyMedium)
                Text(text = "--embed-thumbnail: Embed thumbnail in the video as cover art", style = MaterialTheme.typography.bodyMedium)
                Text(text = "--sub-langs: Languages of the subtitles to download. e.g. --sub-langs en,ar", style = MaterialTheme.typography.bodyMedium)
                Text(text = "--sponsorblock-remove: Remove sponsorblock segments. e.g. --sponsorblock-remove all", style = MaterialTheme.typography.bodyMedium)
                Text(text = "-o, --output: Output filename template.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.ok))
            }
        }
    )
}
