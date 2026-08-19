package com.junkfood.seal.download.engine.identity

import com.junkfood.seal.download.engine.subtitle.discovery.LanguageMatcher
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import kotlinx.serialization.Serializable

@Serializable
data class VideoIdentity(
    val videoId: String,
    val canonicalUrl: String,
    val playlistId: String? = null,
    val playlistIndex: Int? = null,
    val title: String = "",
    val durationSeconds: Int? = null,
)

@Serializable
data class SubtitleIdentity(
    val videoId: String,
    val playlistId: String? = null,
    val language: String,
    val normalizedLanguage: String = LanguageMatcher.normalizeLangCode(language),
    val source: SubtitleSource = SubtitleSource.UNKNOWN,
    val format: SubtitleOutputFormat = SubtitleOutputFormat.SRT,
)

@Serializable
enum class ContentType {
    SUBTITLE,
    AUDIO,
    VIDEO,
}

@Serializable
enum class ContentState {
    VALID,
    MISSING,
    INVALID,
    DUPLICATE,
    AMBIGUOUS,
    STALE,
    UNAVAILABLE,
}

@Serializable
enum class MatchConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
}

@Serializable
data class ContentRequirement(
    val video: VideoIdentity,
    val contentType: ContentType,
    val subtitle: SubtitleIdentity? = null,
    val expectedFormat: String? = null,
) {
    val stableKey: String
        get() =
            buildString {
                append(contentType.name)
                append(':')
                append(video.videoId)
                subtitle?.let {
                    append(':')
                    append(it.normalizedLanguage)
                    append(':')
                    append(it.source.name)
                }
            }
}

@Serializable
data class ExistingContentIdentity(
    val videoId: String?,
    val playlistId: String? = null,
    val playlistIndex: Int? = null,
    val language: String? = null,
    val normalizedLanguage: String? = language?.let { LanguageMatcher.normalizeLangCode(it) },
    val source: SubtitleSource = SubtitleSource.UNKNOWN,
)
