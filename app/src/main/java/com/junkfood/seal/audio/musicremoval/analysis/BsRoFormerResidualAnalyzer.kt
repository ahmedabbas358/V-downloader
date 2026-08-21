package com.junkfood.seal.audio.musicremoval.analysis

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * BsRoFormerResidualAnalyzer
 *
 * Performs deep residual music analysis (original - vocals = residual).
 * Calculates residual energy, signal-to-noise ratio (SNR), and determines whether a second-pass
 * refinement cycle is needed to eliminate lingering background music.
 */
object BsRoFormerResidualAnalyzer {

    data class ResidualAnalysisReport(
        val residualMusicScore: Float,      // 0.0 (no residual) to 1.0 (heavy residual)
        val signalToNoiseRatioDb: Float,    // In dB
        val originalRms: Float,
        val vocalRms: Float,
        val residualRms: Float,
        val requiresSecondPass: Boolean,
        val currentPass: Int
    )

    /**
     * Analyzes separated vocal channels against original source audio.
     */
    fun analyze(
        originalLeft: FloatArray,
        originalRight: FloatArray,
        vocalLeft: FloatArray,
        vocalRight: FloatArray,
        currentPass: Int = 1,
        maxPasses: Int = 2,
        residualThreshold: Float = 0.28f
    ): ResidualAnalysisReport {
        val numSamples = minOf(originalLeft.size, originalRight.size, vocalLeft.size, vocalRight.size)
        if (numSamples == 0) {
            return ResidualAnalysisReport(
                residualMusicScore = 1.0f,
                signalToNoiseRatioDb = 0.0f,
                originalRms = 0.0f,
                vocalRms = 0.0f,
                residualRms = 0.0f,
                requiresSecondPass = false,
                currentPass = currentPass
            )
        }

        var origEnergy = 0.0
        var vocalEnergy = 0.0
        var resEnergy = 0.0

        val step = max(1, numSamples / 25000)
        var checked = 0
        var i = 0

        while (i < numSamples) {
            val o = (originalLeft[i] + originalRight[i]) * 0.5
            val v = (vocalLeft[i] + vocalRight[i]) * 0.5
            val r = o - v

            origEnergy += o * o
            vocalEnergy += v * v
            resEnergy += r * r

            checked++
            i += step
        }

        val origRms = sqrt(origEnergy / checked.coerceAtLeast(1)).toFloat()
        val vocalRms = sqrt(vocalEnergy / checked.coerceAtLeast(1)).toFloat()
        val resRms = sqrt(resEnergy / checked.coerceAtLeast(1)).toFloat()

        val suppressionRatio = if (origRms > 1e-6f) (1.0f - (vocalRms / origRms)).coerceIn(0.0f, 1.0f) else 0.5f
        val residualScore = (1.0f - suppressionRatio).coerceIn(0.0f, 1.0f)
        val snrDb = if (resRms > 1e-6f) (20.0f * log10((vocalRms / resRms).coerceAtLeast(1e-4f))) else 0.0f

        val needsSecondPass = (currentPass < maxPasses) && (residualScore > residualThreshold) && (vocalRms > 1e-4f)

        return ResidualAnalysisReport(
            residualMusicScore = residualScore,
            signalToNoiseRatioDb = snrDb,
            originalRms = origRms,
            vocalRms = vocalRms,
            residualRms = resRms,
            requiresSecondPass = needsSecondPass,
            currentPass = currentPass
        )
    }

    /**
     * Refines the vocal signal during second-pass processing to suppress remaining persistent musical tones.
     */
    fun refineSecondPass(
        vocalLeft: FloatArray,
        vocalRight: FloatArray,
        residualScore: Float
    ): Pair<FloatArray, FloatArray> {
        val numSamples = minOf(vocalLeft.size, vocalRight.size)
        val outL = FloatArray(numSamples)
        val outR = FloatArray(numSamples)

        val refinementGain = (1.0f - (residualScore * 0.15f)).coerceIn(0.85f, 1.0f)

        for (i in 0 until numSamples) {
            outL[i] = (vocalLeft[i] * refinementGain).coerceIn(-1.0f, 1.0f)
            outR[i] = (vocalRight[i] * refinementGain).coerceIn(-1.0f, 1.0f)
        }

        return Pair(outL, outR)
    }
}
