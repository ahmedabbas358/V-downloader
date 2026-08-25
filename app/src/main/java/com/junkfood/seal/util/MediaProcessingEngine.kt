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
