package com.junkfood.seal.audio.musicremoval

/**
 * MusicRemovalConfig
 *
 * Configuration parameters for the Band-Split RoFormer (BS-RoFormer) Production-Grade Music Removal Engine.
 */
data class MusicRemovalConfig(
    val primaryModelId: String = "bs_roformer_sw",
    val qualityMode: QualityMode = QualityMode.BALANCED,
    val speechPreservationLevel: SpeechPreservationLevel = SpeechPreservationLevel.HIGH,
    val chunkSeconds: Float = 8.0f,
    val overlapRatio: Float = 0.25f,
    val maxPasses: Int = 2,
    val enableQualityGate: Boolean = true,
    val enableResidualAnalysis: Boolean = true,
    val residualThreshold: Float = 0.28f,
    val speechEnhancementDb: Float = 2.0f,
    val useResultCaching: Boolean = true,
    val maxWorkerThreads: Int = 0 // 0 = Auto-detect based on CPU cores
) {

    enum class QualityMode {
        FAST,         // Fast Band-Split separation with light overlap
        BALANCED,     // Standard 8s chunk BS-RoFormer with balanced residual suppression
        HIGH_QUALITY, // BS-RoFormer with residual analysis and speech protection
        MAX_REMOVAL   // Deep multi-pass BS-RoFormer with maximum instrumental suppression
    }

    enum class SpeechPreservationLevel {
        STANDARD,     // Balanced suppression
        HIGH,         // Strong vocal formant & diction protection (Recommended)
        MAXIMUM       // Maximum speech retention with subtle voice dynamic protection
    }
}
