package com.junkfood.seal.audio.musicremoval.preprocessor

/**
 * AudioMetadata
 *
 * Comprehensive metadata extracted from source audio/video containers.
 */
data class AudioMetadata(
    val codec: String,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
    val bitrate: Long = 0L,
    val format: String,
    val bitDepth: Int = 16,
    val fileSizeBytes: Long = 0L
)
