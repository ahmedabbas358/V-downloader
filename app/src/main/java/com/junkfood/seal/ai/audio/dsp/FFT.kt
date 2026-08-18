package com.junkfood.seal.ai.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * FFT - High-performance Radix-2 Fast Fourier Transform
 *
 * Implements Cooley-Tukey in-place FFT on FloatArray real/imaginary buffers.
 * Optimized for Android CPU inference with zero per-frame object allocation.
 */
object FFT {

    /**
     * In-place Forward Fast Fourier Transform.
     * Length N must be a power of 2.
     *
     * @param real Real components array of size N
     * @param imag Imaginary components array of size N
     */
    fun forward(real: FloatArray, imag: FloatArray) {
        transform(real, imag, inverse = false)
    }

    /**
     * In-place Inverse Fast Fourier Transform.
     * Length N must be a power of 2.
     *
     * @param real Real components array of size N
     * @param imag Imaginary components array of size N
     */
    fun inverse(real: FloatArray, imag: FloatArray) {
        transform(real, imag, inverse = true)
    }

    private fun transform(real: FloatArray, imag: FloatArray, inverse: Boolean) {
        val n = real.size
        require(n and (n - 1) == 0) { "FFT size must be a power of 2, received $n" }
        require(imag.size == n) { "Real and imaginary arrays must have equal length" }

        // 1. Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // 2. Cooley-Tukey butterfly computation
        val sign = if (inverse) 1.0 else -1.0
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = sign * (2.0 * PI / len)
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]

                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI

                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }

        // 3. Normalization for inverse transform
        if (inverse) {
            val invN = 1.0f / n
            for (k in 0 until n) {
                real[k] *= invN
                imag[k] *= invN
            }
        }
    }
}
