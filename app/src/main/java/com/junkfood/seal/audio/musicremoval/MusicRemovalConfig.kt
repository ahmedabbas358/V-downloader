package com.junkfood.seal.audio.musicremoval

/**
 * MusicRemovalConfig
 *
 * Configuration parameters for the modular Music Removal & Source Separation Engine.
 */
data class MusicRemovalConfig(
    val qualityMode: QualityMode = QualityMode.BALANCED,
    val speechPreservationLevel: SpeechPreservationLevel = SpeechPreservationLevel.HIGH,
    val secondaryModelPolicy: SecondaryModelPolicy = SecondaryModelPolicy.AUTO,
    val enableMusicDetectionGate: Boolean = true,
    val musicDetectionThreshold: Float = 0.15f,
    val speechEnhancementDb: Float = 3.5f,
    val useResultCaching: Boolean = true,
    val maxWorkerThreads: Int = 0 // 0 = Auto-detect based on CPU cores
) {

    enum class QualityMode {
        FAST,         // Lightweight native DSP / Fast MDX
        BALANCED,     // Demucs v4 Hybrid or MDX23C
        HIGH_QUALITY, // Demucs v4 with residual fallback verification
        MAX_REMOVAL   // Multi-model ensemble (Demucs + MDX + RoFormer) with aggressive suppression
    }

    enum class SpeechPreservationLevel {
        STANDARD,     // Balanced suppression
        HIGH,         // Strong formant & consonant protection
        MAXIMUM       // Preserves all subtle voice dynamics, breath, and sibilants
    }

    enum class SecondaryModelPolicy {
        AUTO,         // Engaged only when SeparationQualityEvaluator detects music leakage
        ALWAYS,       // Always run secondary model and blend
        NEVER         // Single-pass processing only
    }

    enum class DeviceProfile {
        LOW,          // Low RAM / budget CPU: chunk size 2s, native DSP / INT8
        BALANCED,     // Mid-range: chunk size 5s, FP16/FP32 Demucs or MDX
        HIGH,         // High-end: chunk size 8s, Demucs v4 + dynamic fallback
        MAX_QUALITY   // Flagship device: chunk size 10s, Multi-model ensemble
    }
}
