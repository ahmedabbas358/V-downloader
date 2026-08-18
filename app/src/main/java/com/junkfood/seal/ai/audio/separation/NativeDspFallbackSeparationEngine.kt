package com.junkfood.seal.ai.audio.separation

import com.junkfood.seal.ai.audio.dsp.OverlapAddProcessor
import com.junkfood.seal.ai.audio.dsp.STFT
import com.junkfood.seal.ai.audio.pipeline.SeparationOptions
import com.junkfood.seal.ai.audio.quality.QualityAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * NativeDspFallbackSeparationEngine
 *
 * High-performance, zero-download DSP vocal isolation engine.
 * Combines Blumlein Mid-Side stereo matrixing with adaptive spectral gating
 * to extract human voices and cancel out stereo instruments and background music.
 */
object NativeDspFallbackSeparationEngine : AudioSeparationEngine {

    override val engineName: String = "Native DSP Spectral Engine"

    override suspend fun separate(
        input: AudioInput,
        options: SeparationOptions,
        onProgress: ((Float, String) -> Unit)?
    ): SeparationResult = withContext(Dispatchers.Default) {
        val totalSamples = input.leftChannel.size
        val sampleRate = input.sampleRate

        onProgress?.invoke(0.1f, "جاري استخراج الصوت البشري عبر معالج الترددات الطيفي...")

        val (procLeft, procRight) = OverlapAddProcessor.processStereo(
            leftChannel = input.leftChannel,
            rightChannel = input.rightChannel,
            chunkSamples = 176400, // 4.0s chunks
            overlapSamples = 22050, // 0.5s overlap
            onProgress = { p ->
                onProgress?.invoke(0.1f + p * 0.7f, "جاري فصل الآلات الموسيقية: ${(p * 100).toInt()}%")
            }
        ) { leftChunk, rightChunk ->
            val n = leftChunk.size
            // 1. Blumlein Mid-Side decomposition: Center channel (Vocals) vs Side channel (Stereo instruments/Pans)
            val mid = FloatArray(n)
            val side = FloatArray(n)

            for (i in 0 until n) {
                val l = leftChunk[i]
                val r = rightChunk[i]
                mid[i] = (l + r) * 0.5f
                side[i] = (l - r) * 0.5f
            }

            // 2. Spectral STFT filtering on Mid channel
            val nFft = 2048
            val hopLength = 512
            val midSpec = STFT.stft(mid, nFft, hopLength)
            val sideSpec = STFT.stft(side, nFft, hopLength)

            val hzPerBin = sampleRate.toFloat() / nFft
            val vocalMinBin = (120f / hzPerBin).toInt().coerceIn(0, midSpec.numBins - 1)
            val vocalMaxBin = (7500f / hzPerBin).toInt().coerceIn(0, midSpec.numBins - 1)

            val cleanReal = Array(midSpec.numBins) { FloatArray(midSpec.numFrames) }
            val cleanImag = Array(midSpec.numBins) { FloatArray(midSpec.numFrames) }

            for (f in 0 until midSpec.numFrames) {
                for (b in 0 until midSpec.numBins) {
                    val mR = midSpec.real[b][f]
                    val mI = midSpec.imag[b][f]
                    val sR = sideSpec.real[b][f]
                    val sI = sideSpec.imag[b][f]

                    val midMag = sqrt(mR * mR + mI * mI)
                    val sideMag = sqrt(sR * sR + sI * sI)

                    // Compute center-to-side ratio (Vocals are centered, instruments are stereo/panned)
                    val centerRatio = if (midMag + sideMag > 1e-6f) {
                        (midMag / (midMag + sideMag * 1.5f)).coerceIn(0.05f, 1.0f)
                    } else {
                        0.5f
                    }

                    val freqGain = when {
                        b < vocalMinBin -> 0.05f // Bass / kick drum cut
                        b in vocalMinBin..vocalMaxBin -> centerRatio
                        else -> centerRatio * 0.4f // Cymbals / hi-hats cut
                    }

                    cleanReal[b][f] = mR * freqGain
                    cleanImag[b][f] = mI * freqGain
                }
            }

            val cleanSpec = STFT.ComplexSpectrogram(midSpec.numFrames, midSpec.numBins, cleanReal, cleanImag)
            val cleanMid = STFT.istft(cleanSpec, nFft, hopLength, targetLength = n)

            // Reconstruct clean stereo vocal stem
            val vocalL = FloatArray(n)
            val vocalR = FloatArray(n)
            for (i in 0 until n) {
                val v = cleanMid[i]
                vocalL[i] = v
                vocalR[i] = v
            }
            Pair(vocalL, vocalR)
        }

        onProgress?.invoke(0.9f, "جاري تقييم جودة الصوت ونقاء الكلام...")
        val quality = QualityAnalyzer.analyze(input.leftChannel, procLeft, procRight, sampleRate)

        onProgress?.invoke(1.0f, "تم استخراج الصوت البشري بنجاح.")
        SeparationResult(
            vocalLeft = procLeft,
            vocalRight = procRight,
            quality = quality,
            modelUsed = engineName
        )
    }
}
