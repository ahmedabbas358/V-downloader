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
                    return filterBestByPolicy(wildMatches, policy)
                }
            }
        }

        if (policy != SubtitleTypePolicy.ANY) {
            // Specific policy filtering
            val exactMatches = candidateTracks.filter { normalizeLangCode(it.languageCode) == normQuery }
            if (exactMatches.isNotEmpty()) {
                val filtered = filterByPolicy(exactMatches, policy)
                if (filtered.isNotEmpty()) return filtered
            }

            val baseMatches = candidateTracks.filter { getBaseLanguageCode(it.languageCode) == baseQuery }
            if (baseMatches.isNotEmpty()) {
                val filtered = filterByPolicy(baseMatches, policy)
                if (filtered.isNotEmpty()) return filtered
            }

            return emptyList()
        }

        // 5-Tier Canonical Subtitle Resolution Policy:
        // Tier 1: Exact Manual
        val exactManual = candidateTracks.filter {
            normalizeLangCode(it.languageCode) == normQuery && it.source == SubtitleSource.MANUAL
        }
        if (exactManual.isNotEmpty()) return exactManual

        // Tier 2: Manual Variant (e.g. ar-SA, ar-EG for ar query)
        val manualVariant = candidateTracks.filter {
            getBaseLanguageCode(it.languageCode) == baseQuery && it.source == SubtitleSource.MANUAL
        }
        if (manualVariant.isNotEmpty()) {
            return manualVariant.sortedBy { it.languageCode.length }
        }

        // Tier 3: Exact Automatic
        val exactAuto = candidateTracks.filter {
            normalizeLangCode(it.languageCode) == normQuery && it.source == SubtitleSource.AUTO_GENERATED
        }
        if (exactAuto.isNotEmpty()) return exactAuto

        // Tier 4: Automatic Variant
        val autoVariant = candidateTracks.filter {
            getBaseLanguageCode(it.languageCode) == baseQuery && it.source == SubtitleSource.AUTO_GENERATED
        }
        if (autoVariant.isNotEmpty()) {
            return autoVariant.sortedBy { it.languageCode.length }
        }

        // Tier 5: Translated subtitle (if allowed in candidateTracks)
        val translated = candidateTracks.filter {
            (normalizeLangCode(it.languageCode) == normQuery || getBaseLanguageCode(it.languageCode) == baseQuery) &&
                it.source == SubtitleSource.TRANSLATED
        }
        if (translated.isNotEmpty()) {
            return translated.sortedBy { it.languageCode.length }
        }

        // Tier 6: Regional fallback if user requested "ar-SA" but only "ar" exists
        if (normQuery.contains("-")) {
            val fallbackMatches = candidateTracks.filter {
                normalizeLangCode(it.languageCode) == baseQuery
            }
            if (fallbackMatches.isNotEmpty()) {
                return filterBestByPolicy(fallbackMatches, policy)
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

    private fun filterBestByPolicy(
        tracks: List<SubtitleTrack>,
        policy: SubtitleTypePolicy,
    ): List<SubtitleTrack> {
        val policyFiltered = filterByPolicy(tracks, policy)
        if (policy != SubtitleTypePolicy.ANY || policyFiltered.isEmpty()) {
            return policyFiltered
        }
        val bestPriority = policyFiltered.minOf { sourcePriority(it.source) }
        return policyFiltered
            .filter { sourcePriority(it.source) == bestPriority }
            .sortedWith(compareByDescending<SubtitleTrack> { it.isOriginal }.thenBy { it.languageCode.length })
    }

    private fun sourcePriority(source: SubtitleSource): Int =
        when (source) {
            SubtitleSource.MANUAL -> 0
            SubtitleSource.AUTO_GENERATED -> 1
            SubtitleSource.TRANSLATED -> 2
            SubtitleSource.UNKNOWN -> 3
        }
}
