package com.junkfood.seal.download.engine.subtitle.conversion

import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * SubtitleConverter handles lightweight, high-performance subtitle format conversions
 * (VTT ↔ SRT, TTML → SRT, VTT → ASS) without invoking heavy FFmpeg processes for text operations.
 *
 * All operations are stream-based, memory-efficient, and UTF-8 / RTL safe.
 */
object SubtitleConverter {

    private val SRT_TIMESTAMP_REGEX = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})""")
    private val VTT_TIMESTAMP_REGEX = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})\.(\d{3})""")
    private val TTML_P_REGEX = Regex("""<p\s+begin="([^"]+)"\s+end="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Converts a subtitle file to target format atomically.
     */
    suspend fun convert(
        sourceFile: File,
        targetFormat: SubtitleOutputFormat,
        outputFile: File? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceExt = sourceFile.extension.lowercase(Locale.US)
            val sourceFormat = SubtitleOutputFormat.fromExtension(sourceExt)

            val dest = outputFile ?: File(
                sourceFile.parentFile,
                sourceFile.nameWithoutExtension + "." + targetFormat.extension
            )

            if (sourceFormat == targetFormat) {
                if (sourceFile.absolutePath != dest.absolutePath) {
                    sourceFile.copyTo(dest, overwrite = true)
                }
                return@runCatching dest
            }

            val tempDest = File(dest.parentFile, dest.name + ".tmp_${System.currentTimeMillis()}")

            when {
                sourceFormat == SubtitleOutputFormat.VTT && targetFormat == SubtitleOutputFormat.SRT -> {
                    convertVttToSrtStream(sourceFile, tempDest)
                }
                sourceFormat == SubtitleOutputFormat.SRT && targetFormat == SubtitleOutputFormat.VTT -> {
                    convertSrtToVttStream(sourceFile, tempDest)
                }
                sourceFormat == SubtitleOutputFormat.TTML && targetFormat == SubtitleOutputFormat.SRT -> {
                    convertTtmlToSrtStream(sourceFile, tempDest)
                }
                sourceFormat == SubtitleOutputFormat.VTT && targetFormat == SubtitleOutputFormat.ASS -> {
                    convertVttToAssStream(sourceFile, tempDest)
                }
                else -> {
                    // Fallback to direct text conversion
                    sourceFile.copyTo(tempDest, overwrite = true)
                }
            }

            // Atomic rename
            if (dest.exists()) dest.delete()
            if (!tempDest.renameTo(dest)) {
                tempDest.copyTo(dest, overwrite = true)
                tempDest.delete()
            }
            dest
        }
    }

    /**
     * Converts WebVTT to SRT using line-by-line streaming.
     */
    fun convertVttToSrtStream(vttFile: File, srtFile: File) {
        srtFile.parentFile?.mkdirs()
        BufferedReader(InputStreamReader(FileInputStream(vttFile), StandardCharsets.UTF_8)).use { reader ->
            BufferedWriter(OutputStreamWriter(FileOutputStream(srtFile), StandardCharsets.UTF_8)).use { writer ->
                var line: String?
                var cueIndex = 1
                var inHeader = true
                var lastWasEmpty = true

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line!!.trim()

                    if (inHeader) {
                        if (currentLine.startsWith("WEBVTT") || currentLine.startsWith("NOTE") ||
                            currentLine.startsWith("STYLE") || currentLine.startsWith("REGION")
                        ) {
                            continue
                        }
                        if (currentLine.isEmpty()) {
                            inHeader = false
                            continue
                        }
                        inHeader = false
                    }

                    // Convert VTT timestamps to SRT timestamps
                    if (currentLine.contains("-->")) {
                        val parts = currentLine.split("-->")
                        if (parts.size == 2) {
                            if (!lastWasEmpty) {
                                writer.newLine()
                            }
                            writer.write(cueIndex.toString())
                            writer.newLine()
                            cueIndex++

                            val startStr = formatVttToSrtTime(parts[0].trim())
                            val endStr = formatVttToSrtTime(parts[1].trim().split(Regex("""\s+"""))[0])

                            writer.write("$startStr --> $endStr")
                            writer.newLine()
                            lastWasEmpty = false
                        }
                    } else if (currentLine.isNotEmpty()) {
                        // Strip VTT formatting tags e.g. <c.color>, </c>, <v Speaker>, and inline timestamps <00:00:01.000>
                        val cleanText = currentLine
                            .replace(Regex("""<\d{1,2}:\d{2}(?::\d{2})?\.\d{3}>"""), "")
                            .replace(Regex("""<[^>]+>"""), "")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&quot;", "\"")
                            .replace("&#39;", "'")
                            .trim()

                        if (cleanText.isNotEmpty()) {
                            writer.write(cleanText)
                            writer.newLine()
                            lastWasEmpty = false
                        }
                    } else {
                        if (!lastWasEmpty) {
                            writer.newLine()
                            lastWasEmpty = true
                        }
                    }
                }
            }
        }
    }

    private fun formatVttToSrtTime(time: String): String {
        val clean = time.trim()
        val parts = clean.split(":")
        return when (parts.size) {
            2 -> {
                // MM:SS.mmm -> 00:MM:SS,mmm
                val m = parts[0].padStart(2, '0')
                val s = parts[1].replace('.', ',')
                "00:$m:$s"
            }
            3 -> {
                // HH:MM:SS.mmm -> HH:MM:SS,mmm
                val h = parts[0].padStart(2, '0')
                val m = parts[1].padStart(2, '0')
                val s = parts[2].replace('.', ',')
                "$h:$m:$s"
            }
            else -> clean.replace('.', ',')
        }
    }

    /**
     * Converts SRT to WebVTT format streaming.
     */
    fun convertSrtToVttStream(srtFile: File, vttFile: File) {
        vttFile.parentFile?.mkdirs()
        BufferedReader(InputStreamReader(FileInputStream(srtFile), StandardCharsets.UTF_8)).use { reader ->
            BufferedWriter(OutputStreamWriter(FileOutputStream(vttFile), StandardCharsets.UTF_8)).use { writer ->
                writer.write("WEBVTT")
                writer.newLine()
                writer.newLine()

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line!!
                    // Replace SRT comma in timestamps with WebVTT period
                    if (currentLine.contains("-->")) {
                        val vttLine = currentLine.replace(Regex("""(\d{2}:\d{2}:\d{2}),(\d{3})"""), "$1.$2")
                        writer.write(vttLine)
                    } else {
                        writer.write(currentLine)
                    }
                    writer.newLine()
                }
            }
        }
    }

    /**
     * Converts TTML/XML subtitles to SRT format.
     */
    fun convertTtmlToSrtStream(ttmlFile: File, srtFile: File) {
        val ttmlContent = ttmlFile.readText(StandardCharsets.UTF_8)
        var cueIndex = 1

        srtFile.parentFile?.mkdirs()
        BufferedWriter(OutputStreamWriter(FileOutputStream(srtFile), StandardCharsets.UTF_8)).use { writer ->
            val matches = TTML_P_REGEX.findAll(ttmlContent)
            for (match in matches) {
                val begin = formatTtmlTime(match.groupValues[1])
                val end = formatTtmlTime(match.groupValues[2])
                val text = match.groupValues[3]
                    .replace(Regex("""<br\s*/?>"""), "\n")
                    .replace(Regex("""<[^>]+>"""), "")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .trim()

                if (text.isNotEmpty()) {
                    writer.write(cueIndex.toString())
                    writer.newLine()
                    writer.write("$begin --> $end")
                    writer.newLine()
                    writer.write(text)
                    writer.newLine()
                    writer.newLine()
                    cueIndex++
                }
            }
        }
    }

    private fun formatTtmlTime(time: String): String {
        // Formats e.g. "00:00:01.500" or "1.5s"
        return if (time.contains(":")) {
            time.replace(".", ",")
        } else if (time.endsWith("s")) {
            val seconds = time.removeSuffix("s").toDoubleOrNull() ?: 0.0
            val h = (seconds / 3600).toInt()
            val m = ((seconds % 3600) / 60).toInt()
            val s = (seconds % 60).toInt()
            val ms = ((seconds - seconds.toInt()) * 1000).toInt()
            String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms)
        } else {
            time
        }
    }

    /**
     * Converts WebVTT to basic Advanced SubStation Alpha (ASS).
     */
    fun convertVttToAssStream(vttFile: File, assFile: File) {
        assFile.parentFile?.mkdirs()
        BufferedWriter(OutputStreamWriter(FileOutputStream(assFile), StandardCharsets.UTF_8)).use { writer ->
            writer.write("[Script Info]\nTitle: Converted Subtitle\nScriptType: v4.00+\nWrapStyle: 0\nScaledBorderAndShadow: yes\nPlayResX: 1280\nPlayResY: 720\n\n")
            writer.write("[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
            writer.write("Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n")
            writer.write("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")

            BufferedReader(InputStreamReader(FileInputStream(vttFile), StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                var startTime = ""
                var endTime = ""
                val textBuilder = StringBuilder()

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line!!.trim()
                    if (currentLine.contains("-->")) {
                        if (startTime.isNotEmpty() && textBuilder.isNotEmpty()) {
                            writer.write("Dialogue: 0,$startTime,$endTime,Default,,0,0,0,,${textBuilder.toString().replace("\n", "\\N")}\n")
                            textBuilder.clear()
                        }
                        val parts = currentLine.split("-->")
                        startTime = formatAssTime(parts[0].trim())
                        endTime = formatAssTime(parts[1].trim().split(" ")[0])
                    } else if (currentLine.isNotEmpty() && !currentLine.startsWith("WEBVTT")) {
                        if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                        textBuilder.append(currentLine.replace(Regex("""<[^>]+>"""), ""))
                    } else if (currentLine.isEmpty() && startTime.isNotEmpty() && textBuilder.isNotEmpty()) {
                        writer.write("Dialogue: 0,$startTime,$endTime,Default,,0,0,0,,${textBuilder.toString().replace("\n", "\\N")}\n")
                        textBuilder.clear()
                        startTime = ""
                    }
                }
                if (startTime.isNotEmpty() && textBuilder.isNotEmpty()) {
                    writer.write("Dialogue: 0,$startTime,$endTime,Default,,0,0,0,,${textBuilder.toString().replace("\n", "\\N")}\n")
                }
            }
        }
    }

    private fun formatAssTime(vttTime: String): String {
        // Convert "00:01:23.456" to "0:01:23.45" (ASS uses 10ms centiseconds)
        val clean = if (!vttTime.contains(":") || vttTime.indexOf(":") == vttTime.lastIndexOf(":")) "00:$vttTime" else vttTime
        val parts = clean.split(":")
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        val sParts = parts[2].split(".")
        val s = sParts[0].toIntOrNull() ?: 0
        val cs = ((sParts.getOrNull(1)?.toIntOrNull() ?: 0) / 10).coerceIn(0, 99)
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
    }

    /**
     * Shifts subtitle timestamps in an SRT or VTT file by a given offset in milliseconds.
     */
    suspend fun shiftSubtitleTiming(
        file: File,
        offsetMillis: Long,
        outputFile: File = file
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists() || !file.isFile) {
                throw SubtitleFailure.InvalidSubtitle("File does not exist: ${file.absolutePath}")
            }

            val isVtt = file.name.endsWith(".vtt", ignoreCase = true)
            val separator = if (isVtt) "." else ","

            val lines = file.readLines(StandardCharsets.UTF_8)
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
            outputFile.writeText(modifiedLines.joinToString("\n"), StandardCharsets.UTF_8)
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
}
