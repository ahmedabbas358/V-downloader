package com.junkfood.seal.download.engine.builder

import com.junkfood.seal.util.CONVERT_ASS
import com.junkfood.seal.util.CONVERT_LRC
import com.junkfood.seal.util.CONVERT_SRT
import com.junkfood.seal.util.CONVERT_VTT

/**
 * SubtitleOptionBuilder
 *
 * Builds all yt-dlp subtitle-related options with intelligent language expansion,
 * format preferences, and conversion settings.
 *
 * Design Principles:
 * - Always include auto-generated and manual subtitles for maximum coverage.
 * - Expand bare language codes to include region variants (e.g., "ar" -> "ar,ar-.*,ar-orig,.*-ar").
 * - Always specify --sub-format to prefer SRT and fallback gracefully.
 * - Convert subtitles to the user's preferred format.
 */
object SubtitleOptionBuilder {

    /** Default subtitle language pattern: all available */
    private const val DEFAULT_LANG_PATTERN = "all"

    /** Preferred subtitle format order for yt-dlp */
    private const val SUB_FORMAT_PREFERENCE = "srt/best/ass/vtt/lrc"

    /**
     * Builds a yt-dlp --sub-langs value from a raw language string.
     *
     * Clean targeting:
     * - Empty or "all" -> "all"
     * - Specific code (e.g. "ar") -> "ar,ar-.*" (targets Arabic without duplicate cross-translations)
     * - Multiple codes (comma-separated) -> cleaned and joined
     */
    fun buildSubLangsOption(rawLang: String): String {
        val trimmed = rawLang.trim()
        if (trimmed.isEmpty() || trimmed.equals("all", ignoreCase = true)) {
            return DEFAULT_LANG_PATTERN
        }

        val langs = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (langs.isEmpty()) return DEFAULT_LANG_PATTERN

        val expanded = langs.flatMap { lang ->
            when {
                lang.equals("all", ignoreCase = true) -> listOf("all")
                lang.contains(".*") -> listOf(lang)
                lang.contains("-") -> listOf(lang, "${lang.substringBefore("-")}.*")
                else -> listOf(lang, "$lang-.*", ".*-$lang", "$lang-orig")
            }
        }.distinct().joinToString(",")

        return expanded
    }

    /**
     * Returns the --sub-format value for maximum compatibility.
     */
    fun getSubFormatPreference(): String = SUB_FORMAT_PREFERENCE

    /**
     * Maps the user's convertSubtitle preference to the yt-dlp --convert-subs value.
     *
     * @param convertSubtitle The integer preference for subtitle conversion format
     * @return The yt-dlp format string (e.g., "srt", "ass", "vtt", "lrc")
     */
    fun getConvertSubsValue(convertSubtitle: Int): String {
        return when (convertSubtitle) {
            CONVERT_ASS -> "ass"
            CONVERT_SRT -> "srt"
            CONVERT_VTT -> "vtt"
            CONVERT_LRC -> "lrc"
            else -> "srt" // Default to SRT for maximum compatibility
        }
    }

    /**
     * Holds a complete set of subtitle options ready to be applied to a YoutubeDLRequest.
     */
    data class SubtitleOptions(
        val writeSubs: Boolean,
        val writeAutoSubs: Boolean,
        val subLangs: String,
        val subFormat: String,
        val convertSubs: String,
        val embedSubs: Boolean,
    )

    /**
     * Builds a complete SubtitleOptions from download preferences for subtitle-only downloads
     * (--skip-download mode).
     *
     * @param subtitleLanguage Raw subtitle language preference
     * @param convertSubtitle Subtitle conversion format preference
     * @return Complete SubtitleOptions with all fields populated
     */
    fun buildForSubtitleOnlyDownload(
        subtitleLanguage: String,
        convertSubtitle: Int,
        autoSubtitle: Boolean = true,
        autoTranslatedSubtitles: Boolean = true,
    ): SubtitleOptions {
        return SubtitleOptions(
            writeSubs = true,
            writeAutoSubs = true,
            subLangs = buildSubLangsOption(subtitleLanguage),
            subFormat = SUB_FORMAT_PREFERENCE,
            convertSubs = getConvertSubsValue(convertSubtitle),
            embedSubs = false,
        )
    }

    /**
     * Builds SubtitleOptions for video/audio downloads that also include subtitles.
     *
     * @param subtitleLanguage Raw subtitle language preference
     * @param convertSubtitle Subtitle conversion format preference
     * @param autoSubtitle Whether to include auto-generated subtitles
     * @param autoTranslatedSubtitles Whether to include auto-translated subtitles
     * @param embedSubtitle Whether to embed subtitles in the container
     * @return Complete SubtitleOptions
     */
    fun buildForMediaWithSubtitles(
        subtitleLanguage: String,
        convertSubtitle: Int,
        autoSubtitle: Boolean = true,
        autoTranslatedSubtitles: Boolean = true,
        embedSubtitle: Boolean = false,
    ): SubtitleOptions {
        return SubtitleOptions(
            writeSubs = true,
            writeAutoSubs = true,
            subLangs = buildSubLangsOption(subtitleLanguage),
            subFormat = SUB_FORMAT_PREFERENCE,
            convertSubs = getConvertSubsValue(convertSubtitle),
            embedSubs = embedSubtitle,
        )
    }
}
