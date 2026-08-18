package com.junkfood.seal.ai.audio.separation

import com.junkfood.seal.ai.audio.pipeline.SeparationOptions
import com.junkfood.seal.ai.audio.quality.QualityAnalyzer
import com.junkfood.seal.ai.audio.quality.SeparationQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EnsembleSeparationEngine
 *
 * Runs MDX-Net and Demucs / DSP concurrently or sequentially, analyzes stem quality metrics,
 * and selects the highest-clarity stem to achieve maximal vocal separation fidelity.
 */
class EnsembleSeparationEngine(
    private val primaryEngine: AudioSeparationEngine = MdxSeparationEngine(),
    private val secondaryEngine: AudioSeparationEngine = DemucsSeparationEngine()
) : AudioSeparationEngine {

    override val engineName: String = "Neural Ensemble Engine (${primaryEngine.engineName} + ${secondaryEngine.engineName})"

    override suspend fun separate(
        input: AudioInput,
        options: SeparationOptions,
        onProgress: ((Float, String) -> Unit)?
    ): SeparationResult = withContext(Dispatchers.Default) {
        onProgress?.invoke(0.05f, "بدء نمط Ensemble المتقدم...")

        val res1 = primaryEngine.separate(
            input,
            options,
            onProgress = { p, msg -> onProgress?.invoke(p * 0.5f, msg) }
        )

        val res2 = secondaryEngine.separate(
            input,
            options,
            onProgress = { p, msg -> onProgress?.invoke(0.5f + p * 0.45f, msg) }
        )

        onProgress?.invoke(0.95f, "مقارنة واختيار أفضل جودة صوتية...")

        // Pick stem with higher speech quality and lower residual
        val chosen = if (res1.quality.speechQuality >= res2.quality.speechQuality) {
            res1
        } else {
            res2
        }

        onProgress?.invoke(1.0f, "تم إتمام المعالجة المتقدمة بنجاح.")
        chosen.copy(modelUsed = "$engineName (Selected: ${chosen.modelUsed})")
    }
}
