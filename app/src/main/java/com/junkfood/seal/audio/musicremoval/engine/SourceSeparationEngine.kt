package com.junkfood.seal.audio.musicremoval.engine

import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.analysis.ResidualAnalyzer

/**
 * AudioInput
 *
 * Input audio buffer for source separation.
 */
data class AudioInput(
    val leftChannel: FloatArray,
    val rightChannel: FloatArray,
    val sampleRate: Int = 44100
)

/**
 * SeparationResult
 *
 * Output of a source separation operation.
 */
data class SeparationResult(
    val vocalLeft: FloatArray,
    val vocalRight: FloatArray,
    val quality: ResidualAnalyzer.QualityEvaluation,
    val modelUsed: String,
    val processingTimeMs: Long = 0L
)

/**
 * SourceSeparationEngine
 *
 * Unified interface for neural and native audio separation engines.
 */
interface SourceSeparationEngine {
    val engineName: String
    val isAvailable: Boolean

    suspend fun separate(
        input: AudioInput,
        config: MusicRemovalConfig,
        onProgress: ((Float, String) -> Unit)? = null
    ): SeparationResult
}
