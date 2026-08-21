package com.junkfood.seal.audio.musicremoval.cache

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import java.io.File
import java.security.MessageDigest

/**
 * BsRoFormerCacheManager
 *
 * Provides result caching for processed audio files to avoid redundant inference on identical media.
 */
object BsRoFormerCacheManager {

    private const val TAG = "BsRoFormerCacheManager"
    private const val CACHE_SUBDIR = "bs_roformer_cache"

    fun getCacheDirectory(appContext: Context = context): File {
        val dir = File(appContext.cacheDir, CACHE_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun computeFileSignature(file: File, config: MusicRemovalConfig): String {
        val raw = "${file.name}_${file.length()}_${file.lastModified()}_${config.qualityMode}_${config.speechPreservationLevel}_${config.primaryModelId}"
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getCachedFile(sourceFile: File, config: MusicRemovalConfig, appContext: Context = context): File? {
        val sig = computeFileSignature(sourceFile, config)
        val cached = File(getCacheDirectory(appContext), "$sig.${sourceFile.extension}")
        return if (cached.exists() && cached.length() > 0) cached else null
    }

    fun putInCache(sourceFile: File, processedFile: File, config: MusicRemovalConfig, appContext: Context = context) {
        try {
            val sig = computeFileSignature(sourceFile, config)
            val cacheTarget = File(getCacheDirectory(appContext), "$sig.${sourceFile.extension}")
            processedFile.copyTo(cacheTarget, overwrite = true)
            Log.d(TAG, "Cached BS-RoFormer result for ${sourceFile.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache BS-RoFormer result", e)
        }
    }

    fun clearCache(appContext: Context = context) {
        getCacheDirectory(appContext).deleteRecursively()
    }
}
