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
        return when (config.qualityMode) {
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
    }
}
