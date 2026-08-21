package com.junkfood.seal.audio.musicremoval.model

/**
 * BsRoFormerArchitecture
 *
 * Defines official neural network architectures for Band-Split RoFormer (BS-RoFormer).
 */
enum class BsRoFormerArchitecture(val displayName: String) {
    BS_ROFORMER_SW("BS-RoFormer Multi-Stem / Vocal (SW)"),
    BS_ROFORMER_VOCALS("BS-RoFormer Vocals v1"),
    BS_ROFORMER_VIPERX("BS-RoFormer ViperX Studio Vocals"),
    BS_ROFORMER_LARGE_INST("BS-RoFormer Large Ep 337 Instrumental")
}

/**
 * AudioStem
 *
 * Represents individual source stems supported by the model architecture.
 */
enum class AudioStem(val stemName: String) {
    VOCALS("vocals"),
    INSTRUMENTAL("instrumental"),
    DRUMS("drums"),
    BASS("bass"),
    GUITAR("guitar"),
    PIANO("piano"),
    OTHER("other")
}

/**
 * BsRoFormerModelSpec
 *
 * Complete specification for on-device and neural BS-RoFormer inference.
 */
data class BsRoFormerModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val architecture: BsRoFormerArchitecture,
    val fileName: String,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val chunkSamples: Int = 352800,     // 8.0s @ 44.1kHz
    val overlapSamples: Int = 88200,    // 2.0s @ 44.1kHz (25% overlap)
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val memoryRequirementMb: Int = 180,
    val supportedStems: List<AudioStem> = listOf(AudioStem.VOCALS, AudioStem.INSTRUMENTAL),
    val isPrimary: Boolean = false
)
