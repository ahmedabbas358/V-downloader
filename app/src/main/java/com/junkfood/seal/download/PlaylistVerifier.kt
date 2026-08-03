package com.junkfood.seal.download

import android.util.Log
import com.junkfood.seal.App.Companion.audioDownloadDir
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.App.Companion.videoDownloadDir
import com.junkfood.seal.R
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.NotificationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object PlaylistVerifier {
    private const val TAG = "PlaylistVerifier"

    data class VerificationItem(
        val index: Int,
        val title: String,
        val url: String,
        val playlistUrl: String,
        val playlistTitle: String,
        val preferences: DownloadPreferences
    )

    data class VerificationResult(
        val totalCount: Int,
        val foundCount: Int,
        val missingCount: Int,
        val redownloadedCount: Int
    )

    /**
     * Checks all expected items in a playlist download batch against the actual output directory.
     * Re-downloads missing items one-by-one with delays to prevent YouTube rate-limiting.
     */
    suspend fun verifyAndRetryPlaylist(
        items: List<VerificationItem>
    ): VerificationResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext VerificationResult(0, 0, 0, 0)

        val firstItem = items.first()
        val preferences = firstItem.preferences
        val isSubtitleOnly = preferences.skipDownload && preferences.downloadSubtitle
        val playlistTitle = firstItem.playlistTitle.ifEmpty { "Playlist" }
        val cleanPlaylistName = FileUtil.cleanFileName(playlistTitle)

        // Determine base folder path
        val baseDir = if (preferences.extractAudio) {
            if (preferences.privateDirectory) context.filesDir.absolutePath else audioDownloadDir
        } else {
            if (preferences.privateDirectory) context.filesDir.absolutePath else videoDownloadDir
        }

        val targetDirFile = if (isSubtitleOnly) {
            File(baseDir, "[Subtitles] $cleanPlaylistName")
        } else if (preferences.subdirectoryPlaylistTitle) {
            File(baseDir, cleanPlaylistName)
        } else {
            File(baseDir)
        }

        Log.d(TAG, "Verifying playlist items in directory: ${targetDirFile.absolutePath}")

        val existingFiles = if (targetDirFile.exists()) {
            targetDirFile.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }

        val missingItems = mutableListOf<VerificationItem>()
        val foundItems = mutableListOf<VerificationItem>()

        for (item in items) {
            val prefixPadded = String.format(java.util.Locale.US, "%03d - ", item.index)
            
            // Search if any existing file starts with the index prefix "001 - " or contains index
            val matchFound = existingFiles.any { file ->
                val fileName = file.name
                (fileName.startsWith(prefixPadded) || fileName.contains(prefixPadded)) && file.length() > 0L
            }

            if (matchFound) {
                foundItems.add(item)
            } else {
                missingItems.add(item)
            }
        }

        Log.d(TAG, "Playlist Verification: Total=${items.size}, Found=${foundItems.size}, Missing=${missingItems.size}")

        if (missingItems.isEmpty()) {
            NotificationUtil.finishNotification(
                notificationId = playlistTitle.hashCode(),
                title = context.getString(R.string.download_queue),
                text = context.getString(R.string.playlist_download_complete, items.size, items.size)
            )
            return@withContext VerificationResult(
                totalCount = items.size,
                foundCount = items.size,
                missingCount = 0,
                redownloadedCount = 0
            )
        }

        // Notify user about missing items retry
        NotificationUtil.notifyProgress(
            title = playlistTitle,
            text = context.getString(R.string.playlist_retry_missing, missingItems.size),
            progress = 0
        )

        var redownloadedCount = 0
        val maxRetries = 3

        for ((idx, missingItem) in missingItems.withIndex()) {
            // Apply delay between retries to avoid rate limits
            delay(3000L)

            val individualUrl = missingItem.url.ifEmpty { missingItem.playlistUrl }
            val fallbackPrefs = missingItem.preferences.copy(
                downloadPlaylist = false
            )

            val fallbackTaskKey = "fallback_${missingItem.index}_${System.currentTimeMillis()}"

            // Fetch video info for individual item explicitly
            val infoResult = DownloadUtil.fetchVideoInfoFromUrl(
                url = individualUrl,
                playlistIndex = if (missingItem.url.isEmpty()) missingItem.index else null,
                preferences = fallbackPrefs,
                taskKey = fallbackTaskKey
            )

            val videoInfo = infoResult.getOrNull()
            if (videoInfo != null) {
                var success = false
                for (attempt in 1..maxRetries) {
                    Log.d(TAG, "Re-downloading missing item index ${missingItem.index} (attempt $attempt/$maxRetries): ${missingItem.title}")
                    
                    val result = DownloadUtil.downloadVideo(
                        videoInfo = videoInfo,
                        playlistUrl = missingItem.playlistUrl,
                        playlistItem = missingItem.index,
                        taskId = fallbackTaskKey,
                        downloadPreferences = fallbackPrefs,
                        skipDownload = fallbackPrefs.skipDownload,
                        isFallback = true,
                        fallbackPlaylistTitle = missingItem.playlistTitle,
                        progressCallback = null
                    )

                    if (result.isSuccess) {
                        success = true
                        redownloadedCount++
                        break
                    }
                    delay(4000L)
                }
            }

            val progressPercent = ((idx + 1) * 100) / missingItems.size
            NotificationUtil.notifyProgress(
                title = playlistTitle,
                text = context.getString(R.string.playlist_retry_missing, missingItems.size - (idx + 1)),
                progress = progressPercent
            )
        }

        NotificationUtil.finishNotification(
            notificationId = playlistTitle.hashCode(),
            title = playlistTitle,
            text = context.getString(R.string.playlist_download_complete, foundItems.size + redownloadedCount, items.size)
        )

        return@withContext VerificationResult(
            totalCount = items.size,
            foundCount = foundItems.size,
            missingCount = missingItems.size,
            redownloadedCount = redownloadedCount
        )
    }
}
