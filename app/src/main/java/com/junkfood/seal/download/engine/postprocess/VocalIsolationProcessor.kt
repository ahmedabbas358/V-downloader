package com.junkfood.seal.download.engine.postprocess

import android.util.Log
import com.junkfood.seal.util.MediaProcessingEngine
import com.junkfood.seal.util.MusicRemovalEngine
import java.io.File

/**
 * VocalIsolationProcessor
 *
 * Coordinates audio transformation pipelines:
 * 1. Multi-tier studio vocal isolation & music removal via [MusicRemovalEngine]
 * 2. Speech clarity and noise suppression filters via [MediaProcessingEngine]
 */
object VocalIsolationProcessor {

    private const val TAG = "VocalIsolationProcessor"

    /**
     * Processes downloaded media files to remove music / isolate vocals.
     *
     * @param filePaths The list of target file paths
     * @param isAudioOnly Whether the files are audio-only
     * @param onProgress Callback receiving progress (0..100) and status message
     * @return Updated list of processed file paths
     */
    suspend fun removeMusicFromFiles(
        filePaths: List<String>,
        isAudioOnly: Boolean,
        onProgress: ((Float, String) -> Unit)? = null,
    ): List<String> {
        if (filePaths.isEmpty()) return emptyList()

        Log.d(TAG, "Starting vocal isolation for ${filePaths.size} file(s)...")
        return try {
            MusicRemovalEngine.processFiles(
                filePaths = filePaths,
                isAudioOnly = isAudioOnly,
                onProgress = onProgress,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Vocal isolation failed", e)
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
