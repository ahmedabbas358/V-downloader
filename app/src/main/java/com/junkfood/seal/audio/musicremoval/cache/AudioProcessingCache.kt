package com.junkfood.seal.audio.musicremoval.cache

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.model.ModelManager
import java.io.File

/**
 * AudioProcessingCache
 *
 * Caches separation outputs based on SHA-256 hash of the input file + config signature
 * to avoid duplicate processing.
 */
object AudioProcessingCache {

    private const val TAG = "AudioProcessingCache"
    private const val CACHE_DIR_NAME = "audio_separation_cache"

    fun getCacheDirectory(appContext: Context = context): File {
        val dir = File(appContext.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun computeCacheKey(
        inputFile: File,
        config: MusicRemovalConfig
    ): String {
        val fileHash = ModelManager.computeSha256(inputFile).take(16)
        val configSig = "${config.qualityMode}_${config.speechPreservationLevel}_${config.speechEnhancementDb}"
        return "${fileHash}_$configSig"
    }

    fun getCachedFile(
        inputFile: File,
        config: MusicRemovalConfig,
        appContext: Context = context
    ): File? {
        val key = computeCacheKey(inputFile, config)
        val ext = inputFile.extension
        val cachedFile = File(getCacheDirectory(appContext), "${key}_cached.$ext")
        return if (cachedFile.exists() && cachedFile.length() > 0) cachedFile else null
    }

    fun putInCache(
        inputFile: File,
        processedFile: File,
        config: MusicRemovalConfig,
        appContext: Context = context
    ) {
        try {
            val key = computeCacheKey(inputFile, config)
            val ext = inputFile.extension
            val cachedFile = File(getCacheDirectory(appContext), "${key}_cached.$ext")
            processedFile.copyTo(cachedFile, overwrite = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache processed audio result: ${e.message}")
        }
    }

    fun clearCache(appContext: Context = context) {
        getCacheDirectory(appContext).deleteRecursively()
    }
}
