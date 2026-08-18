package com.junkfood.seal.ai.audio.separation

import com.junkfood.seal.ai.audio.pipeline.SeparationOptions
import com.junkfood.seal.ai.audio.quality.SeparationQuality

/**
 * AudioInput represents a normalized multi-channel PCM audio signal.
 */
data class AudioInput(
    val leftChannel: FloatArray,
    val rightChannel: FloatArray,
    val sampleRate: Int = 44100,
)

/**
 * SeparationResult contains the isolated vocal stem and evaluated quality metrics.
 */
data class SeparationResult(
    val vocalLeft: FloatArray,
    val vocalRight: FloatArray,
    val quality: SeparationQuality,
    val modelUsed: String,
)

/**
 * AudioSeparationEngine
 *
 * Core interface for on-device neural and spectral audio separation engines.
 */
interface AudioSeparationEngine {

    val engineName: String

    suspend fun separate(
        input: AudioInput,
        options: SeparationOptions,
        onProgress: ((Float, String) -> Unit)? = null,
    ): SeparationResult
}
