package com.junkfood.seal.audio.musicremoval.postprocessor

import kotlin.math.abs
import kotlin.math.max

/**
 * BsRoFormerPostProcessor
 *
 * Polishes the separated audio: eliminates DC offset, normalizes peaks safely, prevents clipping,
 * and maintains consistent loudness without altering pitch or tempo.
 */
object BsRoFormerPostProcessor {

    /**
     * Applies full post-processing chain to the separated audio channels.
     */
    fun process(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        applyDcOffsetRemoval: Boolean = true,
        targetPeak: Float = 0.95f
    ): Pair<FloatArray, FloatArray> {
        val numSamples = minOf(leftChannel.size, rightChannel.size)
        if (numSamples == 0) return Pair(FloatArray(0), FloatArray(0))

        val outL = FloatArray(numSamples)
        val outR = FloatArray(numSamples)

        // 1. DC Offset Removal
        var dcOffsetL = 0.0
        var dcOffsetR = 0.0
        if (applyDcOffsetRemoval) {
            val step = max(1, numSamples / 10000)
            var count = 0
            var i = 0
            while (i < numSamples) {
                dcOffsetL += leftChannel[i]
                dcOffsetR += rightChannel[i]
                count++
                i += step
            }
            if (count > 0) {
                dcOffsetL /= count
                dcOffsetR /= count
            }
        }

        // 2. Compute Peak
        var peak = 0.0f
        for (i in 0 until numSamples) {
            val sL = (leftChannel[i] - dcOffsetL.toFloat())
            val sR = (rightChannel[i] - dcOffsetR.toFloat())
            outL[i] = sL
            outR[i] = sR

            val aL = abs(sL)
            val aR = abs(sR)
            if (aL > peak) peak = aL
            if (aR > peak) peak = aR
        }

        // 3. Peak Normalization & Anti-Clipping Soft Limiter
        val gain = if (peak > 1.0f) {
            targetPeak / peak
        } else if (peak > 0.05f && peak < 0.70f) {
            (targetPeak / peak).coerceAtMost(1.35f)
        } else {
            1.0f
        }

        for (i in 0 until numSamples) {
            val vL = outL[i] * gain
            val vR = outR[i] * gain

            // Soft saturation knee to guarantee zero clipping distortion
            outL[i] = softLimit(vL)
            outR[i] = softLimit(vR)
        }

        return Pair(outL, outR)
    }

    private fun softLimit(sample: Float): Float {
        return if (sample > 0.95f) {
            0.95f + (sample - 0.95f) * 0.2f
        } else if (sample < -0.95f) {
            -0.95f + (sample + 0.95f) * 0.2f
        } else {
            sample
        }.coerceIn(-0.99f, 0.99f)
    }
}
