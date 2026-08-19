package com.junkfood.seal.audio.musicremoval.postprocessor

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ResidualSuppression
 *
 * Suppresses residual harmonic leakage, faint instrument reverb tails, and background noise.
 */
object ResidualSuppression {

    /**
     * Suppresses low-level musical residuals in stereo audio.
     */
    fun suppressResiduals(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        suppressionStrength: Float = 0.85f,
        noiseFloorDb: Float = -45.0f
    ): Pair<FloatArray, FloatArray> {
        val numSamples = minOf(leftChannel.size, rightChannel.size)
        val outL = FloatArray(numSamples)
        val outR = FloatArray(numSamples)

        val threshold = 10.0f.pow(noiseFloorDb / 20.0f)

        val windowSize = 512
        val halfWin = windowSize / 2

        var env = 0.0f
        val alpha = 0.005f // Envelope smoothing

        for (i in 0 until numSamples) {
            val l = leftChannel[i]
            val r = rightChannel[i]
            val instEnergy = (l * l + r * r) * 0.5f

            env = env * (1.0f - alpha) + instEnergy * alpha
            val currentRms = sqrt(env)

            val gain = when {
                currentRms > threshold * 2.0f -> 1.0f
                currentRms < threshold -> (1.0f - suppressionStrength * 0.85f).coerceAtLeast(0.02f)
                else -> {
                    val t = (currentRms - threshold) / (threshold)
                    ((1.0f - suppressionStrength * 0.85f) + t * (suppressionStrength * 0.85f)).coerceIn(0.02f, 1.0f)
                }
            }

            outL[i] = (l * gain).coerceIn(-1.0f, 1.0f)
            outR[i] = (r * gain).coerceIn(-1.0f, 1.0f)
        }

        return Pair(outL, outR)
    }
}
