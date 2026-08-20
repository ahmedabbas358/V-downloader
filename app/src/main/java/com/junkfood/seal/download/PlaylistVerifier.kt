package com.junkfood.seal.download

import android.content.Context
import android.util.Log
import com.junkfood.seal.App
import com.junkfood.seal.util.COMMAND_DIRECTORY
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.VideoInfo
import com.junkfood.seal.download.engine.identity.ContentRequirement
import com.junkfood.seal.download.engine.identity.ContentState
import com.junkfood.seal.download.engine.identity.ContentType
import com.junkfood.seal.download.engine.identity.MatchConfidence
import com.junkfood.seal.download.engine.identity.SubtitleIdentity
import com.junkfood.seal.download.engine.identity.VideoIdentity
import com.junkfood.seal.download.engine.integrity.ContentIntegrityScanner
import com.junkfood.seal.download.engine.integrity.MissingSummary
import com.junkfood.seal.download.engine.integrity.RequirementResult
import com.junkfood.seal.download.engine.playlist.PlaylistManifest
import com.junkfood.seal.download.engine.playlist.PlaylistManifestItem
import com.junkfood.seal.download.engine.playlist.PlaylistManifestStore
import com.junkfood.seal.download.engine.resilience.FileCollisionResolver
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
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
            val maxScanDepth = if (!customDirectoryPath.isNullOrBlank()) 2 else 2
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
                                file.name.endsWith(".tmp", ignoreCase = true)) {
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

            val playlistId =
                ContentIntegrityScanner.extractPlaylistId(playlistInfo.webpageUrl ?: cleanUrl)
                    .ifBlank { ContentIntegrityScanner.extractPlaylistId(cleanUrl) }
                    .ifBlank { cleanUrl.hashCode().toString() }
            val contentType =
                when {
                    isSubtitleOnly -> ContentType.SUBTITLE
                    isAudioOnly -> ContentType.AUDIO
                    else -> ContentType.VIDEO
                }
            val subtitleLanguage = primarySubtitleLanguage(preferences.subtitleLanguage)
            val subtitleFormat = subtitleOutputFormat(preferences.convertSubtitle)
            val requirements =
                entries.mapIndexed { indexZero, entry ->
                    val index = indexZero + 1
                    val entryTitle = entry.title ?: "Track $index"
                    val rawUrl = entry.url.orEmpty()
                    val itemUrl = when {
                        rawUrl.startsWith("http", ignoreCase = true) -> rawUrl
                        !entry.id.isNullOrEmpty() -> "https://www.youtube.com/watch?v=${entry.id}"
                        else -> ""
                    }
                    val videoId =
                        entry.id.orEmpty()
                            .ifBlank { ContentIntegrityScanner.extractVideoId(itemUrl).orEmpty() }
                            .ifBlank { "unknown_${playlistId}_$index" }
                    val canonicalUrl = itemUrl.ifBlank { ContentIntegrityScanner.canonicalVideoUrl(videoId) }
                    ContentRequirement(
                        video =
                            VideoIdentity(
                                videoId = videoId,
                                canonicalUrl = canonicalUrl,
                                playlistId = playlistId,
                                playlistIndex = index,
                                title = entryTitle,
                                durationSeconds = entry.duration?.toInt(),
                            ),
                        contentType = contentType,
                        subtitle =
                            if (contentType == ContentType.SUBTITLE) {
                                SubtitleIdentity(
                                    videoId = videoId,
                                    playlistId = playlistId,
                                    language = subtitleLanguage,
                                    source = SubtitleSource.UNKNOWN,
                                    format = subtitleFormat,
                                )
                            } else {
                                null
                            },
                        expectedFormat = if (contentType == ContentType.SUBTITLE) subtitleFormat.extension else null,
                    )
                }

            val integrityReport =
                ContentIntegrityScanner.scan(
                    requirements = requirements,
                    directories = candidateDirs,
                )

            val foundItems =
                integrityReport.results
                    .filter { it.state == ContentState.VALID }
                    .map { it.toVerificationItem(playlistUrl, playlistTitle, preferences) }
            val invalidItems =
                integrityReport.results
                    .filter { it.state == ContentState.INVALID }
                    .map { it.toVerificationItem(playlistUrl, playlistTitle, preferences) }
            val ambiguousItems =
                integrityReport.results
                    .filter { it.state == ContentState.AMBIGUOUS }
                    .map { it.toVerificationItem(playlistUrl, playlistTitle, preferences) }
            val missingItems =
                integrityReport.results
                    .filter { it.state == ContentState.MISSING || it.state == ContentState.INVALID }
                    .map { it.toVerificationItem(playlistUrl, playlistTitle, preferences) }

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

            PlaylistManifestStore.writeAtomic(
                targetDir,
                PlaylistManifest(
                    playlistId = playlistId,
                    title = playlistTitle,
                    canonicalUrl = cleanUrl,
                    totalItems = entries.size,
                    orderedItems =
                        integrityReport.results.map { result ->
                            val req = result.requirement
                            PlaylistManifestItem(
                                index = req.video.playlistIndex ?: 0,
                                videoId = req.video.videoId,
                                title = req.video.title,
                                canonicalUrl = req.video.canonicalUrl,
                                state = result.state,
                                contentType = req.contentType,
                                localPath = result.matchedFile?.absolutePath,
                                language = req.subtitle?.normalizedLanguage,
                                source = req.subtitle?.source?.name,
                                format = req.expectedFormat,
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
                summary = integrityReport.summary,
                ambiguousItems = ambiguousItems,
                invalidItems = invalidItems,
            )
        }
    }

    private fun RequirementResult.toVerificationItem(
        playlistUrl: String,
        playlistTitle: String,
        preferences: DownloadPreferences,
    ): VerificationItem {
        val req = requirement
        val file = matchedFile
        return VerificationItem(
            index = req.video.playlistIndex ?: 0,
            title = req.video.title,
            url = req.video.canonicalUrl.ifBlank { playlistUrl },
            playlistUrl = playlistUrl,
            playlistTitle = playlistTitle,
            preferences = preferences,
            matchedFilePath = file?.absolutePath,
            matchedFileSize = file?.length() ?: 0L,
            videoId = req.video.videoId,
            state = state,
            confidence = confidence,
        )
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

    private fun calculateLevenshteinSimilarity(s1: String, s2: String): Double {
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
            val effectiveTitle = if (isSubOnly) numberedTitle else baseTitle

            val viewState = Task.ViewState(
                url = itemUrl,
                title = if (isSubOnly) "[Subtitle] $numberedTitle" else numberedTitle,
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
