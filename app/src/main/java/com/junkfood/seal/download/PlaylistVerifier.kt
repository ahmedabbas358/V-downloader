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
        private val claimedPaths = mutableSetOf<String>()

        fun findByVideoId(videoId: String, exts: Set<String>): LocalFileRecord? {
            if (videoId.isBlank() || videoId.length < 4) return null
            val match = files.firstOrNull { record ->
                if (claimedPaths.contains(record.absolutePath)) return@firstOrNull false
                val ext = record.name.substringAfterLast('.', "").lowercase(Locale.US)
                val matchesExt = exts.isEmpty() || exts.contains(ext) || record.isPartial
                if (!matchesExt) return@firstOrNull false

                // Check for exact video ID patterns in filename
                val name = record.name
                name.contains("[$videoId]", ignoreCase = true) ||
                name.contains("_$videoId", ignoreCase = true) ||
                name.contains("-$videoId", ignoreCase = true) ||
                name.contains(" $videoId ", ignoreCase = true) ||
                name.contains(" $videoId.", ignoreCase = true) ||
                name.contains(" $videoId[", ignoreCase = true) ||
                (name.contains(videoId, ignoreCase = true) && !name.contains(Regex("[a-zA-Z0-9_-]$videoId[a-zA-Z0-9_-]")))
            }
            if (match != null) {
                claimedPaths.add(match.absolutePath)
            }
            return match
        }

        fun findByIndexedTitle(index: Int, normalizedTitle: String, exts: Set<String>): LocalFileRecord? {
            if (index <= 0) return null
            val indexPadded3 = "%03d".format(Locale.US, index)
            val indexPadded2 = "%02d".format(Locale.US, index)
            val indexStr = index.toString()

            val match = files.firstOrNull { record ->
                if (claimedPaths.contains(record.absolutePath)) return@firstOrNull false
                val ext = record.name.substringAfterLast('.', "").lowercase(Locale.US)
                val matchesExt = exts.isEmpty() || exts.contains(ext) || record.isPartial
                if (!matchesExt) return@firstOrNull false

                val startsWithIndex = record.name.startsWith("$indexPadded3 - ") ||
                        record.name.startsWith("$indexPadded3-") ||
                        record.name.startsWith("$indexPadded3. ") ||
                        record.name.startsWith("${indexPadded3}_") ||
                        record.name.startsWith("$indexPadded2 - ") ||
                        record.name.startsWith("$indexPadded2-") ||
                        record.name.startsWith("$indexPadded2. ") ||
                        record.name.startsWith("${indexPadded2}_") ||
                        record.name.startsWith("$indexStr - ") ||
                        record.name.startsWith("$indexStr-") ||
                        record.name.startsWith("$indexStr. ") ||
                        record.name.startsWith("[$indexStr]") ||
                        record.name.startsWith("($indexStr)") ||
                        record.name.startsWith("#$indexStr ") ||
                        record.name.startsWith("#$indexPadded2 ")

                if (!startsWithIndex) return@firstOrNull false

                if (normalizedTitle.isBlank()) return@firstOrNull true

                val cleanedName = cleanFileNameForMatching(record.name)
                val normalizedRecordName = normalizeText(cleanedName)
                if (normalizedRecordName == normalizedTitle) return@firstOrNull true
                if (normalizedTitle.length >= 3 && normalizedRecordName.contains(normalizedTitle)) return@firstOrNull true
                if (normalizedRecordName.length >= 3 && normalizedTitle.contains(normalizedRecordName)) return@firstOrNull true

                // In an indexed playlist directory, a file matching the index prefix and extension is a match
                val recordTokens = normalizedRecordName.split(' ').filter { it.length >= 2 }.toSet()
                val titleTokens = normalizedTitle.split(' ').filter { it.length >= 2 }.toSet()
                if (recordTokens.isEmpty() || titleTokens.isEmpty()) return@firstOrNull true

                val intersection = recordTokens.intersect(titleTokens)
                val union = recordTokens.union(titleTokens)
                val jaccard = intersection.size.toFloat() / union.size.toFloat()
                if (jaccard >= 0.15f || intersection.isNotEmpty()) return@firstOrNull true

                // Fallback: index match is decisive inside playlist directory
                return@firstOrNull true
            }
            if (match != null) {
                claimedPaths.add(match.absolutePath)
            }
            return match
        }

        private fun extractIndexFromFileName(name: String): Int? {
            val match = Regex("""^#?(\d{1,4})\s*[-._\s]|^\[(\d{1,4})\]|^\((\d{1,4})\)""").find(name.trim())
            return (match?.groupValues?.get(1)?.ifEmpty { null }
                ?: match?.groupValues?.get(2)?.ifEmpty { null }
                ?: match?.groupValues?.get(3)?.ifEmpty { null })?.toIntOrNull()
        }

        fun findByExactName(
            targetIndex: Int = 0,
            normalizedTitle: String,
            exts: Set<String>,
            playlistTitleNormalized: String = ""
        ): LocalFileRecord? {
            if (normalizedTitle.isBlank() || normalizedTitle.length < 3) return null

            // Extract unique title keywords excluding common playlist title tokens
            val playlistTokens = if (playlistTitleNormalized.isNotBlank()) {
                playlistTitleNormalized.split(' ').filter { it.length >= 2 }.toSet()
            } else emptySet()

            val match = files.firstOrNull { record ->
                if (claimedPaths.contains(record.absolutePath)) return@firstOrNull false
                val ext = record.name.substringAfterLast('.', "").lowercase(Locale.US)
                val matchesExt = exts.isEmpty() || exts.contains(ext) || record.isPartial
                if (!matchesExt) return@firstOrNull false

                // If record has an explicit playlist index prefix (e.g. "043 - "),
                // it CANNOT match an item with a different targetIndex!
                val recordIndex = extractIndexFromFileName(record.name)
                if (recordIndex != null && targetIndex > 0 && recordIndex != targetIndex) {
                    return@firstOrNull false
                }
                
                val cleanedName = cleanFileNameForMatching(record.name)
                val normalizedRecordName = normalizeText(cleanedName)
                if (normalizedRecordName == normalizedTitle) return@firstOrNull true
                if (normalizedTitle.length >= 6 && normalizedRecordName.equals(normalizedTitle, ignoreCase = true)) return@firstOrNull true

                val recordTokens = normalizedRecordName.split(' ').filter { it.length >= 2 }.toSet()
                val titleTokens = normalizedTitle.split(' ').filter { it.length >= 2 }.toSet()

                // Filter out common playlist tokens to focus on unique video title tokens
                val uniqueRecordTokens = if (playlistTokens.isNotEmpty()) recordTokens - playlistTokens else recordTokens
                val uniqueTitleTokens = if (playlistTokens.isNotEmpty()) titleTokens - playlistTokens else titleTokens

                if (uniqueRecordTokens.isNotEmpty() && uniqueTitleTokens.isNotEmpty()) {
                    val uniqueIntersection = uniqueRecordTokens.intersect(uniqueTitleTokens)
                    val uniqueUnion = uniqueRecordTokens.union(uniqueTitleTokens)
                    val jaccard = uniqueIntersection.size.toFloat() / uniqueUnion.size.toFloat()
                    val shorterSize = minOf(uniqueRecordTokens.size, uniqueTitleTokens.size)
                    val overlapRatio = uniqueIntersection.size.toFloat() / shorterSize.toFloat()

                    // Match only if unique title tokens overlap with high fidelity
                    if (jaccard >= 0.55f || overlapRatio >= 0.7f) {
                        return@firstOrNull true
                    }
                } else if (recordTokens.isNotEmpty() && titleTokens.isNotEmpty() && playlistTokens.isEmpty()) {
                    val intersection = recordTokens.intersect(titleTokens)
                    val union = recordTokens.union(titleTokens)
                    val jaccard = intersection.size.toFloat() / union.size.toFloat()
                    if (jaccard >= 0.75f) {
                        return@firstOrNull true
                    }
                }
                false
            }
            if (match != null) {
                claimedPaths.add(match.absolutePath)
            }
            return match
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
            val cleanPlaylistName = FileUtil.cleanFileName(playlistTitle).trim().ifBlank { "Playlist" }
            val listId = Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(cleanUrl)?.groupValues?.get(1).orEmpty()
            val defaultBaseDir = if (isAudioOnly) File(App.audioDownloadDir) else File(App.videoDownloadDir)

            // Step 1: Collect ALL candidate search directories
            val candidateDirs = mutableListOf<File>()
            var targetDocumentDir: DocumentFile? = null
            var finalDirPath = ""

            if (!customDirectoryPath.isNullOrBlank()) {
                if (customDirectoryPath.startsWith("content://")) {
                    targetDocumentDir = DocumentFile.fromTreeUri(context, Uri.parse(customDirectoryPath))
                    finalDirPath = customDirectoryPath
                } else {
                    val customDir = File(customDirectoryPath)
                    if (customDir.exists() && customDir.isDirectory) {
                        candidateDirs.add(customDir)
                        finalDirPath = customDir.absolutePath
                    }
                }
            }

            if (preferences.commandDirectory.isNotBlank()) {
                val cmdDir = File(preferences.commandDirectory)
                if (cmdDir.exists() && cmdDir.isDirectory) {
                    candidateDirs.add(cmdDir)
                    val cmdSub = File(cmdDir, if (isSubtitleOnly) "[Subtitles] $cleanPlaylistName" else cleanPlaylistName)
                    if (cmdSub.exists() && cmdSub.isDirectory) candidateDirs.add(0, cmdSub)
                }
            }

            // Primary target folders
            val subFolder = File(defaultBaseDir, "[Subtitle] $cleanPlaylistName")
            val subFolderPlural = File(defaultBaseDir, "[Subtitles] $cleanPlaylistName")
            val plainFolder = File(defaultBaseDir, cleanPlaylistName)
            val subFolderVideo = File(App.videoDownloadDir, "[Subtitle] $cleanPlaylistName")
            val subFolderVideoPlural = File(App.videoDownloadDir, "[Subtitles] $cleanPlaylistName")
            val plainFolderVideo = File(App.videoDownloadDir, cleanPlaylistName)
            val subFolderAudio = File(App.audioDownloadDir, "[Subtitle] $cleanPlaylistName")
            val subFolderAudioPlural = File(App.audioDownloadDir, "[Subtitles] $cleanPlaylistName")
            val plainFolderAudio = File(App.audioDownloadDir, cleanPlaylistName)

            if (isSubtitleOnly) {
                if (subFolder.exists() && subFolder.isDirectory) candidateDirs.add(0, subFolder)
                if (subFolderPlural.exists() && subFolderPlural.isDirectory && !candidateDirs.contains(subFolderPlural)) candidateDirs.add(0, subFolderPlural)
                if (subFolderVideo.exists() && subFolderVideo.isDirectory && !candidateDirs.contains(subFolderVideo)) candidateDirs.add(subFolderVideo)
                if (subFolderVideoPlural.exists() && subFolderVideoPlural.isDirectory && !candidateDirs.contains(subFolderVideoPlural)) candidateDirs.add(subFolderVideoPlural)
                if (subFolderAudio.exists() && subFolderAudio.isDirectory && !candidateDirs.contains(subFolderAudio)) candidateDirs.add(subFolderAudio)
                if (subFolderAudioPlural.exists() && subFolderAudioPlural.isDirectory && !candidateDirs.contains(subFolderAudioPlural)) candidateDirs.add(subFolderAudioPlural)
                if (plainFolder.exists() && plainFolder.isDirectory && !candidateDirs.contains(plainFolder)) candidateDirs.add(plainFolder)
                if (plainFolderVideo.exists() && plainFolderVideo.isDirectory && !candidateDirs.contains(plainFolderVideo)) candidateDirs.add(plainFolderVideo)
            } else {
                if (plainFolder.exists() && plainFolder.isDirectory) candidateDirs.add(0, plainFolder)
                if (plainFolderVideo.exists() && plainFolderVideo.isDirectory && !candidateDirs.contains(plainFolderVideo)) candidateDirs.add(plainFolderVideo)
                if (plainFolderAudio.exists() && plainFolderAudio.isDirectory && !candidateDirs.contains(plainFolderAudio)) candidateDirs.add(plainFolderAudio)
                if (subFolder.exists() && subFolder.isDirectory && !candidateDirs.contains(subFolder)) candidateDirs.add(subFolder)
                if (subFolderPlural.exists() && subFolderPlural.isDirectory && !candidateDirs.contains(subFolderPlural)) candidateDirs.add(subFolderPlural)
            }

            if (listId.isNotEmpty()) {
                val idFolderVideo = File(App.videoDownloadDir, "Playlist_$listId")
                val idFolderAudio = File(App.audioDownloadDir, "Playlist_$listId")
                if (idFolderVideo.exists() && idFolderVideo.isDirectory && !candidateDirs.contains(idFolderVideo)) candidateDirs.add(idFolderVideo)
                if (idFolderAudio.exists() && idFolderAudio.isDirectory && !candidateDirs.contains(idFolderAudio)) candidateDirs.add(idFolderAudio)
            }

            // Also check fuzzy sibling subdirectories matching the playlist name
            fun findFuzzySiblingDirs(base: File) {
                if (!base.exists() || !base.isDirectory) return
                val normTarget = normalizeText(cleanPlaylistName)
                base.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                    val normSub = normalizeText(cleanFileNameForMatching(sub.name))
                    if (normSub.isNotEmpty() && (normSub == normTarget || (normTarget.length >= 4 && normSub.contains(normTarget)) || (normSub.length >= 4 && normTarget.contains(normSub)))) {
                        if (!candidateDirs.contains(sub)) candidateDirs.add(sub)
                    }
                }
            }
            findFuzzySiblingDirs(File(App.videoDownloadDir))
            findFuzzySiblingDirs(File(App.audioDownloadDir))

            val preferred = if (isSubtitleOnly) subFolder else plainFolder
            if (finalDirPath.isEmpty()) {
                finalDirPath = candidateDirs.firstOrNull { it.exists() }?.absolutePath ?: preferred.absolutePath
            }

            Log.d(TAG, "scanPlaylist: Target Directory determined as $finalDirPath with ${candidateDirs.size} candidate scan folders")

            // Step 2: Build LocalFileIndex (Memory Map)
            val minFileSize = if (isSubtitleOnly) 5L else 512L
            val localFiles = mutableListOf<LocalFileRecord>()
            val scannedPaths = mutableSetOf<String>()

            fun scanDir(dir: File) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && scannedPaths.add(file.absolutePath)) {
                        val name = file.name
                        val length = file.length()
                        val isPartial = name.endsWith(".part", ignoreCase = true) || name.endsWith(".ytdl", ignoreCase = true)
                        if (isPartial || length >= minFileSize) {
                            localFiles.add(LocalFileRecord(name, file.absolutePath, length, isPartial, null, file))
                        }
                    }
                }
            }

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
            }

            candidateDirs.forEach { scanDir(it) }

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
                    rawUrl.startsWith("/") -> "https://www.youtube.com$rawUrl"
                    else -> ""
                }
                val videoId = entry.id.orEmpty().ifBlank {
                    FileCollisionResolver.extractVideoId(itemUrl, fallbackId = "")
                }
                val normalizedTitle = normalizeText(cleanFileNameForMatching(entryTitle))

                val identity = PlaylistItemIdentity(videoId, normalizedTitle, expectedExts, contentType)

                // Identity matching: 1. VideoId -> 2. Index+Title -> 3. Cleaned title match
                var matchedRecord = fileIndex.findByVideoId(identity.videoId, identity.expectedExts)
                if (matchedRecord == null) {
                    matchedRecord = fileIndex.findByIndexedTitle(index, identity.expectedTitle, identity.expectedExts)
                }
                if (matchedRecord == null && identity.expectedTitle.isNotBlank()) {
                    matchedRecord = fileIndex.findByExactName(
                        targetIndex = index,
                        normalizedTitle = identity.expectedTitle,
                        exts = identity.expectedExts,
                        playlistTitleNormalized = normalizeText(cleanPlaylistName)
                    )
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
                    if (identity.videoId.isEmpty() && identity.expectedTitle.isBlank()) {
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

            // Persist manifest in app internal storage
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
        // Strip media and subtitle extensions safely without mutilating titles containing dots
        var name = fileName.replace(Regex("""\.(?:mp4|mkv|webm|m4a|mp3|opus|flac|wav|aac|ogg|m4b|mka|avi|mov|ts|3gp|m4v|srt|vtt|ass|lrc|json3|srv\d?|ttml|sub|ssa|part|ytdl)$""", RegexOption.IGNORE_CASE), "")
        // Strip subtitle language suffixes e.g. .ar, .en, .ar-orig, .auto
        name = name.replace(Regex("""\.(?:[a-zA-Z]{2,3}(?:-[a-zA-Z0-9_-]+)*|auto|orig|synced)$""", RegexOption.IGNORE_CASE), "")
        // Strip numbering prefixes e.g. "001 - ", "01. ", "1 - ", "#01 ", "[01] ", "(01) ", "001 "
        name = name.replace(Regex("""^(?:#?\d{1,4}\s*[-._)\]\s]\s*|\[\d{1,4}\]\s*|\(\d{1,4}\)\s*)"""), "")
        // Strip quality tags e.g. [1080p], (720p), .4k.
        name = name.replace(Regex("""[\.\[\(]\s*(?:\d{3,4}p|4k|2k|8k|hd|fhd|uhd)\s*[\.\]\)]""", RegexOption.IGNORE_CASE), "")
        // Strip media descriptor tags
        name = name.replace(Regex("""[\(\[]\s*(?:official(?:\s+music)?\s+video|official\s+audio|audio|lyrics|music\s+video|مترجم|ترجمة|فيديو\s+كليب|كليب\s+رسمي|حصريا|حصرياً|كامل)\s*[\)\]]""", RegexOption.IGNORE_CASE), "")
        // Strip video IDs in brackets or parens e.g. [dQw4w9WgXcQ]
        name = name.replace(Regex("""[\(\[]\s*[a-zA-Z0-9_-]{11}\s*[\)\]]"""), "")
        // Strip [Subtitles] or [Subtitle] tag if present
        name = name.removePrefix("[Subtitles] ").removePrefix("[Subtitle] ")
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
            if (item.state == com.junkfood.seal.download.engine.playlist.AuditState.DOWNLOADED) {
                return@forEach
            }

            val baseTitle = item.title
            val itemUrl = when {
                item.url.isNotBlank() && item.url.startsWith("http", ignoreCase = true) -> item.url
                item.videoId.isNotBlank() -> "https://www.youtube.com/watch?v=${item.videoId}"
                item.playlistUrl.isNotBlank() -> item.playlistUrl
                else -> ""
            }
            if (itemUrl.isBlank()) return@forEach

            val effectiveTargetDir = if (isSubOnly && targetDirectory.isNotBlank()) {
                val cleanTitle = FileUtil.cleanFileName(item.playlistTitle).trim()
                if (cleanTitle.isNotEmpty() && !targetDirectory.contains("[Subtitle]") && !targetDirectory.contains("[Subtitles]")) {
                    val subFolderName = "[Subtitle] $cleanTitle"
                    val dirFile = File(targetDirectory)
                    if (dirFile.name.equals(cleanTitle, ignoreCase = true)) {
                        File(dirFile.parentFile ?: dirFile, subFolderName).absolutePath
                    } else {
                        targetDirectory
                    }
                } else {
                    targetDirectory
                }
            } else {
                targetDirectory
            }

            val itemPrefs = item.preferences.copy(
                downloadPlaylist = false,
                playlistNumbering = true,
                useDownloadArchive = false,
                skipDownload = isSubOnly,
                downloadSubtitle = if (isSubOnly) true else item.preferences.downloadSubtitle,
                autoSubtitle = if (isSubOnly) true else item.preferences.autoSubtitle,
                autoTranslatedSubtitles = if (isSubOnly) true else item.preferences.autoTranslatedSubtitles,
                extractAudio = if (isSubOnly) false else isAudioOnly,
                commandDirectory = effectiveTargetDir.ifBlank { item.preferences.commandDirectory },
                subdirectoryPlaylistTitle = isSubOnly || item.preferences.subdirectoryPlaylistTitle,
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

    /**
     * Calculates normalized Levenshtein similarity between two strings (0.0 to 1.0).
     */
    fun calculateLevenshteinSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty()) return if (s2.isEmpty()) 1.0 else 0.0
        if (s2.isEmpty()) return 0.0

        val maxLen = maxOf(s1.length, s2.length)
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        val distance = dp[s1.length][s2.length]
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }
}
