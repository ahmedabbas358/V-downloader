package com.junkfood.seal.audio.musicremoval.postprocessor

import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.detection.SpeechDetector
import kotlin.math.abs
import kotlin.math.pow

/**
 * SpeechProtection
 *
 * Protects human vocal clarity, naturalness, and intelligibility while
 * ensuring background music is NEVER re-introduced into the output stream.
 */
object SpeechProtection {

    /**
     * Applies vocal formant enhancement and inter-speech noise gating.
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

        // Detect active speech frames across the track
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

            if (isSpeech) {
                // In active speech frames: boost vocal presence and consonants on the clean separated track
                val presenceFactor = 1.0f + (boostLinear - 1.0f) * speechProb
                outL[i] = (pL * presenceFactor).coerceIn(-1.0f, 1.0f)
                outR[i] = (pR * presenceFactor).coerceIn(-1.0f, 1.0f)
            } else {
                // In non-speech (music-only) segments: deeply suppress any musical leakage / instrument tails
                outL[i] = (pL * 0.05f).coerceIn(-1.0f, 1.0f)
                outR[i] = (pR * 0.05f).coerceIn(-1.0f, 1.0f)
            }
        }

        return Pair(outL, outR)
    }
}
