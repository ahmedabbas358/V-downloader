package com.junkfood.seal.download.engine.subtitle.model

/**
 * Sealed hierarchy of classified subtitle engine failures for intelligent retry,
 * error reporting, and recovery decisions.
 */
sealed class SubtitleFailure(
    val userFriendlyMessage: String,
    val isRecoverable: Boolean = false,
    val cause: Throwable? = null
) : Exception(userFriendlyMessage, cause) {

    // Content availability
    data object NoSubtitles : SubtitleFailure("No subtitles available for this video", isRecoverable = false)
    data class LanguageUnavailable(val requestedLang: String) :
        SubtitleFailure("No subtitles available in language '$requestedLang'", isRecoverable = false)
    data class AutomaticCaptionsUnavailable(val lang: String) :
        SubtitleFailure("Auto-generated captions unavailable for language '$lang'", isRecoverable = false)
    data class TranslatedCaptionsUnavailable(val lang: String) :
        SubtitleFailure("Auto-translated subtitles unavailable for language '$lang'", isRecoverable = false)

    // Video availability
    data object VideoUnavailable : SubtitleFailure("Video is unavailable", isRecoverable = false)
    data object PrivateVideo : SubtitleFailure("Video is private", isRecoverable = false)
    data object AgeRestricted : SubtitleFailure("Video is age-restricted and requires authentication", isRecoverable = false)
    data object GeoRestricted : SubtitleFailure("Video is geo-restricted in your region", isRecoverable = false)
    data object LiveStreamEnded : SubtitleFailure("Subtitles are not yet processed for this live stream", isRecoverable = false)

    // Security & Challenges
    data object AuthenticationRequired : SubtitleFailure("Authentication/cookies required to access subtitles", isRecoverable = false)
    data class PoTokenRequired(val videoId: String) :
        SubtitleFailure("Proof of Origin (PO) token required by YouTube", isRecoverable = true)
    data class JsChallengeFailed(val reason: String) :
        SubtitleFailure("YouTube JavaScript challenge/EJS execution failed: $reason", isRecoverable = true)

    // Network & HTTP
    data class NetworkError(val details: String, override val cause: Throwable? = null) :
        SubtitleFailure("Network connection error: $details", isRecoverable = true, cause = cause)
    data class Http403(val url: String? = null) :
        SubtitleFailure("YouTube returned HTTP 403 Forbidden", isRecoverable = true)
    data class Http429(val retryAfterSeconds: Long? = null) :
        SubtitleFailure("YouTube rate limit reached (HTTP 429 Too Many Requests)", isRecoverable = true)
    data class Timeout(val durationMs: Long) :
        SubtitleFailure("Subtitle extraction timed out after ${durationMs}ms", isRecoverable = true)

    // Processing & Integrity
    data class InvalidSubtitle(val reason: String, val path: String? = null) :
        SubtitleFailure("Invalid subtitle payload received: $reason", isRecoverable = true)
    data class EmptySubtitleFile(val path: String) :
        SubtitleFailure("Downloaded subtitle file is empty", isRecoverable = true)
    data class ConversionFailed(val fromFormat: String, val toFormat: String, val reason: String) :
        SubtitleFailure("Failed to convert subtitle from $fromFormat to $toFormat: $reason", isRecoverable = false)
    data class StorageFailed(val destination: String, val reason: String) :
        SubtitleFailure("Failed to write subtitle file to $destination: $reason", isRecoverable = true)

    // Process & Extraction Backend
    data class YtDlpFailure(val exitCode: Int, val rawError: String) :
        SubtitleFailure("yt-dlp subtitle process failed ($exitCode): $rawError", isRecoverable = true)
    data object Canceled : SubtitleFailure("Subtitle extraction was canceled by user", isRecoverable = false)
    data class Unknown(val rawMessage: String, override val cause: Throwable? = null) :
        SubtitleFailure("Unexpected subtitle error: $rawMessage", isRecoverable = false, cause = cause)

    companion object {
        /**
         * Analyzes an exception or yt-dlp stderr output to classify it into a specific SubtitleFailure.
         */
        fun fromThrowable(th: Throwable?, rawOutput: String? = null): SubtitleFailure {
            if (th == null && rawOutput == null) return Unknown("No error details available")

            val combined = buildString {
                th?.let {
                    append(it.message.orEmpty()).append(" ")
                    append(it::class.java.simpleName).append(" ")
                }
                rawOutput?.let { append(it) }
            }.lowercase()

            return when {
                combined.contains("canceled") || combined.contains("cancelled") || combined.contains("canceledexception") ->
                    Canceled

                combined.contains("429") || combined.contains("too many requests") ||
                        combined.contains("rate limit") || combined.contains("bot detection") ->
                    Http429()

                combined.contains("sign in to confirm you're not a bot") ||
                        combined.contains("confirm you’re not a bot") ||
                        combined.contains("po_token") || combined.contains("po token") ||
                        combined.contains("proof of origin") ->
                    PoTokenRequired("")

                combined.contains("http error 403") || combined.contains("forbidden") ->
                    Http403()

                combined.contains("private video") || combined.contains("this video is private") ->
                    PrivateVideo

                combined.contains("age-restricted") || combined.contains("sign in to confirm your age") ->
                    AgeRestricted

                combined.contains("not available in your country") || combined.contains("geo-restricted") || combined.contains("uploader has not made this video available") ->
                    GeoRestricted

                combined.contains("this video is unavailable") || combined.contains("video unavailable") ->
                    VideoUnavailable

                combined.contains("timeout") || combined.contains("timed out") || combined.contains("sockettimeout") ->
                    Timeout(20000L)

                combined.contains("failed to solve") || combined.contains("challenge") || combined.contains("ejs") || combined.contains("n token") ->
                    JsChallengeFailed(th?.message ?: "JavaScript challenge failed")

                combined.contains("no subtitles") || combined.contains("there's no subtitles") || combined.contains("video has no subtitles") ->
                    NoSubtitles

                combined.contains("unable to download video subtitles") ->
                    InvalidSubtitle("Unable to download subtitles stream")

                combined.contains("connectexception") || combined.contains("unknownhostexception") ||
                        combined.contains("no address associated with hostname") || combined.contains("network is unreachable") ->
                    NetworkError(th?.message ?: "Network unreachable", th)

                else ->
                    Unknown(th?.message ?: rawOutput ?: "Unknown extraction failure", th)
            }
        }
    }
}
