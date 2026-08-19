package com.junkfood.seal.audio.musicremoval.analysis

import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.model.UvrModelRegistry
import com.junkfood.seal.audio.musicremoval.model.UvrModelSpec

/**
 * UvrModelSelector
 *
 * Selects the optimal UVR model strategy based on user preferences, device memory,
 * and audio characteristics.
 */
object UvrModelSelector {

    sealed interface Strategy {
        data class SingleUvrModel(val spec: UvrModelSpec) : Strategy
        data class EnsembleUvrModels(val primarySpec: UvrModelSpec, val secondarySpec: UvrModelSpec) : Strategy
    }

    /**
     * Resolves the UVR model strategy to execute.
     */
    fun selectStrategy(config: MusicRemovalConfig): Strategy {
        return selectStrategyChain(config).first()
    }

    /**
     * Returns an ordered, bounded UVR-only strategy chain.
     *
     * The first strategy is the user's preferred quality/speed target. Later
     * strategies are deterministic alternatives used only when the quality
     * evaluator reports poor residual suppression or speech damage.
     */
    fun selectStrategyChain(config: MusicRemovalConfig): List<Strategy> {
        val preferred = when (config.qualityMode) {
            MusicRemovalConfig.QualityMode.FAST -> {
                Strategy.SingleUvrModel(UvrModelRegistry.UVR_MDX23C_VOCALS)
            }
            MusicRemovalConfig.QualityMode.BALANCED -> {
                Strategy.SingleUvrModel(UvrModelRegistry.UVR_MDX_NET_VOCALS_HQ)
            }
            MusicRemovalConfig.QualityMode.HIGH_QUALITY -> {
                Strategy.SingleUvrModel(UvrModelRegistry.UVR_HTDEMUCS_V4)
            }
            MusicRemovalConfig.QualityMode.MAX_REMOVAL -> {
                Strategy.EnsembleUvrModels(
                    primarySpec = UvrModelRegistry.UVR_HTDEMUCS_V4,
                    secondarySpec = UvrModelRegistry.UVR_MDX23C_VOCALS
                )
            }
        }

        if (config.secondaryModelPolicy == MusicRemovalConfig.SecondaryModelPolicy.NEVER) {
            return listOf(preferred)
        }

        val fallbacks =
            listOf(
                preferred,
                Strategy.SingleUvrModel(UvrModelRegistry.UVR_MDX23C_VOCALS),
                Strategy.SingleUvrModel(UvrModelRegistry.UVR_MDX_NET_VOCALS_HQ),
                Strategy.SingleUvrModel(UvrModelRegistry.UVR_VR_ARCH_VOCALS),
                Strategy.SingleUvrModel(UvrModelRegistry.UVR_HTDEMUCS_V4),
                Strategy.EnsembleUvrModels(
                    primarySpec = UvrModelRegistry.UVR_HTDEMUCS_V4,
                    secondarySpec = UvrModelRegistry.UVR_MDX23C_VOCALS,
                ),
            )

        return fallbacks
            .distinctBy {
                when (it) {
                    is Strategy.SingleUvrModel -> "single:${it.spec.id}"
                    is Strategy.EnsembleUvrModels -> "ensemble:${it.primarySpec.id}:${it.secondarySpec.id}"
                }
            }
            .take(if (config.secondaryModelPolicy == MusicRemovalConfig.SecondaryModelPolicy.ALWAYS) 4 else 3)
    }
}
