package com.junkfood.seal.download.engine.subtitle.youtube

/**
 * YouTube Compatibility Layer.
 *
 * Encapsulates knowledge about YouTube extractor mechanics, EJS runtime requirements,
 * subtitle stream formats, and yt-dlp option mapping.
 */
object YoutubeCompatibility {

    const val EXTRACTOR_NAME_YOUTUBE = "youtube"

    /** Preferred subtitle formats offered by YouTube CDN */
    val SUPPORTED_RAW_FORMATS = listOf("vtt", "ttml", "srv1", "srv2", "srv3", "json3")

    /**
     * Checks if a URL belongs to YouTube.
     */
    fun isYouTubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("youtube-nocookie.com")
    }

    /**
     * Extracts canonical YouTube video ID from various URL patterns.
     */
    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""(?:v=|\/v\/|youtu\.be\/|\/embed\/|\/shorts\/|\/live\/)([a-zA-Z0-9_-]{11})"""),
            Regex("""^[a-zA-Z0-9_-]{11}$""")
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return if (match.groupValues.size > 1) match.groupValues[1] else match.value
            }
        }
        return null
    }

    /**
     * Maps user preferences to standard yt-dlp subtitle parameters.
     */
    fun buildSubtitleCliOptions(
        subLangs: String,
        subFormat: String = "srt/best/ass/vtt",
        convertFormat: String = "srt",
        writeAutoSubs: Boolean = true
    ): List<Pair<String, String>> {
        val options = mutableListOf<Pair<String, String>>()
        options.add(Pair("--write-subs", ""))
        if (writeAutoSubs) {
            options.add(Pair("--write-auto-subs", ""))
        }
        if (subLangs.isNotBlank()) {
            options.add(Pair("--sub-langs", subLangs))
        }
        if (subFormat.isNotBlank()) {
            options.add(Pair("--sub-format", subFormat))
        }
        if (convertFormat.isNotBlank()) {
            options.add(Pair("--convert-subs", convertFormat))
        }
        return options
    }
}
