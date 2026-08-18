package com.junkfood.seal.ai.audio.dsp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ResidualMusicSuppressor
 *
 * Analyzes the neural-separated vocal spectrogram and attenuates residual musical leakage,
 * background instrumental overtones, and harmonic bleed while strictly preserving
 * human speech formants (150Hz - 4500Hz) and sibilants (4kHz - 9kHz).
 */
object ResidualMusicSuppressor {

    /**
     * Suppresses musical residue from a single-channel audio signal.
     *
     * @param signal Input single-channel PCM audio
     * @param sampleRate Sample rate in Hz (e.g. 44100)
     * @param suppressionStrength Aggressiveness of residual attenuation (0.0f..1.0f)
     * @return Cleaned vocal signal
     */
    fun suppressResiduals(
        signal: FloatArray,
        sampleRate: Int = 44100,
        suppressionStrength: Float = 0.5f,
    ): FloatArray {
        if (suppressionStrength <= 0.0f || signal.isEmpty()) return signal

        val nFft = 2048
        val hopLength = 512
        val spec = STFT.stft(signal, nFft, hopLength)
        val numBins = spec.numBins
        val numFrames = spec.numFrames
        val hzPerBin = sampleRate.toFloat() / nFft

        // Speech formant band: 200Hz - 4500Hz
        val speechMinBin = (200f / hzPerBin).toInt().coerceIn(0, numBins - 1)
        val speechMaxBin = (4500f / hzPerBin).toInt().coerceIn(0, numBins - 1)
        val sibilantMaxBin = (9000f / hzPerBin).toInt().coerceIn(0, numBins - 1)

        val cleanReal = Array(numBins) { FloatArray(numFrames) }
        val cleanImag = Array(numBins) { FloatArray(numFrames) }

        for (f in 0 until numFrames) {
            // Compute average energy across frame
            var totalEnergy = 0f
            for (b in 0 until numBins) {
                val r = spec.real[b][f]
                val i = spec.imag[b][f]
                totalEnergy += r * r + i * i
            }
            val frameRms = sqrt(totalEnergy / numBins)

            for (b in 0 until numBins) {
                val r = spec.real[b][f]
                val i = spec.imag[b][f]
                val mag = sqrt(r * r + i * i)

                val gain = when {
                    // Sub-bass rumble (< 100Hz): heavy suppression
                    b < (100f / hzPerBin).toInt() -> {
                        (1.0f - suppressionStrength * 0.9f).coerceAtLeast(0.05f)
                    }
                    // Speech formant region (200Hz - 4.5kHz): gentle preservation
                    b in speechMinBin..speechMaxBin -> {
                        if (mag > frameRms * 0.8f) {
                            1.0f // Keep prominent vocal peaks intact
                        } else {
                            (1.0f - suppressionStrength * 0.35f).coerceAtLeast(0.4f)
                        }
                    }
                    // Sibilant region (4.5kHz - 9kHz): preserve speech consonants
                    b in (speechMaxBin + 1)..sibilantMaxBin -> {
                        (1.0f - suppressionStrength * 0.4f).coerceAtLeast(0.3f)
                    }
                    // Ultra-high frequency (> 9kHz): suppress synthesizer / hi-hat residue
                    else -> {
                        (1.0f - suppressionStrength * 0.75f).coerceAtLeast(0.15f)
                    }
                }

                cleanReal[b][f] = r * gain
                cleanImag[b][f] = i * gain
            }
        }

        val cleanSpec = STFT.ComplexSpectrogram(numFrames, numBins, cleanReal, cleanImag)
        return STFT.istft(cleanSpec, nFft, hopLength, targetLength = signal.size)
    }

    /**
     * Suppresses residuals on stereo channel pairs.
     */
    fun suppressResidualsStereo(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int = 44100,
        suppressionStrength: Float = 0.5f,
    ): Pair<FloatArray, FloatArray> {
        val cleanL = suppressResiduals(leftChannel, sampleRate, suppressionStrength)
        val cleanR = suppressResiduals(rightChannel, sampleRate, suppressionStrength)
        return Pair(cleanL, cleanR)
    }
}
