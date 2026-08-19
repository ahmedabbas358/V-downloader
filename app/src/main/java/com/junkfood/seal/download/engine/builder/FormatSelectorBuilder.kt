package com.junkfood.seal.download.engine.builder

import androidx.annotation.CheckResult
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FORMAT_COMPATIBILITY
import com.junkfood.seal.util.FORMAT_QUALITY
import com.junkfood.seal.util.HIGH
import com.junkfood.seal.util.LOW
import com.junkfood.seal.util.M4A
import com.junkfood.seal.util.MEDIUM
import com.junkfood.seal.util.OPUS
import com.junkfood.seal.util.connectWithDelimiter

/**
 * FormatSelectorBuilder
 *
 * Builds yt-dlp format selector strings, guaranteeing that audio is ALWAYS
 * merged with video for any quality/resolution selection. YouTube 720p+
 * streams are video-only DASH, so downloading without +bestaudio produces
 * silent video.
 *
 * Design Principles:
 * - Every video format selection MUST include +bestaudio/best fallback.
 * - Resolution-based selections use height-capped selectors with audio merge.
 * - Custom format ID strings are validated and augmented if needed.
 * - Format sorters prioritize codec and resolution cleanly.
 */
object FormatSelectorBuilder {

    /**
     * Builds a yt-dlp format selector from user preferences.
     * Guarantees audio is always merged with video selections.
     *
     * @param preferences The download preferences containing format/resolution info
     * @return A format selector string for the -f option, or empty if default should be used
     */
    fun buildFormatSelector(preferences: DownloadPreferences): String {
        return preferences.run {
            when {
                // Skip download (subtitle-only) — no format needed
                skipDownload -> ""

                // Audio-only extraction — no video format needed
                extractAudio -> ""

                // User selected a specific format ID
                formatIdString.isNotEmpty() -> ensureAudioMerged(formatIdString, mergeAudioStream)

                // Resolution-based automatic selection
                else -> buildResolutionSelector(videoResolution)
            }
        }
    }

    private val KNOWN_AUDIO_FORMATS = setOf(
        "139", "140", "141", "249", "250", "251", "256", "258", "325", "327", "328", "599", "600",
        "ba", "wa", "bestaudio", "worstaudio", "audio", "m4a", "opus", "aac", "mp3", "flac", "ogg"
    )

    /**
     * Ensures that a format ID string always includes audio merging.
     *
     * Rules:
     * - If format already contains audio indicators (+ba, bestaudio, /best, known audio itags), leave as-is.
     * - If format is a bare video format ID, append +bestaudio/best.
     * - If format contains multiple video IDs joined by +, treat as multi-stream.
     */
    fun ensureAudioMerged(formatId: String, hasMultipleAudioStreams: Boolean = false): String {
        if (formatId.isBlank()) return ""

        val parts = formatId.split("+", "/")
        val hasAudio = parts.any { part ->
            val clean = part.trim().lowercase()
            clean in KNOWN_AUDIO_FORMATS ||
            clean.contains("ba") ||
            clean.contains("bestaudio") ||
            clean.contains("wa") ||
            clean.contains("worstaudio") ||
            clean.contains("audio") ||
            clean == "b" || clean.startsWith("b[")
        } || formatId.contains("/best", ignoreCase = true)

        if (hasAudio) return formatId

        // Bare format ID(s) — append audio merge
        return "$formatId+bestaudio/best"
    }

    /**
     * Builds a resolution-capped format selector with guaranteed audio merge.
     *
     * @param resolution Resolution preference index:
     *   0 = Best available
     *   1 = 2160p (4K)
     *   2 = 1440p
     *   3 = 1080p
     *   4 = 720p
     *   5 = 480p
     *   6 = 360p
     *   7 = Worst
     */
    fun buildResolutionSelector(resolution: Int): String {
        return when (resolution) {
            1 -> "bv*[height<=2160]+ba/b[height<=2160]/bv*+ba/b"
            2 -> "bv*[height<=1440]+ba/b[height<=1440]/bv*+ba/b"
            3 -> "bv*[height<=1080]+ba/b[height<=1080]/bv*+ba/b"
            4 -> "bv*[height<=720]+ba/b[height<=720]/bv*+ba/b"
            5 -> "bv*[height<=480]+ba/b[height<=480]/bv*+ba/b"
            6 -> "bv*[height<=360]+ba/b[height<=360]/bv*+ba/b"
            7 -> "wv*+wa/w/b"
            else -> "bv*+ba/b"
        }
    }

    /**
     * Builds a format selector for playlist items with a maximum height constraint.
     *
     * @param maxHeight Maximum video height (e.g., 1080, 720)
     * @return Format selector string with audio merge
     */
    fun buildPlaylistFormatSelector(maxHeight: Int): String {
        return if (maxHeight > 0) {
            "bestvideo[height<=$maxHeight]+bestaudio/bestvideo+bestaudio/best"
        } else {
            "bestvideo+bestaudio/best"
        }
    }

    @CheckResult
    fun toAudioFormatSorter(preferences: DownloadPreferences): String =
        preferences.run {
            if (!useCustomAudioPreset) return@run ""
            val format =
                when (audioFormat) {
                    M4A -> "acodec:aac"
                    OPUS -> "acodec:opus"
                    else -> ""
                }
            val quality =
                when (audioQuality) {
                    HIGH -> "abr~192"
                    MEDIUM -> "abr~128"
                    LOW -> "abr~64"
                    else -> ""
                }
            return@run connectWithDelimiter(format, quality, delimiter = ",")
        }

    @CheckResult
    fun toVideoFormatSorter(preferences: DownloadPreferences): String =
        preferences.run {
            val format =
                when (videoFormat) {
                    FORMAT_COMPATIBILITY -> "proto,vcodec:h264,ext"
                    FORMAT_QUALITY ->
                        if (supportAv1HardwareDecoding) {
                            "vcodec:av01"
                        } else {
                            "vcodec:vp9.2"
                        }
                    else -> ""
                }
            val res =
                when (videoResolution) {
                    1 -> "res:2160"
                    2 -> "res:1440"
                    3 -> "res:1080"
                    4 -> "res:720"
                    5 -> "res:480"
                    6 -> "res:360"
                    7 -> "+res"
                    else -> ""
                }
            val sorter = if (videoFormat == FORMAT_COMPATIBILITY) {
                connectWithDelimiter(format, res, delimiter = ",")
            } else {
                connectWithDelimiter(res, format, delimiter = ",")
            }
            return@run sorter
        }

    @CheckResult
    fun toFormatSorter(preferences: DownloadPreferences): String =
        connectWithDelimiter(
            toVideoFormatSorter(preferences),
            toAudioFormatSorter(preferences),
            delimiter = ",",
        )
}
