package com.junkfood.seal.audio.musicremoval.model

/**
 * UvrModelArchitecture
 *
 * The official neural network architectures used by Ultimate Vocal Remover (UVR).
 */
enum class UvrModelArchitecture(val displayName: String) {
    MDX_NET("UVR MDX-Net"),
    MDX23C("UVR MDX23C Dilated Spectrogram"),
    VR_ARCH("UVR Cascaded VR Architecture"),
    DEMUCS_V4("UVR HTDemucs v4 Hybrid Transformer")
}

/**
 * UvrModelSpec
 *
 * Complete specification of a verified UVR model for on-device inference.
 */
data class UvrModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val architecture: UvrModelArchitecture,
    val fileName: String,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val chunkSamples: Int = 176400,    // 4.0s @ 44.1kHz
    val overlapSamples: Int = 22050,   // 0.5s @ 44.1kHz
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val memoryRequirementMb: Int = 120,
    val isPrimary: Boolean = false
)
