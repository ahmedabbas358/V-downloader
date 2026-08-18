package com.junkfood.seal.ai.audio.dsp

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * STFT - Short-Time Fourier Transform and Inverse STFT
 *
 * Provides exact spectral decomposition and reconstruction with Hann windowing
 * and Overlap-Add (OLA) normalization.
 */
object STFT {

    data class ComplexSpectrogram(
        val numFrames: Int,
        val numBins: Int, // (nFft / 2) + 1
        val real: Array<FloatArray>, // [numBins][numFrames]
        val imag: Array<FloatArray>, // [numBins][numFrames]
    ) {
        val magnitude: Array<FloatArray> by lazy {
            Array(numBins) { b ->
                FloatArray(numFrames) { f ->
                    val r = real[b][f]
                    val i = imag[b][f]
                    sqrt(r * r + i * i)
                }
            }
        }

        val phase: Array<FloatArray> by lazy {
            Array(numBins) { b ->
                FloatArray(numFrames) { f ->
                    atan2(imag[b][f], real[b][f])
                }
            }
        }
    }

    /**
     * Generates a standard Hann window.
     */
    fun createHannWindow(size: Int): FloatArray {
        val window = FloatArray(size)
        for (i in 0 until size) {
            window[i] = (0.5 * (1.0 - cos(2.0 * PI * i / size))).toFloat()
        }
        return window
    }

    /**
     * Computes the forward Short-Time Fourier Transform.
     *
     * @param signal Single-channel PCM audio samples
     * @param nFft FFT window size (e.g. 2048 or 4096)
     * @param hopLength Hop size between consecutive frames (e.g. 512)
     * @return ComplexSpectrogram containing real and imaginary frequency bins
     */
    fun stft(
        signal: FloatArray,
        nFft: Int = 2048,
        hopLength: Int = 512,
    ): ComplexSpectrogram {
        val window = createHannWindow(nFft)
        val numBins = (nFft / 2) + 1
        val numFrames = ((signal.size - nFft).coerceAtLeast(0) / hopLength) + 1

        val realOut = Array(numBins) { FloatArray(numFrames) }
        val imagOut = Array(numBins) { FloatArray(numFrames) }

        val frameReal = FloatArray(nFft)
        val frameImag = FloatArray(nFft)

        for (frameIdx in 0 until numFrames) {
            val startSample = frameIdx * hopLength
            for (k in 0 until nFft) {
                val sampleIdx = startSample + k
                val sampleVal = if (sampleIdx < signal.size) signal[sampleIdx] else 0.0f
                frameReal[k] = sampleVal * window[k]
                frameImag[k] = 0.0f
            }

            FFT.forward(frameReal, frameImag)

            for (b in 0 until numBins) {
                realOut[b][frameIdx] = frameReal[b]
                imagOut[b][frameIdx] = frameImag[b]
            }
        }

        return ComplexSpectrogram(numFrames, numBins, realOut, imagOut)
    }

    /**
     * Computes the Inverse Short-Time Fourier Transform (iSTFT) using Overlap-Add.
     *
     * @param spec Complex spectrogram
     * @param nFft FFT window size
     * @param hopLength Hop size
     * @param targetLength Expected output length in samples
     * @return Reconstructed time-domain audio samples
     */
    fun istft(
        spec: ComplexSpectrogram,
        nFft: Int = 2048,
        hopLength: Int = 512,
        targetLength: Int = (spec.numFrames - 1) * hopLength + nFft,
    ): FloatArray {
        val window = createHannWindow(nFft)
        val outSignal = FloatArray(targetLength)
        val windowWeight = FloatArray(targetLength)

        val frameReal = FloatArray(nFft)
        val frameImag = FloatArray(nFft)

        for (frameIdx in 0 until spec.numFrames) {
            val startSample = frameIdx * hopLength

            // Reconstruct full Hermitian symmetric spectrum
            for (b in 0 until spec.numBins) {
                frameReal[b] = spec.real[b][frameIdx]
                frameImag[b] = spec.imag[b][frameIdx]
            }
            for (b in spec.numBins until nFft) {
                val mirrorBin = nFft - b
                frameReal[b] = spec.real[mirrorBin][frameIdx]
                frameImag[b] = -spec.imag[mirrorBin][frameIdx]
            }

            FFT.inverse(frameReal, frameImag)

            for (k in 0 until nFft) {
                val sampleIdx = startSample + k
                if (sampleIdx < targetLength) {
                    val w = window[k]
                    outSignal[sampleIdx] += frameReal[k] * w
                    windowWeight[sampleIdx] += w * w
                }
            }
        }

        // Normalize by accumulated window power to avoid amplitude modulation
        for (i in 0 until targetLength) {
            val weight = windowWeight[i]
            if (weight > 1e-6f) {
                outSignal[i] /= weight
            }
        }

        return outSignal
    }
}
