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
        val modelFile = UvrModelManager.getModelFile(modelSpec, context)

        // Ensure model is downloaded and verified
        if (!UvrModelManager.isModelAvailable(modelSpec, context) || !UvrModelManager.verifyModelIntegrity(modelSpec, context)) {
            Log.d(TAG, "Model ${modelSpec.name} not present locally, triggering on-demand download...")
            onProgress?.invoke(0.05f, "تنزيل نموذج UVR (${modelSpec.name})...")
            UvrModelManager.downloadModel(modelSpec, context) { p, msg ->
                onProgress?.invoke(0.05f + p * 0.15f, msg)
            }.getOrThrow()
        }

        onProgress?.invoke(0.20f, "تهيئة جلسة UVR (${modelSpec.name})...")
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            if (config.maxWorkerThreads > 0) {
                setIntraOpNumThreads(config.maxWorkerThreads)
            }
        }

        val session = env.createSession(modelFile.absolutePath, sessionOptions)

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
    }
}
