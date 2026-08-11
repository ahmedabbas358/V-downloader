package com.junkfood.seal.download

import android.content.Context
import android.util.Log
import com.junkfood.seal.App
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
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
            val defaultBaseDir = if (isAudioOnly) File(App.audioDownloadDir) else File(App.videoDownloadDir)

            // Smart directory resolution
            val cleanedUserDir = customDirectoryPath?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
                var p = raw.replace(Regex("^(?:All files|Internal storage|Storage|emulated/\\d+)[/\\\\]*", RegexOption.IGNORE_CASE), "")
                p.trimStart('/', '\\')
            }

            val searchRoots = listOfNotNull(
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
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            ).filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }

            val mainTargetDir: File = when {
                !customDirectoryPath.isNullOrBlank() -> {
                    val directFile = File(customDirectoryPath.trim())
                    if (directFile.exists() && directFile.isDirectory) {
                        directFile
                    } else {
                        val relPath = cleanedUserDir ?: customDirectoryPath.trim().removePrefix("/")
                        var resolved: File? = null
                        for (root in listOf(defaultBaseDir) + searchRoots) {
                            val candidate = File(root, relPath)
                            if (candidate.exists() && candidate.isDirectory) {
                                resolved = candidate
                                break
                            }
                        }
                        resolved ?: if (directFile.isAbsolute) directFile else File(defaultBaseDir, relPath)
                    }
                }
                isSubtitleOnly && cleanPlaylistName.isNotEmpty() -> {
                    File(defaultBaseDir, "[Subtitles] $cleanPlaylistName")
                }
                preferences.subdirectoryPlaylistTitle && cleanPlaylistName.isNotEmpty() -> {
                    File(defaultBaseDir, cleanPlaylistName)
                }
                else -> {
                    defaultBaseDir
                }
            }

            val baseDirFile = defaultBaseDir

            // Gather candidate directories
            val candidateDirs = mutableSetOf<File>()
            candidateDirs.add(mainTargetDir)
            candidateDirs.add(baseDirFile)
            candidateDirs.addAll(searchRoots)

            if (cleanPlaylistName.isNotEmpty()) {
                candidateDirs.add(File(baseDirFile, "[Subtitles] $cleanPlaylistName"))
                candidateDirs.add(File(baseDirFile, cleanPlaylistName))
                searchRoots.forEach { root ->
                    candidateDirs.add(File(root, "[Subtitles] $cleanPlaylistName"))
                    candidateDirs.add(File(root, cleanPlaylistName))
                }
            }

            // Parent directories
            val parent1 = mainTargetDir.parentFile
            if (parent1 != null && parent1.exists()) {
                candidateDirs.add(parent1)
            }
            val parent2 = baseDirFile.parentFile
            if (parent2 != null && parent2.exists()) {
                candidateDirs.add(parent2)
            }

            val normalizedPlaylistName = normalizeText(cleanPlaylistName)
            if (normalizedPlaylistName.length > 2) {
                val playlistTokens = normalizedPlaylistName.split(" ").filter { it.length >= 2 }
                searchRoots.forEach { root ->
                    if (root.exists() && root.isDirectory) {
                        root.walkTopDown().maxDepth(3).filter { it.isDirectory }.forEach { dir ->
                            val cleanDirName = dir.name.replace(Regex("^\\[?subtitles?\\]?", RegexOption.IGNORE_CASE), "").trim()
                            val dirNameNorm = normalizeText(cleanDirName)
                            if (dirNameNorm.contains(normalizedPlaylistName) || normalizedPlaylistName.contains(dirNameNorm)) {
                                candidateDirs.add(dir)
                            } else if (playlistTokens.isNotEmpty()) {
                                val matchCount = playlistTokens.count { dirNameNorm.contains(it) }
                                if (matchCount >= (playlistTokens.size * 0.65).toInt().coerceAtLeast(1)) {
                                    candidateDirs.add(dir)
                                }
                            }
                        }
                    }
                }
            }

            // Explicitly add specific subdirectories to bypass potential Scoped Storage walkTopDown limitations
            if (cleanPlaylistName.isNotEmpty()) {
                val subtitleDir = File(defaultBaseDir, "[Subtitles] $cleanPlaylistName")
                candidateDirs.add(subtitleDir)
                val videoDir = File(defaultBaseDir, cleanPlaylistName)
                candidateDirs.add(videoDir)
            }

            // Gather all files recursively (up to 4 subfolder levels deep)
            val allCandidateFiles = candidateDirs.flatMap { dir ->
                if (!dir.exists()) return@flatMap emptyList<File>()
                dir.walkTopDown()
                    .maxDepth(4)
                    .filter { file ->
                        file.isFile &&
                        !file.name.endsWith(".part", ignoreCase = true) &&
                        !file.name.endsWith(".ytdl", ignoreCase = true) &&
                        !file.name.endsWith(".tmp", ignoreCase = true) &&
                        file.length() > (if (isSubtitleOnly) 10L else 512L)
                    }
                    .toList()
            }.distinctBy { it.absolutePath }

            // Diagnostic logging
            Log.d(TAG, "scanPlaylist: mainTargetDir=${mainTargetDir.absolutePath}")
            Log.d(TAG, "scanPlaylist: candidateDirs=${candidateDirs.map { it.absolutePath }}")
            Log.d(TAG, "scanPlaylist: found ${allCandidateFiles.size} candidate files on disk")
            allCandidateFiles.take(10).forEach { f ->
                Log.d(TAG, "  candidate: ${f.name} (${f.length()} bytes)")
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

                val rawTitleClean = entryTitle.removePrefix("[Subtitle] ").replace(Regex("^#\\d+\\s*"), "").trim()
                val normalizedTitle = normalizeText(rawTitleClean)
                val titleTokens = normalizedTitle.split(" ").filter { it.length >= 2 }

                val matchFound = allCandidateFiles.any { file ->
                    val fileName = file.name
                    val ext = file.extension.lowercase(Locale.US)
                    val isValidType = if (isSubtitleOnly) {
                        ext in listOf("srt", "vtt", "ass", "lrc", "sub", "sbv")
                    } else if (isAudioOnly) {
                        ext in listOf("mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "mka", "mp4", "mkv", "webm", "3gp", "m4b")
                    } else {
                        ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp", "mp3", "m4a", "opus", "flac", "wav", "aac", "ogg")
                    }
                    if (!isValidType) return@any false

                    val cleanFileName = cleanFileNameForMatching(fileName)
                    val normalizedFileName = normalizeText(cleanFileName)

                    var isMatched = false

                    // Strategy 1: Video ID Match
                    if (extractedId.length >= 4 && (fileName.contains(extractedId, ignoreCase = true) || normalizedFileName.contains(extractedId.lowercase(Locale.US)))) {
                        isMatched = true
                    }

                    // Strategy 2: Numeric Playlist Index Match
                    if (!isMatched && index > 0) {
                        val formattedIndex3 = String.format(Locale.US, "%03d", index)
                        val formattedIndex2 = String.format(Locale.US, "%02d", index)
                        val indexMatches = fileName.contains(formattedIndex3) ||
                                fileName.contains(formattedIndex2) ||
                                Regex("(?:^|[\\[\\(\\_\\-\\s#])0*${index}(?:[\\s\\-\\_\\.\\]\\)]|$)").containsMatchIn(fileName)

                        if (indexMatches) {
                            if (titleTokens.isNotEmpty()) {
                                val matchCount = titleTokens.count { normalizedFileName.contains(it) }
                                if (matchCount >= 1) {
                                    isMatched = true
                                }
                            } else {
                                isMatched = true
                            }
                        }
                    }

                    // Strategy 3: Normalized Substring Title Match
                    if (!isMatched && normalizedTitle.length >= 4) {
                        if (normalizedFileName.contains(normalizedTitle) || normalizedTitle.contains(normalizedFileName)) {
                            isMatched = true
                        }
                    }

                    // Strategy 4: Fuzzy Word Token Match (>= 85% words match)
                    if (!isMatched && titleTokens.isNotEmpty()) {
                        val matchedTokenCount = titleTokens.count { token -> normalizedFileName.contains(token) }
                        val required = (titleTokens.size * 0.85).toInt().coerceAtLeast(1)
                        if (matchedTokenCount >= required) {
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

    private fun cleanFileNameForMatching(fileName: String): String {
        var name = fileName.substringBeforeLast('.')
        name = name.replace(Regex("""\.(?:[a-z]{2}(?:-[a-zA-Z]{2,4})*|auto|orig)$""", RegexOption.IGNORE_CASE), "")
        return name
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

    private const val TAG = "PlaylistVerifier"

    /**
     * Verify completed playlist items by re-scanning the playlist metadata and
     * checking which files are already present on disk.
     *
     * Called from [DownloaderV2Impl.checkPlaylistCompletion] after all tasks for
     * a playlist have reached a terminal state (Completed / Error / Canceled).
     *
     * This overload intentionally does NOT re-enqueue missing items because the
     * caller does not pass a [DownloaderV2] reference. It logs the verification
     * result so developers and crash reporters can see what was missing.
     */
    suspend fun verifyAndRetryPlaylist(
        items: List<VerificationItem>
    ) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            Log.d(TAG, "verifyAndRetryPlaylist: empty items list, nothing to verify")
            return@withContext
        }

        val first = items.first()
        val playlistUrl = first.playlistUrl
        val preferences = first.preferences

        Log.d(
            TAG,
            "verifyAndRetryPlaylist: verifying ${items.size} items for playlist '${first.playlistTitle}'"
        )

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

            if (scanResult.missingItems.isNotEmpty()) {
                scanResult.missingItems.forEach { missing ->
                    Log.w(
                        TAG,
                        "verifyAndRetryPlaylist: MISSING #${missing.index} — ${missing.title}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "verifyAndRetryPlaylist: scan failed for $playlistUrl", e)
        }
    }
}
