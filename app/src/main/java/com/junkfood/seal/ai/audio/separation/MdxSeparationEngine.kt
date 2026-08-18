package com.junkfood.seal.ai.audio.separation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.junkfood.seal.ai.audio.dsp.OverlapAddProcessor
import com.junkfood.seal.ai.audio.model.ModelManager
import com.junkfood.seal.ai.audio.model.ModelRegistry
import com.junkfood.seal.ai.audio.model.ModelSpec
import com.junkfood.seal.ai.audio.pipeline.SeparationOptions
import com.junkfood.seal.ai.audio.quality.QualityAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * MdxSeparationEngine
 *
 * Executes MDX-Net / UVR neural source separation via ONNX Runtime.
 * Features chunked streaming inference to ensure zero out-of-memory errors on mobile devices.
 */
class MdxSeparationEngine(
    private val modelSpec: ModelSpec = ModelRegistry.MDX_VOCALS_DEFAULT
) : AudioSeparationEngine {

    override val engineName: String = "MDX-Net AI Engine (${modelSpec.name})"

    companion object {
        private const val TAG = "MdxSeparationEngine"
    }

    override suspend fun separate(
        input: AudioInput,
        options: SeparationOptions,
        onProgress: ((Float, String) -> Unit)?
    ): SeparationResult = withContext(Dispatchers.Default) {
        val modelFile = ModelManager.getModelFile(modelSpec)
        if (!modelFile.exists() || modelFile.length() < 1024L) {
            Log.w(TAG, "ONNX model not found on disk, delegating to Native DSP Spectral Engine...")
            return@withContext NativeDspFallbackSeparationEngine.separate(input, options, onProgress)
        }

        onProgress?.invoke(0.05f, "جاري تهيئة بيئة الذكاء الاصطناعي ONNX Runtime...")
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
        }

        val session = try {
            env.createSession(modelFile.absolutePath, sessionOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX session, falling back to DSP", e)
            return@withContext NativeDspFallbackSeparationEngine.separate(input, options, onProgress)
        }

        try {
            val chunkSamples = modelSpec.chunkSamples
            val overlapSamples = modelSpec.overlapSamples
            val inputName = session.inputNames.iterator().next()

            val (procLeft, procRight) = OverlapAddProcessor.processStereo(
                leftChannel = input.leftChannel,
                rightChannel = input.rightChannel,
                chunkSamples = chunkSamples,
                overlapSamples = overlapSamples,
                onProgress = { p ->
                    onProgress?.invoke(0.1f + p * 0.75f, "معالجة الذكاء الاصطناعي: ${(p * 100).toInt()}%")
                }
            ) { leftChunk, rightChunk ->
                val bufferSize = 2 * chunkSamples
                val floatBuffer = FloatBuffer.allocate(bufferSize)

                // Interleave stereo channels: [2, chunkSamples]
                for (i in 0 until chunkSamples) {
                    floatBuffer.put(leftChunk[i])
                }
                for (i in 0 until chunkSamples) {
                    floatBuffer.put(rightChunk[i])
                }
                floatBuffer.rewind()

                val inputTensor = OnnxTensor.createTensor(
                    env,
                    floatBuffer,
                    longArrayOf(1, 2, chunkSamples.toLong())
                )

                val outLeft = FloatArray(chunkSamples)
                val outRight = FloatArray(chunkSamples)

                inputTensor.use { tensor ->
                    val result = session.run(mapOf(inputName to tensor))
                    result.use { res ->
                        val outputTensor = res.get(0) as OnnxTensor
                        val outputBuffer = outputTensor.floatBuffer
                        outputBuffer.rewind()

                        val halfSize = outputBuffer.remaining() / 2
                        val copyLen = minOf(chunkSamples, halfSize)

                        for (i in 0 until copyLen) {
                            outLeft[i] = outputBuffer.get()
                        }
                        for (i in 0 until copyLen) {
                            outRight[i] = outputBuffer.get()
                        }
                    }
                }

                Pair(outLeft, outRight)
            }

            onProgress?.invoke(0.9f, "جاري فحص دقة وجودة الصوت المفصول...")
            val quality = QualityAnalyzer.analyze(input.leftChannel, procLeft, procRight, input.sampleRate)

            SeparationResult(
                vocalLeft = procLeft,
                vocalRight = procRight,
                quality = quality,
                modelUsed = engineName
            )
        } finally {
            session.close()
        }
    }
}
