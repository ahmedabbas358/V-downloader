package com.junkfood.seal.download.engine.postprocess

import android.util.Log
import com.junkfood.seal.ai.audio.pipeline.AudioSeparationPipeline
import com.junkfood.seal.ai.audio.pipeline.SeparationOptions
import com.junkfood.seal.util.MediaProcessingEngine
import java.io.File

/**
 * VocalIsolationProcessor
 *
 * Coordinates audio transformation pipelines:
 * 1. Neural & Spectral vocal isolation via [AudioSeparationPipeline]
 * 2. Speech clarity and noise suppression filters via [MediaProcessingEngine]
 */
object VocalIsolationProcessor {

    private const val TAG = "VocalIsolationProcessor"

    /**
     * Processes downloaded media files to remove music / isolate vocals via AI.
     *
     * @param filePaths The list of target file paths
     * @param isAudioOnly Whether the files are audio-only
     * @param options Separation options and quality mode
     * @param onProgress Callback receiving progress (0..100) and status message
     * @return Updated list of processed file paths
     */
    suspend fun removeMusicFromFiles(
        filePaths: List<String>,
        isAudioOnly: Boolean,
        options: SeparationOptions = SeparationOptions(),
        onProgress: ((Float, String) -> Unit)? = null,
    ): List<String> {
        if (filePaths.isEmpty()) return emptyList()

        Log.d(TAG, "Starting AI vocal isolation for ${filePaths.size} file(s)...")
        return try {
            AudioSeparationPipeline.processFiles(
                filePaths = filePaths,
                isAudioOnly = isAudioOnly,
                options = options,
                onProgress = onProgress,
            )
        } catch (e: Exception) {
            Log.e(TAG, "AI Vocal isolation failed", e)
            throw e
        }
    }

    /**
     * Applies speech clarity and dynamic denoising filter to a media file.
     */
    suspend fun applySpeechClarity(
        inputFile: File,
        outputFile: File,
    ): Result<File> {
        return MediaProcessingEngine.applySpeechClarityFilter(inputFile, outputFile)
    }
}
