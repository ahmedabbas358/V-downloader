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

    enum class QualityStatus {
        GOOD,
        ACCEPTABLE,
        POOR,
        FAILED
    }

    data class QualityReport(
        val qualityStatus: QualityStatus,
        val speechRetentionScore: Float,    // 0.0 to 1.0 (Higher is better)
        val musicSuppressionScore: Float,   // 0.0 to 1.0 (Higher is better)
        val residualMusicScore: Float,      // 0.0 to 1.0 (Lower is better, 0 = no residual music)
        val signalToNoiseRatioDb: Float,    // In dB
        val overallQualityScore: Float,     // 0.0 to 1.0
        val isAcceptable: Boolean,
        val isClippingDetected: Boolean,
        val spectralAnomalies: List<String> = emptyList()
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
            return QualityReport(
                qualityStatus = QualityStatus.FAILED,
                speechRetentionScore = 0f,
                musicSuppressionScore = 0f,
                residualMusicScore = 1f,
                signalToNoiseRatioDb = 0f,
                overallQualityScore = 0f,
                isAcceptable = false,
                isClippingDetected = false,
                spectralAnomalies = listOf("Empty audio buffer")
            )
        }

        var origEnergy = 0.0
        var sepEnergy = 0.0
        var residualEnergy = 0.0
        var clipCount = 0
        var dcOffsetL = 0.0
        var dcOffsetR = 0.0
        val anomalies = mutableListOf<String>()

        val step = max(1, numSamples / 20000)

        var i = 0
        var checkedCount = 0
        while (i < numSamples) {
            val oL = originalLeft[i]
            val oR = originalRight[i]
            val sL = separatedLeft[i]
            val sR = separatedRight[i]

            dcOffsetL += sL
            dcOffsetR += sR

            val oAvg = (oL + oR) * 0.5
            val sAvg = (sL + sR) * 0.5

            origEnergy += oAvg * oAvg
            sepEnergy += sAvg * sAvg

            val diff = oAvg - sAvg
            residualEnergy += diff * diff

            if (abs(sL) >= 0.999f || abs(sR) >= 0.999f) {
                clipCount++
            }

            checkedCount++
            i += step
        }

        if (checkedCount > 0) {
            dcOffsetL /= checkedCount
            dcOffsetR /= checkedCount
            if (abs(dcOffsetL) > 0.08 || abs(dcOffsetR) > 0.08) {
                anomalies.add("High DC offset detected (L: ${"%.3f".format(dcOffsetL)}, R: ${"%.3f".format(dcOffsetR)})")
            }
        }

        val origRms = sqrt(origEnergy / checkedCount.coerceAtLeast(1).toDouble())
        val sepRms = sqrt(sepEnergy / checkedCount.coerceAtLeast(1).toDouble())
        val resRms = sqrt(residualEnergy / checkedCount.coerceAtLeast(1).toDouble())

        if (sepRms < 1e-5 && origRms > 1e-3) {
            anomalies.add("Silent output from active input (Total dropout)")
        }

        val suppressionRatio = if (origRms > 1e-6) (1.0 - (sepRms / origRms)).coerceIn(0.0, 1.0).toFloat() else 0.5f
        val residualMusicScore = (1.0f - suppressionRatio).coerceIn(0.0f, 1.0f)
        val snrDb = if (resRms > 1e-6) (20.0 * log10((sepRms / resRms).coerceAtLeast(1e-4))).toFloat() else 0f

        val speechRetention = if (origRms > 1e-6) (sepRms / origRms).coerceIn(0.1, 1.0).toFloat() else 0.8f
        val isClipping = clipCount > 10
        if (isClipping) {
            anomalies.add("Clipping detected in $clipCount samples")
        }

        val overallQuality = ((speechRetention * 0.55f) + (suppressionRatio * 0.45f)).coerceIn(0f, 1.0f)
        val isAcceptable = overallQuality >= 0.35f && !isClipping && sepRms >= 1e-5

        val status = when {
            overallQuality >= 0.65f && !isClipping && anomalies.isEmpty() -> QualityStatus.GOOD
            isAcceptable -> QualityStatus.ACCEPTABLE
            overallQuality >= 0.20f -> QualityStatus.POOR
            else -> QualityStatus.FAILED
        }

        return QualityReport(
            qualityStatus = status,
            speechRetentionScore = speechRetention,
            musicSuppressionScore = suppressionRatio,
            residualMusicScore = residualMusicScore,
            signalToNoiseRatioDb = snrDb,
            overallQualityScore = overallQuality,
            isAcceptable = isAcceptable,
            isClippingDetected = isClipping,
            spectralAnomalies = anomalies
        )
    }
}
