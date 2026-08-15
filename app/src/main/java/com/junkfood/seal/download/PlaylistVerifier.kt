package com.junkfood.seal.download

import android.content.Context
import android.util.Log
import com.junkfood.seal.App
import com.junkfood.seal.App.Companion.context
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
        val preferences: DownloadPreferences
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
            val isAudioOnly = preferences.extractAudio
            val playlistTitle = playlistInfo.title ?: "Playlist"
            val cleanPlaylistName = FileUtil.cleanFileName(playlistTitle)
            val defaultBaseDir = if (isAudioOnly) File(App.audioDownloadDir) else File(App.videoDownloadDir)

            // Collect all potential storage roots
            val searchRoots = mutableListOf<File>()

            // 1. User preferences & standard dirs
            val configuredCustomDir = COMMAND_DIRECTORY.getString().trim().takeIf { it.isNotBlank() }?.let { File(it) }
            if (configuredCustomDir != null && configuredCustomDir.exists()) searchRoots.add(configuredCustomDir)

            val customDir = customDirectoryPath?.trim()?.takeIf { it.isNotBlank() }?.let { File(it) }
            if (customDir != null && customDir.exists()) searchRoots.add(customDir)

            searchRoots.addAll(
                listOfNotNull(
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
                    android.os.Environment.getExternalStorageDirectory(),
                )
            )

            val uniqueRoots = searchRoots.filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }

            // Discover candidate directories (direct playlist folder, subtitle folder, audio folder)
            val candidateDirs = mutableSetOf<File>()
            val playlistTokens = normalizeText(cleanPlaylistName).split(" ").filter { it.length >= 2 }

            uniqueRoots.forEach { root ->
                candidateDirs.add(root)
                if (cleanPlaylistName.isNotEmpty()) {
                    candidateDirs.add(File(root, cleanPlaylistName))
                    candidateDirs.add(File(root, "[Subtitles] $cleanPlaylistName"))
                    candidateDirs.add(File(root, "[Subtitle] $cleanPlaylistName"))
                    candidateDirs.add(File(root, "[Audio] $cleanPlaylistName"))
                    candidateDirs.add(File(root, "Audio/$cleanPlaylistName"))
                    candidateDirs.add(File(root, "Audio/[Subtitles] $cleanPlaylistName"))
                }
            }

            // Deep search candidate directories using token matching
            uniqueRoots.forEach { root ->
                try {
                    root.walkTopDown().maxDepth(4).filter { it.isDirectory }.forEach { dir ->
                        val cleanDirName = dir.name.replace(Regex("^\\[?(?:subtitles?|audio|videos?)\\]?\\s*", RegexOption.IGNORE_CASE), "")
                        val dirNorm = normalizeText(cleanDirName)
                        val matchesPlaylist = playlistTokens.isNotEmpty() && playlistTokens.count { dirNorm.contains(it) } >= (playlistTokens.size * 0.4).toInt().coerceAtLeast(1)
                        if (matchesPlaylist || dirNorm.contains(normalizeText(cleanPlaylistName)) || normalizeText(cleanPlaylistName).contains(dirNorm)) {
                            candidateDirs.add(dir)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error discovering subdirectories in: ${root.absolutePath}", e)
                }
            }

            // Gather all candidate media and subtitle files
            val minFileSize = if (isSubtitleOnly) 10L else 512L
            val allCandidateFiles = candidateDirs.flatMap { dir ->
                if (!dir.exists() || !dir.isDirectory) return@flatMap emptyList<File>()
                try {
                    dir.walkTopDown()
                        .maxDepth(4)
                        .filter { file ->
                            file.isFile &&
                            !file.name.endsWith(".part", ignoreCase = true) &&
                            !file.name.endsWith(".ytdl", ignoreCase = true) &&
                            !file.name.endsWith(".tmp", ignoreCase = true) &&
                            file.length() > minFileSize
                        }
                        .toList()
                } catch (e: Exception) {
                    emptyList()
                }
            }.distinctBy { it.absolutePath }

            Log.d(TAG, "scanPlaylist: found ${allCandidateFiles.size} candidate files across ${candidateDirs.size} folders")

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

                val rawTitleClean = entryTitle
                    .removePrefix("[Subtitle] ")
                    .replace(Regex("^#\\d+\\s*"), "")
                    .replace(Regex("^\\d{1,4}\\s*[-_.]\\s*"), "")
                    .trim()

                val normalizedTitle = normalizeText(rawTitleClean)
                val titleTokens = normalizedTitle.split(" ").filter { it.length >= 2 }
                val significantTokens = titleTokens.filter { it.length >= 2 && it !in STOP_WORDS }

                val matchFound = allCandidateFiles.any { file ->
                    val ext = file.extension.lowercase(Locale.US)
                    val isValidType = if (isSubtitleOnly) {
                        ext in SUBTITLE_EXTS
                    } else if (isAudioOnly) {
                        ext in AUDIO_EXTS || ext in VIDEO_EXTS
                    } else {
                        ext in VIDEO_EXTS || ext in AUDIO_EXTS
                    }
                    if (!isValidType) return@any false

                    val fileName = file.name
                    val cleanName = cleanFileNameForMatching(fileName)
                    val normalizedFileName = normalizeText(cleanName)

                    var isMatched = false

                    // Strategy 1: Video ID Match
                    if (extractedId.length >= 4 && (fileName.contains(extractedId, ignoreCase = true) || normalizedFileName.contains(extractedId.lowercase(Locale.US)))) {
                        isMatched = true
                    }

                    // Strategy 2: Bracketed ID in filename e.g. [a1B2c3D4]
                    if (!isMatched && extractedId.length >= 4) {
                        val idInBrackets = Regex("\\[([a-zA-Z0-9_-]{8,15})\\]").find(fileName)?.groupValues?.get(1)
                        if (idInBrackets != null && idInBrackets.equals(extractedId, ignoreCase = true)) {
                            isMatched = true
                        }
                    }

                    // Strategy 3: Numeric Playlist Index Match with Title Token Confirmation
                    if (!isMatched && index > 0) {
                        val formattedIndex3 = String.format(Locale.US, "%03d", index)
                        val formattedIndex2 = String.format(Locale.US, "%02d", index)
                        val indexMatches = fileName.startsWith(formattedIndex3) ||
                                fileName.startsWith(formattedIndex2) ||
                                fileName.contains(formattedIndex3) ||
                                fileName.contains(formattedIndex2) ||
                                Regex("(?:^|[\\[\\(\\_\\-\\s#])0*${index}(?:[\\s\\-\\_\\.\\]\\)]|$)").containsMatchIn(fileName)

                        if (indexMatches) {
                            val tokensToCheck = if (significantTokens.isNotEmpty()) significantTokens else titleTokens
                            if (tokensToCheck.isNotEmpty()) {
                                val matchCount = tokensToCheck.count { normalizedFileName.contains(it) }
                                if (matchCount >= 1) {
                                    isMatched = true
                                }
                            } else {
                                isMatched = true
                            }
                        }
                    }

                    // Strategy 4: Normalized Substring Title Match
                    if (!isMatched && normalizedTitle.length >= 4) {
                        if (normalizedFileName.contains(normalizedTitle) || normalizedTitle.contains(normalizedFileName)) {
                            isMatched = true
                        }
                    }

                    // Strategy 5: Fuzzy Word Token Match (>= 40% significant words match)
                    if (!isMatched) {
                        val tokensToCheck = if (significantTokens.isNotEmpty()) significantTokens else titleTokens
                        if (tokensToCheck.isNotEmpty()) {
                            val matchedTokenCount = tokensToCheck.count { token -> normalizedFileName.contains(token) }
                            val required = (tokensToCheck.size * 0.4).toInt().coerceAtLeast(1)
                            if (matchedTokenCount >= required) {
                                isMatched = true
                            }
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
        // Strip auto-translated subtitle suffixes like .ar-en, .ar-orig, .ar
        name = name.replace(Regex("""\.(?:[a-zA-Z0-9_\-]+)$"""), "")
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
