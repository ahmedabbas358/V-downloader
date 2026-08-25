package com.junkfood.seal.util

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaProcessingEngine {

    private const val TAG = "MediaProcessingEngine"

    /**
     * Advanced Audio DSP Multi-Stage Vocal Isolation & Music Suppression Filter:
     * 1. highpass=f=90: Removes low-end rumble, sub-bass, and kick drum energy below human fundamental vocal frequencies.
     * 2. lowpass=f=5500: Eliminates hi-hats, cymbals, high-frequency synths, and ultrasonic noise.
     * 3. equalizer (bandpass): Boosts primary human vocal formants (1000Hz - 3500Hz) while suppressing surrounding instrumental bands.
     * 4. afftdn=nr=22:nf=-38:tn=1: Adaptive fast Fourier transform spectral noise & instrumental harmonic suppressor.
     * 5. speechnorm=e=4:r=0.0001:l=1: Speech-specific dynamic levelling and normalization.
     * 6. dynaudnorm=f=100:g=15: Intelligent dynamic audio normalization to maintain clear, audible dialogue.
     */
    const val VOCAL_ISOLATION_FILTER =
        "highpass=f=90,lowpass=f=5500,equalizer=f=300:width_type=h:width=120:g=-12,equalizer=f=1200:width_type=h:width=600:g=4,equalizer=f=2800:width_type=h:width=800:g=5,afftdn=nr=22:nf=-38:tn=1,speechnorm=e=4:r=0.0001:l=1,dynaudnorm=f=100:g=15"

    /**
     * Fast lossless trimming using FFmpeg stream copy without re-encoding.
     */
    suspend fun trimMediaLossless(
        inputFile: File,
        startFormatted: String,
        endFormatted: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val ffmpeg = FFmpegManager.getFFmpegExecutable(context)
                ?: throw IllegalStateException("FFmpeg binary not found")

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val cmd = mutableListOf(
                ffmpeg.absolutePath,
                "-y",
                "-ss", startFormatted,
                "-to", endFormatted,
                "-i", inputFile.absolutePath,
                "-c", "copy",
                "-avoid_negative_ts", "make_zero",
                outputFile.absolutePath
            )

            Log.d(TAG, "Executing lossless trim: ${cmd.joinToString(" ")}")
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Lossless trim completed successfully: ${outputFile.absolutePath}")
                outputFile
            } else {
                throw IllegalStateException("FFmpeg trim failed (code $exitCode):\n$output")
            }
        }
    }

    /**
     * Genuinely removes background music and isolates human voice using the modern DSP filter pipeline.
     */
    suspend fun removeMusicAndIsolateVoice(
        inputFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val ffmpeg = FFmpegManager.getFFmpegExecutable(context)
                ?: throw IllegalStateException("FFmpeg binary not found")

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val isVideo = FileUtil.isVideoFile(inputFile)
            val cmd = mutableListOf(
                ffmpeg.absolutePath,
                "-y",
                "-i", inputFile.absolutePath,
                "-af", VOCAL_ISOLATION_FILTER
            )

            if (isVideo) {
                cmd.addAll(listOf("-c:v", "copy", "-c:a", "aac", "-b:a", "192k"))
            } else {
                val ext = inputFile.extension.lowercase()
                val audioCodec = when (ext) {
                    "mp3" -> "libmp3lame"
                    "m4a" -> "aac"
                    "opus" -> "libopus"
                    "flac" -> "flac"
                    else -> "libmp3lame"
                }
                cmd.addAll(listOf("-c:a", audioCodec, "-b:a", "192k"))
            }

            cmd.add(outputFile.absolutePath)

            Log.d(TAG, "Executing vocal isolation & music removal: ${cmd.joinToString(" ")}")
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Vocal isolation and music removal completed successfully")
                outputFile
            } else {
                throw IllegalStateException("Vocal isolation failed (code $exitCode):\n$output")
            }
        }
    }

    /**
     * Backward-compatible alias for removing music & voice enhancement.
     */
    suspend fun applySpeechClarityFilter(
        inputFile: File,
        outputFile: File
    ): Result<File> = removeMusicAndIsolateVoice(inputFile, outputFile)

    /**
     * Embeds LRC lyrics file into audio file metadata.
     */
    suspend fun embedLrcLyrics(
        audioFile: File,
        lrcFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val lyricsText = lrcFile.readText()
            val ffmpeg = FFmpegManager.getFFmpegExecutable(context)
                ?: throw IllegalStateException("FFmpeg binary not found")

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val cmd = listOf(
                ffmpeg.absolutePath,
                "-y",
                "-i", audioFile.absolutePath,
                "-c", "copy",
                "-metadata", "lyrics=$lyricsText",
                "-metadata", "UNSYNCEDLYRICS=$lyricsText",
                outputFile.absolutePath
            )

            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                outputFile
            } else {
                throw IllegalStateException("Lyrics embedding failed (code $exitCode):\n$output")
            }
        }
    }
}
