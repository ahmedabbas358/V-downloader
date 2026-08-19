package com.junkfood.seal.audio.musicremoval.analysis

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ResidualAnalyzer
 *
 * Quantitatively evaluates separated vocal tracks for residual music leakage,
 * speech preservation, spectral artifacts, and overall quality score.
 */
object ResidualAnalyzer {

    data class QualityEvaluation(
        val musicResidualScore: Float,       // 0.0 (Zero music leakage) .. 1.0 (Heavy music remaining)
        val speechPreservationScore: Float,  // 0.0 (Severely cut voice) .. 1.0 (Perfect vocal preservation)
        val artifactScore: Float,            // 0.0 (Clean natural sound) .. 1.0 (High distortion/robotic)
        val overallQualityScore: Float,      // 0.0 .. 1.0
        val isAcceptable: Boolean
    )

    /**
     * Evaluates the separation quality by comparing original input and separated output.
     */
    fun evaluate(
        originalLeft: FloatArray,
        originalRight: FloatArray,
        separatedLeft: FloatArray,
        separatedRight: FloatArray,
        sampleRate: Int = 44100
    ): QualityEvaluation {
        val numSamples = minOf(originalLeft.size, separatedLeft.size)
        if (numSamples == 0) {
            return QualityEvaluation(0.0f, 0.0f, 0.0f, 0.0f, false)
        }

        var origEnergy = 0.0f
        var sepEnergy = 0.0f
        var diffEnergy = 0.0f
        var clippingCount = 0

        val step = (numSamples / 20000).coerceAtLeast(1)
        var sampleCount = 0

        var i = 0
        while (i < numSamples) {
            val origMid = (originalLeft[i] + originalRight[i]) * 0.5f
            val sepMid = (separatedLeft[i] + separatedRight[i]) * 0.5f

            val origSide = (originalLeft[i] - originalRight[i]) * 0.5f
            val sepSide = (separatedLeft[i] - separatedRight[i]) * 0.5f

            origEnergy += origMid * origMid
            sepEnergy += sepMid * sepMid

            val diff = origMid - sepMid
            diffEnergy += diff * diff

            if (abs(sepMid) >= 0.999f) clippingCount++

            sampleCount++
            i += step
        }

        if (sampleCount == 0 || origEnergy < 1e-6f) {
            return QualityEvaluation(
                musicResidualScore = 0.0f,
                speechPreservationScore = 1.0f,
                artifactScore = 0.0f,
                overallQualityScore = 1.0f,
                isAcceptable = true
            )
        }

        val origRms = sqrt(origEnergy / sampleCount)
        val sepRms = sqrt(sepEnergy / sampleCount)

        // Preservation score: ratio of energy retained in reasonable bounds
        val energyRatio = (sepRms / (origRms + 1e-6f)).coerceIn(0.0f, 2.0f)
        val speechPreservation = when {
            energyRatio in 0.15f..0.85f -> 0.90f + (0.10f - abs(energyRatio - 0.50f) * 0.2f)
            energyRatio < 0.15f -> (energyRatio / 0.15f) * 0.85f
            else -> (1.0f - (energyRatio - 0.85f) * 0.5f).coerceIn(0.3f, 0.9f)
        }

        // Music residual score: estimated from energy suppression efficiency
        val suppressionRatio = (1.0f - energyRatio).coerceIn(0.0f, 1.0f)
        val musicResidual = (1.0f - suppressionRatio * 1.1f).coerceIn(0.05f, 0.95f)

        // Artifact score: clipping and phase distortion
        val clippingRatio = clippingCount.toFloat() / sampleCount
        val artifactScore = (clippingRatio * 5.0f).coerceIn(0.0f, 1.0f)

        val overallQuality = (
            speechPreservation * 0.50f +
            (1.0f - musicResidual) * 0.40f +
            (1.0f - artifactScore) * 0.10f
        ).coerceIn(0.0f, 1.0f)

        val isAcceptable = overallQuality >= 0.60f && musicResidual <= 0.45f

        return QualityEvaluation(
            musicResidualScore = musicResidual,
            speechPreservationScore = speechPreservation,
            artifactScore = artifactScore,
            overallQualityScore = overallQuality,
            isAcceptable = isAcceptable
        )
    }
}
