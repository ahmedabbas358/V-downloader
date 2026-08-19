package com.junkfood.seal.audio.musicremoval.preprocessor

import kotlin.math.abs
import kotlin.math.max

/**
 * UvrAudioPreprocessor
 *
 * Prepares raw decoded audio buffers for UVR model inference:
 * - Dynamic range normalization
 * - Stereo channel alignment
 * - Gain tracking for bit-perfect post-processing restoration
 */
object UvrAudioPreprocessor {

    data class PreprocessedAudio(
        val leftChannel: FloatArray,
        val rightChannel: FloatArray,
        val normalizationGain: Float,
        val originalPeak: Float
    )

    /**
     * Preprocesses audio channels with normalization and peak protection.
     */
    fun preprocess(
        rawLeft: FloatArray,
        rawRight: FloatArray,
        sampleRate: Int = 44100
    ): PreprocessedAudio {
        val numSamples = minOf(rawLeft.size, rawRight.size)
        if (numSamples == 0) {
            return PreprocessedAudio(FloatArray(0), FloatArray(0), 1.0f, 0.0f)
        }

        var peak = 0.0f
        for (i in 0 until numSamples) {
            peak = max(peak, abs(rawLeft[i]))
            peak = max(peak, abs(rawRight[i]))
        }

        val targetPeak = 0.95f
        val normGain = if (peak > 1e-4f) (targetPeak / peak).coerceIn(0.5f, 2.0f) else 1.0f

        val normLeft = FloatArray(numSamples)
        val normRight = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            normLeft[i] = (rawLeft[i] * normGain).coerceIn(-1.0f, 1.0f)
            normRight[i] = (rawRight[i] * normGain).coerceIn(-1.0f, 1.0f)
        }

        return PreprocessedAudio(
            leftChannel = normLeft,
            rightChannel = normRight,
            normalizationGain = normGain,
            originalPeak = peak
        )
    }

    /**
     * Restores original dynamic gain after UVR model separation.
     */
    fun restoreGain(
        left: FloatArray,
        right: FloatArray,
        normalizationGain: Float
    ): Pair<FloatArray, FloatArray> {
        val invGain = if (normalizationGain > 1e-4f) 1.0f / normalizationGain else 1.0f
        val n = minOf(left.size, right.size)
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        for (i in 0 until n) {
            outL[i] = (left[i] * invGain).coerceIn(-1.0f, 1.0f)
            outR[i] = (right[i] * invGain).coerceIn(-1.0f, 1.0f)
        }

        return Pair(outL, outR)
    }
}
