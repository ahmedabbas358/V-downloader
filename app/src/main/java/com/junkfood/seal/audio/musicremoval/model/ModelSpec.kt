package com.junkfood.seal.audio.musicremoval.model

/**
 * ModelSpec
 *
 * Defines metadata, architecture, input shapes, and integrity properties for an ONNX neural model.
 */
data class ModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val type: ModelType,
    val fileName: String,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val chunkSamples: Int = 176400,    // 4.0 seconds @ 44.1kHz
    val overlapSamples: Int = 22050,   // 0.5 seconds @ 44.1kHz
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val isPrimary: Boolean = false,
) {
    enum class ModelType {
        DEMUCS_V4,      // Meta Demucs v4 Hybrid Transformer (HTDemucs)
        MDX23C,         // UVR MDX23C / MDX-Net dilated DenseNet
        ROFORMER,       // BS-RoFormer / Mel-Band RoFormer
        NATIVE_DSP      // Built-in zero-download DSP fallback
    }
}
