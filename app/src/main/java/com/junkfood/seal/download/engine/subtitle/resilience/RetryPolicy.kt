package com.junkfood.seal.download.engine.subtitle.resilience

import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import kotlin.random.Random

/**
 * Retry decision for a failed subtitle operation.
 */
data class RetryDecision(
    val shouldRetry: Boolean,
    val delayMs: Long = 0L,
    val maxRetries: Int = 2,
    val nextAction: String? = null
)

/**
 * RetryPolicy coordinates granular retry decisions tailored to the classified [SubtitleFailure].
 */
object RetryPolicy {

    /**
     * Determines whether and how to retry based on failure type and current attempt.
     */
    fun evaluate(failure: SubtitleFailure, currentAttempt: Int): RetryDecision {
        return when (failure) {
            is SubtitleFailure.Http429 -> {
                if (currentAttempt <= 2) {
                    val baseDelay = (failure.retryAfterSeconds ?: 6L) * 1000L
                    val backoff = baseDelay * (1 shl (currentAttempt - 1)) + Random.nextLong(500L, 2000L)
                    RetryDecision(shouldRetry = true, delayMs = backoff, maxRetries = 2, nextAction = "Cooling down after 429")
                } else {
                    RetryDecision(shouldRetry = false)
                }
            }

            is SubtitleFailure.Http403 -> {
                if (currentAttempt <= 2) {
                    val backoff = 1500L * currentAttempt + Random.nextLong(300L, 800L)
                    RetryDecision(shouldRetry = true, delayMs = backoff, maxRetries = 2, nextAction = "Rotating YouTube client strategy")
                } else {
                    RetryDecision(shouldRetry = false)
                }
            }

            is SubtitleFailure.PoTokenRequired -> {
                if (currentAttempt <= 1) {
                    RetryDecision(shouldRetry = true, delayMs = 1000L, maxRetries = 1, nextAction = "Requesting PO Token")
                } else {
                    RetryDecision(shouldRetry = false)
                }
            }

            is SubtitleFailure.Timeout, is SubtitleFailure.NetworkError -> {
                if (currentAttempt <= 3) {
                    val backoff = (1000L * (1 shl (currentAttempt - 1))) + Random.nextLong(200L, 800L)
                    RetryDecision(shouldRetry = true, delayMs = backoff, maxRetries = 3, nextAction = "Retrying after network glitch")
                } else {
                    RetryDecision(shouldRetry = false)
                }
            }

            is SubtitleFailure.InvalidSubtitle, is SubtitleFailure.EmptySubtitleFile -> {
                if (currentAttempt <= 2) {
                    RetryDecision(shouldRetry = true, delayMs = 1200L, maxRetries = 2, nextAction = "Attempting fallback subtitle format")
                } else {
                    RetryDecision(shouldRetry = false)
                }
            }

            is SubtitleFailure.StorageFailed -> {
                if (currentAttempt <= 1) {
                    RetryDecision(shouldRetry = true, delayMs = 500L, maxRetries = 1, nextAction = "Retrying destination write")
                } else {
                    RetryDecision(shouldRetry = false)
                }
            }

            is SubtitleFailure.YtDlpFailure -> {
                if (currentAttempt <= 2 && failure.isRecoverable) {
                    val backoff = 1800L * currentAttempt + Random.nextLong(200L, 600L)
                    RetryDecision(shouldRetry = true, delayMs = backoff, maxRetries = 2, nextAction = "Retrying yt-dlp extraction")
                } else {
                    RetryDecision(shouldRetry = false)
                }
            }

            is SubtitleFailure.NoSubtitles,
            is SubtitleFailure.LanguageUnavailable,
            is SubtitleFailure.PrivateVideo,
            is SubtitleFailure.AgeRestricted,
            is SubtitleFailure.GeoRestricted,
            is SubtitleFailure.VideoUnavailable,
            is SubtitleFailure.Canceled,
            is SubtitleFailure.ConversionFailed -> {
                // Permanent failures: DO NOT retry
                RetryDecision(shouldRetry = false)
            }

            else -> {
                if (currentAttempt <= 1 && failure.isRecoverable) {
                    RetryDecision(shouldRetry = true, delayMs = 1500L, maxRetries = 1)
                } else {
                    RetryDecision(shouldRetry = false)
                }
            }
        }
    }
}
