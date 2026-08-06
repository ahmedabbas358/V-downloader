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

    data class ScanResult(
        val playlistTitle: String,
        val targetDirectory: String,
        val totalCount: Int,
        val foundItems: List<VerificationItem>,
        val missingItems: List<VerificationItem>,
    )

    suspend fun scanPlaylist(
        playlistUrl: String,
        preferences: DownloadPreferences
    ): Result<ScanResult> = withContext(Dispatchers.IO) {
        runCatching {
            val infoResult = DownloadUtil.getPlaylistOrVideoInfo(
                playlistURL = playlistUrl,
                downloadPreferences = preferences,
            )
            val info = infoResult.getOrThrow()
            val playlistInfo = info as? com.junkfood.seal.util.PlaylistResult
                ?: throw IllegalStateException("URL is not a playlist")
            val entries = playlistInfo.entries ?: emptyList()
            
            val isSubtitleOnly = preferences.skipDownload && preferences.downloadSubtitle
            val playlistTitle = playlistInfo.title ?: "Playlist"
            val cleanPlaylistName = FileUtil.cleanFileName(playlistTitle)
            
            val baseDir = if (preferences.extractAudio) {
                if (preferences.privateDirectory) context.filesDir.absolutePath else audioDownloadDir
            } else {
                if (preferences.privateDirectory) context.filesDir.absolutePath else videoDownloadDir
            }

            val targetDirFile = if (isSubtitleOnly && cleanPlaylistName.isNotEmpty()) {
                File(baseDir, "[Subtitles] $cleanPlaylistName")
            } else if (preferences.subdirectoryPlaylistTitle && cleanPlaylistName.isNotEmpty()) {
                File(baseDir, cleanPlaylistName)
            } else {
                File(baseDir)
            }

            val candidateDirs = mutableListOf<File>()
            if (targetDirFile.exists()) candidateDirs.add(targetDirFile)
            val baseDirFile = File(baseDir)
            if (baseDirFile.exists() && !candidateDirs.contains(baseDirFile)) candidateDirs.add(baseDirFile)
            if (cleanPlaylistName.isNotEmpty()) {
                val subDir = File(baseDir, "[Subtitles] $cleanPlaylistName")
                if (subDir.exists() && !candidateDirs.contains(subDir)) candidateDirs.add(subDir)
                val playlistDir = File(baseDir, cleanPlaylistName)
                if (playlistDir.exists() && !candidateDirs.contains(playlistDir)) candidateDirs.add(playlistDir)
            }

            val allCandidateFiles = candidateDirs.flatMap { dir ->
                dir.listFiles()?.filter { file ->
                    file.isFile &&
                    !file.name.endsWith(".part", ignoreCase = true) &&
                    !file.name.endsWith(".ytdl", ignoreCase = true) &&
                    file.length() > (if (isSubtitleOnly) 50L else 1024L)
                }?.toList() ?: emptyList()
            }

            val foundItems = mutableListOf<VerificationItem>()
            val missingItems = mutableListOf<VerificationItem>()

            entries.forEachIndexed { indexZero, entry ->
                val index = indexZero + 1
                val entryTitle = entry.title ?: "Track $index"
                val rawUrl = entry.url.orEmpty()
                val itemUrl = when {
                    rawUrl.startsWith("http", ignoreCase = true) -> rawUrl
                    !entry.id.isNullOrEmpty() -> "https://www.youtube.com/watch?v=${entry.id}"
                    else -> ""
                }
                val item = VerificationItem(
                    index = index,
                    title = entryTitle,
                    url = itemUrl,
                    playlistUrl = playlistUrl,
                    playlistTitle = playlistTitle,
                    preferences = preferences
                )

                val extractedId = entry.id.orEmpty().ifEmpty {
                    when {
                        itemUrl.contains("v=") -> itemUrl.substringAfter("v=").substringBefore("&").substringBefore("?")
                        itemUrl.contains("youtu.be/") -> itemUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                        else -> ""
                    }
                }
                val rawTitle = entryTitle.removePrefix("[Subtitle] ").trim()
                val normalizedTitle = rawTitle.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9\\u0600-\\u06FF]"), "")
                val titleWords = normalizedTitle.chunked(6).filter { it.length >= 4 }

                val matchFound = allCandidateFiles.any { file ->
                    val fileName = file.name
                    val normalizedFileName = fileName.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9\\u0600-\\u06FF]"), "")

                    var matches = false
                    if (extractedId.length >= 4 && (fileName.contains(extractedId) || normalizedFileName.contains(extractedId.lowercase()))) {
                        matches = true
                    }
                    if (!matches && index > 0) {
                        val indexRegex = Regex("^(?:0*${index}[ \\-\\_\\.\\]]|\\[0*${index}\\])")
                        if (indexRegex.containsMatchIn(fileName)) {
                            matches = true
                        }
                    }
                    if (!matches && normalizedTitle.isNotEmpty() && normalizedFileName.contains(normalizedTitle)) {
                        matches = true
                    }
                    if (!matches && titleWords.isNotEmpty() && titleWords.all { normalizedFileName.contains(it) }) {
                        matches = true
                    }
                    if (!matches) return@any false

                    if (isSubtitleOnly) {
                        fileName.endsWith(".srt", ignoreCase = true) ||
                        fileName.endsWith(".vtt", ignoreCase = true) ||
                        fileName.endsWith(".ass", ignoreCase = true) ||
                        fileName.endsWith(".lrc", ignoreCase = true)
                    } else {
                        !fileName.endsWith(".png", ignoreCase = true) &&
                        !fileName.endsWith(".jpg", ignoreCase = true) &&
                        !fileName.endsWith(".webp", ignoreCase = true)
                    }
                }

                if (matchFound) {
                    foundItems.add(item)
                } else {
                    missingItems.add(item)
                }
            }

            ScanResult(
                playlistTitle = playlistTitle,
                targetDirectory = targetDirFile.absolutePath,
                totalCount = entries.size,
                foundItems = foundItems,
                missingItems = missingItems
            )
        }
    }

    suspend fun enqueueMissingItems(
        missingItems: List<VerificationItem>,
        downloader: DownloaderV2
    ) = withContext(Dispatchers.Default) {
        val isSubOnly = missingItems.firstOrNull()?.preferences?.run { skipDownload && downloadSubtitle } == true
        missingItems.forEach { item ->
            val baseTitle = item.title
            val itemUrl = item.url.ifEmpty { item.playlistUrl }
            val viewState = Task.ViewState(
                url = itemUrl,
                title = if (isSubOnly) "[Subtitle] $baseTitle" else baseTitle,
                duration = 0,
                uploader = item.playlistTitle,
                thumbnailUrl = null,
                isSubOnly = isSubOnly
            )
            val task = Task(
                url = itemUrl,
                preferences = item.preferences,
                type = Task.TypeInfo.Playlist(
                    index = item.index,
                    playlistTitle = item.playlistTitle,
                    playlistUrl = item.playlistUrl
                )
            )
            val state = Task.State(
                downloadState = Task.DownloadState.Idle,
                videoInfo = null,
                viewState = viewState
            )
            downloader.enqueue(TaskFactory.TaskWithState(task, state))
        }
    }

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

        val allCandidateFiles = mutableListOf<File>()
        if (targetDirFile.exists()) {
            allCandidateFiles.addAll(targetDirFile.walkTopDown().filter { it.isFile && it.length() > 0L })
        }
        val baseDirFile = File(baseDir)
        if (baseDirFile.exists() && baseDirFile != targetDirFile) {
            allCandidateFiles.addAll(baseDirFile.walkTopDown().filter { it.isFile && it.length() > 0L })
        }

        val missingItems = mutableListOf<VerificationItem>()
        val foundItems = mutableListOf<VerificationItem>()

        for (item in items) {
            val prefixPadded = String.format(java.util.Locale.US, "%03d - ", item.index)
            val cleanTitle = FileUtil.cleanFileName(item.title)
            val shortTitle = if (cleanTitle.length > 6) cleanTitle.take(6) else cleanTitle
            val urlId = if (item.url.contains("v=")) item.url.substringAfter("v=").substringBefore("&") else ""
            
            val matchFound = allCandidateFiles.any { file ->
                val fileName = file.name
                fileName.startsWith(prefixPadded) ||
                    fileName.contains(prefixPadded) ||
                    (cleanTitle.isNotEmpty() && fileName.contains(cleanTitle, ignoreCase = true)) ||
                    (shortTitle.isNotEmpty() && fileName.contains(shortTitle, ignoreCase = true)) ||
                    (urlId.isNotEmpty() && fileName.contains(urlId, ignoreCase = true))
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
                downloadPlaylist = true
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
