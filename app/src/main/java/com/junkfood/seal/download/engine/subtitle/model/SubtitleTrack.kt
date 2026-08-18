package com.junkfood.seal.download.engine.subtitle.model

import com.junkfood.seal.util.SubtitleFormat
import kotlinx.serialization.Serializable

/**
 * SubtitleSource defines the provenance of a subtitle track.
 */
@Serializable
enum class SubtitleSource {
    MANUAL,
    AUTO_GENERATED,
    TRANSLATED,
    UNKNOWN
}

/**
 * SubtitleAvailability captures if a track is immediately downloadable or needs auth/tokens.
 */
@Serializable
enum class SubtitleAvailability {
    AVAILABLE,
    REQUIRES_AUTH,
    REQUIRES_PO_TOKEN,
    GEO_RESTRICTED,
    UNKNOWN
}

/**
 * Supported subtitle output and storage formats.
 */
@Serializable
enum class SubtitleOutputFormat(val extension: String) {
    SRT("srt"),
    VTT("vtt"),
    ASS("ass"),
    TTML("ttml"),
    LRC("lrc");

    companion object {
        fun fromExtension(ext: String?): SubtitleOutputFormat {
            return when (ext?.lowercase()?.trim()?.removePrefix(".")) {
                "srt" -> SRT
                "vtt" -> VTT
                "ass", "ssa" -> ASS
                "ttml", "xml", "srv1", "srv2", "srv3" -> TTML
                "lrc" -> LRC
                else -> SRT
            }
        }
    }
}

/**
 * Structured SubtitleTrack holding rich metadata about a caption track.
 */
@Serializable
data class SubtitleTrack(
    val languageCode: String,
    val languageName: String? = null,
    val originalLanguageCode: String? = null,
    val isAutomatic: Boolean = false,
    val isTranslated: Boolean = false,
    val isOriginal: Boolean = true,
    val formats: List<SubtitleFormat> = emptyList(),
    val source: SubtitleSource = SubtitleSource.MANUAL,
    val availability: SubtitleAvailability = SubtitleAvailability.AVAILABLE,
    val directUrl: String? = null
) {
    val displayName: String
        get() {
            val base = languageName ?: languageCode
            return when {
                isTranslated -> "$base (Auto-translated)"
                isAutomatic -> "$base (Auto-generated)"
                else -> base
            }
        }
}

/**
 * Complete inventory of discovered subtitle tracks for a media item.
 */
@Serializable
data class SubtitleInventory(
    val videoId: String,
    val title: String = "",
    val manualTracks: List<SubtitleTrack> = emptyList(),
    val autoTracks: List<SubtitleTrack> = emptyList(),
    val translatedTracks: List<SubtitleTrack> = emptyList(),
    val discoveredAt: Long = System.currentTimeMillis(),
    val extractor: String = "youtube",
    val ytDlpVersion: String? = null
) {
    val allTracks: List<SubtitleTrack>
        get() = manualTracks + autoTracks + translatedTracks

    fun isEmpty(): Boolean = allTracks.isEmpty()
    fun isNotEmpty(): Boolean = allTracks.isNotEmpty()
}
