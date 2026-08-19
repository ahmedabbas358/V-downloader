package com.junkfood.seal.audio.musicremoval.engine

import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.analysis.ResidualAnalyzer
import com.junkfood.seal.audio.musicremoval.preprocessor.AudioChunkProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * NativeDspEngine
 *
 * High-performance, zero-download DSP audio separation engine.
 * Utilizes multi-band STFT, harmonic formant peak tracking, Mid-Side spatial cancellation,
 * and adaptive spectral subtraction to isolate human voice without neural model files.
 */
object NativeDspEngine : SourceSeparationEngine {

    override val engineName: String = "Native High-Precision DSP Engine"
    override val isAvailable: Boolean = true

    private const val FFT_SIZE = 2048
    private const val HOP_SIZE = 512

    override suspend fun separate(
        input: AudioInput,
        config: MusicRemovalConfig,
        onProgress: ((Float, String) -> Unit)?
    ): SeparationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val totalSamples = minOf(input.leftChannel.size, input.rightChannel.size)
        if (totalSamples == 0) {
            return@withContext SeparationResult(
                vocalLeft = FloatArray(0),
                vocalRight = FloatArray(0),
                quality = ResidualAnalyzer.QualityEvaluation(0f, 1f, 0f, 1f, true),
                modelUsed = engineName
            )
        }

        onProgress?.invoke(0.10f, "بدء المعالجة الطيفية المتقدمة (Native DSP)...")

        val (outLeft, outRight) = AudioChunkProcessor.processStreaming(
            leftChannel = input.leftChannel,
            rightChannel = input.rightChannel,
            chunkSamples = 88200,   // 2.0 seconds
            overlapSamples = 11025, // 0.25 seconds
            onProgress = { p ->
                onProgress?.invoke(0.10f + p * 0.70f, "عزل الترددات الصوتية: ${(p * 100).toInt()}%")
            }
        ) { leftChunk, rightChunk ->
            processChunkDsp(leftChunk, rightChunk, input.sampleRate, config)
        }

        val quality = ResidualAnalyzer.evaluate(
            originalLeft = input.leftChannel,
            originalRight = input.rightChannel,
            separatedLeft = outLeft,
            separatedRight = outRight,
            sampleRate = input.sampleRate
        )

        SeparationResult(
            vocalLeft = outLeft,
            vocalRight = outRight,
            quality = quality,
            modelUsed = engineName,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun processChunkDsp(
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int,
        config: MusicRemovalConfig
    ): Pair<FloatArray, FloatArray> {
        val n = minOf(left.size, right.size)
        val numFrames = (n - FFT_SIZE) / HOP_SIZE + 1
        if (numFrames <= 0) return Pair(left.clone(), right.clone())

        val mid = FloatArray(n)
        val side = FloatArray(n)
        for (i in 0 until n) {
            mid[i] = (left[i] + right[i]) * 0.5f
            side[i] = (left[i] - right[i]) * 0.5f
        }

        val window = FloatArray(FFT_SIZE) { i ->
            (0.5f * (1.0f - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
        }

        val outMid = FloatArray(n)
        val outSide = FloatArray(n)
        val normWeight = FloatArray(n)

        val realMid = FloatArray(FFT_SIZE)
        val imagMid = FloatArray(FFT_SIZE)
        val realSide = FloatArray(FFT_SIZE)
        val imagSide = FloatArray(FFT_SIZE)
        val magMid = FloatArray(FFT_SIZE / 2 + 1)
        val magSide = FloatArray(FFT_SIZE / 2 + 1)

        val hzPerBin = sampleRate.toFloat() / FFT_SIZE
        val bin150Hz = (150.0f / hzPerBin).toInt().coerceIn(0, FFT_SIZE / 2)
        val bin300Hz = (300.0f / hzPerBin).toInt().coerceIn(0, FFT_SIZE / 2)
        val bin3400Hz = (3400.0f / hzPerBin).toInt().coerceIn(0, FFT_SIZE / 2)
        val bin4500Hz = (4500.0f / hzPerBin).toInt().coerceIn(0, FFT_SIZE / 2)

        for (frame in 0 until numFrames) {
            val offset = frame * HOP_SIZE

            for (i in 0 until FFT_SIZE) {
                realMid[i] = mid[offset + i] * window[i]
                imagMid[i] = 0.0f
                realSide[i] = side[offset + i] * window[i]
                imagSide[i] = 0.0f
            }

            fft(realMid, imagMid)
            fft(realSide, imagSide)

            for (k in 0..FFT_SIZE / 2) {
                magMid[k] = sqrt(realMid[k] * realMid[k] + imagMid[k] * imagMid[k])
                magSide[k] = sqrt(realSide[k] * realSide[k] + imagSide[k] * imagSide[k])
            }

            // Local median smoothing for formant peak prominence
            val smoothMag = FloatArray(FFT_SIZE / 2 + 1)
            for (k in 1 until FFT_SIZE / 2) {
                smoothMag[k] = (magMid[k - 1] + magMid[k] + magMid[k + 1]) / 3.0f
            }

            for (k in 0..FFT_SIZE / 2) {
                val midVal = magMid[k]
                val sideVal = magSide[k]
                val smoothVal = smoothMag[k]

                var gain = 1.0f

                // 1. Sub-bass kill: eliminate kicks, bass, 808s
                if (k < bin150Hz) {
                    gain *= (k.toFloat() / bin150Hz).coerceIn(0.01f, 1.0f) * 0.02f
                }

                // 2. High-freq cutoff: eliminate cymbals, hi-hats, high synths
                if (k > bin4500Hz) {
                    val excess = (k - bin4500Hz).toFloat() / (FFT_SIZE / 2 - bin4500Hz)
                    gain *= (1.0f - excess).coerceIn(0.01f, 1.0f) * 0.03f
                }

                // 3. Side-channel cancellation
                if (sideVal > 1e-6f) {
                    val sideRatio = (sideVal / (midVal + 1e-6f)).coerceIn(0.0f, 2.0f)
                    val sideSuppression = (1.0f - sideRatio * 1.25f).coerceIn(0.05f, 1.0f)
                    gain *= sideSuppression
                }

                // 4. Formant Peak Isolation
                if (k in bin300Hz..bin3400Hz) {
                    val peakProminence = (midVal / (smoothVal + 1e-6f)).coerceIn(0.5f, 3.0f)
                    if (peakProminence > 1.15f) {
                        gain *= 1.20f // Harmonic voice formant boost
                    } else {
                        gain *= 0.15f // Background instrument attenuation
                    }
                } else if (k >= bin150Hz && k < bin300Hz) {
                    gain *= 0.25f
                } else if (k > bin3400Hz && k <= bin4500Hz) {
                    gain *= 0.10f
                }

                gain = gain.coerceIn(0.0f, 1.3f)
                realMid[k] *= gain
                imagMid[k] *= gain

                if (k > 0 && k < FFT_SIZE / 2) {
                    realMid[FFT_SIZE - k] = realMid[k]
                    imagMid[FFT_SIZE - k] = -imagMid[k]
                }
            }

            ifft(realMid, imagMid)

            for (i in 0 until FFT_SIZE) {
                val outIdx = offset + i
                outMid[outIdx] += realMid[i] * window[i]
                normWeight[outIdx] += window[i] * window[i]
            }
        }

        val outL = FloatArray(n)
        val outR = FloatArray(n)
        for (i in 0 until n) {
            val w = normWeight[i]
            val m = if (w > 1e-4f) outMid[i] / w else mid[i]
            outL[i] = m.coerceIn(-1.0f, 1.0f)
            outR[i] = m.coerceIn(-1.0f, 1.0f)
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

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wstepR = cos(ang).toFloat()
            val wstepI = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                    val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR
                    imag[i + k + len / 2] = uI - vI
                    val nextWR = wR * wstepR - wI * wstepI
                    wI = wR * wstepI + wI * wstepR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun ifft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        for (i in 0 until n) imag[i] = -imag[i]
        fft(real, imag)
        val scale = 1.0f / n
        for (i in 0 until n) {
            real[i] *= scale
            imag[i] = -imag[i] * scale
        }
    }
}
