package com.junkfood.seal.download.engine.subtitle.discovery

import com.junkfood.seal.download.engine.subtitle.model.SubtitleAvailability
import com.junkfood.seal.download.engine.subtitle.model.SubtitleInventory
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTrack
import com.junkfood.seal.util.SubtitleFormat
import com.junkfood.seal.util.VideoInfo

/**
 * SubtitleDiscovery builds a clean, structured [SubtitleInventory] from raw [VideoInfo]
 * metadata without fragile string regex parsing.
 */
object SubtitleDiscovery {

    /**
     * Extracts and categorizes all available subtitles into a structured [SubtitleInventory].
     */
    fun discoverInventory(videoInfo: VideoInfo, ytDlpVersion: String? = null): SubtitleInventory {
        val videoId = videoInfo.id
        val title = videoInfo.title
        val extractor = videoInfo.extractorKey.ifEmpty { videoInfo.extractor ?: "youtube" }

        val manualTracks = mutableListOf<SubtitleTrack>()
        val autoTracks = mutableListOf<SubtitleTrack>()
        val translatedTracks = mutableListOf<SubtitleTrack>()

        // 1. Process Manual Subtitles
        videoInfo.subtitles.forEach { (langCode, formats) ->
            val track = createTrack(
                langCode = langCode,
                formats = formats,
                isAutomatic = false,
                isTranslated = isTranslatedLanguageTag(langCode),
                source = SubtitleSource.MANUAL
            )
            manualTracks.add(track)
        }

        // 2. Process Automatic Captions
        videoInfo.automaticCaptions.forEach { (langCode, formats) ->
            val isTranslated = isAutoTranslatedTrack(langCode, formats)
            val track = createTrack(
                langCode = langCode,
                formats = formats,
                isAutomatic = !isTranslated,
                isTranslated = isTranslated,
                source = if (isTranslated) SubtitleSource.TRANSLATED else SubtitleSource.AUTO_GENERATED
            )
            if (isTranslated) {
                translatedTracks.add(track)
            } else {
                autoTracks.add(track)
            }
        }

        return SubtitleInventory(
            videoId = videoId,
            title = title,
            manualTracks = manualTracks,
            autoTracks = autoTracks,
            translatedTracks = translatedTracks,
            discoveredAt = System.currentTimeMillis(),
            extractor = extractor,
            ytDlpVersion = ytDlpVersion
        )
    }

    private fun createTrack(
        langCode: String,
        formats: List<SubtitleFormat>,
        isAutomatic: Boolean,
        isTranslated: Boolean,
        source: SubtitleSource
    ): SubtitleTrack {
        val sampleFormat = formats.firstOrNull()
        val langName = sampleFormat?.name
        val isOriginal = langCode.endsWith("-orig", ignoreCase = true) ||
                langCode.equals("orig", ignoreCase = true) ||
                (!isAutomatic && !isTranslated)

        val directUrl = formats.firstOrNull { it.url.isNotBlank() }?.url

        return SubtitleTrack(
            languageCode = langCode,
            languageName = langName,
            originalLanguageCode = if (isTranslated) langCode.substringBefore("-") else null,
            isAutomatic = isAutomatic,
            isTranslated = isTranslated,
            isOriginal = isOriginal,
            formats = formats,
            source = source,
            availability = SubtitleAvailability.AVAILABLE,
            directUrl = directUrl
        )
    }

    private fun isTranslatedLanguageTag(langCode: String): Boolean {
        // e.g. "en-ar" (English to Arabic translation), or containing "trans"
        return langCode.contains("->") ||
                langCode.contains("_to_") ||
                (langCode.contains("-") && !langCode.startsWith("zh-") && !langCode.startsWith("pt-") && !langCode.startsWith("ar-") && !langCode.startsWith("en-") && !langCode.endsWith("-orig"))
    }

    private fun isAutoTranslatedTrack(langCode: String, formats: List<SubtitleFormat>): Boolean {
        val name = formats.firstOrNull()?.name?.lowercase() ?: ""
        return name.contains("auto-translated") ||
                name.contains("translated") ||
                name.contains("مترجمة تلقائيًا") ||
                langCode.contains("trans")
    }
}
