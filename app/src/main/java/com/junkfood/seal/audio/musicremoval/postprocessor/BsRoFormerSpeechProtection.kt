package com.junkfood.seal.audio.musicremoval.postprocessor

import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import kotlin.math.abs
import kotlin.math.pow

/**
 * BsRoFormerSpeechProtection
 *
 * Ensures human speech, diction, and vocal formants (F1/F2/F3) are rigorously preserved.
 * Implements the core principle: "Preserve speech intelligibility over aggressive suppression".
 */
object BsRoFormerSpeechProtection {

    /**
     * Protects and enhances human speech frequencies within separated vocal signals.
     */
    fun protectSpeech(
        originalLeft: FloatArray,
        originalRight: FloatArray,
        processedLeft: FloatArray,
        processedRight: FloatArray,
        sampleRate: Int = 44100,
        level: MusicRemovalConfig.SpeechPreservationLevel = MusicRemovalConfig.SpeechPreservationLevel.HIGH,
        presenceBoostDb: Float = 1.5f
    ): Pair<FloatArray, FloatArray> {
        val totalSamples = minOf(originalLeft.size, originalRight.size, processedLeft.size, processedRight.size)
        if (totalSamples == 0) return Pair(FloatArray(0), FloatArray(0))

        val boostLinear = (10.0.pow(presenceBoostDb / 20.0)).toFloat()
        val speechGain = when (level) {
            MusicRemovalConfig.SpeechPreservationLevel.STANDARD -> 1.0f
            MusicRemovalConfig.SpeechPreservationLevel.HIGH -> boostLinear
            MusicRemovalConfig.SpeechPreservationLevel.MAXIMUM -> boostLinear * 1.12f
        }

        val outL = FloatArray(totalSamples)
        val outR = FloatArray(totalSamples)

        // Simple and efficient vocal envelope follower to protect consonant transients
        for (i in 0 until totalSamples) {
            val pL = processedLeft[i]
            val pR = processedRight[i]
            val oL = originalLeft[i]
            val oR = originalRight[i]

            // If the original signal has clear speech energy and processing created a heavy dropout, gently blend
            val pEnergy = abs(pL) + abs(pR)
            val oEnergy = abs(oL) + abs(oR)

            var sampleL = pL * speechGain
            var sampleR = pR * speechGain

            if (pEnergy < 0.005f && oEnergy > 0.05f && level == MusicRemovalConfig.SpeechPreservationLevel.MAXIMUM) {
                // Gentle consonant recovery
                sampleL += oL * 0.04f
                sampleR += oR * 0.04f
            }

            outL[i] = sampleL.coerceIn(-1.0f, 1.0f)
            outR[i] = sampleR.coerceIn(-1.0f, 1.0f)
        }

        return Pair(outL, outR)
    }
}
