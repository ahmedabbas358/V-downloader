package com.junkfood.seal.util

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FFmpegManager
 *
 * Centralizes discovery, configuration, and execution of the on-device FFmpeg binary.
 * Ensures robust LD_LIBRARY_PATH resolution across all Android architectures and storage locations.
 */
object FFmpegManager {

    private const val TAG = "FFmpegManager"

    data class ProcessResult(
        val exitCode: Int,
        val output: String,
        val isSuccess: Boolean = exitCode == 0,
    )

    /**
     * Finds the native FFmpeg executable extracted by youtubedl-android or the system.
     */
    fun getFFmpegExecutable(appContext: Context = context): File? {
        val candidates = listOfNotNull(
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/bin/ffmpeg"),
            File(appContext.noBackupFilesDir, "packages/ffmpeg/usr/bin/ffmpeg"),
            File(appContext.noBackupFilesDir, "packages/ffmpeg/bin/ffmpeg"),
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/bin/ffmpeg"),
            File(appContext.noBackupFilesDir, "usr/bin/ffmpeg"),
            File(appContext.filesDir, "youtubedl-android/packages/ffmpeg/usr/bin/ffmpeg"),
            File(appContext.filesDir, "packages/ffmpeg/usr/bin/ffmpeg"),
            File(appContext.filesDir, "bin/ffmpeg"),
            File(appContext.filesDir, "ffmpeg"),
        )

        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile && candidate.name == "ffmpeg") {
                if (!candidate.canExecute()) {
                    candidate.setExecutable(true, false)
                }
                if (candidate.canExecute()) {
                    return candidate
                }
            }
        }

        // Deep search within packages and app storage directories for exact "ffmpeg" binary
        val searchDirs = listOfNotNull(
            File(appContext.noBackupFilesDir, "youtubedl-android"),
            File(appContext.noBackupFilesDir, "packages"),
            appContext.noBackupFilesDir,
            File(appContext.filesDir, "youtubedl-android"),
            File(appContext.filesDir, "packages"),
            appContext.filesDir,
        )

        for (dir in searchDirs) {
            if (dir.exists()) {
                val found = dir.walkTopDown().maxDepth(5).firstOrNull {
                    it.name == "ffmpeg" && it.isFile
                }
                if (found != null) {
                    if (!found.canExecute()) {
                        found.setExecutable(true, false)
                    }
                    if (found.canExecute()) {
                        return found
                    }
                }
            }
        }

        return null
    }

    /**
     * Executes an arbitrary FFmpeg command with automatic library path configuration.
     */
    suspend fun executeCommand(
        command: List<String>,
        appContext: Context = context,
        onLine: ((String) -> Unit)? = null
    ): ProcessResult = withContext(Dispatchers.IO) {
        val ffmpegBin = getFFmpegExecutable(appContext)
            ?: return@withContext ProcessResult(-1, "FFmpeg executable not found", false)

        val fullCommand = if (command.firstOrNull() == ffmpegBin.absolutePath) {
            command
        } else {
            listOf(ffmpegBin.absolutePath) + command
        }

        val processBuilder = ProcessBuilder(fullCommand)
        val env = processBuilder.environment()

        val possibleLibDirs = listOfNotNull(
            ffmpegBin.parentFile?.parentFile?.let { File(it, "lib") },
            ffmpegBin.parentFile?.parentFile?.let { File(it, "usr/lib") },
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib"),
            File(appContext.noBackupFilesDir, "packages/ffmpeg/usr/lib"),
            File(appContext.filesDir, "youtubedl-android/packages/ffmpeg/usr/lib"),
            File(appContext.filesDir, "packages/ffmpeg/usr/lib"),
            File(appContext.applicationInfo.nativeLibraryDir)
        ).filter { it.exists() && it.isDirectory }.map { it.absolutePath }.distinct()

        val ldLibraryPath = (possibleLibDirs + listOfNotNull(System.getenv("LD_LIBRARY_PATH"))).joinToString(":")
        env["LD_LIBRARY_PATH"] = ldLibraryPath
        env["PATH"] = "${ffmpegBin.parent}:${System.getenv("PATH") ?: ""}"
        env["TMPDIR"] = appContext.cacheDir.absolutePath

        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        val outputLog = StringBuilder()
        val readerThread = Thread {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        onLine?.invoke(line)
                        outputLog.appendLine(line)
                    }
                }
            } catch (_: Exception) {}
        }
        readerThread.start()

        val exitCode = try {
            kotlinx.coroutines.runInterruptible {
                process.waitFor()
            }
        } finally {
            if (process.isAlive) process.destroy()
        }
        readerThread.join(1000L)

        ProcessResult(exitCode, outputLog.toString(), exitCode == 0)
    }
}

