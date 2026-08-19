package com.junkfood.seal.audio.musicremoval.detection

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SpeechDetector
 *
 * Implements frame-by-frame Voice Activity Detection (VAD) and speech presence tracking.
 */
object SpeechDetector {

    data class VadMask(
        val isSpeechFrame: BooleanArray,
        val speechProbabilities: FloatArray,
        val overallSpeechRatio: Float
    )

    /**
     * Computes a frame-by-frame speech activity mask over the audio signal.
     */
    fun computeVadMask(
        monoSignal: FloatArray,
        sampleRate: Int = 44100,
        frameSize: Int = 1024,
        hopSize: Int = 512
    ): VadMask {
        val numFrames = (monoSignal.size - frameSize) / hopSize + 1
        if (numFrames <= 0) {
            return VadMask(
                isSpeechFrame = BooleanArray(1) { true },
                speechProbabilities = FloatArray(1) { 1.0f },
                overallSpeechRatio = 1.0f
            )
        }

        val isSpeech = BooleanArray(numFrames)
        val probs = FloatArray(numFrames)
        var speechFrameCount = 0

        // Compute noise floor estimate from lowest 10% frames
        val energies = FloatArray(numFrames)
        for (f in 0 until numFrames) {
            val start = f * hopSize
            var energy = 0.0f
            for (i in 0 until frameSize) {
                val s = monoSignal[start + i]
                energy += s * s
            }
            energies[f] = energy / frameSize
        }

        val sortedEnergies = energies.clone().apply { sort() }
        val noiseFloor = sortedEnergies[(numFrames * 0.15f).toInt()].coerceAtLeast(1e-6f)
        val speechThreshold = noiseFloor * 4.0f

        for (f in 0 until numFrames) {
            val start = f * hopSize
            var zeroCrossings = 0
            var prev = monoSignal[start]

            for (i in 1 until frameSize) {
                val s = monoSignal[start + i]
                if ((s >= 0 && prev < 0) || (s < 0 && prev >= 0)) {
                    zeroCrossings++
                }
                prev = s
            }

            val zcr = zeroCrossings.toFloat() / frameSize
            val energy = energies[f]

            // Speech characteristic: energy > threshold & normal conversational ZCR
            val energyRatio = (energy / speechThreshold).coerceIn(0.0f, 10.0f)
            val zcrSuitability = if (zcr in 0.02f..0.22f) 1.0f else (1.0f - abs(zcr - 0.12f) * 4.0f).coerceIn(0.1f, 1.0f)

            val prob = ((energyRatio / (energyRatio + 1.0f)) * 0.7f + zcrSuitability * 0.3f).coerceIn(0.0f, 1.0f)
            probs[f] = prob
            val speechDetected = prob > 0.40f

            isSpeech[f] = speechDetected
            if (speechDetected) speechFrameCount++
        }

        val ratio = speechFrameCount.toFloat() / numFrames.coerceAtLeast(1)
        return VadMask(
            isSpeechFrame = isSpeech,
            speechProbabilities = probs,
            overallSpeechRatio = ratio
        )
    }
}
