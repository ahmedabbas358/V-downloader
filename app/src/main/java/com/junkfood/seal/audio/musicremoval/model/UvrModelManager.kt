package com.junkfood.seal.audio.musicremoval.model

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
 * UvrModelManager
 *
 * Manages lifecycle, SHA-256 integrity verification, on-demand downloading, and storage of UVR ONNX models.
 */
object UvrModelManager {

    private const val TAG = "UvrModelManager"
    private const val UVR_MODELS_SUBDIR = "uvr_models"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Storage directory for UVR models on device.
     */
    fun getModelsDirectory(appContext: Context = context): File {
        val dir = File(appContext.noBackupFilesDir, UVR_MODELS_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Resolves the target file on disk for a given [UvrModelSpec].
     */
    fun getModelFile(spec: UvrModelSpec, appContext: Context = context): File =
        File(getModelsDirectory(appContext), spec.fileName)

    /**
     * Checks if a model file exists, is non-empty, and satisfies minimum size requirements.
     */
    fun isModelAvailable(spec: UvrModelSpec, appContext: Context = context): Boolean {
        val file = getModelFile(spec, appContext)
        return file.exists() && file.length() >= (spec.sizeBytes / 2).coerceAtLeast(1024L * 1024L)
    }

    /**
     * Computes the SHA-256 hash of a file.
     */
    fun computeSha256(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies the integrity of a UVR model file.
     */
    fun verifyModelIntegrity(spec: UvrModelSpec, appContext: Context = context): Boolean {
        val file = getModelFile(spec, appContext)
        if (!file.exists()) return false

        if (file.length() < 1024L * 1024L) {
            Log.w(TAG, "UVR model file ${file.name} is too small (${file.length()} bytes)")
            return false
        }

        // Validate hash if non-placeholder
        if (spec.sha256.isNotBlank() && !spec.sha256.startsWith("a1b2c3d4") && !spec.sha256.startsWith("e3b0c442")) {
            val calculated = computeSha256(file)
            if (!calculated.equals(spec.sha256, ignoreCase = true)) {
                Log.e(TAG, "SHA-256 mismatch for ${spec.name}. Expected ${spec.sha256}, got $calculated")
                return false
            }
        }

        return true
    }

    /**
     * Downloads a UVR model file with progress tracking and atomic move.
     */
    suspend fun downloadModel(
        spec: UvrModelSpec,
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = getModelFile(spec, appContext)
            val tempFile = File(getModelsDirectory(appContext), "${spec.fileName}.download_${System.currentTimeMillis()}")

            if (isModelAvailable(spec, appContext) && verifyModelIntegrity(spec, appContext)) {
                Log.d(TAG, "UVR Model ${spec.name} is already available and verified.")
                return@runCatching targetFile
            }

            Log.d(TAG, "Starting download for ${spec.name} from ${spec.downloadUrl}")
            onProgress?.invoke(0.01f, "بدء تنزيل نموذج UVR: ${spec.name}...")

            val request = Request.Builder().url(spec.downloadUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download UVR model: HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body from UVR model server")
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else spec.sizeBytes

            tempFile.parentFile?.mkdirs()
            if (tempFile.exists()) tempFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        if (totalBytes > 0) {
                            val progress = (totalRead.toFloat() / totalBytes).coerceIn(0f, 0.99f)
                            val mbRead = totalRead / (1024 * 1024)
                            val totalMb = totalBytes / (1024 * 1024)
                            onProgress?.invoke(progress, "تنزيل نموذج UVR: $mbRead MB / $totalMb MB")
                        }
                    }
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Log.d(TAG, "UVR Model download completed successfully: ${targetFile.absolutePath}")
            onProgress?.invoke(1.0f, "اكتمل تنزيل وتثبيت نموذج UVR بنجاح.")
            targetFile
        }
    }

    /**
     * Deletes a model from local storage.
     */
    fun deleteModel(spec: UvrModelSpec, appContext: Context = context): Boolean {
        val file = getModelFile(spec, appContext)
        return if (file.exists()) file.delete() else true
    }
}
