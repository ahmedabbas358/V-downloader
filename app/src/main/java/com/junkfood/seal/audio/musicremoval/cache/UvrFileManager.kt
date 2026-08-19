package com.junkfood.seal.audio.musicremoval.cache

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * UvrFileManager
 *
 * Manages atomic temporary file writes, cross-partition moves, validation before finalization,
 * and result caching for the UVR pipeline.
 */
object UvrFileManager {

    private const val TAG = "UvrFileManager"
    private const val CACHE_SUBDIR = "uvr_processed_cache"

    /**
     * Storage directory for processed UVR audio cache.
     */
    fun getCacheDirectory(appContext: Context = context): File {
        val dir = File(appContext.cacheDir, CACHE_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Generates a deterministic cache key.
     */
    fun generateCacheKey(
        inputFile: File,
        config: MusicRemovalConfig
    ): String {
        val inputInfo = "${inputFile.name}_${inputFile.length()}_${inputFile.lastModified()}"
        val configInfo = "${config.qualityMode}_${config.speechPreservationLevel}_${config.speechEnhancementDb}"
        val rawKey = "${inputInfo}_$configInfo"

        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(rawKey.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Retrieves a cached result if valid and non-empty.
     */
    fun getCachedFile(
        inputFile: File,
        config: MusicRemovalConfig,
        appContext: Context = context
    ): File? {
        val key = generateCacheKey(inputFile, config)
        val ext = inputFile.extension.ifBlank { "mp3" }
        val cached = File(getCacheDirectory(appContext), "${key}.$ext")
        return if (cached.exists() && cached.length() > 1024L) cached else null
    }

    /**
     * Stores a processed result in the cache.
     */
    fun putInCache(
        inputFile: File,
        processedFile: File,
        config: MusicRemovalConfig,
        appContext: Context = context
    ) {
        try {
            val key = generateCacheKey(inputFile, config)
            val ext = inputFile.extension.ifBlank { "mp3" }
            val cacheTarget = File(getCacheDirectory(appContext), "${key}.$ext")
            processedFile.copyTo(cacheTarget, overwrite = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache processed file: ${e.message}")
        }
    }

    /**
     * Performs atomic replacement of target file with temporary result.
     */
    suspend fun atomicFinalize(
        tempResultFile: File,
        finalTargetFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!tempResultFile.exists() || tempResultFile.length() < 100L) {
                throw IllegalStateException("Temporary processed file is missing or empty")
            }

            try {
                // Primary: Fast atomic move on same filesystem partition
                Files.move(
                    tempResultFile.toPath(),
                    finalTargetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                // Fallback: Cross-partition safe stream copy and delete
                tempResultFile.inputStream().use { src ->
                    finalTargetFile.outputStream().use { dst ->
                        src.copyTo(dst)
                    }
                }
                tempResultFile.delete()
            }

            if (!finalTargetFile.exists() || finalTargetFile.length() == 0L) {
                throw IllegalStateException("Final target file is empty after atomic move")
            }

            finalTargetFile
        }
    }
}
