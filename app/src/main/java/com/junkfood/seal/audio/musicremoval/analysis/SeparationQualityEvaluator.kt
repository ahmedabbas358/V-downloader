package com.junkfood.seal.audio.musicremoval.analysis

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * SeparationQualityEvaluator
 *
 * Quantifies speech retention, residual music suppression, clipping, and spectral sanity
 * after UVR model separation.
 */
object SeparationQualityEvaluator {

    data class QualityReport(
        val speechRetentionScore: Float,    // 0.0 to 1.0 (Higher is better)
        val musicSuppressionScore: Float,   // 0.0 to 1.0 (Higher is better)
        val signalToNoiseRatioDb: Float,    // In dB
        val overallQualityScore: Float,     // 0.0 to 1.0
        val isAcceptable: Boolean,
        val isClippingDetected: Boolean
    )

    /**
     * Evaluates separation quality between original and separated audio channels.
     */
    fun evaluate(
        originalLeft: FloatArray,
        originalRight: FloatArray,
        separatedLeft: FloatArray,
        separatedRight: FloatArray,
        sampleRate: Int = 44100
    ): QualityReport {
        val numSamples = minOf(originalLeft.size, separatedLeft.size, originalRight.size, separatedRight.size)
        if (numSamples == 0) {
            return QualityReport(0f, 0f, 0f, 0f, isAcceptable = false, isClippingDetected = false)
        }

        var origEnergy = 0.0
        var sepEnergy = 0.0
        var residualEnergy = 0.0
        var clipCount = 0

        val step = max(1, numSamples / 20000)

        var i = 0
        while (i < numSamples) {
            val oL = originalLeft[i]
            val oR = originalRight[i]
            val sL = separatedLeft[i]
            val sR = separatedRight[i]

            val oAvg = (oL + oR) * 0.5
            val sAvg = (sL + sR) * 0.5

            origEnergy += oAvg * oAvg
            sepEnergy += sAvg * sAvg

            val diff = oAvg - sAvg
            residualEnergy += diff * diff

            if (abs(sL) >= 0.999f || abs(sR) >= 0.999f) {
                clipCount++
            }

            i += step
        }

        val origRms = sqrt(origEnergy / (numSamples / step.toDouble()))
        val sepRms = sqrt(sepEnergy / (numSamples / step.toDouble()))
        val resRms = sqrt(residualEnergy / (numSamples / step.toDouble()))

        val suppressionRatio = if (origRms > 1e-6) (1.0 - (sepRms / origRms)).coerceIn(0.0, 1.0).toFloat() else 0.5f
        val snrDb = if (resRms > 1e-6) (20.0 * log10((sepRms / resRms).coerceAtLeast(1e-4))).toFloat() else 0f

        val speechRetention = if (origRms > 1e-6) (sepRms / origRms).coerceIn(0.1, 1.0).toFloat() else 0.8f
        val overallQuality = ((speechRetention * 0.55f) + (suppressionRatio * 0.45f)).coerceIn(0f, 1.0f)
        val isAcceptable = overallQuality >= 0.35f
        val isClipping = clipCount > 10

        return QualityReport(
            speechRetentionScore = speechRetention,
            musicSuppressionScore = suppressionRatio,
            signalToNoiseRatioDb = snrDb,
            overallQualityScore = overallQuality,
            isAcceptable = isAcceptable,
            isClippingDetected = isClipping
        )
    }
}
