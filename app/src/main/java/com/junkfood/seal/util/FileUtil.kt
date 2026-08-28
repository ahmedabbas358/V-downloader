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

    fun isVideoFile(file: File): Boolean = isVideoFile(file.name)
    fun isAudioFile(file: File): Boolean = isAudioFile(file.name)
    fun isSubtitleFile(file: File): Boolean = isSubtitleFile(file.name)

    fun isVideoFile(path: String?): Boolean = !path.isNullOrEmpty() && path.contains(Regex(VIDEO_REGEX))
    fun isAudioFile(path: String?): Boolean = !path.isNullOrEmpty() && path.contains(Regex(AUDIO_REGEX))
    fun isSubtitleFile(path: String?): Boolean = !path.isNullOrEmpty() && path.contains(Regex(SUBTITLE_REGEX))

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

    fun openFile(path: String, onFailureCallback: (Throwable) -> Unit) {
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
            // Relax StrictMode VM policy for file:// URI compatibility with third-party file managers
            try {
                val builder = android.os.StrictMode.VmPolicy.Builder()
                android.os.StrictMode.setVmPolicy(builder.build())
            } catch (_: Exception) {}

            if (path.startsWith("content://")) {
                try {
                    val treeUri = Uri.parse(path)
                    val treeIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(treeIntent, "فتح المجلد / Open with").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    return@runCatching
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open content URI directly: ${e.message}")
                }
            }

            val dir = File(path)
            if (!dir.exists()) dir.mkdirs()

            val relativePath = dir.absolutePath.substringAfter("/storage/emulated/0/", "")
            val fileUri = Uri.fromFile(dir)
            val contentUri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", dir)
            }.getOrNull()

            val candidateIntents = mutableListOf<Intent>()

            // 1. Intents with file:// URI and folder MIME types (widely supported by Solid Explorer, MiXplorer, ZArchiver, Total Commander, Samsung My Files, Xiaomi)
            val folderMimes = listOf("resource/folder", "vnd.android.document/directory", "inode/directory", "*/*")
            for (mime in folderMimes) {
                candidateIntents.add(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, mime)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }

            // 2. Intents with FileProvider Content URI
            if (contentUri != null) {
                for (mime in folderMimes) {
                    candidateIntents.add(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, mime)
                            clipData = ClipData.newRawUri("", contentUri)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                }
            }

            // 3. SAF Tree / Document URI for DocumentsUI and modern SAF explorers
            if (relativePath.isNotEmpty()) {
                val docUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:$relativePath"
                )
                candidateIntents.add(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
                val treeUri = DocumentsContract.buildTreeDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:$relativePath"
                )
                candidateIntents.add(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }

            // Try launching with Intent Chooser or direct intent
            var launched = false
            for (intent in candidateIntents) {
                try {
                    val resolved = context.packageManager.queryIntentActivities(intent, 0)
                    if (resolved.isNotEmpty()) {
                        val chooser = Intent.createChooser(intent, "فتح المجلد / Open directory with").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                        launched = true
                        break
                    }
                } catch (_: Exception) {}
            }

            // If queryIntentActivities was restricted by package visibility, try direct startActivity
            if (!launched) {
                for (intent in candidateIntents) {
                    try {
                        context.startActivity(intent)
                        launched = true
                        break
                    } catch (_: Exception) {}
                }
            }

            if (!launched) {
                Log.w(TAG, "No specific file manager resolved for $path")
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
            clipData = ClipData.newRawUri("", uri)
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
        try {
            if (DocumentsContract.isTreeUri(treeUri)) {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val decodedDocId = Uri.decode(docId)
                val split = decodedDocId.split(":")
                val type = split[0]
                val relativePath = if (split.size > 1) split[1].trimStart('/') else ""
                if ("primary".equals(type, ignoreCase = true)) {
                    val root = Environment.getExternalStorageDirectory().absolutePath
                    return if (relativePath.isNotEmpty()) "$root/$relativePath" else root
                } else {
                    val extFile = File("/storage/$type/$relativePath")
                    if (extFile.exists() || File("/storage/$type").exists()) {
                        return extFile.absolutePath
                    }
                }
            }
            val decodedPath = Uri.decode(treeUri.toString())
            if (decodedPath.contains("primary:")) {
                val last = decodedPath.substringAfter("primary:").trimStart('/')
                return Environment.getExternalStorageDirectory().absolutePath + "/$last"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving real path from uri: $treeUri", e)
        }
        return getExternalDownloadDirectory().absolutePath
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
}
