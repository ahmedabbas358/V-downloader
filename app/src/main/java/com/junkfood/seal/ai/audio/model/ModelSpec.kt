package com.junkfood.seal.ai.audio.model

/**
 * ModelSpec
 *
 * Defines metadata and technical requirements for a neural audio separation model.
 */
data class ModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val type: ModelType,
    val fileName: String,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val chunkSamples: Int = 176400, // 4.0s @ 44.1kHz
    val overlapSamples: Int = 22050, // 0.5s @ 44.1kHz
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
) {
    enum class ModelType {
        MDX_NET,
        DEMUCS,
        CONV_TASNET,
        SPECTRAL_DSP,
    }
}
