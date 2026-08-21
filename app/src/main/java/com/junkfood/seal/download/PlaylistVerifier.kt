package com.junkfood.seal.download

import android.content.Context
import android.util.Log
import com.junkfood.seal.App
import com.junkfood.seal.download.engine.identity.ContentState
import com.junkfood.seal.download.engine.identity.ContentType
import com.junkfood.seal.download.engine.identity.MatchConfidence
import com.junkfood.seal.download.engine.integrity.MissingSummary
import com.junkfood.seal.download.engine.playlist.PlaylistManifest
import com.junkfood.seal.download.engine.playlist.PlaylistManifestItem
import com.junkfood.seal.download.engine.playlist.PlaylistManifestStore
import com.junkfood.seal.download.engine.resilience.FileCollisionResolver
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import com.junkfood.seal.download.engine.subtitle.validation.SubtitleValidator
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.VideoInfo
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
        val matchedFileSize: Long = 0L,
        val videoId: String = "",
        val state: ContentState = ContentState.MISSING,
        val confidence: MatchConfidence = MatchConfidence.UNKNOWN,
    )

    data class ScanResult(
        val playlistTitle: String,
        val targetDirectory: String,
        val totalCount: Int,
        val foundItems: List<VerificationItem>,
        val missingItems: List<VerificationItem>,
        val summary: MissingSummary = MissingSummary(
            expected = totalCount,
            found = foundItems.size,
            missing = missingItems.size,
            ambiguous = 0,
            invalid = 0,
            duplicate = 0,
            stale = 0,
            unavailable = 0,
        ),
        val ambiguousItems: List<VerificationItem> = emptyList(),
        val invalidItems: List<VerificationItem> = emptyList(),
    )

    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "mka", "m4b")
    private val SUBTITLE_EXTS = setOf("srt", "vtt", "ass", "lrc", "sub", "sbv", "ttml")

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
            val cleanUrl = com.junkfood.seal.util.findURLsFromString(playlistUrl, firstMatchOnly = true).firstOrNull()
                ?: playlistUrl.trim().removeSurrounding("'", "'").removeSurrounding("\"", "\"")

            val infoResult = DownloadUtil.getPlaylistOrVideoInfo(
                playlistURL = cleanUrl,
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

            // Collect target candidate search folders with strict scoping
            val candidateDirs = mutableSetOf<File>()

            if (!customDirectoryPath.isNullOrBlank()) {
                val customDir = File(customDirectoryPath.trim())
                if (customDir.exists() && customDir.isDirectory) {
                    candidateDirs.add(customDir)
                    customDir.listFiles()?.filter { it.isDirectory }?.forEach { candidateDirs.add(it) }
                }
            } else if (preferences.commandDirectory.isNotBlank()) {
                val prefDir = File(preferences.commandDirectory.trim())
                if (prefDir.exists() && prefDir.isDirectory) {
                    candidateDirs.add(prefDir)
                    prefDir.listFiles()?.filter { it.isDirectory }?.forEach { candidateDirs.add(it) }
                }
            } else {
                // Determine targeted playlist folder without scanning unrelated global folders
                val relevantRoots = listOfNotNull(
                    defaultBaseDir,
                    File(App.videoDownloadDir),
                    File(App.audioDownloadDir),
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "V-Downloader"),
                    File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Seal"),
                ).filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }

                relevantRoots.forEach { root ->
                    if (isSubtitleOnly) {
                        val subDirs = listOf(
                            File(root, "[Subtitles] $cleanPlaylistName"),
                            File(root, "Subtitles/$cleanPlaylistName"),
                            File(root, "subtitles_$cleanPlaylistName"),
                            File(root, cleanPlaylistName),
                        )
                        subDirs.filter { it.exists() && it.isDirectory }.forEach { candidateDirs.add(it) }
                    } else if (isAudioOnly) {
                        val audioDirs = listOf(
                            File(root, "[Audio] $cleanPlaylistName"),
                            File(root, "Audio/$cleanPlaylistName"),
                            File(root, cleanPlaylistName),
                        )
                        audioDirs.filter { it.exists() && it.isDirectory }.forEach { candidateDirs.add(it) }
                    } else {
                        val videoDirs = listOf(
                            File(root, cleanPlaylistName),
                            File(root, "[Video] $cleanPlaylistName"),
                        )
                        videoDirs.filter { it.exists() && it.isDirectory }.forEach { candidateDirs.add(it) }
                    }
                }

                if (candidateDirs.isEmpty()) {
                    candidateDirs.add(defaultBaseDir)
                }
            }

            // Gather candidate files strictly matching the requested content type
            val minFileSize = if (isSubtitleOnly) 10L else 1024L
            val maxScanDepth = 2
            val allCandidateFiles = candidateDirs.flatMap { dir ->
                if (!dir.exists() || !dir.isDirectory) return@flatMap emptyList<File>()
                try {
                    dir.walkTopDown()
                        .maxDepth(maxScanDepth)
                        .filter { file ->
                            if (!file.isFile || file.length() < minFileSize) return@filter false
                            val ext = file.extension.lowercase(Locale.US)
                            if (file.name.endsWith(".part", ignoreCase = true) ||
                                file.name.endsWith(".ytdl", ignoreCase = true) ||
                                file.name.endsWith(".tmp", ignoreCase = true) ||
                                file.name.endsWith(".temp", ignoreCase = true)) {
                                return@filter false
                            }

                            // Strict extension checking by content type
                            if (isSubtitleOnly) {
                                ext in SUBTITLE_EXTS
                            } else if (isAudioOnly) {
                                ext in AUDIO_EXTS
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

            val availableFiles = allCandidateFiles.toMutableList()
            val foundItems = mutableListOf<VerificationItem>()
            val missingItems = mutableListOf<VerificationItem>()

            // High-precision 1-to-1 matching engine
            for ((indexZero, entry) in entries.withIndex()) {
                val index = indexZero + 1
                val entryTitle = entry.title ?: "Track $index"
                val rawUrl = entry.url.orEmpty()
                val itemUrl = when {
                    rawUrl.startsWith("http", ignoreCase = true) -> rawUrl
                    !entry.id.isNullOrEmpty() -> "https://www.youtube.com/watch?v=${entry.id}"
                    else -> ""
                }
                val videoId = entry.id.orEmpty().ifBlank {
                    FileCollisionResolver.extractVideoId(itemUrl, fallbackId = "")
                }

                val normalizedTitle = normalizeText(entryTitle)
                val titleTokens = normalizedTitle.split(" ").filter { it.length >= 2 && it !in STOP_WORDS }

                val formattedIndex1 = index.toString()
                val formattedIndex2 = String.format(Locale.US, "%02d", index)
                val formattedIndex3 = String.format(Locale.US, "%03d", index)

                var matchedFile: File? = null

                // Search through available local files
                for (file in availableFiles) {
                    val fileName = file.name
                    val cleanedFileName = cleanFileNameForMatching(fileName)
                    val normalizedFileName = normalizeText(cleanedFileName)

                    // Strategy 1: Video ID Match (Highest confidence)
                    if (videoId.isNotEmpty()) {
                        val idMatch = fileName.contains(videoId, ignoreCase = true) ||
                                fileName.contains("[$videoId]", ignoreCase = true) ||
                                fileName.contains("_$videoId", ignoreCase = true) ||
                                fileName.contains("-$videoId", ignoreCase = true) ||
                                normalizedFileName.contains(videoId.lowercase(Locale.US))

                        if (idMatch) {
                            matchedFile = file
                            break
                        }
                    }

                    // Strategy 2: Exact Normalized Title Match or High Levenshtein Similarity (>= 0.75)
                    if (normalizedTitle.isNotBlank()) {
                        if (normalizedFileName == normalizedTitle ||
                            normalizedFileName.contains(normalizedTitle) ||
                            (normalizedFileName.length >= 8 && normalizedTitle.contains(normalizedFileName)) ||
                            calculateLevenshteinSimilarity(normalizedFileName, normalizedTitle) >= 0.75
                        ) {
                            matchedFile = file
                            break
                        }
                    }

                    // Strategy 3: Prefix & Bracketed Index Match (001 - Title, 01 - Title, #01 Title, [001] Title, etc.)
                    val startsWithIndex = fileName.startsWith("$formattedIndex3 - ") ||
                            fileName.startsWith("$formattedIndex3. ") ||
                            fileName.startsWith("${formattedIndex3}_") ||
                            fileName.startsWith("$formattedIndex3 ") ||
                            fileName.startsWith("$formattedIndex3-") ||
                            fileName.startsWith("$formattedIndex2 - ") ||
                            fileName.startsWith("$formattedIndex2. ") ||
                            fileName.startsWith("${formattedIndex2}_") ||
                            fileName.startsWith("$formattedIndex2 ") ||
                            fileName.startsWith("$formattedIndex2-") ||
                            fileName.startsWith("[$formattedIndex3]") ||
                            fileName.startsWith("($formattedIndex3)") ||
                            fileName.startsWith("[$formattedIndex2]") ||
                            fileName.startsWith("($formattedIndex2)") ||
                            fileName.startsWith("#$formattedIndex1 ") ||
                            fileName.startsWith("#$formattedIndex2 ") ||
                            fileName.startsWith("#$formattedIndex3 ") ||
                            Regex("""(?:^|[\[\(\_\-\s#])$formattedIndex3(?:[\s\-\_\.\]\)]|$)""").containsMatchIn(fileName) ||
                            Regex("""(?:^|[\[\(\_\-\s#])$formattedIndex2(?:[\s\-\_\.\]\)]|$)""").containsMatchIn(fileName) ||
                            (index < 10 && Regex("""(?:^|[\[\(\_\-\s#])$formattedIndex1(?:[\s\-\_\.\]\)]|$)""").containsMatchIn(fileName))

                    if (startsWithIndex) {
                        if (titleTokens.isNotEmpty()) {
                            val matchedCount = titleTokens.count { token -> normalizedFileName.contains(token) }
                            val requiredCount = (titleTokens.size * 0.3).toInt().coerceAtLeast(1)
                            if (matchedCount >= requiredCount || normalizedFileName.contains(normalizedTitle.take(15))) {
                                matchedFile = file
                                break
                            }
                        } else {
                            matchedFile = file
                            break
                        }
                    }

                    // Strategy 4: High Significant Token Overlap (>= 70% significant words match and file length is comparable)
                    if (titleTokens.size >= 3) {
                        val matchedTokenCount = titleTokens.count { token -> normalizedFileName.contains(token) }
                        val ratio = matchedTokenCount.toDouble() / titleTokens.size.toDouble()
                        if (ratio >= 0.70 && normalizedFileName.length >= normalizedTitle.length * 0.35) {
                            matchedFile = file
                            break
                        }
                    }
                }

                // Subtitle-specific sanity check
                if (matchedFile != null && isSubtitleOnly) {
                    val isValidSub = SubtitleValidator.validateFile(matchedFile).isSuccess
                    if (!isValidSub && matchedFile.length() < 10L) {
                        Log.w(TAG, "Rejecting corrupt/empty subtitle file on disk: ${matchedFile.absolutePath}")
                        matchedFile = null
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
                            matchedFileSize = matchedFile.length(),
                            videoId = videoId,
                            state = ContentState.VALID,
                            confidence = MatchConfidence.HIGH,
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
                            preferences = preferences,
                            videoId = videoId,
                            state = ContentState.MISSING,
                            confidence = MatchConfidence.UNKNOWN,
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

            // Persist manifest for quick lookup
            val playlistId = videoIdOrPlaylistId(playlistInfo.webpageUrl ?: cleanUrl)
            PlaylistManifestStore.writeAtomic(
                targetDir,
                PlaylistManifest(
                    playlistId = playlistId,
                    title = playlistTitle,
                    canonicalUrl = cleanUrl,
                    totalItems = entries.size,
                    orderedItems = (foundItems + missingItems).sortedBy { it.index }.map { item ->
                        PlaylistManifestItem(
                            index = item.index,
                            videoId = item.videoId,
                            title = item.title,
                            canonicalUrl = item.url,
                            state = item.state,
                            contentType = when {
                                isSubtitleOnly -> ContentType.SUBTITLE
                                isAudioOnly -> ContentType.AUDIO
                                else -> ContentType.VIDEO
                            },
                            localPath = item.matchedFilePath,
                            language = primarySubtitleLanguage(preferences.subtitleLanguage),
                            format = subtitleOutputFormat(preferences.convertSubtitle).extension,
                        )
                    },
                ),
            )

            ScanResult(
                playlistTitle = playlistTitle,
                targetDirectory = targetDir.absolutePath,
                totalCount = entries.size,
                foundItems = foundItems,
                missingItems = missingItems,
                summary = MissingSummary(
                    expected = entries.size,
                    found = foundItems.size,
                    missing = missingItems.size,
                    ambiguous = 0,
                    invalid = 0,
                    duplicate = 0,
                    stale = 0,
                    unavailable = 0,
                ),
                ambiguousItems = emptyList(),
                invalidItems = emptyList(),
            )
        }
    }

    private fun videoIdOrPlaylistId(url: String): String {
        val listMatch = Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(url)
        if (listMatch != null) return listMatch.groupValues[1]
        return url.hashCode().toString()
    }

    private fun primarySubtitleLanguage(raw: String): String =
        raw.split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.equals("all", ignoreCase = true) }
            ?: "ar"

    private fun subtitleOutputFormat(convertSubtitle: Int): SubtitleOutputFormat =
        when (convertSubtitle) {
            com.junkfood.seal.util.CONVERT_ASS -> SubtitleOutputFormat.ASS
            com.junkfood.seal.util.CONVERT_LRC -> SubtitleOutputFormat.LRC
            com.junkfood.seal.util.CONVERT_VTT -> SubtitleOutputFormat.VTT
            else -> SubtitleOutputFormat.SRT
        }

    /**
     * Cleans metadata, resolution tags, language suffixes and common descriptors from file name before comparison.
     */
    fun cleanFileNameForMatching(fileName: String): String {
        var name = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        // Strip subtitle language codes e.g. .ar, .en, .ar-en, .ar-en-US, .ar-orig, .auto, .synced
        name = name.replace(Regex("""\.(?:[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)*|auto|orig|synced)$""", RegexOption.IGNORE_CASE), "")
        // Strip resolution tags (e.g. [1080p], (720p), .1080p, [4k], [HD], (1080p))
        name = name.replace(Regex("""[\.\[\(]\s*(?:\d{3,4}p|4k|2k|8k|hd|fhd|uhd)\s*[\.\]\)]""", RegexOption.IGNORE_CASE), "")
        // Strip common descriptive tags like (Official Video), [Lyrics], etc.
        name = name.replace(Regex("""[\(\[]\s*(?:official(?:\s+music)?\s+video|official\s+audio|audio|lyrics|music\s+video|مترجم|ترجمة|فيديو\s+كليب|كليب\s+رسمي|حصريا|حصرياً|كامل)\s*[\)\]]""", RegexOption.IGNORE_CASE), "")
        return name.trim()
    }

    /**
     * Advanced Arabic, Persian, Urdu & multilingual text normalizer:
     * - Normalizes Arabic Alef variants (أ, إ, آ, ٱ -> ا)
     * - Normalizes Taa Marbuta (ة, ۀ -> ه)
     * - Normalizes Yaa / Alef Maksura (ى, ي, ی -> ي)
     * - Normalizes Kaf (ك, ک -> ك)
     * - Normalizes Waw / Yaa with Hamza (ؤ -> و, ئ -> ي)
     * - Strips Harakat / Tashkeel, Shadda, Sukun & Tatweel
     * - Lowercases and removes Arabic & Latin punctuation
     */
    fun normalizeText(text: String): String {
        val arabicNormalized = text
            .replace(Regex("[أإآٱ]"), "ا")
            .replace(Regex("[ةۀ]"), "ه")
            .replace(Regex("[ىيی]"), "ي")
            .replace(Regex("[كک]"), "ك")
            .replace(Regex("[ؤ]"), "و")
            .replace(Regex("[ئ]"), "ي")
            .replace(Regex("[\u064B-\u0652\u0670\u0640]"), "") // Tashkeel & Tatweel

        return arabicNormalized.lowercase(Locale.US)
            .replace(Regex("[\\p{Punct}\\s\\u060C\\u061B\\u061F\\u066A\\u066B\\u066C«»“”‘’–—…]+"), " ")
            .trim()
    }

    /**
     * Calculates normalized Levenshtein similarity ratio between 0.0 and 1.0.
     */
    fun calculateLevenshteinSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        val maxLen = maxOf(len1, len2)
        return 1.0 - (dp[len1][len2].toDouble() / maxLen)
    }

    /**
     * Enqueues verified missing items into the download queue.
     */
    suspend fun enqueueMissingItems(
        missingItems: List<VerificationItem>,
        targetDirectory: String = "",
        downloader: DownloaderV2
    ) = withContext(Dispatchers.Default) {
        if (missingItems.isEmpty()) return@withContext
        val isSubOnly = missingItems.firstOrNull()?.preferences?.run { skipDownload && downloadSubtitle } == true
        val isAudioOnly = missingItems.firstOrNull()?.preferences?.run { extractAudio && !skipDownload } == true

        missingItems.forEach { item ->
            val baseTitle = item.title
            val itemUrl = item.url.ifEmpty { item.playlistUrl }

            val itemPrefs = item.preferences.copy(
                downloadPlaylist = false,
                playlistNumbering = true,
                useDownloadArchive = false,
                skipDownload = isSubOnly,
                downloadSubtitle = if (isSubOnly) true else item.preferences.downloadSubtitle,
                autoSubtitle = if (isSubOnly) true else item.preferences.autoSubtitle,
                autoTranslatedSubtitles = if (isSubOnly) true else item.preferences.autoTranslatedSubtitles,
                extractAudio = if (isSubOnly) false else isAudioOnly,
                commandDirectory = targetDirectory.ifBlank { item.preferences.commandDirectory },
                subdirectoryPlaylistTitle = false,
            )

            val formattedIndex = String.format(Locale.US, "%03d", item.index)
            val numberedTitle = "$formattedIndex - $baseTitle"
            val effectiveTitle = if (isSubOnly) "[Subtitle] $numberedTitle" else numberedTitle

            val viewState = Task.ViewState(
                url = itemUrl,
                title = effectiveTitle,
                duration = 0,
                uploader = item.playlistTitle,
                thumbnailUrl = null,
                isSubOnly = isSubOnly
            )
            val videoInfo = VideoInfo(
                id = FileCollisionResolver.extractVideoId(itemUrl, fallbackId = "item_${item.index}"),
                title = numberedTitle,
                webpageUrl = itemUrl,
                originalUrl = itemUrl,
                uploader = item.playlistTitle,
                extractor = "Youtube",
                extractorKey = "Youtube"
            )
            val task = Task(
                url = itemUrl,
                preferences = itemPrefs,
                type = Task.TypeInfo.Playlist(
                    index = item.index,
                    playlistTitle = item.playlistTitle,
                    playlistUrl = item.playlistUrl,
                    isFallback = true
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
