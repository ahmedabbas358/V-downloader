package com.junkfood.seal.audio.musicremoval.postprocessor

import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * UvrSpeechProtection
 *
 * Preserves human speech formants (120Hz-3800Hz), vowels, consonants, and voice dynamics
 * after UVR model separation.
 */
object UvrSpeechProtection {

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 256

    /**
     * Protects speech cues in separated audio.
     */
    fun protectSpeech(
        originalLeft: FloatArray,
        originalRight: FloatArray,
        processedLeft: FloatArray,
        processedRight: FloatArray,
        sampleRate: Int = 44100,
        level: MusicRemovalConfig.SpeechPreservationLevel = MusicRemovalConfig.SpeechPreservationLevel.HIGH,
        presenceBoostDb: Float = 3.0f
    ): Pair<FloatArray, FloatArray> {
        val n = minOf(originalLeft.size, originalRight.size, processedLeft.size, processedRight.size)
        if (n == 0) return Pair(FloatArray(0), FloatArray(0))

        val boostLinear = Math.pow(10.0, (presenceBoostDb / 20.0)).toFloat()
        val speechWeight = when (level) {
            MusicRemovalConfig.SpeechPreservationLevel.STANDARD -> 1.0f
            MusicRemovalConfig.SpeechPreservationLevel.HIGH -> boostLinear
            MusicRemovalConfig.SpeechPreservationLevel.MAXIMUM -> boostLinear * 1.15f
        }

        val outL = FloatArray(n)
        val outR = FloatArray(n)

        val window = FloatArray(FFT_SIZE) { i ->
            (0.5f * (1.0f - cos(2.0 * Math.PI * i / (FFT_SIZE - 1)))).toFloat()
        }

        val normWeight = FloatArray(n)
        val numFrames = (n - FFT_SIZE) / HOP_SIZE + 1

        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)

        val binFreq = sampleRate.toFloat() / FFT_SIZE
        val speechMinBin = (120.0f / binFreq).toInt().coerceIn(0, FFT_SIZE / 2)
        val speechMaxBin = (3800.0f / binFreq).toInt().coerceIn(0, FFT_SIZE / 2)

        for (f in 0 until numFrames) {
            val frameOffset = f * HOP_SIZE

            for (i in 0 until FFT_SIZE) {
                val sAvg = (processedLeft[frameOffset + i] + processedRight[frameOffset + i]) * 0.5f
                real[i] = sAvg * window[i]
                imag[i] = 0.0f
            }

            fft(real, imag)

            for (k in 0 until FFT_SIZE / 2) {
                if (k in speechMinBin..speechMaxBin) {
                    real[k] *= speechWeight
                    imag[k] *= speechWeight
                    if (k > 0) {
                        real[FFT_SIZE - k] = real[k]
                        imag[FFT_SIZE - k] = -imag[k]
                    }
                }
            }

            ifft(real, imag)

            for (i in 0 until FFT_SIZE) {
                val idx = frameOffset + i
                val w = window[i]
                val enhancedSample = real[i]

                val pL = processedLeft[idx]
                val pR = processedRight[idx]

                outL[idx] += (pL * 0.6f + enhancedSample * 0.4f) * w
                outR[idx] += (pR * 0.6f + enhancedSample * 0.4f) * w
                normWeight[idx] += w
            }
        }

        for (i in 0 until n) {
            val w = normWeight[i]
            if (w > 1e-4f) {
                outL[i] = (outL[i] / w).coerceIn(-1.0f, 1.0f)
                outR[i] = (outR[i] / w).coerceIn(-1.0f, 1.0f)
            } else {
                outL[i] = processedLeft[i]
                outR[i] = processedRight[i]
            }
        }

        return Pair(outL, outR)
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var l = 1
        while (l < n) {
            val step = l shl 1
            val angle = -Math.PI / l
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()
            var wR = 1.0f
            var wI = 0.0f

            for (m in 0 until l) {
                for (i in m until n step step) {
                    val pair = i + l
                    val tr = wR * real[pair] - wI * imag[pair]
                    val ti = wR * imag[pair] + wI * real[pair]
                    real[pair] = real[i] - tr
                    imag[pair] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti
                }
                val nextWR = wR * wStepR - wI * wStepI
                val nextWI = wR * wStepI + wI * wStepR
                wR = nextWR
                wI = nextWI
            }
            l = step
        }
    }

    private fun ifft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        for (i in 0 until n) imag[i] = -imag[i]
        fft(real, imag)
        val invN = 1.0f / n
        for (i in 0 until n) {
            real[i] *= invN
            imag[i] = -imag[i] * invN
        }
    }
}
