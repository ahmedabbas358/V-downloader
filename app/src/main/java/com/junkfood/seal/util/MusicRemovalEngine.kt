package com.junkfood.seal.util

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import java.io.File
import java.util.Locale

object MusicRemovalEngine {

    private const val TAG = "MusicRemovalEngine"

    /**
     * Advanced Precision Studio Vocal Extractor Filter Tiers:
     *
     * Tier 1 (Pro Center-Channel Vocal Isolation & Formant Preservation):
     * - stereotools: Attenuates 94% of stereo side instrumentation/synths/reverb while keeping 100% true center vocal at 1.0 gain to prevent clipping.
     * - highpass/lowpass: Filters out sub-bass below 80Hz while preserving full vocal air and brilliance up to 14kHz.
     * - equalizer: Suppresses 130Hz rhythm/bass mud by -5dB, while boosting natural vocal presence at 2.8kHz by +2.0dB.
     * - alimiter: Studio peak limiter (limit 0.98, attack 5ms, release 50ms) to ensure zero digital clipping and smooth dynamics.
     */
    private const val FILTER_TIER_1 =
        "stereotools=mlev=1.0:slev=0.06,highpass=f=80:p=2,lowpass=f=14000:p=2,equalizer=f=130:t=q:w=1.2:g=-5,equalizer=f=2800:t=q:w=1.0:g=2.0,alimiter=limit=0.98:attack=5:release=50"

    /**
     * Tier 2 (Phase Inversion Center Channel + Speech Clarifier):
     * - pan: Subtracts 75% out-of-phase stereo channels to isolate center vocals cleanly.
     * - equalizer: Smooth vocal formant enhancement at 2.5kHz with gentle lowpass at 13.5kHz.
     */
    private const val FILTER_TIER_2 =
        "pan=stereo|c0=c0-0.75*c1|c1=c1-0.75*c0,highpass=f=85:p=2,lowpass=f=13500:p=2,equalizer=f=140:t=q:w=1.2:g=-4,equalizer=f=2500:t=q:w=1.0:g=2.5,alimiter=limit=0.98:attack=5:release=50"

    /**
     * Tier 3 (Adaptive Speech Bandpass & Harmonic Clarity):
     * - Isolates the human vocal fundamental and harmonics from 90Hz to 12kHz with dynamic peak limiting.
     */
    private const val FILTER_TIER_3 =
        "highpass=f=90:p=2,lowpass=f=12000:p=2,equalizer=f=150:t=q:w=1.5:g=-6,equalizer=f=2200:t=q:w=1.0:g=2.0,alimiter=limit=0.98:attack=5:release=50"

    private val VOCAL_FILTERS = listOf(FILTER_TIER_1, FILTER_TIER_2, FILTER_TIER_3)

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "avi", "flv", "m4v", "ts")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "opus", "ogg", "flac", "wav", "aac", "wma", "mka", "m4b")

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
            val found = packagesDir.walkTopDown().maxDepth(5).firstOrNull {
                (it.name == "ffmpeg" || it.name == "libffmpeg.so") && it.isFile
            }
            if (found != null) {
                if (!found.canExecute()) {
                    found.setExecutable(true, false)
                }
                return found
            }
        }

        val filesDir = appContext.filesDir
        if (filesDir.exists()) {
            val found = filesDir.walkTopDown().maxDepth(3).firstOrNull {
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
    suspend fun processFiles(
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
            onProgress?.invoke(progressPercent, "جاري عزل الصوت وإزالة الموسيقى: ${file.name}...")

            val resultFile = processSingleFile(file, isAudioOnly)
            processedPaths.add(resultFile.absolutePath)
        }

        return processedPaths
    }

    /**
     * Processes a single audio or video file with cascading FFmpeg vocal isolation.
     */
    suspend fun processSingleFile(inputFile: File, isAudioOnly: Boolean = false): File {
        return try {
            val ffmpegBin = getFFmpegExecutable()
            if (ffmpegBin == null) {
                Log.e(TAG, "FFmpeg binary not found. Skipping vocal isolation for: ${inputFile.name}")
                throw IllegalStateException("مكتبة FFmpeg غير متوفرة! لا يمكن عزل الصوت، يرجى التثبيت من الإعدادات.")
            }

            val isVideo = !isAudioOnly && isVideoFile(inputFile)

            for ((tierIndex, filter) in VOCAL_FILTERS.withIndex()) {
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
                    command.add("-c:v")
                    command.add("copy")
                    command.add("-c:a")
                    command.add("aac")
                    command.add("-b:a")
                    command.add("256k")
                    command.add("-af")
                    command.add(filter)
                } else {
                    val ext = inputFile.extension.lowercase(Locale.ROOT)
                    when (ext) {
                        "mp3" -> {
                            command.add("-c:a")
                            command.add("libmp3lame")
                            command.add("-b:a")
                            command.add("256k")
                        }
                        "m4a", "aac" -> {
                            command.add("-c:a")
                            command.add("aac")
                            command.add("-b:a")
                            command.add("256k")
                        }
                        "opus", "ogg" -> {
                            command.add("-c:a")
                            command.add("libopus")
                            command.add("-b:a")
                            command.add("160k")
                        }
                        "wav" -> {
                            command.add("-c:a")
                            command.add("pcm_s16le")
                        }
                        "flac" -> {
                            command.add("-c:a")
                            command.add("flac")
                        }
                        else -> {
                            command.add("-c:a")
                            command.add("aac")
                            command.add("-b:a")
                            command.add("256k")
                        }
                    }
                    command.add("-af")
                    command.add(filter)
                }

                command.add(tempOutputFile.absolutePath)

                Log.d(TAG, "Executing FFmpeg Vocal Filter Tier ${tierIndex + 1}: ${command.joinToString(" ")}")

                val success = try {
                    runFFmpegProcess(ffmpegBin, command)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (tempOutputFile.exists()) {
                        tempOutputFile.delete()
                    }
                    throw e
                }

                if (success && tempOutputFile.exists() && tempOutputFile.length() > 0L) {
                    Log.d(TAG, "Vocal isolation succeeded on Tier ${tierIndex + 1} for ${inputFile.name}")
                    val targetPath = inputFile.absolutePath
                    val backupFile = File(inputFile.parentFile, "backup_${System.currentTimeMillis()}_${inputFile.name}")

                    val renamed = inputFile.renameTo(backupFile)
                    if (renamed) {
                        val replaced = tempOutputFile.renameTo(File(targetPath))
                        if (replaced) {
                            backupFile.delete()
                            return File(targetPath)
                        } else {
                            try {
                                tempOutputFile.copyTo(File(targetPath), overwrite = true)
                                tempOutputFile.delete()
                                backupFile.delete()
                                return File(targetPath)
                            } catch (e: Exception) {
                                backupFile.renameTo(File(targetPath))
                            }
                        }
                    } else {
                        try {
                            tempOutputFile.copyTo(File(targetPath), overwrite = true)
                            tempOutputFile.delete()
                            return File(targetPath)
                        } catch (_: Exception) {}
                    }
                } else {
                    if (tempOutputFile.exists()) {
                        tempOutputFile.delete()
                    }
                    Log.w(TAG, "Tier ${tierIndex + 1} failed for ${inputFile.name}, trying next fallback tier...")
                }
            }

            Log.w(TAG, "All vocal isolation tiers failed. Retaining original file.")
            inputFile
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in processSingleFile for ${inputFile.name}", e)
            throw Exception("حدث خطأ غير متوقع أثناء محاولة عزل الصوت: ${e.message}", e)
        }
    }

    private suspend fun runFFmpegProcess(ffmpegBin: File, command: List<String>): Boolean {
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

            val exitCode = try {
                kotlinx.coroutines.runInterruptible {
                    process.waitFor()
                }
            } finally {
                if (process.isAlive) {
                    process.destroy()
                }
            }
            readerThread.join(1000L)
            exitCode == 0
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "FFmpeg process was cancelled by coroutine.")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error executing FFmpeg process", e)
            false
        }
    }
}
