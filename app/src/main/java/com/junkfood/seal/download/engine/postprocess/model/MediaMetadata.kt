package com.junkfood.seal.download.engine.postprocess.model

enum class MediaType {
    VIDEO, AUDIO, SUBTITLE, UNKNOWN
}

/**
 * Unified representation of media metadata for the pipeline and UI.
 * This class accurately describes the properties of a file without conflating
 * duration into subtitles or losing typing safety.
 */
data class MediaMetadata(
    val path: String,
    val fileName: String,
    val mediaType: MediaType,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val resolution: String? = null,
    val bitrate: Long? = null,
    val language: String? = null
)
