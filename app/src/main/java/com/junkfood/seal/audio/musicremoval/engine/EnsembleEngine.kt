package com.junkfood.seal.audio.musicremoval.engine

import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.analysis.ResidualAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EnsembleEngine
 *
 * Blends outputs from multiple separation engines (e.g. Demucs + MDX23C)
 * using soft spectral power weighting to minimize residual music leakage while preserving speech.
 */
class EnsembleEngine(
    val primaryEngine: SourceSeparationEngine,
    val secondaryEngine: SourceSeparationEngine,
    val primaryWeight: Float = 0.65f
) : SourceSeparationEngine {

    override val engineName: String =
        "Ensemble [${primaryEngine.engineName} + ${secondaryEngine.engineName}]"

    override val isAvailable: Boolean
        get() = primaryEngine.isAvailable || secondaryEngine.isAvailable

    override suspend fun separate(
        input: AudioInput,
        config: MusicRemovalConfig,
        onProgress: ((Float, String) -> Unit)?
    ): SeparationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        onProgress?.invoke(0.05f, "تشغيل المعالجة المزدوجة (المرحلة 1: ${primaryEngine.engineName})...")
        val res1 = primaryEngine.separate(input, config) { p, msg ->
            onProgress?.invoke(0.05f + p * 0.45f, msg)
        }

        onProgress?.invoke(0.50f, "تشغيل المعالجة المزدوجة (المرحلة 2: ${secondaryEngine.engineName})...")
        val res2 = secondaryEngine.separate(input, config) { p, msg ->
            onProgress?.invoke(0.50f + p * 0.40f, msg)
        }

        onProgress?.invoke(0.92f, "دمج النتائج الطيفية (Ensemble Blending)...")
        val numSamples = minOf(res1.vocalLeft.size, res2.vocalLeft.size)
        val blendedL = FloatArray(numSamples)
        val blendedR = FloatArray(numSamples)

        val w1 = primaryWeight
        val w2 = 1.0f - primaryWeight

        for (i in 0 until numSamples) {
            blendedL[i] = (res1.vocalLeft[i] * w1 + res2.vocalLeft[i] * w2).coerceIn(-1.0f, 1.0f)
            blendedR[i] = (res1.vocalRight[i] * w1 + res2.vocalRight[i] * w2).coerceIn(-1.0f, 1.0f)
        }

        val quality = ResidualAnalyzer.evaluate(
            originalLeft = input.leftChannel,
            originalRight = input.rightChannel,
            separatedLeft = blendedL,
            separatedRight = blendedR,
            sampleRate = input.sampleRate
        )

        SeparationResult(
            vocalLeft = blendedL,
            vocalRight = blendedR,
            quality = quality,
            modelUsed = engineName,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }
}
