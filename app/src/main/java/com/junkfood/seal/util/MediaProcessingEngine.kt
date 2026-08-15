package com.junkfood.seal.util

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaProcessingEngine {

    private const val TAG = "MediaProcessingEngine"

    const val SPEECH_CLARITY_FILTER =
        "highpass=f=100,lowpass=f=6000,equalizer=f=1000:width_type=h:width=500:g=3,equalizer=f=3000:width_type=h:width=1000:g=4,afftdn=nr=12:nf=-30:tn=1,dynaudnorm=f=120:g=11"

    /**
     * Fast lossless trimming using FFmpeg stream copy without re-encoding.
     * @param inputFile The source media file (video or audio)
     * @param startFormatted Start timestamp (e.g. "00:00:10" or "10")
     * @param endFormatted End timestamp (e.g. "00:01:30" or "90")
     * @param outputFile Destination file
     */
    suspend fun trimMediaLossless(
        inputFile: File,
        startFormatted: String,
        endFormatted: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val ffmpeg = MusicRemovalEngine.getFFmpegExecutable(context)
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
     * Applies the speech clarity and noise suppression filter to enhance lectures, podcasts, or dialogs.
     */
    suspend fun applySpeechClarityFilter(
        inputFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val ffmpeg = MusicRemovalEngine.getFFmpegExecutable(context)
                ?: throw IllegalStateException("FFmpeg binary not found")

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val isVideo = MusicRemovalEngine.isVideoFile(inputFile)
            val cmd = mutableListOf(
                ffmpeg.absolutePath,
                "-y",
                "-i", inputFile.absolutePath,
                "-af", SPEECH_CLARITY_FILTER
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

            Log.d(TAG, "Executing speech clarity filter: ${cmd.joinToString(" ")}")
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Speech clarity filtering completed successfully")
                outputFile
            } else {
                throw IllegalStateException("Speech clarity filtering failed (code $exitCode):\n$output")
            }
        }
    }

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
            val ffmpeg = MusicRemovalEngine.getFFmpegExecutable(context)
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
