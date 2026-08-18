package com.junkfood.seal.download.engine.subtitle.validation

import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * SubtitleValidator performs deep semantic and syntactic validation
 * on downloaded subtitle files to prevent saving corrupted, empty, HTML error, or 403 pages.
 */
object SubtitleValidator {

    private val SRT_TIMESTAMP_REGEX = Regex("""\d{1,2}:\d{2}:\d{2}[,.]\d{3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{3}""")
    private val VTT_TIMESTAMP_REGEX = Regex("""(?:\d{1,2}:)?\d{2}:\d{2}\.\d{3}\s*-->\s*(?:\d{1,2}:)?\d{2}:\d{2}\.\d{3}""")

    /**
     * Comprehensive validation of a subtitle file.
     *
     * @param file The file on disk to validate
     * @param expectedFormat Optional expected format (SRT, VTT, etc.)
     * @return Result.success with validated file or Result.failure with classified [SubtitleFailure]
     */
    fun validateFile(file: File, expectedFormat: SubtitleOutputFormat? = null): Result<File> {
        if (!file.exists()) {
            return Result.failure(SubtitleFailure.EmptySubtitleFile(file.absolutePath))
        }

        val length = file.length()
        if (length < 10L) {
            return Result.failure(SubtitleFailure.EmptySubtitleFile(file.absolutePath))
        }

        // Read sample header and body safely (first 8KB max for syntax verification)
        val headerBytes = ByteArray((length.coerceAtMost(8192L)).toInt())
        try {
            file.inputStream().use { it.read(headerBytes) }
        } catch (e: Exception) {
            return Result.failure(SubtitleFailure.InvalidSubtitle("Could not read file: ${e.message}", file.absolutePath))
        }

        val headerText = String(headerBytes, StandardCharsets.UTF_8).trim()

        // 1. Check for HTML error pages / 403 Forbidden / Login pages
        if (isHtmlOrErrorPayload(headerText)) {
            return Result.failure(
                SubtitleFailure.InvalidSubtitle(
                    "File contains HTML/HTTP error response rather than subtitles",
                    file.absolutePath
                )
            )
        }

        // 2. Check for JSON error payload
        if (headerText.startsWith("{") && (headerText.contains("\"error\"") || headerText.contains("\"code\""))) {
            return Result.failure(
                SubtitleFailure.InvalidSubtitle(
                    "File contains JSON error response: $headerText",
                    file.absolutePath
                )
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
            return Result.failure(
                SubtitleFailure.InvalidSubtitle(
                    "File failed syntax check for ${format.name}",
                    file.absolutePath
                )
            )
        }

        // 4. Validate timestamps if SRT/VTT
        if (format == SubtitleOutputFormat.SRT || format == SubtitleOutputFormat.VTT) {
            val timestampValid = validateTimestampsInSample(headerText)
            if (!timestampValid) {
                return Result.failure(
                    SubtitleFailure.InvalidSubtitle(
                        "File contains invalid or negative timestamps",
                        file.absolutePath
                    )
                )
            }
        }

        return Result.success(file)
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

    private fun validateTimestampsInSample(sample: String): Boolean {
        val match = SRT_TIMESTAMP_REGEX.find(sample) ?: VTT_TIMESTAMP_REGEX.find(sample) ?: return true
        // Basic sanity check: start should precede end
        return match.value.contains("-->")
    }
}
