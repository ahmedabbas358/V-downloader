package com.junkfood.seal.audio.musicremoval.analysis

import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.detection.MusicDetector
import com.junkfood.seal.audio.musicremoval.device.DeviceProfileManager
import com.junkfood.seal.audio.musicremoval.engine.DemucsEngine
import com.junkfood.seal.audio.musicremoval.engine.EnsembleEngine
import com.junkfood.seal.audio.musicremoval.engine.MDXEngine
import com.junkfood.seal.audio.musicremoval.engine.NativeDspEngine
import com.junkfood.seal.audio.musicremoval.engine.RoFormerEngine
import com.junkfood.seal.audio.musicremoval.engine.SourceSeparationEngine
import com.junkfood.seal.audio.musicremoval.model.ModelManager
import com.junkfood.seal.audio.musicremoval.model.ModelRegistry

/**
 * AdaptiveSelector
 *
 * Automatically chooses the optimal source separation strategy based on:
 * - Acoustic characteristics (Music presence, speech ratio, SNR)
 * - Device capabilities (RAM, CPU cores, thermal state)
 * - User quality configuration
 */
object AdaptiveSelector {

    sealed class Strategy {
        object SkipSeparation : Strategy()
        data class ExecuteEngine(val engine: SourceSeparationEngine) : Strategy()
    }

    fun selectStrategy(
        detection: MusicDetector.DetectionResult,
        config: MusicRemovalConfig
    ): Strategy {
        // 1. Gate: If detection shows pure speech or zero music, skip heavy separation
        if (config.enableMusicDetectionGate && !detection.hasMusic && detection.isSpeechOnly) {
            return Strategy.SkipSeparation
        }

        val deviceProfile = DeviceProfileManager.getDeviceProfile(context)
        val isDemucsReady = ModelManager.isModelAvailable(ModelRegistry.DEMUCS_V4_HYBRID, context)
        val isMdxReady = ModelManager.isModelAvailable(ModelRegistry.MDX23C_VOCALS, context)
        val isRoFormerReady = ModelManager.isModelAvailable(ModelRegistry.ROFORMER_MELBAND, context)

        val selectedEngine: SourceSeparationEngine = when (config.qualityMode) {
            MusicRemovalConfig.QualityMode.FAST -> {
                when {
                    isMdxReady -> MDXEngine(ModelRegistry.MDX23C_VOCALS)
                    else -> NativeDspEngine
                }
            }

            MusicRemovalConfig.QualityMode.BALANCED -> {
                when {
                    isDemucsReady && deviceProfile != MusicRemovalConfig.DeviceProfile.LOW -> DemucsEngine()
                    isMdxReady -> MDXEngine()
                    else -> NativeDspEngine
                }
            }

            MusicRemovalConfig.QualityMode.HIGH_QUALITY -> {
                when {
                    isDemucsReady && isMdxReady -> {
                        EnsembleEngine(
                            primaryEngine = DemucsEngine(),
                            secondaryEngine = MDXEngine(),
                            primaryWeight = 0.70f
                        )
                    }
                    isDemucsReady -> DemucsEngine()
                    isMdxReady -> MDXEngine()
                    else -> NativeDspEngine
                }
            }

            MusicRemovalConfig.QualityMode.MAX_REMOVAL -> {
                when {
                    isDemucsReady && isRoFormerReady -> {
                        EnsembleEngine(
                            primaryEngine = DemucsEngine(),
                            secondaryEngine = RoFormerEngine(),
                            primaryWeight = 0.60f
                        )
                    }
                    isDemucsReady && isMdxReady -> {
                        EnsembleEngine(
                            primaryEngine = DemucsEngine(),
                            secondaryEngine = MDXEngine(),
                            primaryWeight = 0.65f
                        )
                    }
                    isDemucsReady -> DemucsEngine()
                    isMdxReady -> MDXEngine()
                    else -> NativeDspEngine
                }
            }
        }

        return Strategy.ExecuteEngine(selectedEngine)
    }
}
