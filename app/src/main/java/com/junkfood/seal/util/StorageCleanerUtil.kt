package com.junkfood.seal.util

import android.os.Environment
import android.util.Log
import com.junkfood.seal.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StorageCleanerUtil {

    private const val TAG = "StorageCleanerUtil"

    data class CleanupReport(
        val scannedFilesCount: Int,
        val orphanFilesCount: Int,
        val totalReclaimableBytes: Long,
        val orphanFiles: List<File>
    ) {
        val formattedReclaimableSize: String
            get() = FileUtil.formatFileSize(totalReclaimableBytes)
    }

    private val targetExtensions = setOf("part", "ytdl", "tmp", "temp")

    /**
     * Scans for temporary, partial, and orphaned files across all download locations and app cache.
     */
    suspend fun scanOrphanFiles(): CleanupReport = withContext(Dispatchers.IO) {
        val searchRoots = listOfNotNull(
            File(App.videoDownloadDir),
            File(App.audioDownloadDir),
            File(App.privateDownloadDir),
            App.context.cacheDir,
            App.context.externalCacheDir,
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "V-Downloader"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seal"),
        ).filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }

        var scannedCount = 0
        val orphanFiles = mutableListOf<File>()
        var totalBytes = 0L

        searchRoots.forEach { root ->
            try {
                root.walkTopDown().maxDepth(5).forEach { file ->
                    if (file.isFile) {
                        scannedCount++
                        val ext = file.extension.lowercase()
                        val isTempName = file.name.contains(".part-Frag") ||
                                file.name.endsWith(".ytdl", ignoreCase = true) ||
                                file.name.endsWith(".part", ignoreCase = true) ||
                                file.name.endsWith(".tmp", ignoreCase = true)

                        if (ext in targetExtensions || isTempName || (file.length() == 0L && file.name.endsWith(".srt"))) {
                            val isOlderThan10Min = System.currentTimeMillis() - file.lastModified() > 10 * 60 * 1000
                            if (isOlderThan10Min) {
                                orphanFiles.add(file)
                                totalBytes += file.length()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning root: ${root.absolutePath}", e)
            }
        }

        CleanupReport(
            scannedFilesCount = scannedCount,
            orphanFilesCount = orphanFiles.size,
            totalReclaimableBytes = totalBytes,
            orphanFiles = orphanFiles
        )
    }

    /**
     * Deletes the identified orphan files and returns the reclaimed bytes.
     */
    suspend fun cleanOrphanFiles(files: List<File>): Long = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        files.forEach { file ->
            try {
                val len = file.length()
                if (file.delete()) {
                    reclaimed += len
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete: ${file.absolutePath}", e)
            }
        }
        reclaimed
    }
}
