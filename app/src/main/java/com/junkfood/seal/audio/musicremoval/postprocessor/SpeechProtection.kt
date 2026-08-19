package com.junkfood.seal.audio.musicremoval.postprocessor

import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.detection.SpeechDetector
import kotlin.math.abs
import kotlin.math.pow

/**
 * SpeechProtection
 *
 * Protects human vocal clarity, naturalness, consonants, and sibilants from being overly muted.
 */
object SpeechProtection {

    /**
     * Applies vocal formant preservation and speech clarity boosting.
     */
    fun protectSpeech(
        originalLeft: FloatArray,
        originalRight: FloatArray,
        processedLeft: FloatArray,
        processedRight: FloatArray,
        sampleRate: Int = 44100,
        level: MusicRemovalConfig.SpeechPreservationLevel = MusicRemovalConfig.SpeechPreservationLevel.HIGH,
        presenceBoostDb: Float = 3.5f
    ): Pair<FloatArray, FloatArray> {
        val numSamples = minOf(originalLeft.size, processedLeft.size)
        val outL = FloatArray(numSamples)
        val outR = FloatArray(numSamples)

        val boostLinear = 10.0f.pow(presenceBoostDb / 20.0f)
        val blendOrigWeight = when (level) {
            MusicRemovalConfig.SpeechPreservationLevel.STANDARD -> 0.05f
            MusicRemovalConfig.SpeechPreservationLevel.HIGH -> 0.12f
            MusicRemovalConfig.SpeechPreservationLevel.MAXIMUM -> 0.22f
        }

        // Detect active speech frames
        val midSignal = FloatArray(numSamples) { i -> (originalLeft[i] + originalRight[i]) * 0.5f }
        val vad = SpeechDetector.computeVadMask(midSignal, sampleRate)

        val frameSize = 1024
        val hopSize = 512

        for (i in 0 until numSamples) {
            val frameIdx = (i / hopSize).coerceIn(0, vad.isSpeechFrame.lastIndex)
            val isSpeech = vad.isSpeechFrame[frameIdx]
            val speechProb = vad.speechProbabilities[frameIdx]

            val pL = processedLeft[i]
            val pR = processedRight[i]
            val oL = originalLeft[i]
            val oR = originalRight[i]

            if (isSpeech) {
                // In active speech frames: apply clarity boost + subtle original formant bleed to keep naturalness
                val boostedL = pL * (1.0f + (boostLinear - 1.0f) * speechProb)
                val boostedR = pR * (1.0f + (boostLinear - 1.0f) * speechProb)

                val effectiveBlend = blendOrigWeight * speechProb
                outL[i] = (boostedL * (1.0f - effectiveBlend) + oL * effectiveBlend).coerceIn(-1.0f, 1.0f)
                outR[i] = (boostedR * (1.0f - effectiveBlend) + oR * effectiveBlend).coerceIn(-1.0f, 1.0f)
            } else {
                // In non-speech (music-only) segments: output pure separated audio
                outL[i] = pL.coerceIn(-1.0f, 1.0f)
                outR[i] = pR.coerceIn(-1.0f, 1.0f)
            }
        }

        return Pair(outL, outR)
    }
}
