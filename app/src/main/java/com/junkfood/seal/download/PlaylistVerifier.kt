package com.junkfood.seal.download

import android.content.Context
import android.util.Log
import com.junkfood.seal.App
import com.junkfood.seal.util.COMMAND_DIRECTORY
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.PreferenceUtil.getString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object PlaylistVerifier {

    private const val TAG = "PlaylistVerifier"

    data class VerificationItem(
        val index: Int,
        val title: String,
        val url: String,
        val playlistUrl: String,
        val playlistTitle: String,
        val preferences: DownloadPreferences,
        val matchedFilePath: String? = null,
        val matchedFileSize: Long = 0L
    )

    data class ScanResult(
        val playlistTitle: String,
        val targetDirectory: String,
        val totalCount: Int,
        val foundItems: List<VerificationItem>,
        val missingItems: List<VerificationItem>,
    )

    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "mka", "m4b")
    private val SUBTITLE_EXTS = setOf("srt", "vtt", "ass", "lrc", "sub", "sbv")

    private val STOP_WORDS = setOf(
        "the", "and", "or", "for", "in", "on", "at", "to", "a", "an", "is", "of", "with",
        "this", "that", "from", "by", "video", "audio", "hd", "mp4", "m4a", "ep", "part",
        "vol", "ch", "chapter", "episode", "full", "official", "arabic", "english", "course",
        "tutorial", "lesson", "free", "hq", "1080p", "720p", "4k", "2024", "2025", "2026",
        "فيديو", "صوت", "شرح", "درس", "حلقة", "كامل", "الجزء", "دورة", "كورس", "مترجم", "ترجمة"
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
            val isAudioOnly = preferences.extractAudio && !isSubtitleOnly
            val isVideo = !isSubtitleOnly && !isAudioOnly

            val playlistTitle = playlistInfo.title ?: "Playlist"
            val cleanPlaylistName = FileUtil.cleanFileName(playlistTitle)
            val defaultBaseDir = if (isAudioOnly) File(App.audioDownloadDir) else File(App.videoDownloadDir)

            // Collect target candidate search folders
            val candidateDirs = mutableSetOf<File>()

            if (!customDirectoryPath.isNullOrBlank()) {
                val customDir = File(customDirectoryPath.trim())
                if (customDir.exists() && customDir.isDirectory) {
                    candidateDirs.add(customDir)
                    // Also include immediate subfolders
                    customDir.listFiles()?.filter { it.isDirectory }?.forEach { candidateDirs.add(it) }
                }
            }

            // Standard folders if no custom or custom empty
            if (candidateDirs.isEmpty()) {
                val baseRoots = listOfNotNull(
                    File(App.videoDownloadDir),
                    File(App.audioDownloadDir),
                    File(App.videoDownloadDir, "Audio"),
                    File(App.audioDownloadDir, "Audio"),
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "V-Downloader"),
                    File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Seal"),
                    File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "V-Downloader/Audio"),
                    File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Seal/Audio"),
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                ).filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }

                // Check for dedicated playlist subfolder in roots first
                if (cleanPlaylistName.isNotBlank()) {
                    baseRoots.forEach { root ->
                        val subPlaylistFolder = File(root, cleanPlaylistName)
                        if (subPlaylistFolder.exists() && subPlaylistFolder.isDirectory) {
                            candidateDirs.add(subPlaylistFolder)
                        }
                        val subTitleFolder = File(root, "[Subtitles] $cleanPlaylistName")
                        if (subTitleFolder.exists() && subTitleFolder.isDirectory) {
                            candidateDirs.add(subTitleFolder)
                        }
                        val subAudioFolder = File(root, "[Audio] $cleanPlaylistName")
                        if (subAudioFolder.exists() && subAudioFolder.isDirectory) {
                            candidateDirs.add(subAudioFolder)
                        }
                    }
                }

                // If no specific playlist folder exists, add baseRoots
                if (candidateDirs.isEmpty()) {
                    candidateDirs.addAll(baseRoots)
                }
            }

            // Gather candidate files strictly matching the requested content type
            val minFileSize = if (isSubtitleOnly) 10L else 1024L
            val allCandidateFiles = candidateDirs.flatMap { dir ->
                if (!dir.exists() || !dir.isDirectory) return@flatMap emptyList<File>()
                try {
                    dir.walkTopDown()
                        .maxDepth(3)
                        .filter { file ->
                            if (!file.isFile || file.length() < minFileSize) return@filter false
                            val ext = file.extension.lowercase(Locale.US)
                            if (file.name.endsWith(".part", ignoreCase = true) ||
                                file.name.endsWith(".ytdl", ignoreCase = true) ||
                                file.name.endsWith(".tmp", ignoreCase = true)) {
                                return@filter false
                            }

                            // Strict extension checking by content type
                            if (isSubtitleOnly) {
                                ext in SUBTITLE_EXTS
                            } else if (isAudioOnly) {
                                ext in AUDIO_EXTS || ext in VIDEO_EXTS
                            } else {
                                ext in VIDEO_EXTS
                            }
                        }
                        .toList()
                } catch (e: Exception) {
                    emptyList()
                }
            }.distinctBy { it.absolutePath }

            Log.d(TAG, "scanPlaylist: found ${allCandidateFiles.size} candidate files for mode (subOnly=$isSubtitleOnly, audioOnly=$isAudioOnly, video=$isVideo)")

            // Mutable pool of available files for 1-to-1 matching
            val availableFiles = allCandidateFiles.toMutableList()

            val foundItems = mutableListOf<VerificationItem>()
            val missingItems = mutableListOf<VerificationItem>()

            // Verification matching loop
            entries.forEachIndexed { indexZero, entry ->
                val index = indexZero + 1
                val entryTitle = entry.title ?: "Track $index"
                val rawUrl = entry.url.orEmpty()
                val itemUrl = when {
                    rawUrl.startsWith("http", ignoreCase = true) -> rawUrl
                    !entry.id.isNullOrEmpty() -> "https://www.youtube.com/watch?v=${entry.id}"
                    else -> ""
                }

                val extractedId = entry.id.orEmpty().ifEmpty {
                    when {
                        itemUrl.contains("v=") -> itemUrl.substringAfter("v=").substringBefore("&").substringBefore("?")
                        itemUrl.contains("youtu.be/") -> itemUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                        else -> ""
                    }
                }

                val rawTitleClean = entryTitle
                    .removePrefix("[Subtitle] ")
                    .replace(Regex("^#\\d+\\s*"), "")
                    .replace(Regex("^\\d{1,4}\\s*[-_.]\\s*"), "")
                    .trim()

                val normalizedTitle = normalizeText(rawTitleClean)
                val titleTokens = normalizedTitle.split(" ").filter { it.length >= 2 }
                val significantTokens = titleTokens.filter { it.length >= 3 && it !in STOP_WORDS }

                val formattedIndex3 = String.format(Locale.US, "%03d", index)
                val formattedIndex2 = String.format(Locale.US, "%02d", index)
                val formattedIndex1 = index.toString()

                // Find best matching candidate file in available pool
                var matchedFile: File? = null

                for (file in availableFiles) {
                    val fileName = file.name
                    val cleanName = cleanFileNameForMatching(fileName)
                    val normalizedFileName = normalizeText(cleanName)

                    // Strategy 1: Exact Video ID Match (High Confidence)
                    if (extractedId.length >= 6) {
                        if (fileName.contains(extractedId, ignoreCase = true)) {
                            matchedFile = file
                            break
                        }
                    }

                    // Strategy 2: Exact Full Title Match
                    if (normalizedTitle.length >= 4 && normalizedFileName == normalizedTitle) {
                        matchedFile = file
                        break
                    }

                    // Strategy 3: Formatted Index Match + Title Confirmation
                    val indexMatches = fileName.startsWith(formattedIndex3) ||
                            fileName.startsWith(formattedIndex2) ||
                            fileName.startsWith("#$formattedIndex1") ||
                            fileName.startsWith("#$formattedIndex2") ||
                            fileName.startsWith("#$formattedIndex3") ||
                            Regex("(?:^|[\\[\\(\\_\\-\\s#])$formattedIndex3(?:[\\s\\-\\_\\.\\]\\)]|$)").containsMatchIn(fileName) ||
                            Regex("(?:^|[\\[\\(\\_\\-\\s#])$formattedIndex2(?:[\\s\\-\\_\\.\\]\\)]|$)").containsMatchIn(fileName)

                    if (indexMatches) {
                        val tokensToCheck = if (significantTokens.isNotEmpty()) significantTokens else titleTokens
                        if (tokensToCheck.isNotEmpty()) {
                            val matchedCount = tokensToCheck.count { normalizedFileName.contains(it) }
                            val requiredCount = (tokensToCheck.size * 0.4).toInt().coerceAtLeast(1)
                            if (matchedCount >= requiredCount) {
                                matchedFile = file
                                break
                            }
                        } else {
                            matchedFile = file
                            break
                        }
                    }

                    // Strategy 4: High Significant Token Overlap (>= 70% significant words match and file length is comparable)
                    if (significantTokens.size >= 3) {
                        val matchedTokenCount = significantTokens.count { token -> normalizedFileName.contains(token) }
                        val ratio = matchedTokenCount.toDouble() / significantTokens.size.toDouble()
                        if (ratio >= 0.70 && normalizedFileName.length >= normalizedTitle.length * 0.4) {
                            matchedFile = file
                            break
                        }
                    }
                }

                if (matchedFile != null) {
                    // Consume the matched file so it CANNOT be matched by any other item!
                    availableFiles.remove(matchedFile)

                    foundItems.add(
                        VerificationItem(
                            index = index,
                            title = entryTitle,
                            url = itemUrl,
                            playlistUrl = playlistUrl,
                            playlistTitle = playlistTitle,
                            preferences = preferences,
                            matchedFilePath = matchedFile.absolutePath,
                            matchedFileSize = matchedFile.length()
                        )
                    )
                } else {
                    missingItems.add(
                        VerificationItem(
                            index = index,
                            title = entryTitle,
                            url = itemUrl,
                            playlistUrl = playlistUrl,
                            playlistTitle = playlistTitle,
                            preferences = preferences
                        )
                    )
                }
            }

            // Determine optimal destination directory
            val bestCandidateDir = candidateDirs.filter { it.exists() && it.isDirectory }
                .maxByOrNull { dir ->
                    allCandidateFiles.count { it.parentFile?.absolutePath == dir.absolutePath }
                }

            val targetDir = bestCandidateDir ?: run {
                if (isSubtitleOnly && cleanPlaylistName.isNotEmpty()) {
                    File(defaultBaseDir, "[Subtitles] $cleanPlaylistName")
                } else if (cleanPlaylistName.isNotEmpty()) {
                    File(defaultBaseDir, cleanPlaylistName)
                } else {
                    defaultBaseDir
                }
            }

            ScanResult(
                playlistTitle = playlistTitle,
                targetDirectory = targetDir.absolutePath,
                totalCount = entries.size,
                foundItems = foundItems,
                missingItems = missingItems
            )
        }
    }

    private fun cleanFileNameForMatching(fileName: String): String {
        var name = fileName.substringBeforeLast('.')
        // Strip language codes e.g. .ar, .en, .ar-en, .ar-orig, .auto
        name = name.replace(Regex("""\.(?:[a-zA-Z]{2}(?:-[a-zA-Z]{2,4})*|auto|orig)$""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""[\.\[\(]\d{3,4}p[\.\]\)]""", RegexOption.IGNORE_CASE), "")
        return name
    }

    /**
     * Advanced Arabic and multilingual text normalizer:
     * - Normalizes Arabic Alef variants (أ, إ, آ -> ا)
     * - Normalizes Taa Marbuta (ة -> ه)
     * - Normalizes Yaa / Alef Maksura (ى -> ي)
     * - Strips Harakat / Tashkeel & Tatweel
     * - Lowercases and removes punctuation
     */
    fun normalizeText(text: String): String {
        val arabicNormalized = text
            .replace(Regex("[أإآٱ]"), "ا")
            .replace(Regex("[ة]"), "ه")
            .replace(Regex("[ى]"), "ي")
            .replace(Regex("[\u064B-\u0652\u0670\u0640]"), "") // Tashkeel & Tatweel

        return arabicNormalized.lowercase(Locale.US)
            .replace(Regex("[\\p{Punct}\\s]+"), " ")
            .trim()
    }

    suspend fun enqueueMissingItems(
        missingItems: List<VerificationItem>,
        targetDirectory: String = "",
        downloader: DownloaderV2
    ) = withContext(Dispatchers.Default) {
        val isSubOnly = missingItems.firstOrNull()?.preferences?.run { skipDownload && downloadSubtitle } == true
        missingItems.forEach { item ->
            val baseTitle = item.title
            val itemUrl = item.url.ifEmpty { item.playlistUrl }

            val itemPrefs = if (targetDirectory.isNotBlank()) {
                item.preferences.copy(
                    playlistNumbering = true,
                    commandDirectory = targetDirectory
                )
            } else {
                item.preferences.copy(playlistNumbering = true)
            }

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
                preferences = itemPrefs,
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

    suspend fun verifyAndRetryPlaylist(
        items: List<VerificationItem>
    ) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext

        val first = items.first()
        val playlistUrl = first.playlistUrl
        val preferences = first.preferences

        try {
            val scanResult = scanPlaylist(
                playlistUrl = playlistUrl,
                preferences = preferences
            ).getOrThrow()

            Log.d(
                TAG,
                "verifyAndRetryPlaylist: scan complete — " +
                    "total=${scanResult.totalCount}, " +
                    "found=${scanResult.foundItems.size}, " +
                    "missing=${scanResult.missingItems.size}, " +
                    "dir=${scanResult.targetDirectory}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "verifyAndRetryPlaylist: scan failed for $playlistUrl", e)
        }
    }
}
