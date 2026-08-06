package com.junkfood.seal.download

import android.content.Context
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.FileUtil.audioDownloadDir
import com.junkfood.seal.util.FileUtil.videoDownloadDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object PlaylistVerifier {

    data class VerificationItem(
        val index: Int,
        val title: String,
        val url: String,
        val playlistUrl: String,
        val playlistTitle: String,
        val preferences: DownloadPreferences
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
        preferences: DownloadPreferences,
        customDirectoryPath: String? = null
    ): Result<ScanResult> = withContext(Dispatchers.IO) {
        runCatching {
            val infoResult = DownloadUtil.getPlaylistOrVideoInfo(
                playlistURL = playlistUrl,
                downloadPreferences = preferences,
            )
            val info = infoResult.getOrThrow()
            val playlistInfo = info as? com.junkfood.seal.util.PlaylistResult
                ?: throw IllegalStateException("الرابط المرفق ليس قائمة تشغيل صالحة")
            val entries = playlistInfo.entries ?: emptyList()
            if (entries.isEmpty()) {
                throw IllegalStateException("قائمة التشغيل فارغة أو لا تحتوي على عناصر متاحة")
            }

            val isSubtitleOnly = preferences.skipDownload && preferences.downloadSubtitle
            val isAudioOnly = preferences.extractAudio
            val playlistTitle = playlistInfo.title ?: "Playlist"
            val cleanPlaylistName = FileUtil.cleanFileName(playlistTitle)

            val defaultBaseDir = if (isAudioOnly) {
                if (preferences.privateDirectory) context.filesDir.absolutePath else audioDownloadDir
            } else {
                if (preferences.privateDirectory) context.filesDir.absolutePath else videoDownloadDir
            }

            val baseDir = if (!customDirectoryPath.isNullOrBlank() && File(customDirectoryPath).exists()) {
                customDirectoryPath
            } else {
                defaultBaseDir
            }

            val mainTargetDir = if (isSubtitleOnly && cleanPlaylistName.isNotEmpty()) {
                File(baseDir, "[Subtitles] $cleanPlaylistName")
            } else if (preferences.subdirectoryPlaylistTitle && cleanPlaylistName.isNotEmpty()) {
                File(baseDir, cleanPlaylistName)
            } else {
                File(baseDir)
            }

            // Gather candidate directories
            val candidateDirs = mutableSetOf<File>()
            candidateDirs.add(mainTargetDir)
            candidateDirs.add(File(baseDir))

            if (cleanPlaylistName.isNotEmpty()) {
                candidateDirs.add(File(baseDir, "[Subtitles] $cleanPlaylistName"))
                candidateDirs.add(File(baseDir, cleanPlaylistName))
            }

            // Also search parent directory of baseDir if customized
            File(baseDir).parentFile?.let { parent ->
                if (parent.exists()) candidateDirs.add(parent)
            }

            // Gather all files recursively (up to 3 subfolder levels deep)
            val allCandidateFiles = candidateDirs.flatMap { dir ->
                if (!dir.exists()) return@flatMap emptyList<File>()
                dir.walkTopDown()
                    .maxDepth(3)
                    .filter { file ->
                        file.isFile &&
                        !file.name.endsWith(".part", ignoreCase = true) &&
                        !file.name.endsWith(".ytdl", ignoreCase = true) &&
                        !file.name.endsWith(".tmp", ignoreCase = true) &&
                        file.length() > (if (isSubtitleOnly) 30L else 1024L)
                    }
                    .toList()
            }.distinctBy { it.absolutePath }

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

                val rawTitleClean = entryTitle.removePrefix("[Subtitle] ").trim()
                val normalizedTitle = normalizeText(rawTitleClean)
                val titleTokens = normalizedTitle.split(" ").filter { it.length >= 3 }

                val indexPatterns = listOf(
                    Regex("^(?:0*${index}[ \\-\\_\\.\\]]|0*${index}\\$)"),
                    Regex("(?:^|[\\[\\(\\_\\-\\s])0*${index}(?:[\\s\\-\\_\\.\\]\\)]|$)")
                )

                val matchFound = allCandidateFiles.any { file ->
                    val fileName = file.name
                    val fileNameWithoutExt = file.nameWithoutExtension
                    val normalizedFileName = normalizeText(fileNameWithoutExt)

                    // Step 1: Type Validation
                    val ext = file.extension.lowercase(Locale.US)
                    val isValidType = if (isSubtitleOnly) {
                        ext in listOf("srt", "vtt", "ass", "lrc")
                    } else if (isAudioOnly) {
                        ext in listOf("mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "mka", "mp4", "mkv", "webm")
                    } else {
                        ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts")
                    }
                    if (!isValidType) return@any false

                    var isMatched = false

                    // Strategy 1: Video ID Match
                    if (extractedId.length >= 4 && (fileName.contains(extractedId, ignoreCase = true) || normalizedFileName.contains(extractedId.lowercase(Locale.US)))) {
                        isMatched = true
                    }

                    // Strategy 2: Numeric Playlist Index Match
                    if (!isMatched && index > 0) {
                        if (indexPatterns.any { it.containsMatchIn(fileName) }) {
                            isMatched = true
                        }
                    }

                    // Strategy 3: Normalized Substring Title Match
                    if (!isMatched && normalizedTitle.length >= 4) {
                        if (normalizedFileName.contains(normalizedTitle) || normalizedTitle.contains(normalizedFileName)) {
                            isMatched = true
                        }
                    }

                    // Strategy 4: Significant Word Token Match (>= 75% words match)
                    if (!isMatched && titleTokens.isNotEmpty()) {
                        val matchedTokenCount = titleTokens.count { token -> normalizedFileName.contains(token) }
                        if (matchedTokenCount >= (titleTokens.size * 0.75).toInt().coerceAtLeast(1)) {
                            isMatched = true
                        }
                    }

                    isMatched
                }

                if (matchFound) {
                    foundItems.add(item)
                } else {
                    missingItems.add(item)
                }
            }

            ScanResult(
                playlistTitle = playlistTitle,
                targetDirectory = mainTargetDir.absolutePath,
                totalCount = entries.size,
                foundItems = foundItems,
                missingItems = missingItems
            )
        }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase(Locale.US)
            .replace(Regex("[\\p{Punct}\\s\\u064B-\\u0652]+"), " ")
            .trim()
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
}
