package com.junkfood.seal.download.engine.subtitle.discovery

import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTrack
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTypePolicy
import java.util.Locale

/**
 * Intelligent Language Matching Engine for Subtitles.
 *
 * Supports:
 * - Exact code matching (e.g. "ar" == "ar", "en-US" == "en-US")
 * - Base language matching (e.g. "ar" matches "ar-SA", "ar-EG", "ar-orig")
 * - Regional fallback (e.g. "ar-SA" falls back to "ar")
 * - Priority resolution: Manual > Auto-Generated > Auto-Translated (unless overridden by policy)
 * - Multi-language query handling ("ar,en", "ar.*", "all")
 */
object LanguageMatcher {

    private val LANGUAGE_NAME_ALIASES = mapOf(
        "العربية" to "ar",
        "arabic" to "ar",
        "انجليزي" to "en",
        "إنجليزية" to "en",
        "الانجليزية" to "en",
        "الإنجليزية" to "en",
        "english" to "en",
        "فرنسي" to "fr",
        "فرنسية" to "fr",
        "الفرنسية" to "fr",
        "french" to "fr",
        "اسباني" to "es",
        "إسباني" to "es",
        "إسبانية" to "es",
        "الإسبانية" to "es",
        "spanish" to "es",
        "ألماني" to "de",
        "ألمانية" to "de",
        "الألمانية" to "de",
        "german" to "de",
        "تركي" to "tr",
        "تركية" to "tr",
        "التركية" to "tr",
        "turkish" to "tr",
        "ياباني" to "ja",
        "يابانية" to "ja",
        "اليابانية" to "ja",
        "japanese" to "ja",
        "كوري" to "ko",
        "كورية" to "ko",
        "الكورية" to "ko",
        "korean" to "ko",
        "صيني" to "zh",
        "صينية" to "zh",
        "الصينية" to "zh",
        "chinese" to "zh",
        "روسي" to "ru",
        "روسية" to "ru",
        "الروسية" to "ru",
        "russian" to "ru",
        "هندي" to "hi",
        "هندية" to "hi",
        "الهندية" to "hi",
        "hindi" to "hi",
        "فارسي" to "fa",
        "فارسية" to "fa",
        "الفارسية" to "fa",
        "persian" to "fa",
        "أردية" to "ur",
        "اردو" to "ur",
        "urdu" to "ur"
    )

    /**
     * Normalizes a language tag to lowercase standard format (e.g. "en_US" -> "en-us", "العربية" -> "ar").
     */
    fun normalizeLangCode(code: String): String {
        val trimmed = code.trim().lowercase(Locale.US).replace('_', '-')
        return LANGUAGE_NAME_ALIASES[trimmed] ?: trimmed
    }

    /**
     * Extracts base language code without country/script variant (e.g. "ar-SA" -> "ar", "zh-Hans" -> "zh").
     */
    fun getBaseLanguageCode(code: String): String {
        val normalized = normalizeLangCode(code)
        return normalized.substringBefore('-')
    }

    /**
     * Matches requested languages against available tracks in inventory.
     *
     * @param requestedLangs Comma-separated or single language query (e.g., "ar", "ar,en", "all")
     * @param availableTracks List of all available tracks
     * @param policy User preference policy (ORIGINAL, MANUAL, AUTOMATIC, TRANSLATED, ANY)
     * @param allowAutoCaptions Whether auto-generated captions are permitted
     * @param allowTranslatedSubtitles Whether auto-translated subtitles are permitted
     * @return Ordered list of best matching [SubtitleTrack]s (no duplicates)
     */
    fun matchTracks(
        requestedLangs: String,
        availableTracks: List<SubtitleTrack>,
        policy: SubtitleTypePolicy = SubtitleTypePolicy.ANY,
        allowAutoCaptions: Boolean = true,
        allowTranslatedSubtitles: Boolean = true,
    ): List<SubtitleTrack> {
        if (availableTracks.isEmpty()) return emptyList()

        val trimmed = requestedLangs.trim()
        val isAll = trimmed.isEmpty() || trimmed.equals("all", ignoreCase = true)

        val candidateTracks = availableTracks.filter { track ->
            when (track.source) {
                SubtitleSource.MANUAL -> true
                SubtitleSource.AUTO_GENERATED -> allowAutoCaptions
                SubtitleSource.TRANSLATED -> allowTranslatedSubtitles
                SubtitleSource.UNKNOWN -> true
            }
        }

        if (isAll) {
            return filterByPolicy(candidateTracks, policy)
        }

        val queries = trimmed.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val matchedTracks = mutableListOf<SubtitleTrack>()

        for (query in queries) {
            val matchesForQuery = findBestMatchesForQuery(query, candidateTracks, policy)
            for (track in matchesForQuery) {
                if (!matchedTracks.contains(track)) {
                    matchedTracks.add(track)
                }
            }
        }

        return matchedTracks
    }

    private fun findBestMatchesForQuery(
        query: String,
        candidateTracks: List<SubtitleTrack>,
        policy: SubtitleTypePolicy
    ): List<SubtitleTrack> {
        val normQuery = normalizeLangCode(query)
        val baseQuery = getBaseLanguageCode(normQuery)

        // 1. Wildcard / regex query check (e.g. "ar.*", ".*-ar", "ar-orig")
        if (query.contains("*") || query.contains(".")) {
            val regex = runCatching { Regex("(?i)^" + query.replace("*", ".*") + "$") }.getOrNull()
            if (regex != null) {
                val wildMatches = candidateTracks.filter { regex.matches(it.languageCode) }
                if (wildMatches.isNotEmpty()) {
                    return filterByPolicy(wildMatches, policy)
                }
            }
        }

        // 2. Exact Match (e.g. "ar" == "ar" or "ar-sa" == "ar-sa")
        val exactMatches = candidateTracks.filter { normalizeLangCode(it.languageCode) == normQuery }
        if (exactMatches.isNotEmpty()) {
            return filterByPolicy(exactMatches, policy)
        }

        // 3. Base Language Match (e.g. "ar" matches "ar-SA", "ar-EG", "ar-orig")
        val baseMatches = candidateTracks.filter {
            getBaseLanguageCode(it.languageCode) == baseQuery
        }
        if (baseMatches.isNotEmpty()) {
            return filterByPolicy(baseMatches, policy)
        }

        // 4. Regional fallback if user asked for "ar-SA" but only "ar" exists
        if (normQuery.contains("-")) {
            val fallbackMatches = candidateTracks.filter {
                normalizeLangCode(it.languageCode) == baseQuery
            }
            if (fallbackMatches.isNotEmpty()) {
                return filterByPolicy(fallbackMatches, policy)
            }
        }

        return emptyList()
    }

    private fun filterByPolicy(
        tracks: List<SubtitleTrack>,
        policy: SubtitleTypePolicy
    ): List<SubtitleTrack> {
        if (tracks.isEmpty()) return emptyList()

        return when (policy) {
            SubtitleTypePolicy.ORIGINAL -> {
                val originals = tracks.filter { it.isOriginal && it.source == SubtitleSource.MANUAL }
                originals.ifEmpty { tracks.filter { it.isOriginal } }.ifEmpty { tracks }
            }
            SubtitleTypePolicy.MANUAL -> {
                tracks.filter { it.source == SubtitleSource.MANUAL }
            }
            SubtitleTypePolicy.AUTOMATIC -> {
                tracks.filter { it.source == SubtitleSource.AUTO_GENERATED }
            }
            SubtitleTypePolicy.TRANSLATED -> {
                tracks.filter { it.source == SubtitleSource.TRANSLATED }
            }
            SubtitleTypePolicy.ANY -> {
                // Priority ordering: Manual > Auto-Generated > Translated
                // Within same source, prefer exact/shorter codes
                tracks.sortedWith(
                    compareBy<SubtitleTrack> {
                        when (it.source) {
                            SubtitleSource.MANUAL -> 0
                            SubtitleSource.AUTO_GENERATED -> 1
                            SubtitleSource.TRANSLATED -> 2
                            SubtitleSource.UNKNOWN -> 3
                        }
                    }.thenByDescending { it.isOriginal }
                    .thenBy { it.languageCode.length }
                )
            }
        }
    }
}
