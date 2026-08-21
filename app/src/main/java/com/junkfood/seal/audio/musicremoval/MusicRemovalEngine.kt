package com.junkfood.seal.audio.musicremoval

import android.content.Context
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.engine.BsRoFormerMusicRemovalEngine
import com.junkfood.seal.audio.musicremoval.model.BsRoFormerModelRegistry
import com.junkfood.seal.audio.musicremoval.model.BsRoFormerModelSpec
import java.io.File

/**
 * MusicRemovalCapabilities
 *
 * Exposes device capabilities for Band-Split RoFormer (BS-RoFormer) neural music removal.
 */
data class MusicRemovalCapabilities(
    val engineName: String = "Band-Split RoFormer (BS-RoFormer)",
    val isNeuralAccelerated: Boolean = true,
    val supportedModels: List<BsRoFormerModelSpec> = BsRoFormerModelRegistry.ALL_MODELS
)

/**
 * MusicRemovalResult
 *
 * Comprehensive metrics and status report for a music removal operation.
 */
data class MusicRemovalResult(
    val success: Boolean,
    val outputPath: String?,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val model: String,
    val processingTimeMs: Long,
    val passes: Int,
    val qualityScore: Float,
    val musicResidualScore: Float,
    val speechPreservationScore: Float,
    val warnings: List<String> = emptyList(),
    val error: String? = null
)

/**
 * MusicRemovalEngine
 *
 * Root abstraction interface for the music removal subsystem.
 * Exclusively implemented by [BsRoFormerMusicRemovalEngine].
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
     * Processes a single media file and returns detailed [MusicRemovalResult].
     */
    suspend fun separateAudio(
        inputFile: File,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig = MusicRemovalConfig(),
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null
    ): MusicRemovalResult =
        BsRoFormerMusicRemovalEngine.separateAudio(inputFile, isAudioOnly, config, appContext, onProgress)

    companion object {
        val capabilities: MusicRemovalCapabilities
            get() = BsRoFormerMusicRemovalEngine.capabilities

        suspend fun processFiles(
            filePaths: List<String>,
            isAudioOnly: Boolean,
            config: MusicRemovalConfig = MusicRemovalConfig(),
            appContext: Context = context,
            onProgress: ((Float, String) -> Unit)? = null
        ): List<String> {
            return BsRoFormerMusicRemovalEngine.processFiles(filePaths, isAudioOnly, config, appContext, onProgress)
        }

        suspend fun processSingleFile(
            inputFile: File,
            isAudioOnly: Boolean,
            config: MusicRemovalConfig = MusicRemovalConfig(),
            appContext: Context = context,
            onProgress: ((Float, String) -> Unit)? = null
        ): File {
            return BsRoFormerMusicRemovalEngine.processSingleFile(inputFile, isAudioOnly, config, appContext, onProgress)
        }

        suspend fun separateAudio(
            inputFile: File,
            isAudioOnly: Boolean,
            config: MusicRemovalConfig = MusicRemovalConfig(),
            appContext: Context = context,
            onProgress: ((Float, String) -> Unit)? = null
        ): MusicRemovalResult {
            return BsRoFormerMusicRemovalEngine.separateAudio(inputFile, isAudioOnly, config, appContext, onProgress)
        }
    }
}
