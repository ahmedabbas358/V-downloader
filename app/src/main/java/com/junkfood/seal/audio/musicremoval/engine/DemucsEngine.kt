package com.junkfood.seal.audio.musicremoval.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.analysis.ResidualAnalyzer
import com.junkfood.seal.audio.musicremoval.model.ModelManager
import com.junkfood.seal.audio.musicremoval.model.ModelRegistry
import com.junkfood.seal.audio.musicremoval.model.ModelSpec
import com.junkfood.seal.audio.musicremoval.preprocessor.AudioChunkProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * DemucsEngine
 *
 * Executes Meta's Demucs v4 Hybrid Transformer (HTDemucs) via ONNX Runtime.
 */
class DemucsEngine(
    private val modelSpec: ModelSpec = ModelRegistry.DEMUCS_V4_HYBRID
) : SourceSeparationEngine {

    override val engineName: String = "Demucs v4 Hybrid (${modelSpec.name})"

    override val isAvailable: Boolean
        get() = ModelManager.isModelAvailable(modelSpec, context)

    companion object {
        private const val TAG = "DemucsEngine"
    }

    override suspend fun separate(
        input: AudioInput,
        config: MusicRemovalConfig,
        onProgress: ((Float, String) -> Unit)?
    ): SeparationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val modelFile = ModelManager.getModelFile(modelSpec, context)

        if (!isAvailable || !ModelManager.verifyModelIntegrity(modelSpec, context)) {
            Log.w(TAG, "Demucs ONNX model not ready, falling back to Native DSP...")
            return@withContext NativeDspEngine.separate(input, config, onProgress)
        }

        onProgress?.invoke(0.05f, "تهيئة نموذج Demucs v4 Hybrid...")
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            if (config.maxWorkerThreads > 0) {
                setIntraOpNumThreads(config.maxWorkerThreads)
            }
        }

        val session = try {
            env.createSession(modelFile.absolutePath, sessionOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Demucs ONNX session, falling back to Native DSP", e)
            return@withContext NativeDspEngine.separate(input, config, onProgress)
        }

        try {
            val chunkSamples = modelSpec.chunkSamples
            val overlapSamples = modelSpec.overlapSamples
            val inputName = session.inputNames.iterator().next()

            val (outLeft, outRight) = AudioChunkProcessor.processStreaming(
                leftChannel = input.leftChannel,
                rightChannel = input.rightChannel,
                chunkSamples = chunkSamples,
                overlapSamples = overlapSamples,
                onProgress = { p ->
                    onProgress?.invoke(0.10f + p * 0.70f, "فصل عميق عبر Demucs v4: ${(p * 100).toInt()}%")
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
        } finally {
            try { session.close() } catch (_: Exception) {}
        }
    }
}
