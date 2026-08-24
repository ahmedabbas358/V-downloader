package com.junkfood.seal.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.junkfood.seal.App
import com.junkfood.seal.download.engine.identity.ContentType
import com.junkfood.seal.download.engine.identity.MatchConfidence
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

    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "mka", "m4b")
    private val SUBTITLE_EXTS = setOf("srt", "vtt", "ass", "lrc", "sub", "sbv", "ttml")

    data class PlaylistItemIdentity(
        val videoId: String,
        val expectedTitle: String,
        val expectedExts: Set<String>,
        val contentType: ContentType
    )

    data class LocalFileRecord(
        val name: String,
        val absolutePath: String,
        val length: Long,
        val isPartial: Boolean,
        val uri: Uri?,
        val file: File?
    )

    class LocalFileIndex(private val files: List<LocalFileRecord>) {
        fun findByVideoId(videoId: String, exts: Set<String>): LocalFileRecord? {
            if (videoId.isBlank()) return null
            return files.firstOrNull { record ->
                val ext = record.name.substringAfterLast('.', "").lowercase(Locale.US)
                val matchesExt = exts.contains(ext) || record.isPartial
                matchesExt && (
                    record.name.contains("[$videoId]", ignoreCase = true) ||
                    record.name.contains("_$videoId", ignoreCase = true) ||
                    record.name.contains("-$videoId", ignoreCase = true)
                )
            }
        }

        fun findByExactName(normalizedTitle: String, exts: Set<String>): LocalFileRecord? {
            if (normalizedTitle.isBlank()) return null
            return files.firstOrNull { record ->
                val ext = record.name.substringAfterLast('.', "").lowercase(Locale.US)
                val matchesExt = exts.contains(ext) || record.isPartial
                if (!matchesExt) return@firstOrNull false
                
                val cleanedName = cleanFileNameForMatching(record.name)
                val normalizedRecordName = normalizeText(cleanedName)
                normalizedRecordName == normalizedTitle
            }
        }

        fun getAllPartials(): List<LocalFileRecord> = files.filter { it.isPartial }
    }

    suspend fun scanPlaylist(
        playlistUrl: String,
        preferences: DownloadPreferences,
        customDirectoryPath: String? = null,
        context: Context = App.context
    ): Result<com.junkfood.seal.download.engine.playlist.PlaylistAuditResult> = withContext(Dispatchers.IO) {
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
            val contentType = when {
                isSubtitleOnly -> ContentType.SUBTITLE
                isAudioOnly -> ContentType.AUDIO
                else -> ContentType.VIDEO
            }
            val expectedExts = when (contentType) {
                ContentType.SUBTITLE -> SUBTITLE_EXTS
                ContentType.AUDIO -> AUDIO_EXTS
                ContentType.VIDEO -> VIDEO_EXTS
                else -> emptySet()
            }

            val playlistTitle = playlistInfo.title ?: "Playlist"
            val cleanPlaylistName = FileUtil.cleanFileName(playlistTitle)
            val defaultBaseDir = if (isAudioOnly) File(App.audioDownloadDir) else File(App.videoDownloadDir)

            // Step 1: Target the single most accurate directory (No broad scanning)
            var targetDirFile: File? = null
            var targetDocumentDir: DocumentFile? = null
            var finalDirPath = ""

            if (!customDirectoryPath.isNullOrBlank()) {
                if (customDirectoryPath.startsWith("content://")) {
                    targetDocumentDir = DocumentFile.fromTreeUri(context, Uri.parse(customDirectoryPath))
                    finalDirPath = customDirectoryPath
                } else {
                    val customDir = File(customDirectoryPath)
                    if (customDir.exists() && customDir.isDirectory) {
                        targetDirFile = customDir
                        finalDirPath = customDir.absolutePath
                    }
                }
            }
            
            if (targetDirFile == null && targetDocumentDir == null) {
                targetDirFile = if (cleanPlaylistName.isNotEmpty()) {
                    File(defaultBaseDir, cleanPlaylistName)
                } else {
                    defaultBaseDir
                }
                finalDirPath = targetDirFile.absolutePath
            }

            Log.d(TAG, "scanPlaylist: Target Directory determined as $finalDirPath")

            // Step 2: Build LocalFileIndex (Memory Map)
            val minFileSize = if (isSubtitleOnly) 10L else 1024L
            val localFiles = mutableListOf<LocalFileRecord>()

            if (targetDocumentDir != null && targetDocumentDir.exists() && targetDocumentDir.isDirectory) {
                targetDocumentDir.listFiles().forEach { docFile ->
                    if (docFile.isFile) {
                        val name = docFile.name ?: return@forEach
                        val length = docFile.length()
                        val isPartial = name.endsWith(".part", ignoreCase = true) || name.endsWith(".ytdl", ignoreCase = true)
                        
                        if (isPartial || length >= minFileSize) {
                            localFiles.add(LocalFileRecord(name, docFile.uri.toString(), length, isPartial, docFile.uri, null))
                        }
                    }
                }
            } else if (targetDirFile != null && targetDirFile.exists() && targetDirFile.isDirectory) {
                targetDirFile.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val name = file.name
                        val length = file.length()
                        val isPartial = name.endsWith(".part", ignoreCase = true) || name.endsWith(".ytdl", ignoreCase = true)
                        
                        if (isPartial || length >= minFileSize) {
                            localFiles.add(LocalFileRecord(name, file.absolutePath, length, isPartial, null, file))
                        }
                    }
                }
            }

            val fileIndex = LocalFileIndex(localFiles)

            // Step 3: Reconciliation Engine
            val auditItems = mutableListOf<com.junkfood.seal.download.engine.playlist.PlaylistAuditItem>()
            var downloadedCount = 0
            var missingCount = 0
            var partialCount = 0
            var corruptedCount = 0
            var unknownCount = 0

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

                val identity = PlaylistItemIdentity(videoId, normalizedTitle, expectedExts, contentType)

                // Identity matching
                var matchedRecord = fileIndex.findByVideoId(identity.videoId, identity.expectedExts)
                if (matchedRecord == null && identity.expectedTitle.isNotBlank()) {
                    matchedRecord = fileIndex.findByExactName(identity.expectedTitle, identity.expectedExts)
                }

                var auditState = com.junkfood.seal.download.engine.playlist.AuditState.UNKNOWN
                var confidence = MatchConfidence.UNKNOWN
                var finalFile: File? = matchedRecord?.file

                if (matchedRecord != null) {
                    if (matchedRecord.isPartial) {
                        auditState = com.junkfood.seal.download.engine.playlist.AuditState.PARTIAL
                        confidence = MatchConfidence.HIGH
                        partialCount++
                    } else {
                        val isCorrupt = if (isSubtitleOnly && finalFile != null) {
                            matchedRecord.length == 0L || !SubtitleValidator.validateFile(finalFile).isSuccess
                        } else {
                            matchedRecord.length == 0L
                        }

                        if (isCorrupt) {
                            auditState = com.junkfood.seal.download.engine.playlist.AuditState.CORRUPTED
                            confidence = MatchConfidence.HIGH
                            corruptedCount++
                        } else {
                            auditState = com.junkfood.seal.download.engine.playlist.AuditState.DOWNLOADED
                            confidence = MatchConfidence.HIGH
                            downloadedCount++
                        }
                    }
                } else {
                    if (identity.videoId.isEmpty()) {
                        auditState = com.junkfood.seal.download.engine.playlist.AuditState.UNKNOWN
                        confidence = MatchConfidence.LOW
                        unknownCount++
                    } else {
                        auditState = com.junkfood.seal.download.engine.playlist.AuditState.NOT_DOWNLOADED
                        confidence = MatchConfidence.HIGH
                        missingCount++
                    }
                }

                auditItems.add(
                    com.junkfood.seal.download.engine.playlist.PlaylistAuditItem(
                        index = index,
                        title = entryTitle,
                        url = itemUrl,
                        playlistUrl = playlistUrl,
                        playlistTitle = playlistTitle,
                        preferences = preferences,
                        videoId = videoId,
                        matchedFile = finalFile,
                        matchedFileSize = matchedRecord?.length ?: 0L,
                        state = auditState,
                        confidence = confidence,
                    )
                )
            }

            // Persist manifest in app internal storage (never pollute user download directory)
            val manifestDir = File(context.filesDir, "playlist_manifests")
            val playlistId = videoIdOrPlaylistId(playlistInfo.webpageUrl ?: cleanUrl)
            PlaylistManifestStore.writeAtomic(
                manifestDir,
                PlaylistManifest(
                    playlistId = playlistId,
                    title = playlistTitle,
                    canonicalUrl = cleanUrl,
                    totalItems = entries.size,
                    orderedItems = auditItems.sortedBy { it.index }.map { item ->
                        PlaylistManifestItem(
                            index = item.index,
                            videoId = item.videoId,
                            title = item.title,
                            canonicalUrl = item.url,
                            state = item.toLegacyContentState(),
                            contentType = contentType,
                            localPath = item.matchedFile?.absolutePath,
                            language = primarySubtitleLanguage(preferences.subtitleLanguage),
                            format = subtitleOutputFormat(preferences.convertSubtitle).extension,
                        )
                    },
                ),
            )

            com.junkfood.seal.download.engine.playlist.PlaylistAuditResult(
                playlistTitle = playlistTitle,
                targetDirectory = finalDirPath,
                totalCount = entries.size,
                items = auditItems,
                summary = com.junkfood.seal.download.engine.playlist.AuditSummary(
                    expected = entries.size,
                    downloaded = downloadedCount,
                    missing = missingCount,
                    partial = partialCount,
                    corrupted = corruptedCount,
                    unknown = unknownCount,
                    unavailable = 0
                )
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

    fun cleanFileNameForMatching(fileName: String): String {
        var name = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        name = name.replace(Regex("""\.(?:[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)*|auto|orig|synced)$""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""[\.\[\(]\s*(?:\d{3,4}p|4k|2k|8k|hd|fhd|uhd)\s*[\.\]\)]""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""[\(\[]\s*(?:official(?:\s+music)?\s+video|official\s+audio|audio|lyrics|music\s+video|مترجم|ترجمة|فيديو\s+كليب|كليب\s+رسمي|حصريا|حصرياً|كامل)\s*[\)\]]""", RegexOption.IGNORE_CASE), "")
        
        // Remove known video ids from the title if they exist e.g. [dQw4w9WgXcQ]
        name = name.replace(Regex("""\[[a-zA-Z0-9_-]{11}\]"""), "")
        return name.trim()
    }

    fun normalizeText(text: String): String {
        val arabicNormalized = text
            .replace(Regex("[أإآٱ]"), "ا")
            .replace(Regex("[ةۀ]"), "ه")
            .replace(Regex("[ىيی]"), "ي")
            .replace(Regex("[كک]"), "ك")
            .replace(Regex("[ؤ]"), "و")
            .replace(Regex("[ئ]"), "ي")
            .replace(Regex("[\u064B-\u0652\u0670\u0640]"), "")

        return arabicNormalized.lowercase(Locale.US)
            .replace(Regex("[\\p{Punct}\\s\\u060C\\u061B\\u061F\\u066A\\u066B\\u066C«»“”‘’–—…]+"), " ")
            .trim()
    }

    suspend fun enqueueMissingItems(
        missingItems: List<com.junkfood.seal.download.engine.playlist.PlaylistAuditItem>,
        targetDirectory: String = "",
        downloader: DownloaderV2
    ) = withContext(Dispatchers.Default) {
        if (missingItems.isEmpty()) return@withContext
        val isSubOnly = missingItems.firstOrNull()?.preferences?.run { skipDownload && downloadSubtitle } == true
        val isAudioOnly = missingItems.firstOrNull()?.preferences?.run { extractAudio && !skipDownload } == true

        missingItems.forEach { item ->
            if (item.state == com.junkfood.seal.download.engine.playlist.AuditState.DOWNLOADED || 
                item.state == com.junkfood.seal.download.engine.playlist.AuditState.UNKNOWN) {
                return@forEach
            }

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
                durationMs = null,
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
        items: List<com.junkfood.seal.download.engine.playlist.PlaylistAuditItem>
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
                    "found=${scanResult.summary.downloaded}, " +
                    "missing=${scanResult.summary.missing}, " +
                    "dir=${scanResult.targetDirectory}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "verifyAndRetryPlaylist: scan failed for $playlistUrl", e)
        }
    }
}
