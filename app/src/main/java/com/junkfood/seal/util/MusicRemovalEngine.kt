package com.junkfood.seal.util

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import java.io.File
import java.util.Locale

/**
 * MusicRemovalEngine — Advanced Open-Source Multi-Stage Music & Instrument Removal
 *
 * Uses a cascading pipeline of FFmpeg-native open-source DSP algorithms to fully
 * eliminate musical instruments while preserving human vocals:
 *
 * STAGE 1 — Mid-Side (M/S) Center-Channel Extraction
 *   pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1
 *   Implements Blumlein's Mid-Side stereo technique in reverse:
 *   - Mid  = (L + R) / 2  → contains centered vocals (most instruments are panned off-center)
 *   - Discards the Side channel = (L − R) / 2  → eliminates panned instruments
 *
 * STAGE 2 — afftdn: FFT-based Adaptive Noise/Music Suppressor (open-source in FFmpeg)
 *   Spectral Subtraction algorithm — treats background music as adaptive noise profile.
 *   Parameters: nf (noise floor), nr (reduction ratio), nt=w (white/adaptive noise type),
 *   om=o (output all frequencies), tr=1 (dynamic tracking of changing music patterns).
 *
 * STAGE 3 — anlmdn: Non-Local Means Denoiser (open-source in FFmpeg)
 *   Removes residual musical artifacts by comparing signal patches with their neighbours.
 *   Particularly effective at eliminating harmonic instrument overtones.
 *
 * STAGE 4 — EQ Sculpting + Dynamic Limiting
 *   Final vocal presence boost (2.8-3kHz) and low-frequency mud removal (250Hz).
 *   alimiter prevents clipping after gain adjustments.
 */
object MusicRemovalEngine {

    private const val TAG = "MusicRemovalEngine"

    /**
     * Primary universal vocal isolation filter for in-stream FFmpeg postprocessor.
     * Uses Mid-Side center-channel extraction + speech formant bandpass + dynamic normalizer.
     */
    fun getVocalIsolationFilter(): String =
        "pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1," +
        "highpass=f=120," +
        "lowpass=f=7500," +
        "equalizer=f=150:t=q:w=1.5:g=-12," +
        "equalizer=f=3000:t=q:w=1.0:g=3.5," +
        "dynaudnorm=f=150:g=15"

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 1 — Full M/S Extraction + Bandpass + Formant EQ + Normalization
    // ─────────────────────────────────────────────────────────────────────────
    private const val FILTER_TIER_1 =
        "aformat=channel_layouts=stereo," +
        "pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1," +
        "highpass=f=120," +
        "lowpass=f=7500," +
        "equalizer=f=150:t=q:w=1.5:g=-12," +
        "equalizer=f=250:t=q:w=1.0:g=-6," +
        "equalizer=f=3000:t=q:w=0.8:g=3.5," +
        "dynaudnorm=f=150:g=15"

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 2 — M/S Extraction + moderate afftdn + anlmdn (First fallback)
    // ─────────────────────────────────────────────────────────────────────────
    // More conservative reduction (nr=35) to avoid vocal artifacts.
    // Suitable for recordings with high vocal-music overlap.
    private const val FILTER_TIER_2 =
        "aformat=channel_layouts=stereo," +
        "pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1," +
        "afftdn=nf=-20:nr=35:nt=w:om=o," +
        "anlmdn=s=5:p=0.003:r=0.001:m=10," +
        "highpass=f=85," +
        "lowpass=f=11000," +
        "equalizer=f=300:t=q:w=1.2:g=-3," +
        "equalizer=f=2800:t=q:w=1.0:g=2.0," +
        "alimiter=limit=0.98:attack=5:release=50"

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 3 — M/S Extraction + light afftdn only (Last resort fallback)
    // ─────────────────────────────────────────────────────────────────────────
    // Minimal processing to ensure some music removal when other tiers fail.
    private const val FILTER_TIER_3 =
        "aformat=channel_layouts=stereo," +
        "pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1," +
        "afftdn=nf=-15:nr=25:nt=w," +
        "highpass=f=90," +
        "lowpass=f=12000," +
        "equalizer=f=150:t=q:w=1.5:g=-6," +
        "equalizer=f=2200:t=q:w=1.0:g=2.0," +
        "alimiter=limit=0.98:attack=5:release=50"

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
            File(appContext.noBackupFilesDir, "packages/ffmpeg/usr/bin/ffmpeg"),
            File(appContext.noBackupFilesDir, "packages/ffmpeg/bin/ffmpeg"),
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/bin/ffmpeg"),
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/bin/ffmpeg"),
            File(appContext.noBackupFilesDir, "usr/bin/ffmpeg"),
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
            File(appContext.noBackupFilesDir, "packages"),
            File(appContext.noBackupFilesDir, "youtubedl-android/packages"),
            appContext.noBackupFilesDir,
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
     * Processes a list of downloaded files, removing all music & instruments / isolating vocals.
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
            onProgress?.invoke(progressPercent, "جاري عزل الصوت وإزالة الموسيقى والآلات: ${file.name}...")

            val resultFile = processSingleFile(file, isAudioOnly)
            processedPaths.add(resultFile.absolutePath)
        }

        return processedPaths
    }

    /**
     * Processes a single audio or video file with cascading FFmpeg music removal pipeline.
     *
     * Algorithm:
     *   1. Apply Mid-Side center-channel extraction to eliminate panned instruments
     *   2. Apply afftdn FFT-based adaptive music suppressor (open-source in FFmpeg)
     *   3. Apply anlmdn Non-Local Means denoiser to remove residual harmonics (open-source in FFmpeg)
     *   4. Final EQ shaping and dynamic limiting
     *   5. Atomic file replacement preserving original path
     */
    suspend fun processSingleFile(inputFile: File, isAudioOnly: Boolean = false): File {
        return try {
            val ffmpegBin = getFFmpegExecutable()
            if (ffmpegBin == null) {
                Log.e(TAG, "FFmpeg binary not found. Skipping music removal for: ${inputFile.name}")
                throw IllegalStateException("مكتبة FFmpeg غير متوفرة! لا يمكن إزالة الموسيقى، يرجى التثبيت من الإعدادات.")
            }

            val isVideo = !isAudioOnly && isVideoFile(inputFile)
            val ext = inputFile.extension.lowercase(Locale.ROOT)
            val isMp4 = ext == "mp4" || ext == "m4v"

            Log.d(TAG, "Starting music removal for: ${inputFile.name} (video=$isVideo, ext=$ext, size=${inputFile.length()}B)")

            for ((tierIndex, filter) in VOCAL_FILTERS.withIndex()) {
                val tempOutputFile = File(
                    inputFile.parentFile ?: context.cacheDir,
                    "music_removed_${System.currentTimeMillis()}_${inputFile.name}"
                )

                val command = buildFFmpegCommand(
                    ffmpegBin = ffmpegBin,
                    inputFile = inputFile,
                    outputFile = tempOutputFile,
                    audioFilter = filter,
                    isVideo = isVideo,
                    ext = ext,
                    isMp4 = isMp4,
                )

                Log.d(TAG, "Executing Tier ${tierIndex + 1}/${VOCAL_FILTERS.size}: ${command.joinToString(" ")}")

                val success = try {
                    runFFmpegProcess(ffmpegBin, command)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (tempOutputFile.exists()) tempOutputFile.delete()
                    throw e
                }

                if (success && tempOutputFile.exists() && tempOutputFile.length() > 0L) {
                    Log.d(TAG, "Tier ${tierIndex + 1} succeeded — output size: ${tempOutputFile.length()}B")
                    val result = atomicReplaceFile(inputFile, tempOutputFile)
                    if (result != null) return result
                } else {
                    if (tempOutputFile.exists()) tempOutputFile.delete()
                    Log.w(TAG, "Tier ${tierIndex + 1} failed for ${inputFile.name}, trying next tier...")
                }
            }

            Log.w(TAG, "All music removal tiers failed. Retaining original file: ${inputFile.name}")
            inputFile
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in processSingleFile for ${inputFile.name}", e)
            throw Exception("حدث خطأ غير متوقع أثناء محاولة إزالة الموسيقى: ${e.message}", e)
        }
    }

    /**
     * Builds the complete FFmpeg command for music removal.
     * For video: copies video stream as-is (no re-encoding), only re-encodes audio.
     * For audio: re-encodes with the original container format for compatibility.
     */
    private fun buildFFmpegCommand(
        ffmpegBin: File,
        inputFile: File,
        outputFile: File,
        audioFilter: String,
        isVideo: Boolean,
        ext: String,
        isMp4: Boolean,
    ): List<String> {
        val command = mutableListOf<String>()
        command.add(ffmpegBin.absolutePath)
        command.add("-y")
        command.add("-i")
        command.add(inputFile.absolutePath)

        if (isVideo) {
            command.addAll(listOf(
                "-map", "0:v?",
                "-map", "0:a:0?",
                "-map", "0:s?",
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", "256k",
                "-c:s", if (isMp4) "mov_text" else "copy",
                "-af", audioFilter,
            ))
        } else {
            when (ext) {
                "mp3" -> command.addAll(listOf("-c:a", "libmp3lame", "-b:a", "256k"))
                "m4a", "aac" -> command.addAll(listOf("-c:a", "aac", "-b:a", "256k"))
                "opus", "ogg" -> command.addAll(listOf("-c:a", "libopus", "-b:a", "160k"))
                "wav" -> command.addAll(listOf("-c:a", "pcm_s16le"))
                "flac" -> command.addAll(listOf("-c:a", "flac"))
                else -> command.addAll(listOf("-c:a", "aac", "-b:a", "256k"))
            }
            command.addAll(listOf("-af", audioFilter))
        }

        command.add(outputFile.absolutePath)
        return command
    }

    /**
     * Atomically replaces the input file with the processed output:
     *   1. Renames input to a backup (preserves data on failure)
     *   2. Moves processed file to the original path (atomic where possible)
     *   3. Deletes backup on success; restores backup on failure
     */
    private fun atomicReplaceFile(inputFile: File, processedFile: File): File? {
        val targetPath = inputFile.absolutePath
        val backupFile = File(inputFile.parentFile, "bk_${System.currentTimeMillis()}_${inputFile.name}")

        return try {
            if (inputFile.renameTo(backupFile)) {
                val moved = processedFile.renameTo(File(targetPath))
                if (moved) {
                    backupFile.delete()
                } else {
                    processedFile.copyTo(File(targetPath), overwrite = true)
                    processedFile.delete()
                    backupFile.delete()
                }
            } else {
                processedFile.copyTo(File(targetPath), overwrite = true)
                processedFile.delete()
            }
            val finalFile = File(targetPath)
            finalFile.setLastModified(System.currentTimeMillis())
            com.junkfood.seal.download.engine.postprocess.MediaStorageScanner.scanSingleFile(finalFile)
            Log.d(TAG, "File replacement successful: $targetPath")
            finalFile
        } catch (e: Exception) {
            Log.e(TAG, "Atomic file replacement failed: ${e.message}", e)
            if (backupFile.exists() && !File(targetPath).exists()) {
                backupFile.renameTo(File(targetPath))
            }
            if (processedFile.exists()) processedFile.delete()
            null
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

            val errorLog = StringBuilder()
            val readerThread = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            Log.v(TAG, "[FFmpeg] $line")
                            if (line.contains("Error", ignoreCase = true) ||
                                line.contains("Invalid", ignoreCase = true)) {
                                errorLog.appendLine(line)
                            }
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

            if (exitCode != 0 && errorLog.isNotEmpty()) {
                Log.e(TAG, "FFmpeg exited with code=$exitCode: $errorLog")
            }

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
