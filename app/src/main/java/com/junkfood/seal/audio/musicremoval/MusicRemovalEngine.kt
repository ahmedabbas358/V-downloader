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

    /**
     * Processes a single media file and returns detailed [SeparationResult].
     */
    suspend fun separateAudio(
        inputFile: File,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig = MusicRemovalConfig(),
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null
    ): com.junkfood.seal.audio.musicremoval.engine.SeparationResult =
        UvrMusicRemovalEngine.separateAudio(inputFile, isAudioOnly, config, appContext, onProgress)

    companion object {
        val capabilities: MusicRemovalCapabilities
            get() = UvrMusicRemovalEngine.capabilities

        suspend fun processFiles(
            filePaths: List<String>,
            isAudioOnly: Boolean,
            config: MusicRemovalConfig = MusicRemovalConfig(),
            appContext: Context = context,
            onProgress: ((Float, String) -> Unit)? = null
        ): List<String> {
            return UvrMusicRemovalEngine.processFiles(filePaths, isAudioOnly, config, appContext, onProgress)
        }

        suspend fun processSingleFile(
            inputFile: File,
            isAudioOnly: Boolean,
            config: MusicRemovalConfig = MusicRemovalConfig(),
            appContext: Context = context,
            onProgress: ((Float, String) -> Unit)? = null
        ): File {
            return UvrMusicRemovalEngine.processSingleFile(inputFile, isAudioOnly, config, appContext, onProgress)
        }

        suspend fun separateAudio(
            inputFile: File,
            isAudioOnly: Boolean,
            config: MusicRemovalConfig = MusicRemovalConfig(),
            appContext: Context = context,
            onProgress: ((Float, String) -> Unit)? = null
        ): com.junkfood.seal.audio.musicremoval.engine.SeparationResult {
            return UvrMusicRemovalEngine.separateAudio(inputFile, isAudioOnly, config, appContext, onProgress)
        }
    }
}
