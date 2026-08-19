package com.junkfood.seal.download.engine.postprocess

import android.util.Log
import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.MusicRemovalEngine
import java.io.File

/**
 * VocalIsolationProcessor
 *
 * Delegates audio transformation to [MusicRemovalEngine] — the new local, offline-first,
 * modular neural + spectral separation system. No legacy engines or external calls.
 *
 * All music removal logic lives exclusively in the `com.junkfood.seal.audio.musicremoval` package.
 */
object VocalIsolationProcessor {

    private const val TAG = "VocalIsolationProcessor"

    /**
     * Removes music from downloaded media files using the new MusicRemovalEngine.
     *
     * @param filePaths  The list of target file paths
     * @param isAudioOnly Whether the files are audio-only (vs video)
     * @param config     Separation configuration and quality mode
     * @param onProgress Callback receiving progress (0..1) and status message
     * @return Updated list of processed file paths (original path returned on per-file failure)
     */
    suspend fun removeMusicFromFiles(
        filePaths: List<String>,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig = MusicRemovalConfig(),
        onProgress: ((Float, String) -> Unit)? = null,
    ): List<String> {
        if (filePaths.isEmpty()) return emptyList()

        Log.d(TAG, "Starting MusicRemovalEngine for ${filePaths.size} file(s)...")
        return try {
            MusicRemovalEngine.processFiles(
                filePaths = filePaths,
                isAudioOnly = isAudioOnly,
                config = config,
                onProgress = onProgress,
            )
        } catch (e: Exception) {
            Log.e(TAG, "MusicRemovalEngine failed, returning original paths", e)
            // Non-fatal: return original paths so the download is not lost
            filePaths
        }
    }

    /**
     * Processes a single file through the music removal engine.
     * Useful for standalone callers (e.g., post-trim processing).
     */
    suspend fun processSingleFile(
        inputFile: File,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig = MusicRemovalConfig(),
        onProgress: ((Float, String) -> Unit)? = null,
    ): File {
        return try {
            MusicRemovalEngine.processSingleFile(
                inputFile = inputFile,
                isAudioOnly = isAudioOnly,
                config = config,
                onProgress = onProgress,
            )
        } catch (e: Exception) {
            Log.e(TAG, "processSingleFile failed for ${inputFile.name}, preserving original", e)
            inputFile
        }
    }
}
