package com.junkfood.seal.download.engine.subtitle

import com.junkfood.seal.download.Task
import com.junkfood.seal.download.engine.builder.SubtitleOptionBuilder
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import kotlinx.coroutines.sync.Mutex
import kotlin.random.Random

/**
 * SubtitleManager
 *
 * Manages concurrency, anti-ban rate limiting, and mutex locks for subtitle extraction
 * to prevent YouTube IP bans, rate-limiting (HTTP 429), and directory collisions
 * during sequential playlist subtitle downloads.
 */
object SubtitleManager {

    /** Global mutex to ensure sequential execution of subtitle-only tasks */
    val subtitleMutex = Mutex()

    /** Normal inter-item delay for playlist subtitles to prevent bot scraper detection (1.5s - 2.5s) */
    const val DEFAULT_INTER_ITEM_DELAY_MS = 1800L

    /** Extended cooling-off delay when YouTube returns 429 Too Many Requests or Bot Detection (5s - 8s) */
    const val RATE_LIMIT_COOLDOWN_DELAY_MS = 6000L

    /**
     * Checks if a given task is a subtitle-only task (--skip-download mode).
     */
    fun isSubtitleOnlyTask(task: Task): Boolean {
        return task.preferences.skipDownload && task.preferences.downloadSubtitle
    }

    /**
     * Calculates an intelligent anti-ban delay with jitter for sequential playlist items.
     *
     * @param isRateLimited Whether a rate-limit error was encountered recently
     * @return Delay in milliseconds
     */
    fun getAntiBanDelayMs(isRateLimited: Boolean = false): Long {
        val baseDelay = if (isRateLimited) RATE_LIMIT_COOLDOWN_DELAY_MS else DEFAULT_INTER_ITEM_DELAY_MS
        val jitter = Random.nextLong(200L, 800L)
        return baseDelay + jitter
    }

    /**
     * Calculates exponential backoff delay for retry attempts.
     *
     * @param retryAttempt 1 for 1st retry, 2 for 2nd retry
     * @param isRateLimited Whether the failure was caused by rate limiting
     * @return Delay in milliseconds
     */
    fun getRetryBackoffDelayMs(retryAttempt: Int, isRateLimited: Boolean = false): Long {
        val base = if (isRateLimited) 4000L else 2000L
        val multiplier = (1 shl (retryAttempt - 1)).coerceAtLeast(1)
        val jitter = Random.nextLong(300L, 1000L)
        return (base * multiplier) + jitter
    }

    /**
     * Checks if a throwable or error message indicates a YouTube bot block or rate limit.
     */
    fun isRateLimitOrBotError(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val message = throwable.message.orEmpty().lowercase()
        return message.contains("429") ||
            message.contains("too many requests") ||
            message.contains("sign in to confirm you're not a bot") ||
            message.contains("confirm you’re not a bot") ||
            message.contains("bot detection") ||
            message.contains("http error 403") ||
            message.contains("rate limit") ||
            message.contains("blocked")
    }

    /**
     * Builds subtitle options from preferences.
     */
    fun getSubtitleOptions(preferences: DownloadPreferences): SubtitleOptionBuilder.SubtitleOptions {
        return if (preferences.skipDownload) {
            SubtitleOptionBuilder.buildForSubtitleOnlyDownload(
                subtitleLanguage = preferences.subtitleLanguage,
                convertSubtitle = preferences.convertSubtitle,
                autoSubtitle = preferences.autoSubtitle,
                autoTranslatedSubtitles = preferences.autoTranslatedSubtitles,
            )
        } else {
            SubtitleOptionBuilder.buildForMediaWithSubtitles(
                subtitleLanguage = preferences.subtitleLanguage,
                convertSubtitle = preferences.convertSubtitle,
                autoSubtitle = preferences.autoSubtitle,
                autoTranslatedSubtitles = preferences.autoTranslatedSubtitles,
                embedSubtitle = preferences.embedSubtitle
            )
        }
    }
}

