package com.junkfood.seal.ai.audio.pipeline

import com.junkfood.seal.ai.audio.model.ModelRegistry

/**
 * QualityMode defines the performance vs depth tradeoff for neural source separation.
 */
enum class QualityMode(val title: String, val description: String) {
    FAST(
        title = "سريع (Fast)",
        description = "معالجة خفيفة وفورية باستهلاك منخفض للبطارية والذاكرة."
    ),
    BALANCED(
        title = "متوازن (Balanced)",
        description = "النمط الافتراضي الموصى به: دقة استوديو متقدمة وسرعة عالية."
    ),
    MAX_QUALITY(
        title = "أقصى دقة (Max Quality)",
        description = "فصل عميق متقدم مع فلاتر تصفية الترددات المتبقية وتعزيز نقاء الصوت البشري."
    )
}

/**
 * SeparationOptions holds all configuration parameters for the AI separation run.
 */
data class SeparationOptions(
    val qualityMode: QualityMode = QualityMode.BALANCED,
    val preferredModelId: String = ModelRegistry.MDX_VOCALS_DEFAULT.id,
    val enableEnsemble: Boolean = false,
    val residualSuppressionStrength: Float = 0.5f,
    val speechEnhancementDb: Float = 2.0f,
)
