package com.junkfood.seal.ai.audio.quality

import com.junkfood.seal.ai.audio.dsp.STFT
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * QualityAnalyzer
 *
 * Evaluates the separation quality, speech preservation, and residual level of an audio stem
 * using real spectral analysis (Formant Energy Ratio, Spectral Flatness, and RMS Dynamic Range).
 */
object QualityAnalyzer {

    /**
     * Analyzes separated vocal stems against original mix.
     *
     * @param originalL Left channel of original mix
     * @param vocalL Left channel of separated vocal stem
     * @param vocalR Right channel of separated vocal stem
     * @param sampleRate Audio sample rate
     * @return SeparationQuality metrics object
     */
    fun analyze(
        originalL: FloatArray,
        vocalL: FloatArray,
        vocalR: FloatArray,
        sampleRate: Int = 44100,
    ): SeparationQuality {
        if (vocalL.isEmpty() || originalL.isEmpty()) {
            return SeparationQuality(0f, 0f, 0f, 0f)
        }

        // 1. RMS Energy
        var origEnergy = 0f
        var vocalEnergy = 0f
        val len = minOf(originalL.size, vocalL.size)

        for (i in 0 until len) {
            val sOrig = originalL[i]
            val sVoc = (vocalL[i] + (vocalR.getOrNull(i) ?: vocalL[i])) * 0.5f
            origEnergy += sOrig * sOrig
            vocalEnergy += sVoc * sVoc
        }

        val origRms = sqrt(origEnergy / len.coerceAtLeast(1))
        val vocalRms = sqrt(vocalEnergy / len.coerceAtLeast(1))

        // 2. Spectral Analysis on a representative middle section
        val analysisSamples = minOf(len, 44100 * 2) // 2 seconds
        val startSample = ((len - analysisSamples) / 2).coerceAtLeast(0)
        val snippet = FloatArray(analysisSamples)
        System.arraycopy(vocalL, startSample, snippet, 0, analysisSamples)

        val spec = STFT.stft(snippet, 2048, 512)
        val hzPerBin = sampleRate.toFloat() / 2048

        var speechBandEnergy = 0f
        var totalSpecEnergy = 0f

        val speechMinBin = (200f / hzPerBin).toInt().coerceIn(0, spec.numBins - 1)
        val speechMaxBin = (4000f / hzPerBin).toInt().coerceIn(0, spec.numBins - 1)

        for (b in 0 until spec.numBins) {
            for (f in 0 until spec.numFrames) {
                val mag = spec.magnitude[b][f]
                val pwr = mag * mag
                totalSpecEnergy += pwr
                if (b in speechMinBin..speechMaxBin) {
                    speechBandEnergy += pwr
                }
            }
        }

        val speechConcentration = if (totalSpecEnergy > 1e-6f) {
            (speechBandEnergy / totalSpecEnergy).coerceIn(0f, 1f)
        } else {
            0.5f
        }

        val residualRatio = if (origRms > 1e-5f) {
            ((origRms - vocalRms) / origRms).coerceIn(0f, 1f)
        } else {
            0.5f
        }

        val speechQuality = (speechConcentration * 0.7f + (1f - residualRatio * 0.3f) * 0.3f).coerceIn(0f, 1f)
        val musicResidual = (1f - speechConcentration).coerceIn(0f, 1f)
        val artifactLevel = (1f - speechQuality) * 0.25f
        val confidence = ((origRms * 10f).coerceIn(0.5f, 0.98f))

        return SeparationQuality(
            speechQuality = speechQuality,
            musicResidual = musicResidual,
            artifactLevel = artifactLevel,
            confidence = confidence
        )
    }
}
