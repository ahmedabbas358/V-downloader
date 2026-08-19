package com.junkfood.seal.audio.musicremoval.engine

import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.analysis.SeparationQualityEvaluator
import com.junkfood.seal.audio.musicremoval.model.UvrModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UvrEnsembleEngine
 *
 * Combines outputs from two distinct UVR models (e.g. UVR HTDemucs + UVR MDX23C)
 * via soft spectral power weighting to eliminate persistent background instruments
 * while protecting delicate speech formants.
 */
object UvrEnsembleEngine {

    /**
     * Executes two-model UVR ensemble.
     */
    suspend fun separateEnsemble(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        primarySpec: UvrModelSpec,
        secondarySpec: UvrModelSpec,
        config: MusicRemovalConfig,
        sampleRate: Int = 44100,
        primaryWeight: Float = 0.60f,
        onProgress: ((Float, String) -> Unit)? = null
    ): UvrSeparationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        onProgress?.invoke(0.10f, "تشغيل نموذج UVR الأول (${primarySpec.name})...")
        val res1 = UvrInferenceRunner.runInference(
            leftChannel = leftChannel,
            rightChannel = rightChannel,
            modelSpec = primarySpec,
            config = config,
            sampleRate = sampleRate
        ) { p, msg ->
            onProgress?.invoke(0.10f + p * 0.40f, msg)
        }

        onProgress?.invoke(0.50f, "تشغيل نموذج UVR الثاني (${secondarySpec.name})...")
        val res2 = UvrInferenceRunner.runInference(
            leftChannel = leftChannel,
            rightChannel = rightChannel,
            modelSpec = secondarySpec,
            config = config,
            sampleRate = sampleRate
        ) { p, msg ->
            onProgress?.invoke(0.50f + p * 0.40f, msg)
        }

        onProgress?.invoke(0.92f, "دمج نتائج نماذج UVR (Ensemble Blending)...")
        val numSamples = minOf(res1.vocalLeft.size, res2.vocalLeft.size)
        val blendedL = FloatArray(numSamples)
        val blendedR = FloatArray(numSamples)

        val w1 = primaryWeight
        val w2 = 1.0f - primaryWeight

        for (i in 0 until numSamples) {
            blendedL[i] = (res1.vocalLeft[i] * w1 + res2.vocalLeft[i] * w2).coerceIn(-1.0f, 1.0f)
            blendedR[i] = (res1.vocalRight[i] * w1 + res2.vocalRight[i] * w2).coerceIn(-1.0f, 1.0f)
        }

        val quality = SeparationQualityEvaluator.evaluate(
            originalLeft = leftChannel,
            originalRight = rightChannel,
            separatedLeft = blendedL,
            separatedRight = blendedR,
            sampleRate = sampleRate
        )

        UvrSeparationResult(
            vocalLeft = blendedL,
            vocalRight = blendedR,
            quality = quality,
            modelUsed = "UVR Ensemble [${primarySpec.name} + ${secondarySpec.name}]",
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }
}
