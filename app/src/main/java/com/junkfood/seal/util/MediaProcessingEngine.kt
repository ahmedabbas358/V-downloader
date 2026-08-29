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

    /**
     * Embeds subtitle file(s) into video container using FFmpeg stream copy.
     */
    suspend fun embedSubtitlesIntoVideo(
        videoFile: File,
        subtitleFiles: List<File>,
        outputFile: File,
        isMkv: Boolean = videoFile.extension.equals("mkv", ignoreCase = true)
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (subtitleFiles.isEmpty() || !videoFile.exists()) return@runCatching videoFile

            val ffmpeg = FFmpegManager.getFFmpegExecutable(context)
                ?: throw IllegalStateException("FFmpeg binary not found")

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val cmd = mutableListOf(
                ffmpeg.absolutePath,
                "-y",
                "-i", videoFile.absolutePath,
            )

            // Add input for each subtitle file
            subtitleFiles.forEach { subFile ->
                cmd.add("-i")
                cmd.add(subFile.absolutePath)
            }

            // Stream copy video and audio
            cmd.add("-map")
            cmd.add("0:v")
            cmd.add("-map")
            cmd.add("0:a?")

            // Map all subtitles
            subtitleFiles.forEachIndexed { index, _ ->
                cmd.add("-map")
                cmd.add("${index + 1}:s")
            }

            cmd.add("-c")
            cmd.add("copy")

            if (!isMkv) {
                // For MP4 container, set subtitle codec to mov_text
                cmd.add("-c:s")
                cmd.add("mov_text")
            }

            // Add metadata for language if extracted from filename (e.g. title.ar.srt -> ar)
            subtitleFiles.forEachIndexed { index, subFile ->
                val langMatch = Regex("""\.([a-zA-Z]{2,3}(?:-[a-zA-Z0-9_-]+)?)\.[a-zA-Z0-9]+$""").find(subFile.name)
                val lang = langMatch?.groupValues?.get(1)?.substringBefore('-') ?: "und"
                cmd.add("-metadata:s:s:$index")
                cmd.add("language=$lang")
            }

            cmd.add(outputFile.absolutePath)

            Log.d(TAG, "Executing subtitle embedding: ${cmd.joinToString(" ")}")
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Subtitle embedding completed successfully: ${outputFile.absolutePath}")
                outputFile
            } else {
                throw IllegalStateException("FFmpeg subtitle embedding failed (code $exitCode):\n$output")
            }
        }
    }
}
