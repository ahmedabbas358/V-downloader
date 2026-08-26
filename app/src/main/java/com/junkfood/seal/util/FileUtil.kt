package com.junkfood.seal.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import java.util.Locale
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.R
import java.io.File
import okhttp3.internal.closeQuietly

import android.content.ContentUris
import android.provider.MediaStore
import java.io.FileNotFoundException

const val VIDEO_REGEX = "(?i)\\.(mp4|mkv|webm|mov|avi|flv|m4v|ts|3gp)$"
const val AUDIO_REGEX = "(?i)\\.(mp3|aac|opus|m4a|ogg|flac|wav|wma|mka|m4b)$"
const val THUMBNAIL_REGEX = "(?i)\\.(jpg|jpeg|png|webp)$"
const val SUBTITLE_REGEX = "(?i)\\.(lrc|vtt|srt|ass|json3|srv\\d?|ttml|sub|ssa)$"
private const val PRIVATE_DIRECTORY_SUFFIX = ".V-Downloader"

object FileUtil {
    private const val TAG = "FileUtil"

    fun isVideoFile(file: File): Boolean = file.name.contains(Regex(VIDEO_REGEX))
    fun isAudioFile(file: File): Boolean = file.name.contains(Regex(AUDIO_REGEX))
    fun isSubtitleFile(file: File): Boolean = file.name.contains(Regex(SUBTITLE_REGEX))

    fun isVideoFile(path: String): Boolean = path.contains(Regex(VIDEO_REGEX))
    fun isAudioFile(path: String): Boolean = path.contains(Regex(AUDIO_REGEX))
    fun isSubtitleFile(path: String): Boolean = path.contains(Regex(SUBTITLE_REGEX))

    fun createUriForFile(file: File): Uri? {
        if (!file.exists()) return null

        // 1. Try FileProvider first (most standard and compatible with modern Android)
        try {
            return FileProvider.getUriForFile(context, context.getFileProvider(), file)
        } catch (e: Exception) {
            Log.w(TAG, "FileProvider getUriForFile failed for ${file.absolutePath}: ${e.message}")
        }

        // 2. Query MediaStore for scanned media content URI
        try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val queryUri = when {
                isVideoFile(file) -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                isAudioFile(file) -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }
            context.contentResolver.query(
                queryUri,
                projection,
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(file.absolutePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(queryUri, id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore lookup failed for ${file.absolutePath}: ${e.message}")
        }

        // 3. Fallback to file:// URI
        return try {
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }

    fun openFileFromResult(downloadResult: Result<List<String>>) {
        val filePaths = downloadResult.getOrNull()
        if (filePaths.isNullOrEmpty()) return
        openFile(filePaths.first()) {
            ToastUtil.makeToastSuspend(context.getString(R.string.file_unavailable))
        }
    }

    inline fun openFile(path: String, onFailureCallback: (Throwable) -> Unit) {
        try {
            val intent = createIntentForOpeningFile(path)
            if (intent != null) {
                if (intent.data?.scheme == "file") {
                    val builder = android.os.StrictMode.VmPolicy.Builder()
                    android.os.StrictMode.setVmPolicy(builder.build())
                }
                context.startActivity(intent)
            } else {
                throw FileNotFoundException("File does not exist: $path")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open file: $path", t)
            onFailureCallback(t)
        }
    }

    fun openDirectory(path: String, onFailureCallback: (Throwable) -> Unit = {}) {
        path.runCatching {
            val dir = File(path)
            if (!dir.exists()) dir.mkdirs()

            var launched = false

            // 1. Try DocumentsContract Document URI for Files/SAF
            val relativePath = dir.absolutePath.substringAfter("/storage/emulated/0/", "")
            if (relativePath.isNotEmpty()) {
                val docUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:$relativePath"
                )
                val docIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(docIntent)
                    launched = true
                } catch (e: Exception) {
                    Log.w(TAG, "DocumentsContract URI failed: ${e.message}")
                }
            }

            // 2. Try SAF Tree Document URI
            if (!launched && relativePath.isNotEmpty()) {
                val treeUri = DocumentsContract.buildTreeDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:$relativePath"
                )
                val treeIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(treeIntent)
                    launched = true
                } catch (e: Exception) {
                    Log.w(TAG, "Tree URI failed: ${e.message}")
                }
            }

            // 3. Try resource/folder MIME type with VM policy bypass for third-party file managers
            if (!launched) {
                try {
                    val builder = android.os.StrictMode.VmPolicy.Builder()
                    android.os.StrictMode.setVmPolicy(builder.build())
                    val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(dir), "resource/folder")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(folderIntent)
                    launched = true
                } catch (e: Exception) {
                    Log.w(TAG, "resource/folder failed: ${e.message}")
                }
            }

            // 4. Fallback to ACTION_VIEW_DOWNLOADS
            if (!launched) {
                val fallbackIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }.onFailure { onFailureCallback(it) }
    }

    private fun createIntentForFile(path: String?): Intent? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        val uri = if (path.startsWith("content://")) {
            Uri.parse(path)
        } else if (file.exists()) {
            createUriForFile(file)
        } else {
            null
        } ?: return null

        return Intent().apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            data = uri
        }
    }

    fun createIntentForOpeningFile(path: String?): Intent? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        val uri = if (path.startsWith("content://")) {
            Uri.parse(path)
        } else if (file.exists()) {
            createUriForFile(file)
        } else {
            null
        } ?: return null

        val extension = file.extension.ifEmpty { MimeTypeMap.getFileExtensionFromUrl(path) }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.US))
            ?: when {
                isAudioFile(file) -> "audio/*"
                isSubtitleFile(file) -> "text/plain"
                else -> "video/*"
            }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createIntentForSharingFile(path: String?): Intent? =
        createIntentForFile(path)?.apply {
            action = Intent.ACTION_SEND
            val extension = MimeTypeMap.getFileExtensionFromUrl(path.orEmpty())
            val mimeType = data?.let { context.contentResolver.getType(it) }
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.US))
                ?: when {
                    isAudioFile(File(path.orEmpty())) -> "audio/*"
                    isSubtitleFile(File(path.orEmpty())) -> "text/plain"
                    else -> "video/*"
                }
            setDataAndType(data, mimeType)
            putExtra(Intent.EXTRA_STREAM, data)
            clipData = ClipData(null, arrayOf(mimeType), ClipData.Item(data))
        }

    fun Context.getFileProvider() = "$packageName.provider"

    fun String.getFileSize(): Long =
        this.run {
            val length = File(this).length()
            if (length == 0L) DocumentFile.fromSingleUri(context, Uri.parse(this))?.length() ?: 0L
            else length
        }

    fun String.getFileName(): String =
        this.run {
            File(this).nameWithoutExtension.ifEmpty {
                DocumentFile.fromSingleUri(context, Uri.parse(this))?.name ?: "video"
            }
        }

    fun deleteFile(path: String) =
        path.runCatching {
            val file = File(path)
            val deleted = if (file.exists()) {
                file.delete()
            } else {
                DocumentFile.fromSingleUri(context, Uri.parse(this))?.delete() ?: false
            }
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            deleted
        }

    @CheckResult
    fun scanFileToMediaLibraryPostDownload(
        title: String,
        downloadDir: String,
        isSubtitleOnly: Boolean = false,
        videoId: String? = null
    ): List<String> {
        return com.junkfood.seal.download.engine.postprocess.MediaStorageScanner.scanAndRegister(
            title = title,
            downloadDir = downloadDir,
            isSubtitleOnly = isSubtitleOnly,
            videoId = videoId
        )
    }

    fun scanDownloadDirectoryToMediaLibrary(downloadDir: String) =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .run {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
            }

    @CheckResult
    fun moveFilesToSdcard(tempPath: File, sdcardUri: String): Result<List<String>> {
        val uriList = mutableListOf<String>()
        val destDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(sdcardUri)) 
            ?: return Result.failure(Exception("Invalid SD Card URI"))

        val dirCache = mutableMapOf<String, androidx.documentfile.provider.DocumentFile>(tempPath.absolutePath to destDir)

        val res =
            tempPath.runCatching {
                walkTopDown().forEach { file ->
                    if (file.isDirectory) {
                        if (file != tempPath) {
                            val parentFile = file.parentFile ?: return@forEach
                            val parentDoc = dirCache[parentFile.absolutePath] ?: destDir
                            val existingDir = parentDoc.findFile(file.name)
                            val newDir = existingDir ?: parentDoc.createDirectory(file.name)
                            if (newDir != null) {
                                dirCache[file.absolutePath] = newDir
                            }
                        }
                        return@forEach
                    }

                    val parentDoc = dirCache[file.parentFile?.absolutePath] ?: destDir
                    val mimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"

                    val newFile = parentDoc.createFile(mimeType, file.name) ?: return@forEach

                    file.inputStream().use { inputStream ->
                        context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                            inputStream.copyTo(outputStream)
                        } ?: return@forEach
                    }
                    uriList.add(newFile.uri.toString())
                }
                uriList
            }
        tempPath.deleteRecursively()
        return res
    }

    fun clearTempFiles(downloadDir: File): Int {
        var count = 0
        downloadDir.walkTopDown().forEach {
            if (it.isFile && !it.isHidden) {
                if (it.delete()) count++
            }
        }
        return count
    }

    fun Context.getConfigDirectory(): File = cacheDir

    fun Context.getConfigFile(suffix: String = "") = File(getConfigDirectory(), "config$suffix.txt")

    fun Context.getCookiesFile() = File(getConfigDirectory(), "cookies.txt")

    fun getExternalTempDir() =
        File(getExternalDownloadDirectory(), "tmp").apply {
            mkdirs()
            createEmptyFile(".nomedia")
        }

    fun Context.getSdcardTempDir(child: String?): File =
        getExternalTempDir().run { child?.let { resolve(it) } ?: this }

    fun Context.getArchiveFile(): File = filesDir.createEmptyFile("archive.txt").getOrThrow()

    fun Context.getInternalTempDir() = File(filesDir, "tmp")

    internal fun getExternalDownloadDirectory() =
        File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "V-Downloader",
            )
            .also { it.mkdir() }

    internal fun getExternalPrivateDownloadDirectory() =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            PRIVATE_DIRECTORY_SUFFIX,
        )

    fun File.createEmptyFile(fileName: String): Result<File> =
        this.runCatching {
                mkdirs()
                resolve(fileName).apply { this@apply.createNewFile() }
            }
            .onFailure { it.printStackTrace() }

    fun writeContentToFile(content: String, file: File): File = file.apply { writeText(content) }

    fun getRealPath(treeUri: Uri): String {
        val path: String = treeUri.path.toString()
        Log.d(TAG, path)
        if (!path.contains("primary:")) {
            ToastUtil.makeToast("This directory is not supported")
            return getExternalDownloadDirectory().absolutePath
        }
        val last: String = path.split("primary:").last()
        return Environment.getExternalStorageDirectory().absolutePath + "/$last"
    }

    fun cleanFileName(fileName: String): String =
        fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_").replace(Regex("\\s+"), " ").trim()

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0L -> "0 B"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024 -> String.format(java.util.Locale.US, "%.2f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024))
            else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    fun scanFileToMediaLibrary(file: File) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan file to media library", e)
        }
    }

    fun isVideoFile(path: String?): Boolean {
        if (path == null) return false
        val videoExtensions = listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "3gp", "ts", "m4v")
        return videoExtensions.any { path.endsWith(it, ignoreCase = true) }
    }

    fun isAudioFile(path: String?): Boolean {
        if (path == null) return false
        val audioExtensions = listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "opus", "alac")
        return audioExtensions.any { path.endsWith(it, ignoreCase = true) }
    }
}
