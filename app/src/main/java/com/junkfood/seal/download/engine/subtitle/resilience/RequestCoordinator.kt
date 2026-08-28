package com.junkfood.seal.download.engine.subtitle.resilience

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

/**
 * RequestCoordinator manages YouTube extraction concurrency, anti-ban pacing,
 * and rate-limit cooldown across all subtitle and metadata operations.
 */
object RequestCoordinator {

    // Default max concurrent YouTube requests for subtitle operations to avoid IP ban
    private const val MAX_CONCURRENT_YOUTUBE_REQUESTS = 2

    private val requestSemaphore = Semaphore(MAX_CONCURRENT_YOUTUBE_REQUESTS)
    val globalSubtitleMutex = Mutex()

    private var globalCooldownUntilTimestamp: Long = 0L
    private val cooldownMutex = Mutex()

    const val DEFAULT_INTER_ITEM_DELAY_MS = 500L
    const val RATE_LIMIT_COOLDOWN_DELAY_MS = 4000L

    /**
     * Executes a block within controlled concurrency and active cooldown awareness.
     */
    suspend fun <T> withCoordinatedRequest(block: suspend () -> T): T {
        // Wait out any active cooldown before entering permit queue
        checkAndApplyCooldown()

        return requestSemaphore.withPermit {
            checkAndApplyCooldown()
            block()
        }
    }

    /**
     * Sets a global cooldown timestamp when a 429 or Bot block is detected.
     */
    suspend fun triggerRateLimitCooldown(durationMs: Long = RATE_LIMIT_COOLDOWN_DELAY_MS) {
        cooldownMutex.withLock {
            val target = System.currentTimeMillis() + durationMs
            if (target > globalCooldownUntilTimestamp) {
                globalCooldownUntilTimestamp = target
            }
        }
    }

    /**
     * Suspends if there is an active global cooldown period.
     */
    suspend fun checkAndApplyCooldown() {
        val now = System.currentTimeMillis()
        val remaining = globalCooldownUntilTimestamp - now
        if (remaining > 0) {
            val jitter = Random.nextLong(100L, 400L)
            delay(remaining + jitter)
        }
    }

    /**
     * Calculates polite anti-ban pacing delay for playlist iterations.
     */
    fun getPacingDelayMs(isRateLimited: Boolean = false): Long {
        val base = if (isRateLimited) RATE_LIMIT_COOLDOWN_DELAY_MS else DEFAULT_INTER_ITEM_DELAY_MS
        val jitter = if (isRateLimited) Random.nextLong(200L, 800L) else Random.nextLong(100L, 300L)
        return base + jitter
    }
}
