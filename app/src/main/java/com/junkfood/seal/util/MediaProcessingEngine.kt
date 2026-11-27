package com.junkfood.seal.util

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaProcessingEngine {

    private const val TAG = "MediaProcessingEngine"

    fun parseTimestampSeconds(raw: String): Double? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        val plain = cleaned.toDoubleOrNull()
        if (plain != null) return plain.takeIf { it >= 0.0 }
        val parts = cleaned.split(":")
        if (parts.size !in 2..3) return null
        return runCatching {
            val seconds = parts.last().toDouble()
            val minutes = parts[parts.size - 2].toLong()
            val hours = if (parts.size == 3) parts[0].toLong() else 0L
            if (hours < 0 || minutes !in 0..59 || seconds < 0.0 || seconds >= 60.0) return null
            hours * 3600.0 + minutes * 60.0 + seconds
        }.getOrNull()
    }

    private fun formatTimestampSeconds(seconds: Double): String =
        String.format(java.util.Locale.US, "%.3f", seconds.coerceAtLeast(0.0))

    private fun resolveUniqueOutputFile(target: File): File {
        if (!target.exists()) return target
        val parent = target.parentFile
        val base = target.nameWithoutExtension
        val ext = target.extension
        var counter = 1
        while (counter < 10_000) {
            val name = if (ext.isNotEmpty()) "${base}_$counter.$ext" else "${base}_$counter"
            val candidate = File(parent, name)
            if (!candidate.exists()) return candidate
            counter++
        }
        return File(parent, if (ext.isNotEmpty()) "${base}_${System.currentTimeMillis()}.$ext" else "${base}_${System.currentTimeMillis()}")
    }

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
            val startSec = parseTimestampSeconds(startFormatted)
                ?: throw IllegalArgumentException("Invalid start time: $startFormatted")
            val endSec = parseTimestampSeconds(endFormatted)
                ?: throw IllegalArgumentException("Invalid end time: $endFormatted")
            require(endSec > startSec) { "End time must be greater than start time" }

            val uniqueOutput = resolveUniqueOutputFile(outputFile)
            uniqueOutput.parentFile?.mkdirs()

            val cmd = mutableListOf(
                ffmpeg.absolutePath,
                "-y",
                "-ss", formatTimestampSeconds(startSec),
                "-i", inputFile.absolutePath,
                "-t", formatTimestampSeconds(endSec - startSec),
                "-c", "copy",
                "-avoid_negative_ts", "make_zero",
                uniqueOutput.absolutePath
            )

            Log.d(TAG, "Executing lossless trim: ${cmd.joinToString(" ")}")
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && uniqueOutput.exists() && uniqueOutput.length() > 0) {
                Log.d(TAG, "Lossless trim completed successfully: ${uniqueOutput.absolutePath}")
                uniqueOutput
            } else {
                uniqueOutput.delete()
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

            val ext = videoFile.extension.lowercase()
            val validSubtitles = if (ext == "webm") {
                subtitleFiles.filter { it.extension.equals("vtt", ignoreCase = true) }
                    .ifEmpty { subtitleFiles }
            } else {
                subtitleFiles
            }

            // Add input for each subtitle file
            validSubtitles.forEach { subFile ->
                cmd.add("-i")
                cmd.add(subFile.absolutePath)
            }

            // Stream copy video and audio safely
            cmd.add("-map")
            cmd.add("0:v:0?")
            cmd.add("-map")
            cmd.add("0:a?")

            // Map all subtitles
            validSubtitles.forEachIndexed { index, _ ->
                cmd.add("-map")
                cmd.add("${index + 1}:s")
            }

            cmd.add("-c")
            cmd.add("copy")

            when {
                isMkv || ext == "mkv" -> { /* copy SRT/VTT/ASS directly */ }
                ext == "webm" -> {
                    cmd.add("-c:s")
                    cmd.add("webvtt")
                }
                else -> {
                    // For MP4/M4V/MOV containers, set subtitle codec to mov_text
                    cmd.add("-c:s")
                    cmd.add("mov_text")
                }
            }

            // Add metadata for language if extracted from filename (e.g. title.ar.srt -> ar)
            validSubtitles.forEachIndexed { index, subFile ->
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
