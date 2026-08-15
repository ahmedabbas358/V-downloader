package com.junkfood.seal.util

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import java.io.File
import java.util.Locale

object MusicRemovalEngine {

    private const val TAG = "MusicRemovalEngine"

    /**
     * Primary precision acoustic vocal isolation filter:
     * - stereotools: Emphasizes center mono voice while attenuating wide stereo backing music.
     * - highpass/lowpass: Filters out sub-bass below 90Hz and harsh cymbals above 7.2kHz.
     * - equalizer: Suppresses 250Hz rhythm mud while boosting 2.2kHz vocal formant frequencies.
     * - volume: Compensates gain for pristine, loud speech clarity.
     */
    private const val FILTER_TIER_1 =
        "stereotools=mlev=1.6:slev=0.1,highpass=f=90,lowpass=f=7200,equalizer=f=250:t=o:w=1:g=-6,equalizer=f=2200:t=o:w=1:g=5,volume=1.3"

    /**
     * Secondary fallback filter (Phase Inversion Center Channel Extractor):
     */
    private const val FILTER_TIER_2 =
        "pan=stereo|c0=c0-0.65*c1|c1=c1-0.65*c0,highpass=f=100,lowpass=f=7000,volume=1.4"

    /**
     * Tertiary fallback filter (Standard Voice Bandpass):
     */
    private const val FILTER_TIER_3 =
        "highpass=f=120,lowpass=f=6500,volume=1.5"

    private val VOCAL_FILTERS = listOf(FILTER_TIER_1, FILTER_TIER_2, FILTER_TIER_3)

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
            onProgress?.invoke(progressPercent, "جاري إزالة الموسيقى وعزل الصوت: ${file.name}...")

            val resultFile = processSingleFile(file, isAudioOnly)
            processedPaths.add(resultFile.absolutePath)
        }

        return processedPaths
    }

    /**
     * Processes a single audio or video file with cascading FFmpeg vocal isolation.
     */
    fun processSingleFile(inputFile: File, isAudioOnly: Boolean = false): File {
        return try {
            val ffmpegBin = getFFmpegExecutable()
            if (ffmpegBin == null) {
                Log.e(TAG, "FFmpeg binary not found. Skipping vocal isolation for: ${inputFile.name}")
                return inputFile
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
                    command.add("192k")
                    command.add("-af")
                    command.add(filter)
                } else {
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
                    command.add(filter)
                }

                command.add(tempOutputFile.absolutePath)

                Log.d(TAG, "Executing FFmpeg Vocal Filter Tier ${tierIndex + 1}: ${command.joinToString(" ")}")

                val success = runFFmpegProcess(ffmpegBin, command)

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
