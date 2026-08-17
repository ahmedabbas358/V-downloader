package com.junkfood.seal.download.engine.postprocess

import android.media.MediaScannerConnection
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.download.engine.resilience.FileCollisionResolver
import com.junkfood.seal.util.FileUtil
import java.io.File

/**
 * MediaStorageScanner
 *
 * Scans the download directory for files that match a completed download task,
 * registers them with the Android MediaStore, and returns the matched file paths.
 *
 * Design Principles:
 * - Sort matches by lastModified descending to always prefer the newest file.
 * - For generic titles (social media), rely exclusively on Video ID matching.
 * - Fallback to recently modified files (last 5 minutes) if no name match is found.
 * - Properly filter subtitle files from video/audio files.
 */
object MediaStorageScanner {

    private const val TAG = "MediaStorageScanner"

    /** Regex patterns */
    private val SUBTITLE_REGEX = Regex("(?i)\\.(lrc|vtt|srt|ass|json3|srv\\d?|ttml|sub|ssa)$")
    private val THUMBNAIL_REGEX = Regex("(?i)\\.(jpe?g|png|webp|bmp)$")
    private val AUDIO_REGEX = Regex("(?i)\\.(mp3|aac|opus|m4a|ogg|flac|wav)$")

    /**
     * Scans the download directory for files matching the completed download.
     *
     * @param title The video/subtitle title
     * @param downloadDir The directory where files were downloaded
     * @param isSubtitleOnly Whether this was a subtitle-only download
     * @param videoId Optional video ID for precise matching
     * @return List of matched file paths, sorted newest-first
     */
    fun scanAndRegister(
        title: String,
        downloadDir: String,
        isSubtitleOnly: Boolean = false,
        videoId: String? = null,
    ): List<String> {
        val cleanTitleStr = title
            .removePrefix("[Subtitle] ")
            .replace(Regex("^(\\d+\\s*-\\s*|#\\d+\\s*)"), "")
            .trim()
        val rawCleaned = FileUtil.cleanFileName(title.removePrefix("[Subtitle] "))
        val cleanedTitle = FileUtil.cleanFileName(cleanTitleStr)
        val shortTitle = if (cleanedTitle.length > 8) cleanedTitle.take(8) else cleanedTitle
        val normalizedTitle = cleanTitleStr.lowercase(java.util.Locale.US)
            .replace(Regex("[^a-z0-9\\u0600-\\u06FF\\s]"), "").trim()
        val titleWords = normalizedTitle.split("\\s+".toRegex()).filter { it.length >= 2 }
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000L)

        val isGeneric = FileCollisionResolver.isGenericTitle(title)

        val targetDir = File(downloadDir)
        if (!targetDir.exists()) return emptyList()

        // Collect all candidate files
        val allFiles = targetDir.walkTopDown()
            .filter { file ->
                file.isFile &&
                !file.name.endsWith(".part", ignoreCase = true) &&
                !file.name.endsWith(".ytdl", ignoreCase = true) &&
                !file.name.endsWith(".tmp", ignoreCase = true) &&
                file.length() > (if (isSubtitleOnly) 5L else 512L)
            }
            .toList()

        // 1. Primary matching
        val matchedFiles = allFiles.filter { file ->
            val name = file.name
            val path = file.absolutePath
            val normalizedName = name.lowercase(java.util.Locale.US)
                .replace(Regex("[^a-z0-9\\u0600-\\u06FF\\s]"), "").trim()

            val isSubFile = SUBTITLE_REGEX.containsMatchIn(name)
            if (isSubtitleOnly && !isSubFile) return@filter false
            if (!isSubtitleOnly && isSubFile) return@filter false

            var isMatch = false

            // Match by Video ID (highest confidence, always works)
            if (!videoId.isNullOrBlank() && videoId.length >= 4 &&
                (name.contains(videoId) || path.contains(videoId))) {
                isMatch = true
            }

            // Match by title (ONLY if not generic, or if file is very recent)
            if (!isMatch && !isGeneric) {
                if (path.contains(cleanTitleStr) || name.contains(cleanedTitle) ||
                    path.contains(rawCleaned) || name.contains(rawCleaned) ||
                    (shortTitle.isNotEmpty() && name.contains(shortTitle))) {
                    isMatch = true
                }
            } else if (!isMatch && isGeneric && file.lastModified() >= fiveMinutesAgo) {
                // For generic titles, only match very recent files by title
                if (name.contains(cleanedTitle) || path.contains(cleanTitleStr) || name.contains(rawCleaned)) {
                    isMatch = true
                }
            }

            // Match by normalized words (ONLY for non-generic titles)
            if (!isMatch && !isGeneric && titleWords.isNotEmpty()) {
                val matchedCount = titleWords.count { normalizedName.contains(it) }
                if (matchedCount >= (titleWords.size * 0.7).toInt().coerceAtLeast(1)) {
                    isMatch = true
                }
            }

            isMatch
        }
        .sortedByDescending { it.lastModified() }
        .map { it.absolutePath }
        .toMutableList()

        // 2. Fallback: recently modified files
        if (matchedFiles.isEmpty()) {
            val recentlyModified = allFiles.filter { file ->
                val name = file.name
                val isSubFile = SUBTITLE_REGEX.containsMatchIn(name)
                val typeMatches = if (isSubtitleOnly) isSubFile
                    else !isSubFile && !THUMBNAIL_REGEX.containsMatchIn(name)
                typeMatches && file.lastModified() >= fiveMinutesAgo
            }
            .sortedByDescending { it.lastModified() }
            .map { it.absolutePath }

            matchedFiles.addAll(recentlyModified)
        }

        // 3. Register with MediaStore
        if (matchedFiles.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    matchedFiles.toTypedArray(),
                    null,
                    null
                )
            } catch (e: Exception) {
                Log.e(TAG, "MediaScannerConnection error: ${e.message}")
            }
        }

        // 4. Remove thumbnails/subtitles from non-subtitle results
        matchedFiles.removeAll {
            THUMBNAIL_REGEX.containsMatchIn(it) ||
            (!isSubtitleOnly && SUBTITLE_REGEX.containsMatchIn(it))
        }

        // 5. Final sort: newest first
        matchedFiles.sortByDescending { File(it).lastModified() }

        Log.d(TAG, "scanAndRegister: found ${matchedFiles.size} file(s) for '$title' in '$downloadDir'")
        return matchedFiles
    }

    /**
     * Convenience: scan a single file and register it with MediaStore.
     */
    fun scanSingleFile(file: File) {
        if (!file.exists()) return
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "scanSingleFile error: ${e.message}")
        }
    }
}
