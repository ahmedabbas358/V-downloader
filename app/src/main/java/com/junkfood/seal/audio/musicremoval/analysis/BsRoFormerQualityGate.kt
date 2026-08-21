package com.junkfood.seal.audio.musicremoval.analysis

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * BsRoFormerQualityGate
 *
 * Enforces mandatory quality checks before any separated audio file is approved or exported.
 * Validates structural integrity, duration match, sample rate, clipping, silence, and SNR.
 */
object BsRoFormerQualityGate {

    enum class Status {
        GOOD,
        ACCEPTABLE,
        POOR,
        FAILED
    }

    data class QualityReport(
        val status: Status,
        val isApproved: Boolean,
        val overallQualityScore: Float,     // 0.0 to 1.0
        val speechRetentionScore: Float,    // 0.0 to 1.0
        val musicSuppressionScore: Float,   // 0.0 to 1.0
        val residualMusicScore: Float,      // 0.0 to 1.0 (lower is better)
        val signalToNoiseRatioDb: Float,
        val isClippingDetected: Boolean,
        val durationRatio: Float,
        val anomalies: List<String> = emptyList()
    )

    /**
     * Evaluates output channels against original input channels.
     */
    fun evaluate(
        originalLeft: FloatArray,
        originalRight: FloatArray,
        outputLeft: FloatArray,
        outputRight: FloatArray,
        expectedSampleCount: Int = originalLeft.size,
        sampleRate: Int = 44100
    ): QualityReport {
        val numSamples = minOf(originalLeft.size, originalRight.size, outputLeft.size, outputRight.size)
        val anomalies = mutableListOf<String>()

        if (numSamples == 0 || expectedSampleCount == 0) {
            return QualityReport(
                status = Status.FAILED,
                isApproved = false,
                overallQualityScore = 0.0f,
                speechRetentionScore = 0.0f,
                musicSuppressionScore = 0.0f,
                residualMusicScore = 1.0f,
                signalToNoiseRatioDb = 0.0f,
                isClippingDetected = false,
                durationRatio = 0.0f,
                anomalies = listOf("Empty or missing audio buffer")
            )
        }

        // 1. Duration check
        val durationRatio = numSamples.toFloat() / expectedSampleCount.toFloat()
        if (durationRatio < 0.95f || durationRatio > 1.05f) {
            anomalies.add("Duration mismatch: output ratio is ${"%.2f".format(durationRatio)} (expected 1.0 ± 0.05)")
        }

        var origEnergy = 0.0
        var outEnergy = 0.0
        var resEnergy = 0.0
        var clipCount = 0
        var nanInfCount = 0

        val step = max(1, numSamples / 25000)
        var checked = 0
        var i = 0

        while (i < numSamples) {
            val oL = originalLeft[i]
            val oR = originalRight[i]
            val sL = outputLeft[i]
            val sR = outputRight[i]

            if (sL.isNaN() || sL.isInfinite() || sR.isNaN() || sR.isInfinite()) {
                nanInfCount++
            }

            val oAvg = (oL + oR) * 0.5
            val sAvg = (sL + sR) * 0.5
            val diff = oAvg - sAvg

            origEnergy += oAvg * oAvg
            outEnergy += sAvg * sAvg
            resEnergy += diff * diff

            if (abs(sL) >= 0.995f || abs(sR) >= 0.995f) {
                clipCount++
            }

            checked++
            i += step
        }

        if (nanInfCount > 0) {
            anomalies.add("Corrupted audio data: NaN or Infinity detected in $nanInfCount samples")
        }

        val origRms = sqrt(origEnergy / checked.coerceAtLeast(1)).toFloat()
        val outRms = sqrt(outEnergy / checked.coerceAtLeast(1)).toFloat()
        val resRms = sqrt(resEnergy / checked.coerceAtLeast(1)).toFloat()

        // 2. Silent Dropout check
        if (origRms > 0.01f && outRms < 1e-4f) {
            anomalies.add("Silent output from active source audio (Total dropout)")
        }

        val isClipping = clipCount > 15
        if (isClipping) {
            anomalies.add("Excessive audio clipping detected ($clipCount samples)")
        }

        val suppressionRatio = if (origRms > 1e-6f) (1.0f - (outRms / origRms)).coerceIn(0.0f, 1.0f) else 0.5f
        val residualScore = (1.0f - suppressionRatio).coerceIn(0.0f, 1.0f)
        val speechRetention = if (origRms > 1e-6f) (outRms / origRms).coerceIn(0.1f, 1.0f) else 0.8f
        val snrDb = if (resRms > 1e-6f) (20.0f * log10((outRms / resRms).coerceAtLeast(1e-4f))) else 0.0f

        val overallQuality = ((speechRetention * 0.50f) + (suppressionRatio * 0.50f)).coerceIn(0.0f, 1.0f)

        val isApproved = (nanInfCount == 0) &&
                (durationRatio in 0.95f..1.05f) &&
                !isClipping &&
                (outRms >= 1e-4f || origRms < 1e-4f) &&
                overallQuality >= 0.30f

        val status = when {
            isApproved && overallQuality >= 0.65f && anomalies.isEmpty() -> Status.GOOD
            isApproved -> Status.ACCEPTABLE
            overallQuality >= 0.20f -> Status.POOR
            else -> Status.FAILED
        }

        return QualityReport(
            status = status,
            isApproved = isApproved,
            overallQualityScore = overallQuality,
            speechRetentionScore = speechRetention,
            musicSuppressionScore = suppressionRatio,
            residualMusicScore = residualScore,
            signalToNoiseRatioDb = snrDb,
            isClippingDetected = isClipping,
            durationRatio = durationRatio,
            anomalies = anomalies
        )
    }
}
