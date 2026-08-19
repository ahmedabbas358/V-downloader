package com.junkfood.seal.audio.musicremoval.detection

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * MusicDetector
 *
 * Performs fast pre-inference acoustic analysis to detect background music,
 * instrumentation energy, and rhythm elements.
 */
object MusicDetector {

    data class DetectionResult(
        val hasMusic: Boolean,
        val musicScore: Float,         // 0.0 (Pure speech/silence) .. 1.0 (Loud instrumentation)
        val speechScore: Float,        // 0.0 .. 1.0
        val isSpeechOnly: Boolean,
        val isMusicHeavy: Boolean,
        val estimatedSnrDb: Float,
    )

    /**
     * Analyzes audio channels and determines if music removal processing is needed.
     */
    fun analyze(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int = 44100,
        threshold: Float = 0.15f
    ): DetectionResult {
        val numSamples = minOf(leftChannel.size, rightChannel.size)
        if (numSamples < sampleRate / 2) {
            // Very short audio clip (<0.5s), default to processing
            return DetectionResult(
                hasMusic = true,
                musicScore = 0.5f,
                speechScore = 0.5f,
                isSpeechOnly = false,
                isMusicHeavy = false,
                estimatedSnrDb = 15.0f
            )
        }

        // Subsample for fast detection (analyze up to 10 seconds spread throughout the track)
        val step = (numSamples / 44100).coerceAtLeast(1)
        var totalEnergy = 0.0f
        var sideEnergy = 0.0f
        var lowEnergy = 0.0f
        var midEnergy = 0.0f
        var highEnergy = 0.0f
        var zeroCrossings = 0
        var sampleCount = 0

        var prevSample = 0.0f
        var i = 0
        while (i < numSamples) {
            val l = leftChannel[i]
            val r = rightChannel[i]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f

            val midAbs = abs(mid)
            val sideAbs = abs(side)

            totalEnergy += midAbs * midAbs
            sideEnergy += sideAbs * sideAbs

            // Simple differentiator for high freq approximation
            val diff = abs(mid - prevSample)
            highEnergy += diff * diff

            // Zero-crossing count for speech/noise texture
            if ((mid >= 0 && prevSample < 0) || (mid < 0 && prevSample >= 0)) {
                zeroCrossings++
            }

            prevSample = mid
            sampleCount++
            i += step
        }

        if (sampleCount == 0 || totalEnergy < 1e-6f) {
            return DetectionResult(
                hasMusic = false,
                musicScore = 0.0f,
                speechScore = 0.0f,
                isSpeechOnly = true,
                isMusicHeavy = false,
                estimatedSnrDb = 0.0f
            )
        }

        val rms = sqrt(totalEnergy / sampleCount)
        val sideRms = sqrt(sideEnergy / sampleCount)
        val zcr = zeroCrossings.toFloat() / sampleCount

        // Music indicators:
        // 1. Significant Stereo Side energy (Stereo instruments/reverb vs center-panned speech)
        // 2. Continuous high spectral density
        val stereoSpread = (sideRms / (rms + 1e-6f)).coerceIn(0.0f, 1.0f)
        val zcrFeature = (zcr / 0.15f).coerceIn(0.0f, 1.0f)

        // Estimated Music Score
        val rawMusicScore = (stereoSpread * 0.6f + (1.0f - abs(zcrFeature - 0.5f) * 2.0f) * 0.4f)
            .coerceIn(0.0f, 1.0f)

        // Voice presence estimation (human speech typical ZCR is 0.03 - 0.12)
        val isLikelySpeech = zcr in 0.02f..0.25f && rms > 0.005f
        val speechScore = if (isLikelySpeech) (1.0f - stereoSpread * 0.5f).coerceIn(0.2f, 1.0f) else 0.2f

        val hasMusic = rawMusicScore >= threshold || stereoSpread > 0.12f
        val isSpeechOnly = !hasMusic && speechScore > 0.4f
        val isMusicHeavy = rawMusicScore > 0.55f || stereoSpread > 0.45f

        val snrDb = 20.0f * log10((rms / (sideRms + 1e-5f)).coerceAtLeast(1.0f))

        return DetectionResult(
            hasMusic = hasMusic,
            musicScore = rawMusicScore,
            speechScore = speechScore,
            isSpeechOnly = isSpeechOnly,
            isMusicHeavy = isMusicHeavy,
            estimatedSnrDb = snrDb
        )
    }
}
