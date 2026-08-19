package com.junkfood.seal.audio.musicremoval

import android.content.Context
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.engine.UvrMusicRemovalEngine
import com.junkfood.seal.audio.musicremoval.model.UvrModelRegistry
import com.junkfood.seal.audio.musicremoval.model.UvrModelSpec
import java.io.File

/**
 * MusicRemovalCapabilities
 *
 * Exposes device capabilities for UVR neural music removal.
 */
data class MusicRemovalCapabilities(
    val engineName: String = "Ultimate Vocal Remover (UVR)",
    val isNeuralAccelerated: Boolean = true,
    val supportedModels: List<UvrModelSpec> = UvrModelRegistry.ALL_UVR_MODELS
)

/**
 * MusicRemovalEngine
 *
 * Clean root abstraction interface for the music removal subsystem.
 * Exclusively implemented by [UvrMusicRemovalEngine].
 */
interface MusicRemovalEngine {

    val capabilities: MusicRemovalCapabilities
        get() = MusicRemovalCapabilities()

    /**
     * Processes a list of media file paths.
     */
    suspend fun processFiles(
        filePaths: List<String>,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig = MusicRemovalConfig(),
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null
    ): List<String>

    /**
     * Processes a single media file.
     */
    suspend fun processSingleFile(
        inputFile: File,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig = MusicRemovalConfig(),
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null
    ): File

    companion object : MusicRemovalEngine {
        override val capabilities: MusicRemovalCapabilities
            get() = MusicRemovalCapabilities()

        override suspend fun processFiles(
            filePaths: List<String>,
            isAudioOnly: Boolean,
            config: MusicRemovalConfig,
            appContext: Context,
            onProgress: ((Float, String) -> Unit)?
        ): List<String> {
            return UvrMusicRemovalEngine.processFiles(filePaths, isAudioOnly, config, appContext, onProgress)
        }

        override suspend fun processSingleFile(
            inputFile: File,
            isAudioOnly: Boolean,
            config: MusicRemovalConfig,
            appContext: Context,
            onProgress: ((Float, String) -> Unit)?
        ): File {
            return UvrMusicRemovalEngine.processSingleFile(inputFile, isAudioOnly, config, appContext, onProgress)
        }
    }
}
