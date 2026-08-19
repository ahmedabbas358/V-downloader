package com.junkfood.seal.audio.musicremoval.preprocessor

import kotlin.math.abs
import kotlin.math.max

/**
 * AudioPreprocessor
 *
 * Validates, conditions, and normalizes audio waveforms for neural and spectral source separation.
 */
object AudioPreprocessor {

    data class PreprocessedAudio(
        val leftChannel: FloatArray,
        val rightChannel: FloatArray,
        val sampleRate: Int,
        val originalPeak: Float,
        val normalizationGain: Float
    )

    /**
     * Preprocesses and normalizes stereo channels.
     */
    fun preprocess(
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int = 44100,
        targetPeak: Float = 0.95f
    ): PreprocessedAudio {
        val numSamples = minOf(left.size, right.size)
        val procLeft = FloatArray(numSamples)
        val procRight = FloatArray(numSamples)

        var peak = 0.0f
        for (i in 0 until numSamples) {
            val absL = abs(left[i])
            val absR = abs(right[i])
            peak = max(peak, max(absL, absR))
        }

        val gain = if (peak > 0.001f) (targetPeak / peak).coerceIn(0.1f, 10.0f) else 1.0f

        for (i in 0 until numSamples) {
            procLeft[i] = (left[i] * gain).coerceIn(-1.0f, 1.0f)
            procRight[i] = (right[i] * gain).coerceIn(-1.0f, 1.0f)
        }

        return PreprocessedAudio(
            leftChannel = procLeft,
            rightChannel = procRight,
            sampleRate = sampleRate,
            originalPeak = peak,
            normalizationGain = gain
        )
    }

    /**
     * Restores original gain dynamics if desired.
     */
    fun restoreGain(
        left: FloatArray,
        right: FloatArray,
        normalizationGain: Float
    ): Pair<FloatArray, FloatArray> {
        if (abs(normalizationGain - 1.0f) < 0.01f || normalizationGain <= 0.0f) {
            return Pair(left, right)
        }

        val invGain = 1.0f / normalizationGain
        val numSamples = minOf(left.size, right.size)
        val outL = FloatArray(numSamples)
        val outR = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            outL[i] = (left[i] * invGain).coerceIn(-1.0f, 1.0f)
            outR[i] = (right[i] * invGain).coerceIn(-1.0f, 1.0f)
        }

        return Pair(outL, outR)
    }
}
