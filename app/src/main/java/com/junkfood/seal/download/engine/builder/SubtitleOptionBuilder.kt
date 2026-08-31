package com.junkfood.seal.download.engine.builder

import com.junkfood.seal.download.engine.subtitle.discovery.LanguageMatcher
import com.junkfood.seal.util.CONVERT_ASS
import com.junkfood.seal.util.CONVERT_LRC
import com.junkfood.seal.util.CONVERT_SRT
import com.junkfood.seal.util.CONVERT_VTT
import com.junkfood.seal.util.PreferenceUtil.getString

/**
 * SubtitleOptionBuilder
 *
 * Builds all yt-dlp subtitle-related options with intelligent language expansion,
 * format preferences, and conversion settings.
 *
 * Design Principles:
 * - Always include auto-generated and manual subtitles for maximum coverage.
 * - Normalize language names (e.g. "العربية" -> "ar") to ISO codes.
 * - Expand bare language codes to include region variants (e.g., "ar" -> "ar,ar-.*,ar-orig,.*-ar").
 * - Always specify --sub-format to prefer SRT and fallback gracefully.
 * - Convert subtitles to the user's preferred format.
 */
object SubtitleOptionBuilder {

    /** Default subtitle language pattern: all available */
    private const val DEFAULT_LANG_PATTERN = "all"

    /** Preferred subtitle format order for yt-dlp to support all platforms and YouTube formats */
    private const val SUB_FORMAT_PREFERENCE = "srt/best/vtt/ass/lrc/srv3/srv2/srv1"

    /**
     * Builds a yt-dlp --sub-langs value from a raw language string with intelligent language expansion.
     */
    fun buildSubLangsOption(rawLang: String): String {
        val trimmed = rawLang.trim()
        val effectiveLang = when {
            trimmed.isEmpty() -> {
                val pref = runCatching { com.junkfood.seal.util.SUBTITLE_LANGUAGE.getString().trim() }.getOrDefault("")
                if (pref.isNotEmpty() && !pref.equals("all", ignoreCase = true)) pref
                else "all"
            }
            else -> trimmed
        }

        if (effectiveLang.equals("all", ignoreCase = true)) {
            return "all"
        }

        val langs = effectiveLang.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (langs.isEmpty()) return "all"

        val expanded = mutableSetOf<String>()
        for (raw in langs) {
            val lang = LanguageMatcher.normalizeLangCode(raw)
            if (lang.equals("all", ignoreCase = true)) {
                return "all"
            }
            expanded.add(lang)
            val base = LanguageMatcher.getBaseLanguageCode(lang)
            if (base.isNotEmpty()) {
                expanded.add(base)
                expanded.add("$base.*")
                expanded.add("$base-*")
                expanded.add("$base-orig")
            }
            if (!lang.contains(".*") && !lang.contains("-") && !lang.contains("*")) {
                expanded.add("$lang.*")
                expanded.add("$lang-*")
            }
        }

        return expanded.joinToString(",")
    }

    /**
     * Builds dynamic Accept-Language HTTP header prioritizing user's selected language(s).
     */
    fun buildAcceptLanguageHeader(rawLang: String): String {
        val effective = rawLang.trim().ifBlank {
            runCatching { com.junkfood.seal.util.SUBTITLE_LANGUAGE.getString().trim() }.getOrDefault("")
        }
        val langs = effective.split(",")
            .map { LanguageMatcher.normalizeLangCode(it) }
            .filter { it.isNotEmpty() && it != "all" }
        if (langs.isEmpty()) return "en-US,en;q=0.9,ar;q=0.8,*"
        val headerParts = mutableListOf<String>()
        langs.forEachIndexed { index, lang ->
            val q = (1.0 - (index * 0.1)).coerceIn(0.6, 1.0)
            headerParts.add(String.format(java.util.Locale.US, "%s;q=%.1f", lang, q))
        }
        if (!langs.contains("en")) headerParts.add("en;q=0.7")
        headerParts.add("*;q=0.5")
        return headerParts.joinToString(",")
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
     */
    fun buildForSubtitleOnlyDownload(
        subtitleLanguage: String,
        convertSubtitle: Int,
        autoSubtitle: Boolean = true,
        autoTranslatedSubtitles: Boolean = true,
    ): SubtitleOptions {
        val shouldWriteAutoSubs = autoSubtitle || autoTranslatedSubtitles
        return SubtitleOptions(
            writeSubs = true,
            writeAutoSubs = shouldWriteAutoSubs,
            subLangs = buildSubLangsOption(subtitleLanguage),
            subFormat = SUB_FORMAT_PREFERENCE,
            convertSubs = getConvertSubsValue(convertSubtitle),
            embedSubs = false,
        )
    }

    /**
     * Builds SubtitleOptions for video/audio downloads that also include subtitles.
     */
    fun buildForMediaWithSubtitles(
        subtitleLanguage: String,
        convertSubtitle: Int,
        autoSubtitle: Boolean = true,
        autoTranslatedSubtitles: Boolean = true,
        embedSubtitle: Boolean = false,
    ): SubtitleOptions {
        val shouldWriteAutoSubs = autoSubtitle || autoTranslatedSubtitles || embedSubtitle
        val subFormat = if (embedSubtitle) "srt" else getConvertSubsValue(convertSubtitle)
        return SubtitleOptions(
            writeSubs = true,
            writeAutoSubs = shouldWriteAutoSubs,
            subLangs = buildSubLangsOption(subtitleLanguage),
            subFormat = SUB_FORMAT_PREFERENCE,
            convertSubs = subFormat,
            embedSubs = embedSubtitle,
        )
    }
}
