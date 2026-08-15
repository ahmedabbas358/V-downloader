package com.junkfood.seal.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object SubtitleUtil {

    private val SRT_TIMESTAMP_REGEX = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")

    /**
     * Shifts subtitle timestamps in an SRT or VTT file by a given offset in milliseconds.
     * @param file The input subtitle file (.srt or .vtt)
     * @param offsetMillis The offset to shift (e.g. +2000ms to delay, -1500ms to advance)
     * @param outputFile Optional output file, defaults to overwriting the input file
     */
    suspend fun shiftSubtitleTiming(
        file: File,
        offsetMillis: Long,
        outputFile: File = file
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists() || !file.isFile) {
                throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
            }

            val isVtt = file.name.endsWith(".vtt", ignoreCase = true)
            val separator = if (isVtt) "." else ","

            val lines = file.readLines()
            val modifiedLines = lines.map { line ->
                SRT_TIMESTAMP_REGEX.replace(line) { match ->
                    val h1 = match.groupValues[1].toLong()
                    val m1 = match.groupValues[2].toLong()
                    val s1 = match.groupValues[3].toLong()
                    val ms1 = match.groupValues[4].toLong()
                    val totalMs1 = ((h1 * 3600 + m1 * 60 + s1) * 1000 + ms1 + offsetMillis).coerceAtLeast(0L)

                    val h2 = match.groupValues[5].toLong()
                    val m2 = match.groupValues[6].toLong()
                    val s2 = match.groupValues[7].toLong()
                    val ms2 = match.groupValues[8].toLong()
                    val totalMs2 = ((h2 * 3600 + m2 * 60 + s2) * 1000 + ms2 + offsetMillis).coerceAtLeast(0L)

                    "${formatTimestamp(totalMs1, separator)} --> ${formatTimestamp(totalMs2, separator)}"
                }
            }

            outputFile.parentFile?.mkdirs()
            outputFile.writeText(modifiedLines.joinToString("\n"))
            outputFile
        }
    }

    private fun formatTimestamp(totalMs: Long, separator: String): String {
        val hours = totalMs / 3_600_000
        val minutes = (totalMs % 3_600_000) / 60_000
        val seconds = (totalMs % 60_000) / 1000
        val millis = totalMs % 1000
        return String.format(Locale.US, "%02d:%02d:%02d%s%03d", hours, minutes, seconds, separator, millis)
    }

    /**
     * Converts an SRT file content to WebVTT format.
     */
    suspend fun convertSrtToVtt(srtFile: File, vttFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val srtContent = srtFile.readText()
            val vttContent = "WEBVTT\n\n" + srtContent.replace(Regex("""(\d{2}:\d{2}:\d{2}),(\d{3})"""), "$1.$2")
            vttFile.parentFile?.mkdirs()
            vttFile.writeText(vttContent)
            vttFile
        }
    }

    /**
     * Converts a WebVTT file content to SRT format.
     */
    suspend fun convertVttToSrt(vttFile: File, srtFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val lines = vttFile.readLines()
            val filteredLines = lines.filterNot { it.startsWith("WEBVTT") || it.startsWith("NOTE") || it.startsWith("STYLE") }
            val srtContent = filteredLines.joinToString("\n")
                .replace(Regex("""(\d{2}:\d{2}:\d{2})\.(\d{3})"""), "$1,$2")
                .trim()
            srtFile.parentFile?.mkdirs()
            srtFile.writeText(srtContent)
            srtFile
        }
    }
}
