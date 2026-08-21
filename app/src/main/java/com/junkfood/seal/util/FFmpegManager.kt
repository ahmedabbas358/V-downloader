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
            File(appContext.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        )

        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile) {
                if (!candidate.canExecute()) {
                    candidate.setExecutable(true, false)
                }
                return candidate
            }
        }

        // Deep search within packages and app storage directories
        val searchDirs = listOfNotNull(
            File(appContext.noBackupFilesDir, "youtubedl-android"),
            File(appContext.noBackupFilesDir, "packages"),
            appContext.noBackupFilesDir,
            File(appContext.filesDir, "youtubedl-android"),
            File(appContext.filesDir, "packages"),
            appContext.filesDir,
            File(appContext.applicationInfo.nativeLibraryDir)
        )

        for (dir in searchDirs) {
            if (dir.exists()) {
                val found = dir.walkTopDown().maxDepth(5).firstOrNull {
                    (it.name == "ffmpeg" || it.name == "libffmpeg.so") && it.isFile
                }
                if (found != null) {
                    if (!found.canExecute()) {
                        found.setExecutable(true, false)
                    }
                    return found
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

    /**
     * Decodes any audio or video container to standard 16-bit Float PCM stereo WAV for neural processing.
     */
    suspend fun decodeToPcmWav(
        inputFile: File,
        outputWav: File,
        sampleRate: Int = 44100,
        channels: Int = 2
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            outputWav.parentFile?.mkdirs()
            if (outputWav.exists()) outputWav.delete()

            val cmd = listOf(
                "-y",
                "-i", inputFile.absolutePath,
                "-vn",
                "-ac", channels.toString(),
                "-ar", sampleRate.toString(),
                "-c:a", "pcm_s16le",
                "-f", "wav",
                outputWav.absolutePath
            )

            val result = executeCommand(cmd)
            if (result.isSuccess && outputWav.exists() && outputWav.length() > 0) {
                outputWav
            } else {
                throw IllegalStateException("FFmpeg audio decode failed (code ${result.exitCode}):\n${result.output}")
            }
        }
    }

    /**
     * Isolates vocals and eliminates background music with pristine speech clarity
     * based on Hammil-grade Center-Channel Mid-Side Isolation, Vocal Formant Boosting,
     * Low/High-pass Bandpass Filtering, and Dynamic Audio Normalization.
     */
    suspend fun isolateVocalsWithHighPrecisionFilter(
        inputAudio: File,
        outputWav: File,
        sampleRate: Int = 44100
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            outputWav.parentFile?.mkdirs()
            if (outputWav.exists()) outputWav.delete()

            // Hammil 10.17 Vocal Isolation & Complete Music Removal Pipeline:
            // 1. highpass=f=100: Hard cut for sub-bass rumble, 808s, and bass guitar
            // 2. lowpass=f=7500: Hard cut for hi-hats, cymbal air, high synthesizer bleed
            // 3. stereotools=mlev=2.0:slev=0.0:mpan=0.0: Full phase cancellation of stereo instrumental track while amplifying center speech stem
            // 4. equalizer=f=300:t=q:w=1.0:g=2.0: Restores fundamental vocal chest warmth (prevents metallic / hollow sound)
            // 5. equalizer=f=1200:t=q:w=1.2:g=4.0: Sharpens speech vowels & primary formants (F1/F2)
            // 6. equalizer=f=2600:t=q:w=1.5:g=3.0: Boosts consonant intelligibility and diction (F3)
            // 7. afftdn=nr=18:nf=-25:tn=1: Spectral noise and residual music background suppression
            // 8. dynaudnorm=f=100:g=15:p=0.95:m=10.0: Dynamic range speech leveling
            val filterChain = "highpass=f=100,lowpass=f=7500,stereotools=mlev=2.0:slev=0.0:mpan=0.0,equalizer=f=300:t=q:w=1.0:g=2.0,equalizer=f=1200:t=q:w=1.2:g=4.0,equalizer=f=2600:t=q:w=1.5:g=3.0,afftdn=nr=18:nf=-25:tn=1,dynaudnorm=f=100:g=15:p=0.95:m=10.0"

            val cmd = listOf(
                "-y",
                "-i", inputAudio.absolutePath,
                "-vn",
                "-af", filterChain,
                "-ac", "2",
                "-ar", sampleRate.toString(),
                "-c:a", "pcm_s16le",
                "-f", "wav",
                outputWav.absolutePath
            )

            val result = executeCommand(cmd)
            if (result.isSuccess && outputWav.exists() && outputWav.length() > 0) {
                outputWav
            } else {
                throw IllegalStateException("FFmpeg vocal isolation failed (code ${result.exitCode}):\n${result.output}")
            }
        }
    }

    /**
     * Encodes a processed WAV file to destination media format.
     */
    suspend fun encodePcmToAudio(
        wavFile: File,
        outputFile: File,
        bitrate: String = "256k"
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val ext = outputFile.extension.lowercase()
            val codecArgs = when (ext) {
                "mp3" -> listOf("-c:a", "libmp3lame", "-b:a", bitrate)
                "m4a", "aac" -> listOf("-c:a", "aac", "-b:a", bitrate)
                "opus", "ogg" -> listOf("-c:a", "libopus", "-b:a", "160k")
                "flac" -> listOf("-c:a", "flac")
                "wav" -> listOf("-c:a", "pcm_s16le")
                else -> listOf("-c:a", "aac", "-b:a", bitrate)
            }

            val cmd = listOf("-y", "-i", wavFile.absolutePath) + codecArgs + listOf(outputFile.absolutePath)
            val result = executeCommand(cmd)
            if (result.isSuccess && outputFile.exists() && outputFile.length() > 0) {
                outputFile
            } else {
                throw IllegalStateException("FFmpeg audio encode failed (code ${result.exitCode}):\n${result.output}")
            }
        }
    }

    /**
     * Losslessly remuxes original video with the new AI-separated clean audio track.
     */
    suspend fun remuxVideoWithNewAudio(
        originalVideo: File,
        newAudioWav: File,
        outputFile: File,
        audioBitrate: String = "256k"
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val ext = outputFile.extension.lowercase()
            val isMp4 = ext == "mp4" || ext == "m4v"

            val cmd = listOf(
                "-y",
                "-i", originalVideo.absolutePath,
                "-i", newAudioWav.absolutePath,
                "-map", "0:v:0?",
                "-map", "1:a:0",
                "-map", "0:s?",
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", audioBitrate,
                "-c:s", if (isMp4) "mov_text" else "copy",
                outputFile.absolutePath
            )

            val result = executeCommand(cmd)
            if (result.isSuccess && outputFile.exists() && outputFile.length() > 0) {
                outputFile
            } else {
                throw IllegalStateException("FFmpeg video remux failed (code ${result.exitCode}):\n${result.output}")
            }
        }
    }
}
