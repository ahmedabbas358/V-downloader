package com.junkfood.seal.download.engine.resilience

import android.util.Log
import com.junkfood.seal.App
import com.junkfood.seal.util.FileUtil
import java.io.File

/**
 * FileCollisionResolver
 *
 * Determines whether a download task's target file already exists on disk,
 * preventing false-positive matches that cause the wrong video to be returned
 * (especially for Instagram Reels, TikTok, and other social media platforms
 * that use generic, repeated titles like "Video by <username>").
 *
 * Design Principles:
 * - Match ONLY by unique, platform-specific Video ID (minimum 5 characters).
 * - NEVER match by generic/ambiguous title strings.
 * - Extract Video IDs from platform-specific URL patterns.
 */
object FileCollisionResolver {

    private const val TAG = "FileCollisionResolver"

    /** Minimum length for a Video ID to be considered reliable for matching */
    private const val MIN_VIDEO_ID_LENGTH = 5

    /** Regex patterns for subtitle and thumbnail files */
    private val SUBTITLE_REGEX = Regex("(?i)\\.(srt|vtt|ass|ssa|sub|lrc|json3|srv\\d?|ttml)$")
    private val THUMBNAIL_REGEX = Regex("(?i)\\.(jpe?g|png|webp|bmp)$")

    /**
     * Title prefixes that are considered generic/ambiguous and should NOT be used
     * for file matching. These are produced by yt-dlp extractors for social media platforms.
     */
    /**
     * Title prefixes that are considered generic/ambiguous and should NOT be used
     * for file matching. These are produced by yt-dlp extractors for social media platforms.
     */
    private val GENERIC_TITLE_PREFIXES = listOf(
        "video by ",
        "photo by ",
        "reel by ",
        "post by ",
        "tiktok video by ",
        "tiktok video #",
        "watch this video by ",
        "instagram post by ",
        "instagram photo by ",
        "instagram reel by ",
        "tweet by ",
        "video_",
        "reel_",
        "clip_",
        "track_",
        "track ",
        "item ",
        "episode ",
        "part ",
    )

    private val GENERIC_TITLE_EXACT = listOf(
        "video",
        "reel",
        "post",
        "photo",
        "clip",
        "media",
        "track",
        "item",
    )

    /**
     * Checks whether the title is generic/ambiguous (not reliable for file matching).
     */
    fun isGenericTitle(title: String): Boolean {
        val cleaned = title
            .removePrefix("[Subtitle] ")
            .replace(Regex("^#\\d+\\s*"), "")
            .trim()
        val lower = cleaned.lowercase()

        if (GENERIC_TITLE_EXACT.any { lower == it }) return true
        if (GENERIC_TITLE_PREFIXES.any { lower.startsWith(it) }) return true
        if (lower.startsWith("http://") || lower.startsWith("https://")) return true

        return false
    }

    /**
     * Extracts a platform-specific unique Video ID from a URL.
     * Returns empty string if no ID could be reliably extracted.
     */
    fun extractVideoId(url: String, fallbackId: String = ""): String {
        if (url.isBlank()) return fallbackId

        val cleanUrl = url.trim()
        val extracted = when {
            // YouTube
            cleanUrl.contains("v=") ->
                cleanUrl.substringAfter("v=").substringBefore("&").substringBefore("?").substringBefore("/")
            cleanUrl.contains("youtu.be/") ->
                cleanUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").substringBefore("/")
            cleanUrl.contains("/shorts/") ->
                cleanUrl.substringAfter("/shorts/").substringBefore("?").substringBefore("&").substringBefore("/")
            cleanUrl.contains("/live/") ->
                cleanUrl.substringAfter("/live/").substringBefore("?").substringBefore("&").substringBefore("/")

            // Instagram (/reel/, /reels/, /p/, /tv/, /share/reel/)
            cleanUrl.contains("/reel/") ->
                cleanUrl.substringAfter("/reel/").substringBefore("/").substringBefore("?")
            cleanUrl.contains("/reels/") ->
                cleanUrl.substringAfter("/reels/").substringBefore("/").substringBefore("?")
            cleanUrl.contains("/p/") ->
                cleanUrl.substringAfter("/p/").substringBefore("/").substringBefore("?")
            cleanUrl.contains("/tv/") ->
                cleanUrl.substringAfter("/tv/").substringBefore("/").substringBefore("?")

            // TikTok (/video/, /v/, vm.tiktok.com, vt.tiktok.com)
            cleanUrl.contains("/video/") ->
                cleanUrl.substringAfter("/video/").substringBefore("/").substringBefore("?")
            cleanUrl.contains("/v/") ->
                cleanUrl.substringAfter("/v/").substringBefore("/").substringBefore("?")
            cleanUrl.contains("vm.tiktok.com/") ->
                cleanUrl.substringAfter("vm.tiktok.com/").substringBefore("/").substringBefore("?")
            cleanUrl.contains("vt.tiktok.com/") ->
                cleanUrl.substringAfter("vt.tiktok.com/").substringBefore("/").substringBefore("?")

            // Twitter / X
            cleanUrl.contains("/status/") ->
                cleanUrl.substringAfter("/status/").substringBefore("/").substringBefore("?")

            // Facebook
            cleanUrl.contains("facebook.com/reel/") ->
                cleanUrl.substringAfter("facebook.com/reel/").substringBefore("/").substringBefore("?")
            cleanUrl.contains("facebook.com/watch/?v=") ->
                cleanUrl.substringAfter("facebook.com/watch/?v=").substringBefore("&").substringBefore("?")
            cleanUrl.contains("fb.watch/") ->
                cleanUrl.substringAfter("fb.watch/").substringBefore("/").substringBefore("?")

            else -> ""
        }

        val finalId = extracted.trim()
        return if (finalId.length >= MIN_VIDEO_ID_LENGTH) finalId else fallbackId
    }

    /**
     * Checks if a file already exists on disk that matches this specific download task.
     *
     * @param url The download URL
     * @param title The video title (may be generic)
     * @param videoId The yt-dlp extracted video ID (if available)
     * @param isSubtitleOnly Whether this is a subtitle-only download
     * @param isAudioDownload Whether this is an audio-only download
     * @param playlistTitle The playlist title (if applicable)
     * @param isPrivateDirectory Whether files are stored in the private app directory
     *
     * @return The absolute path of the existing file, or null if no reliable match was found.
     */
    fun checkExistingFile(
        url: String,
        title: String,
        videoId: String = "",
        isSubtitleOnly: Boolean = false,
        isAudioDownload: Boolean = false,
        playlistTitle: String = "",
        isPrivateDirectory: Boolean = false,
        subdirectoryPlaylistTitle: Boolean = false,
    ): String? {
        val appContext = App.context

        // 1. Extract the best available unique video ID
        val resolvedId = extractVideoId(url, videoId)

        // 2. Check if title is generic
        val titleIsGeneric = isGenericTitle(title)

        // 3. If the title is generic AND we don't have a reliable video ID, skip entirely
        if (titleIsGeneric && resolvedId.length < MIN_VIDEO_ID_LENGTH) {
            Log.d(TAG, "Skipping existing file check: generic title '$title' with no reliable ID")
            return null
        }

        // 4. Build candidate directories to search
        val baseDir = if (isAudioDownload) {
            if (isPrivateDirectory) appContext.filesDir.absolutePath else App.audioDownloadDir
        } else {
            if (isPrivateDirectory) appContext.filesDir.absolutePath else App.videoDownloadDir
        }

        val cleanPlaylistName = FileUtil.cleanFileName(playlistTitle)
        val candidateDirs = mutableListOf<File>()
        if (cleanPlaylistName.isNotEmpty()) {
            candidateDirs.add(File(baseDir, cleanPlaylistName))
            candidateDirs.add(File(baseDir, "[Subtitles] $cleanPlaylistName"))
        }
        candidateDirs.add(File(baseDir))

        // 5. Clean the title for matching (only used if not generic)
        val rawTitle = title.removePrefix("[Subtitle] ").replace(Regex("^#\\d+\\s*"), "").trim()
        val cleanTitleStr = if (!titleIsGeneric) FileUtil.cleanFileName(rawTitle) else ""

        // 6. Search candidate directories
        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.isDirectory) continue

            val files = dir.walkTopDown().maxDepth(2).filter { file ->
                file.isFile &&
                !file.name.endsWith(".part", ignoreCase = true) &&
                !file.name.endsWith(".ytdl", ignoreCase = true) &&
                !file.name.endsWith(".tmp", ignoreCase = true) &&
                file.length() > (if (isSubtitleOnly) 10L else 1024L)
            }.toList()

            for (file in files) {
                val fileName = file.name
                val isSubFile = SUBTITLE_REGEX.containsMatchIn(fileName)

                // Type filtering
                if (isSubtitleOnly && !isSubFile) continue
                if (!isSubtitleOnly && isSubFile) continue
                if (!isSubtitleOnly && THUMBNAIL_REGEX.containsMatchIn(fileName)) continue

                // Match ONLY by unique Video ID (highest confidence)
                if (resolvedId.length >= MIN_VIDEO_ID_LENGTH && fileName.contains(resolvedId)) {
                    Log.d(TAG, "Found existing file by Video ID '$resolvedId': ${file.absolutePath}")
                    return file.absolutePath
                }

                // Match by exact clean title ONLY if title is not generic and long enough
                val cleanFileNameWithoutExt = FileUtil.cleanFileName(fileName.substringBeforeLast('.'))
                    .replace(Regex("^\\d{1,4}\\s*[-_.]\\s*"), "")
                    .replace(Regex("""\.(?:[a-zA-Z]{2}(?:-[a-zA-Z]{2,4})*|auto|orig)$""", RegexOption.IGNORE_CASE), "")
                    .trim()

                if (!titleIsGeneric && cleanTitleStr.length >= 6 && cleanFileNameWithoutExt.equals(cleanTitleStr, ignoreCase = true)) {
                    Log.d(TAG, "Found existing file by exact title '$cleanTitleStr': ${file.absolutePath}")
                    return file.absolutePath
                }
            }
        }

        return null
    }
}
