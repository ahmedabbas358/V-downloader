package com.junkfood.seal.util

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import java.io.File
import java.util.Locale

object MusicRemovalEngine {

    private const val TAG = "MusicRemovalEngine"

    /**
     * Precision-engineered multi-stage acoustic vocal isolation filter:
     * 1. Center-Channel Vocal Extraction (stereotools): Emphasizes center mono voice (mlev=1.4) while substantially
     *    attenuating wide-panned stereo instruments and backing music (slev=0.1).
     * 2. Vocal Bandpass (highpass/lowpass): Eliminates sub-bass and heavy bass rumble below 85 Hz, and attenuates
     *    high-frequency cymbals, synths, and noise above 7500 Hz.
     * 3. Formant & Speech Clarity EQ: Cuts muddy 250 Hz rhythmic instrument resonance (-5 dB), boosts 2.2 kHz
     *    speech clarity/intelligibility (+4 dB), and tames harsh 6 kHz frequency spikes (-4 dB).
     * 4. Adaptive FFT Denoising (afftdn): Dynamically tracks and suppresses background instrumental noise.
     * 5. Dynamic Audio Normalization (dynaudnorm): Ensures consistent, crystal-clear voice volume across the track
     *    without clipping.
     */
    const val VOCAL_ISOLATION_FILTER =
        "stereotools=mlev=1.4:slev=0.1,highpass=f=85,lowpass=f=7500,equalizer=f=250:width_type=h:width=150:g=-5,equalizer=f=2200:width_type=h:width=1200:g=4,afftdn=nr=16:nf=-38:tn=1,dynaudnorm=f=150:g=15:peak=0.95"

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "avi", "flv", "m4v", "ts")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "opus", "ogg", "flac", "wav", "aac", "wma")

    fun isVideoFile(file: File): Boolean {
        val ext = file.extension.lowercase(Locale.ROOT)
        return VIDEO_EXTENSIONS.contains(ext)
    }

    fun isAudioFile(file: File): Boolean {
        val ext = file.extension.lowercase(Locale.ROOT)
        return AUDIO_EXTENSIONS.contains(ext)
    }

    /**
     * Finds the native FFmpeg executable extracted by youtubedl-android or the system.
     */
    fun getFFmpegExecutable(appContext: Context = context): File? {
        val candidates = listOf(
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/bin/ffmpeg"),
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/bin/ffmpeg"),
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

        // Deep search within packages directory if not found in standard paths
        val packagesDir = File(appContext.noBackupFilesDir, "youtubedl-android/packages")
        if (packagesDir.exists()) {
            val found = packagesDir.walkTopDown().maxDepth(4).firstOrNull {
                (it.name == "ffmpeg" || it.name == "libffmpeg.so") && it.isFile
            }
            if (found != null) {
                if (!found.canExecute()) {
                    found.setExecutable(true, false)
                }
                return found
            }
        }

        return null
    }

    /**
     * Processes a list of downloaded files, removing music / isolating vocals.
     * Returns the updated list of file paths.
     */
    fun processFiles(
        filePaths: List<String>,
        isAudioOnly: Boolean = false,
        onProgress: ((Float, String) -> Unit)? = null
    ): List<String> {
        val processedPaths = mutableListOf<String>()
        val total = filePaths.size.coerceAtLeast(1)

        for ((index, path) in filePaths.withIndex()) {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                processedPaths.add(path)
                continue
            }

            val progressPercent = ((index.toFloat() / total) * 100f)
            onProgress?.invoke(progressPercent, "جاري إزالة الموسيقى: ${file.name}...")

            val resultFile = processSingleFile(file, isAudioOnly)
            processedPaths.add(resultFile.absolutePath)
        }

        return processedPaths
    }

    /**
     * Processes a single audio or video file with FFmpeg vocal isolation.
     */
    fun processSingleFile(inputFile: File, isAudioOnly: Boolean = false): File {
        return try {
            val ffmpegBin = getFFmpegExecutable()
            if (ffmpegBin == null) {
                Log.e(TAG, "FFmpeg binary not found. Skipping vocal isolation for: ${inputFile.name}")
                return inputFile
            }

            val isVideo = !isAudioOnly && isVideoFile(inputFile)
            val tempOutputFile = File(
                inputFile.parentFile ?: context.cacheDir,
                "vocal_isolated_${System.currentTimeMillis()}_${inputFile.name}"
            )

            val command = mutableListOf<String>()
            command.add(ffmpegBin.absolutePath)
            command.add("-y")
            command.add("-i")
            command.add(inputFile.absolutePath)

            if (isVideo) {
                // For video: Copy video stream without re-encoding, filter audio stream
                command.add("-c:v")
                command.add("copy")
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("192k")
                command.add("-af")
                command.add(VOCAL_ISOLATION_FILTER)
            } else {
                // For audio: Apply vocal isolation filter and encode based on format
                val ext = inputFile.extension.lowercase(Locale.ROOT)
                when (ext) {
                    "mp3" -> {
                        command.add("-c:a")
                        command.add("libmp3lame")
                        command.add("-b:a")
                        command.add("192k")
                    }
                    "m4a", "aac" -> {
                        command.add("-c:a")
                        command.add("aac")
                        command.add("-b:a")
                        command.add("192k")
                    }
                    "opus", "ogg" -> {
                        command.add("-c:a")
                        command.add("libopus")
                        command.add("-b:a")
                        command.add("128k")
                    }
                    else -> {
                        command.add("-b:a")
                        command.add("192k")
                    }
                }
                command.add("-af")
                command.add(VOCAL_ISOLATION_FILTER)
            }

            command.add(tempOutputFile.absolutePath)

            Log.d(TAG, "Executing MusicRemovalEngine FFmpeg command: ${command.joinToString(" ")}")

            val success = runFFmpegProcess(ffmpegBin, command)

            if (success && tempOutputFile.exists() && tempOutputFile.length() > 0L) {
                Log.d(TAG, "Vocal isolation succeeded for ${inputFile.name}. Swapping files...")
                val targetPath = inputFile.absolutePath
                val backupFile = File(inputFile.parentFile, "backup_${System.currentTimeMillis()}_${inputFile.name}")
                
                val renamed = inputFile.renameTo(backupFile)
                if (renamed) {
                    val replaced = tempOutputFile.renameTo(File(targetPath))
                    if (replaced) {
                        backupFile.delete()
                        File(targetPath)
                    } else {
                        // Fallback copy
                        try {
                            tempOutputFile.copyTo(File(targetPath), overwrite = true)
                            tempOutputFile.delete()
                            backupFile.delete()
                            File(targetPath)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to copy temp file to target", e)
                            backupFile.renameTo(File(targetPath))
                            inputFile
                        }
                    }
                } else {
                    // If rename failed, try direct copy
                    try {
                        tempOutputFile.copyTo(File(targetPath), overwrite = true)
                        tempOutputFile.delete()
                        File(targetPath)
                    } catch (e: Exception) {
                        Log.e(TAG, "Direct copy fallback failed", e)
                        inputFile
                    }
                }
            } else {
                Log.w(TAG, "FFmpeg vocal isolation failed for ${inputFile.name}. Keeping original file.")
                if (tempOutputFile.exists()) {
                    tempOutputFile.delete()
                }
                inputFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in processSingleFile for ${inputFile.name}", e)
            inputFile
        }
    }

    private fun runFFmpegProcess(ffmpegBin: File, command: List<String>): Boolean {
        return try {
            val processBuilder = ProcessBuilder(command)
            val env = processBuilder.environment()

            val ffmpegDir = ffmpegBin.parentFile?.parentFile ?: File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
            val usrLib = File(ffmpegDir, "usr/lib").absolutePath
            val lib = File(ffmpegDir, "lib").absolutePath
            val nativeLib = context.applicationInfo.nativeLibraryDir

            env["LD_LIBRARY_PATH"] = "$usrLib:$lib:$nativeLib"
            env["PATH"] = "${ffmpegBin.parent}:${System.getenv("PATH") ?: ""}"
            env["TMPDIR"] = context.cacheDir.absolutePath

            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            // Read output stream in background to prevent process blocking
            val readerThread = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            Log.v(TAG, "[FFmpeg] $line")
                        }
                    }
                } catch (_: Exception) {}
            }
            readerThread.start()

            val exitCode = process.waitFor()
            readerThread.join(1000L)
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error executing FFmpeg process", e)
            false
        }
    }
}
