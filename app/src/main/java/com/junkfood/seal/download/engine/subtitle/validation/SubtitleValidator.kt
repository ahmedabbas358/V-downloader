package com.junkfood.seal.download.engine.subtitle.validation

import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * SubtitleValidationReport contains granular integrity metrics of a validated subtitle.
 */
data class SubtitleValidationReport(
    val isValid: Boolean,
    val cueCount: Int,
    val firstTimestampMs: Long,
    val lastTimestampMs: Long,
    val coveragePercent: Float,
    val detectedFormat: SubtitleOutputFormat,
    val failure: SubtitleFailure? = null
)

/**
 * SubtitleValidator performs deep semantic and syntactic validation
 * on downloaded subtitle files to prevent saving corrupted, empty, HTML error, or 403 pages.
 */
object SubtitleValidator {

    private val SRT_TIMESTAMP_REGEX = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})""")
    private val VTT_TIMESTAMP_REGEX = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})\.(\d{3})""")

    /**
     * Comprehensive validation of a subtitle file.
     *
     * @param file The file on disk to validate
     * @param expectedFormat Optional expected format (SRT, VTT, etc.)
     * @param videoDurationSeconds Optional video duration for duration coverage sanity check
     * @return Result.success with validated file or Result.failure with classified [SubtitleFailure]
     */
    fun validateFile(
        file: File,
        expectedFormat: SubtitleOutputFormat? = null,
        videoDurationSeconds: Int? = null
    ): Result<File> {
        val report = validateDetailed(file, expectedFormat, videoDurationSeconds)
        return if (report.isValid) {
            Result.success(file)
        } else {
            Result.failure(report.failure ?: SubtitleFailure.InvalidSubtitle("Validation failed", file.absolutePath))
        }
    }

    /**
     * Detailed validation returning structured [SubtitleValidationReport].
     */
    fun validateDetailed(
        file: File,
        expectedFormat: SubtitleOutputFormat? = null,
        videoDurationSeconds: Int? = null
    ): SubtitleValidationReport {
        if (!file.exists()) {
            return SubtitleValidationReport(
                isValid = false,
                cueCount = 0,
                firstTimestampMs = 0L,
                lastTimestampMs = 0L,
                coveragePercent = 0f,
                detectedFormat = expectedFormat ?: SubtitleOutputFormat.SRT,
                failure = SubtitleFailure.EmptySubtitleFile(file.absolutePath)
            )
        }

        val length = file.length()
        if (length < 10L) {
            return SubtitleValidationReport(
                isValid = false,
                cueCount = 0,
                firstTimestampMs = 0L,
                lastTimestampMs = 0L,
                coveragePercent = 0f,
                detectedFormat = expectedFormat ?: SubtitleOutputFormat.SRT,
                failure = SubtitleFailure.EmptySubtitleFile(file.absolutePath)
            )
        }

        // Read sample header safely (first 8KB max for syntax verification)
        val headerBytes = ByteArray((length.coerceAtMost(8192L)).toInt())
        try {
            file.inputStream().use { it.read(headerBytes) }
        } catch (e: Exception) {
            return SubtitleValidationReport(
                isValid = false,
                cueCount = 0,
                firstTimestampMs = 0L,
                lastTimestampMs = 0L,
                coveragePercent = 0f,
                detectedFormat = expectedFormat ?: SubtitleOutputFormat.SRT,
                failure = SubtitleFailure.InvalidSubtitle("Could not read file: ${e.message}", file.absolutePath)
            )
        }

        val headerText = String(headerBytes, StandardCharsets.UTF_8).trim()

        // 1. Check for HTML error pages / 403 Forbidden / Login pages
        if (isHtmlOrErrorPayload(headerText)) {
            return SubtitleValidationReport(
                isValid = false,
                cueCount = 0,
                firstTimestampMs = 0L,
                lastTimestampMs = 0L,
                coveragePercent = 0f,
                detectedFormat = expectedFormat ?: SubtitleOutputFormat.SRT,
                failure = SubtitleFailure.InvalidSubtitle("File contains HTML/HTTP error response rather than subtitles", file.absolutePath)
            )
        }

        // 2. Check for JSON error payload
        if (headerText.startsWith("{") && (headerText.contains("\"error\"") || headerText.contains("\"code\""))) {
            return SubtitleValidationReport(
                isValid = false,
                cueCount = 0,
                firstTimestampMs = 0L,
                lastTimestampMs = 0L,
                coveragePercent = 0f,
                detectedFormat = expectedFormat ?: SubtitleOutputFormat.SRT,
                failure = SubtitleFailure.InvalidSubtitle("File contains JSON error response: $headerText", file.absolutePath)
            )
        }

        // 3. Format-specific syntax checks
        val format = expectedFormat ?: SubtitleOutputFormat.fromExtension(file.extension)
        val syntaxValid = when (format) {
            SubtitleOutputFormat.VTT -> headerText.startsWith("WEBVTT") || VTT_TIMESTAMP_REGEX.containsMatchIn(headerText)
            SubtitleOutputFormat.SRT -> SRT_TIMESTAMP_REGEX.containsMatchIn(headerText) || headerText.contains("-->")
            SubtitleOutputFormat.ASS -> headerText.contains("[Script Info]") || headerText.contains("Dialogue:") || headerText.contains("[Events]")
            SubtitleOutputFormat.TTML -> headerText.contains("<tt") || headerText.contains("<xml") || headerText.contains("xmlns")
            SubtitleOutputFormat.LRC -> headerText.contains("[") && headerText.contains("]") && headerText.contains(":")
        }

        if (!syntaxValid && !headerText.contains("-->") && !headerText.contains("<p") && !headerText.contains("<span")) {
            return SubtitleValidationReport(
                isValid = false,
                cueCount = 0,
                firstTimestampMs = 0L,
                lastTimestampMs = 0L,
                coveragePercent = 0f,
                detectedFormat = format,
                failure = SubtitleFailure.InvalidSubtitle("File failed syntax check for ${format.name}", file.absolutePath)
            )
        }

        // 4. Deep Cue Parsing & Timestamp Scan
        var cueCount = 0
        var firstTimestampMs = -1L
        var lastTimestampMs = -1L
        var previousEndMs = -1L
        var hasMalformedTimestamps = false

        try {
            BufferedReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line!!.trim()
                    if (currentLine.contains("-->")) {
                        val parsed = parseTimestampRange(currentLine)
                        if (parsed != null) {
                            val (startMs, endMs) = parsed
                            if (startMs > endMs || startMs < 0) {
                                hasMalformedTimestamps = true
                            }
                            if (firstTimestampMs == -1L) {
                                firstTimestampMs = startMs
                            }
                            lastTimestampMs = maxOf(lastTimestampMs, endMs)
                            previousEndMs = endMs
                            cueCount++
                        }
                    } else if (format == SubtitleOutputFormat.ASS && currentLine.startsWith("Dialogue:")) {
                        val parsed = parseAssTimestampRange(currentLine)
                        if (parsed != null) {
                            val (startMs, endMs) = parsed
                            if (firstTimestampMs == -1L) firstTimestampMs = startMs
                            lastTimestampMs = maxOf(lastTimestampMs, endMs)
                            cueCount++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return SubtitleValidationReport(
                isValid = false,
                cueCount = cueCount,
                firstTimestampMs = firstTimestampMs.coerceAtLeast(0L),
                lastTimestampMs = lastTimestampMs.coerceAtLeast(0L),
                coveragePercent = 0f,
                detectedFormat = format,
                failure = SubtitleFailure.InvalidSubtitle("Stream parse error: ${e.message}", file.absolutePath)
            )
        }

        if (hasMalformedTimestamps) {
            return SubtitleValidationReport(
                isValid = false,
                cueCount = cueCount,
                firstTimestampMs = firstTimestampMs.coerceAtLeast(0L),
                lastTimestampMs = lastTimestampMs.coerceAtLeast(0L),
                coveragePercent = 0f,
                detectedFormat = format,
                failure = SubtitleFailure.InvalidSubtitle("File contains inverted/negative timestamps", file.absolutePath)
            )
        }

        // 5. Duration Coverage Sanity Check
        var coverage = 1.0f
        if (videoDurationSeconds != null && videoDurationSeconds > 60 && lastTimestampMs > 0) {
            val videoDurationMs = videoDurationSeconds * 1000L
            coverage = (lastTimestampMs.toFloat() / videoDurationMs.toFloat()).coerceIn(0f, 1.5f)
            // If video is > 3 minutes long and subtitle ends within first 15 seconds with only 1-2 cues, it's a truncated partial download
            if (videoDurationSeconds >= 180 && lastTimestampMs < 15_000L && cueCount <= 2) {
                return SubtitleValidationReport(
                    isValid = false,
                    cueCount = cueCount,
                    firstTimestampMs = firstTimestampMs.coerceAtLeast(0L),
                    lastTimestampMs = lastTimestampMs.coerceAtLeast(0L),
                    coveragePercent = coverage,
                    detectedFormat = format,
                    failure = SubtitleFailure.InvalidSubtitle("Truncated subtitle detected (coverage ${(coverage * 100).toInt()}%, video duration ${videoDurationSeconds}s)", file.absolutePath)
                )
            }
        }

        return SubtitleValidationReport(
            isValid = true,
            cueCount = cueCount,
            firstTimestampMs = firstTimestampMs.coerceAtLeast(0L),
            lastTimestampMs = lastTimestampMs.coerceAtLeast(0L),
            coveragePercent = coverage,
            detectedFormat = format,
            failure = null
        )
    }

    private fun parseTimestampRange(line: String): Pair<Long, Long>? {
        val srtMatch = SRT_TIMESTAMP_REGEX.find(line)
        if (srtMatch != null) {
            val h1 = srtMatch.groupValues[1].toLong()
            val m1 = srtMatch.groupValues[2].toLong()
            val s1 = srtMatch.groupValues[3].toLong()
            val ms1 = srtMatch.groupValues[4].toLong()

            val h2 = srtMatch.groupValues[5].toLong()
            val m2 = srtMatch.groupValues[6].toLong()
            val s2 = srtMatch.groupValues[7].toLong()
            val ms2 = srtMatch.groupValues[8].toLong()

            val start = (h1 * 3600 + m1 * 60 + s1) * 1000 + ms1
            val end = (h2 * 3600 + m2 * 60 + s2) * 1000 + ms2
            return Pair(start, end)
        }

        val vttMatch = VTT_TIMESTAMP_REGEX.find(line)
        if (vttMatch != null) {
            val h1 = vttMatch.groupValues[1].ifEmpty { "0" }.toLong()
            val m1 = vttMatch.groupValues[2].toLong()
            val s1 = vttMatch.groupValues[3].toLong()
            val ms1 = vttMatch.groupValues[4].toLong()

            val h2 = vttMatch.groupValues[5].ifEmpty { "0" }.toLong()
            val m2 = vttMatch.groupValues[6].toLong()
            val s2 = vttMatch.groupValues[7].toLong()
            val ms2 = vttMatch.groupValues[8].toLong()

            val start = (h1 * 3600 + m1 * 60 + s1) * 1000 + ms1
            val end = (h2 * 3600 + m2 * 60 + s2) * 1000 + ms2
            return Pair(start, end)
        }

        return null
    }

    private fun parseAssTimestampRange(line: String): Pair<Long, Long>? {
        val parts = line.split(",")
        if (parts.size >= 3) {
            val startMs = parseAssTimeToMs(parts[1].trim())
            val endMs = parseAssTimeToMs(parts[2].trim())
            if (startMs != null && endMs != null) {
                return Pair(startMs, endMs)
            }
        }
        return null
    }

    private fun parseAssTimeToMs(time: String): Long? {
        val parts = time.split(":")
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val secParts = parts[2].split(".")
        val s = secParts[0].toLongOrNull() ?: return null
        val cs = if (secParts.size > 1) secParts[1].toLongOrNull() ?: 0L else 0L
        return (h * 3600 + m * 60 + s) * 1000 + cs * 10
    }

    private fun isHtmlOrErrorPayload(content: String): Boolean {
        val lower = content.lowercase()
        return lower.startsWith("<!doctype html") ||
                lower.startsWith("<html") ||
                lower.contains("<head>") ||
                lower.contains("403 forbidden") ||
                lower.contains("429 too many requests") ||
                lower.contains("access denied") ||
                lower.contains("sign in to youtube")
    }
}

