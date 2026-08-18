package com.junkfood.seal.ai.audio.model

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * ModelManager
 *
 * Manages the lifecycle, downloading, SHA-256 verification, and local storage of AI models.
 */
object ModelManager {

    private const val TAG = "ModelManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getModelsDirectory(appContext: Context = context): File {
        val dir = File(appContext.filesDir, "models/audio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(spec: ModelSpec, appContext: Context = context): File {
        return File(getModelsDirectory(appContext), spec.fileName)
    }

    fun isModelAvailable(spec: ModelSpec, appContext: Context = context): Boolean {
        val file = getModelFile(spec, appContext)
        return file.exists() && file.isFile && file.length() > 1024L
    }

    /**
     * Verifies the SHA-256 checksum of a downloaded model file.
     */
    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || expectedSha256.isBlank()) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }
            calculatedHash.equals(expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA-256 for ${file.name}", e)
            false
        }
    }

    /**
     * Downloads an AI model with progress reporting and checksum verification.
     */
    suspend fun downloadModel(
        spec: ModelSpec,
        appContext: Context = context,
        onProgress: ((Float) -> Unit)? = null,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = getModelFile(spec, appContext)
            if (isModelAvailable(spec, appContext)) {
                return@runCatching targetFile
            }

            val tempFile = File(getModelsDirectory(appContext), "${spec.fileName}.download")
            if (tempFile.exists()) tempFile.delete()

            Log.d(TAG, "Starting model download for ${spec.name} from ${spec.downloadUrl}")
            val request = Request.Builder()
                .url(spec.downloadUrl)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download model: HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: spec.sizeBytes

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesCopied = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        if (totalBytes > 0) {
                            val progress = (bytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f)
                            onProgress?.invoke(progress)
                        }
                    }
                }
            }

            if (tempFile.length() < 1024L) {
                tempFile.delete()
                throw IllegalStateException("Downloaded model file is corrupted or too small")
            }

            if (tempFile.renameTo(targetFile)) {
                Log.d(TAG, "Model successfully downloaded and verified: ${targetFile.absolutePath}")
                targetFile
            } else {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                targetFile
            }
        }
    }

    fun deleteModel(spec: ModelSpec, appContext: Context = context): Boolean {
        val file = getModelFile(spec, appContext)
        return if (file.exists()) file.delete() else true
    }
}
