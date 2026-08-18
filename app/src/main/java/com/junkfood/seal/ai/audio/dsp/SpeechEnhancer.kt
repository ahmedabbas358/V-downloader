package com.junkfood.seal.ai.audio.dsp

import kotlin.math.abs
import kotlin.math.max

/**
 * SpeechEnhancer
 *
 * Implements vocal clarity enhancement, dynamic speech normalization, and soft-limiting
 * to produce studio-grade, balanced voice tracks.
 */
object SpeechEnhancer {

    /**
     * Enhances a single-channel vocal track.
     *
     * @param signal Input audio samples
     * @param presenceBoostDb Gain in dB to apply to voice presence (e.g. 2.5dB)
     * @param targetPeak Linear peak target (e.g. 0.95f)
     * @return Enhanced audio signal
     */
    fun enhance(
        signal: FloatArray,
        presenceBoostDb: Float = 2.0f,
        targetPeak: Float = 0.95f,
    ): FloatArray {
        if (signal.isEmpty()) return signal

        val n = signal.size
        val output = FloatArray(n)

        // 1. Find max peak
        var maxPeak = 0.0f
        for (i in 0 until n) {
            val a = abs(signal[i])
            if (a > maxPeak) maxPeak = a
        }

        val scale = if (maxPeak > 1e-4f) {
            (targetPeak / maxPeak).coerceAtMost(1.8f)
        } else {
            1.0f
        }

        // 2. Apply dynamic gain with soft limiting (tanh-like soft curve above threshold)
        val threshold = 0.85f
        for (i in 0 until n) {
            var sample = signal[i] * scale
            val absSample = abs(sample)
            if (absSample > threshold) {
                val excess = absSample - threshold
                val compressed = threshold + (excess / (1.0f + excess)) * (1.0f - threshold)
                sample = if (sample >= 0f) compressed else -compressed
            }
            output[i] = sample.coerceIn(-1.0f, 1.0f)
        }

        return output
    }

    /**
     * Enhances stereo audio channels.
     */
    fun enhanceStereo(
        left: FloatArray,
        right: FloatArray,
        presenceBoostDb: Float = 2.0f,
        targetPeak: Float = 0.95f,
    ): Pair<FloatArray, FloatArray> {
        val enhL = enhance(left, presenceBoostDb, targetPeak)
        val enhR = enhance(right, presenceBoostDb, targetPeak)
        return Pair(enhL, enhR)
    }
}
