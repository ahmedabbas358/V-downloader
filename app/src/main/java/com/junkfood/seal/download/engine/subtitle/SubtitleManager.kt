package com.junkfood.seal.download.engine.subtitle

import com.junkfood.seal.download.Task
import com.junkfood.seal.download.engine.builder.SubtitleOptionBuilder
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDiscoveryResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.model.SubtitleProgress
import com.junkfood.seal.download.engine.subtitle.resilience.RequestCoordinator
import com.junkfood.seal.download.engine.subtitle.resilience.RetryPolicy
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.VideoInfo
import kotlinx.coroutines.sync.Mutex
import java.io.File

/**
 * SubtitleManager
 *
 * Primary facade for the Subtitle Subsystem.
 * Exposes thread-safe locks, anti-ban pacing, error classification, option building,
 * and high-level execution pipelines.
 */
object SubtitleManager {

    /** Global mutex to ensure sequential execution of subtitle-only tasks */
    val subtitleMutex: Mutex
        get() = RequestCoordinator.globalSubtitleMutex

    val useCase = SubtitleUseCase()

    /** Normal inter-item delay for playlist subtitles to prevent bot scraper detection (1.5s - 2.5s) */
    const val DEFAULT_INTER_ITEM_DELAY_MS = RequestCoordinator.DEFAULT_INTER_ITEM_DELAY_MS

    /** Extended cooling-off delay when YouTube returns 429 Too Many Requests or Bot Detection (5s - 8s) */
    const val RATE_LIMIT_COOLDOWN_DELAY_MS = RequestCoordinator.RATE_LIMIT_COOLDOWN_DELAY_MS

    /**
     * Checks if a given task is a subtitle-only task (--skip-download mode).
     */
    fun isSubtitleOnlyTask(task: Task): Boolean {
        return task.preferences.skipDownload && task.preferences.downloadSubtitle
    }

    /**
     * Calculates an intelligent anti-ban delay with jitter for sequential playlist items.
     */
    fun getAntiBanDelayMs(isRateLimited: Boolean = false): Long {
        return RequestCoordinator.getPacingDelayMs(isRateLimited)
    }

    /**
     * Calculates exponential backoff delay for retry attempts.
     */
    fun getRetryBackoffDelayMs(retryAttempt: Int, isRateLimited: Boolean = false): Long {
        val failure = if (isRateLimited) SubtitleFailure.Http429() else SubtitleFailure.Timeout(20000L)
        val decision = RetryPolicy.evaluate(failure, retryAttempt)
        return decision.delayMs.coerceAtLeast(1500L)
    }

    /**
     * Checks if a throwable or error message indicates a YouTube bot block or rate limit.
     */
    fun isRateLimitOrBotError(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val failure = SubtitleFailure.fromThrowable(throwable)
        return failure is SubtitleFailure.Http429 ||
                failure is SubtitleFailure.PoTokenRequired ||
                failure is SubtitleFailure.Http403
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

    /**
     * Discovers subtitles for a video.
     */
    suspend fun discoverSubtitles(
        url: String,
        preferences: DownloadPreferences,
        videoInfo: VideoInfo? = null
    ): SubtitleDiscoveryResult {
        return useCase.discoverSubtitles(url, preferences, videoInfo)
    }

    /**
     * Downloads subtitles using the robust Subtitle Engine.
     */
    suspend fun downloadSubtitles(
        url: String,
        videoInfo: VideoInfo? = null,
        preferences: DownloadPreferences,
        destinationDir: File,
        playlistIndex: Int = 0,
        onProgress: (SubtitleProgress) -> Unit = {}
    ): SubtitleDownloadResult {
        return useCase.downloadSubtitles(url, videoInfo, preferences, destinationDir, playlistIndex, onProgress)
    }
}
