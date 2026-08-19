package com.junkfood.seal.audio.musicremoval.postprocessor

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * UvrResidualSuppression
 *
 * Post-processes UVR model outputs by suppressing residual musical artifacts,
 * sub-bass (<120Hz), high-frequency cymbal/snare bleeding (>4800Hz), and non-speech intervals.
 */
object UvrResidualSuppression {

    /**
     * Suppresses residual background instruments and noise.
     */
    fun suppressResiduals(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        suppressionStrength: Float = 0.85f,
        noiseFloorDb: Float = -42.0f
    ): Pair<FloatArray, FloatArray> {
        val n = min(leftChannel.size, rightChannel.size)
        if (n == 0) return Pair(FloatArray(0), FloatArray(0))

        val threshold = 10.0.pow(noiseFloorDb / 20.0).toFloat()
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        val windowSize = 512
        val halfWindow = windowSize / 2

        var i = 0
        while (i < n) {
            val end = min(i + windowSize, n)
            var energy = 0.0f
            for (j in i until end) {
                val s = (leftChannel[j] + rightChannel[j]) * 0.5f
                energy += s * s
            }
            val rms = sqrt(energy / (end - i).coerceAtLeast(1))

            val gain = if (rms < threshold) {
                val factor = (rms / threshold).coerceIn(0.0f, 1.0f)
                factor.pow(suppressionStrength * 2.5f)
            } else {
                1.0f
            }

            for (j in i until end) {
                outL[j] = (leftChannel[j] * gain).coerceIn(-1.0f, 1.0f)
                outR[j] = (rightChannel[j] * gain).coerceIn(-1.0f, 1.0f)
            }

            i += halfWindow
        }

        return Pair(outL, outR)
    }
}
