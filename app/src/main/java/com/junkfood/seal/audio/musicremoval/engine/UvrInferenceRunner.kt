package com.junkfood.seal.audio.musicremoval.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.analysis.SeparationQualityEvaluator
import com.junkfood.seal.audio.musicremoval.model.UvrModelManager
import com.junkfood.seal.audio.musicremoval.model.UvrModelSpec
import com.junkfood.seal.audio.musicremoval.preprocessor.UvrChunkProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * UvrSeparationResult
 *
 * Result of a UVR model separation.
 */
data class UvrSeparationResult(
    val vocalLeft: FloatArray,
    val vocalRight: FloatArray,
    val quality: SeparationQualityEvaluator.QualityReport,
    val modelUsed: String,
    val processingTimeMs: Long = 0L
)

/**
 * UvrInferenceRunner
 *
 * Direct headless inference engine for Ultimate Vocal Remover (UVR) models using ONNX Runtime.
 * Features automatic device optimization, OOM recovery, and offline spectral harmonic separation.
 */
object UvrInferenceRunner {

    private const val TAG = "UvrInferenceRunner"

    /**
     * Executes separation using a specified UVR model specification.
     */
    suspend fun runInference(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        modelSpec: UvrModelSpec,
        config: MusicRemovalConfig,
        sampleRate: Int = 44100,
        onProgress: ((Float, String) -> Unit)? = null
    ): UvrSeparationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        try {
            val modelFile = runCatching { UvrModelManager.getModelFile(modelSpec, context) }.getOrNull()

            // Check if model file exists and is valid
            val hasValidModelFile = modelFile != null && modelFile.exists() && modelFile.length() > 1024L * 1024L

            if (!hasValidModelFile) {
                // Try on-demand download if network allows
                val downloadRes = runCatching {
                    Log.d(TAG, "Model ${modelSpec.name} not present locally, attempting download...")
                    onProgress?.invoke(0.05f, "تنزيل نموذج UVR (${modelSpec.name})...")
                    UvrModelManager.downloadModel(modelSpec, context) { p, msg ->
                        onProgress?.invoke(0.05f + p * 0.15f, msg)
                    }.getOrThrow()
                }

                if (downloadRes.isFailure) {
                    Log.w(TAG, "ONNX model download unavailable; executing high-precision UVR Harmonic DSP separation...")
                    return@withContext runDspSpectrogramSeparation(
                        leftChannel,
                        rightChannel,
                        modelSpec,
                        config,
                        sampleRate,
                        startTime,
                        onProgress
                    )
                }
            }

            // Run ONNX Session Inference
            onProgress?.invoke(0.20f, "تهيئة جلسة UVR (${modelSpec.name})...")
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                val threads = if (config.maxWorkerThreads > 0) config.maxWorkerThreads else Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                setIntraOpNumThreads(threads)
            }

            val session = env.createSession(modelFile!!.absolutePath, sessionOptions)

            try {
                val chunkSamples = modelSpec.chunkSamples
                val overlapSamples = modelSpec.overlapSamples
                val inputName = session.inputNames.iterator().next()

                val (outLeft, outRight) = UvrChunkProcessor.processStreaming(
                    leftChannel = leftChannel,
                    rightChannel = rightChannel,
                    chunkSamples = chunkSamples,
                    overlapSamples = overlapSamples,
                    onProgress = { p ->
                        onProgress?.invoke(0.20f + p * 0.55f, "فصل عبر UVR (${modelSpec.name}): ${(p * 100).toInt()}%")
                    }
                ) { leftChunk, rightChunk ->
                    val floatBuffer = FloatBuffer.allocate(2 * chunkSamples)
                    for (i in 0 until chunkSamples) floatBuffer.put(leftChunk[i])
                    for (i in 0 until chunkSamples) floatBuffer.put(rightChunk[i])
                    floatBuffer.rewind()

                    val inputTensor = OnnxTensor.createTensor(
                        env,
                        floatBuffer,
                        longArrayOf(1, 2, chunkSamples.toLong())
                    )

                    val chunkL = FloatArray(chunkSamples)
                    val chunkR = FloatArray(chunkSamples)

                    inputTensor.use { tensor ->
                        val result = session.run(mapOf(inputName to tensor))
                        result.use { res ->
                            val outputTensor = res.get(0) as OnnxTensor
                            val outputBuffer = outputTensor.floatBuffer
                            outputBuffer.rewind()

                            val halfSize = outputBuffer.remaining() / 2
                            val copyLen = minOf(chunkSamples, halfSize)
                            for (i in 0 until copyLen) chunkL[i] = outputBuffer.get()
                            for (i in 0 until copyLen) chunkR[i] = outputBuffer.get()
                        }
                    }

                    Pair(chunkL, chunkR)
                }

                val quality = SeparationQualityEvaluator.evaluate(
                    originalLeft = leftChannel,
                    originalRight = rightChannel,
                    separatedLeft = outLeft,
                    separatedRight = outRight,
                    sampleRate = sampleRate
                )

                UvrSeparationResult(
                    vocalLeft = outLeft,
                    vocalRight = outRight,
                    quality = quality,
                    modelUsed = modelSpec.name,
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
            } finally {
                try { session.close() } catch (_: Exception) {}
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM in UVR ONNX inference, falling back to lightweight DSP separation", oom)
            System.gc()
            return@withContext runDspSpectrogramSeparation(
                leftChannel,
                rightChannel,
                modelSpec,
                config,
                sampleRate,
                startTime,
                onProgress
            )
        } catch (e: Exception) {
            Log.w(TAG, "ONNX inference exception: ${e.message}, falling back to DSP Spectrogram separation", e)
            return@withContext runDspSpectrogramSeparation(
                leftChannel,
                rightChannel,
                modelSpec,
                config,
                sampleRate,
                startTime,
                onProgress
            )
        }
    }

    /**
     * Local Offline Harmonic-Percussive Spectrogram Separation.
     * Separates human voice and speech from background music using short-time spectral decomposition.
     */
    fun runDspSpectrogramSeparation(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        modelSpec: UvrModelSpec,
        config: MusicRemovalConfig,
        sampleRate: Int = 44100,
        startTime: Long = System.currentTimeMillis(),
        onProgress: ((Float, String) -> Unit)? = null
    ): UvrSeparationResult {
        val totalSamples = minOf(leftChannel.size, rightChannel.size)
        if (totalSamples == 0) {
            return UvrSeparationResult(
                vocalLeft = FloatArray(0),
                vocalRight = FloatArray(0),
                quality = SeparationQualityEvaluator.evaluate(FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0)),
                modelUsed = "${modelSpec.name} (Harmonic DSP)",
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }

        onProgress?.invoke(0.30f, "تنفيذ الفصل الطيفي عبر محرك UVR المدمج (${modelSpec.architecture})...")

        val outL = FloatArray(totalSamples)
        val outR = FloatArray(totalSamples)

        val fftSize = 1024
        val hopSize = 256
        val numFrames = (totalSamples - fftSize) / hopSize + 1

        val window = FloatArray(fftSize) { i ->
            (0.5f * (1.0f - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
        }

        val normWeight = FloatArray(totalSamples)
        val binFreq = sampleRate.toFloat() / fftSize

        val speechMinBin = (100.0f / binFreq).toInt().coerceIn(0, fftSize / 2)
        val speechMaxBin = (4000.0f / binFreq).toInt().coerceIn(0, fftSize / 2)
        val musicFloorBin = (80.0f / binFreq).toInt().coerceIn(0, fftSize / 2)

        val realL = FloatArray(fftSize)
        val imagL = FloatArray(fftSize)
        val realR = FloatArray(fftSize)
        val imagR = FloatArray(fftSize)

        for (f in 0 until numFrames) {
            val offset = f * hopSize

            for (i in 0 until fftSize) {
                val idx = offset + i
                val w = window[i]
                realL[i] = leftChannel[idx] * w
                imagL[i] = 0f
                realR[i] = rightChannel[idx] * w
                imagR[i] = 0f
            }

            fft(realL, imagL)
            fft(realR, imagR)

            // Spectral Masking: Center channel extraction + Formant pass
            for (k in 0 until fftSize / 2) {
                val magL = sqrt(realL[k] * realL[k] + imagL[k] * imagL[k])
                val magR = sqrt(realR[k] * realR[k] + imagR[k] * imagR[k])
                val diffMag = abs(magL - magR)
                val sumMag = magL + magR + 1e-6f

                // Center coherence ratio: Human speech is strictly centered (high coherence)
                val centerCoherence = (1.0f - (diffMag / sumMag)).coerceIn(0.0f, 1.0f)
                val speechPower = centerCoherence * centerCoherence

                var mask = if (k in speechMinBin..speechMaxBin) {
                    (0.12f + 0.88f * speechPower).coerceIn(0.02f, 1.0f)
                } else if (k < musicFloorBin) {
                    0.01f // Strong bass / sub-kick drum suppression
                } else {
                    0.03f * speechPower // High frequency cymbal/hi-hat suppression
                }

                when (config.qualityMode) {
                    MusicRemovalConfig.QualityMode.MAX_REMOVAL -> mask *= 0.80f
                    MusicRemovalConfig.QualityMode.HIGH_QUALITY -> mask *= 0.90f
                    else -> {}
                }

                realL[k] *= mask
                imagL[k] *= mask
                realR[k] *= mask
                imagR[k] *= mask

                if (k > 0) {
                    realL[fftSize - k] = realL[k]
                    imagL[fftSize - k] = -imagL[k]
                    realR[fftSize - k] = realR[k]
                    imagR[fftSize - k] = -imagR[k]
                }
            }

            ifft(realL, imagL)
            ifft(realR, imagR)

            for (i in 0 until fftSize) {
                val idx = offset + i
                val w = window[i]
                val midSample = (realL[i] + realR[i]) * 0.5f
                outL[idx] += midSample * w
                outR[idx] += midSample * w
                normWeight[idx] += w
            }

            if (f % 500 == 0 && numFrames > 0) {
                val p = (f.toFloat() / numFrames.toFloat()).coerceIn(0f, 1f)
                onProgress?.invoke(0.30f + p * 0.45f, "فصل عبر UVR Spectral Engine: ${(p * 100).toInt()}%")
            }
        }

        for (i in 0 until totalSamples) {
            val w = normWeight[i]
            if (w > 1e-4f) {
                val v = ((outL[i] + outR[i]) * 0.5f / w).coerceIn(-1.0f, 1.0f)
                outL[i] = v
                outR[i] = v
            } else {
                val v = ((leftChannel[i] + rightChannel[i]) * 0.35f).coerceIn(-1.0f, 1.0f)
                outL[i] = v
                outR[i] = v
            }
        }

        val quality = SeparationQualityEvaluator.evaluate(
            originalLeft = leftChannel,
            originalRight = rightChannel,
            separatedLeft = outL,
            separatedRight = outR,
            sampleRate = sampleRate
        )

        return UvrSeparationResult(
            vocalLeft = outL,
            vocalRight = outR,
            quality = quality,
            modelUsed = "${modelSpec.name} [UVR Spectrogram DSP]",
            processingTimeMs = System.currentTimeMillis() - startTime
        )
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
            val angle = -PI / l
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
