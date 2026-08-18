package com.junkfood.seal.download.engine.subtitle.resilience

import android.util.Log
import com.junkfood.seal.download.engine.subtitle.logging.SubtitleLogger
import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeClient
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeClientStrategy
import kotlinx.coroutines.delay

/**
 * SubtitleRecoveryManager orchestrates intelligent error recovery, backoff,
 * client rotation, and retry budgets.
 */
object SubtitleRecoveryManager {

    private const val TAG = "SubtitleRecoveryManager"

    /**
     * Executes an operation with comprehensive recovery and retry policies.
     */
    suspend fun <T> executeWithRecovery(
        jobId: String,
        videoId: String,
        operationName: String,
        block: suspend (attempt: Int, clientChain: List<YoutubeClient>) -> T
    ): Result<T> {
        var currentAttempt = 1
        var clientChain = YoutubeClientStrategy.getClientChainForAttempt(currentAttempt)

        while (true) {
            val startTime = System.currentTimeMillis()
            try {
                val result = block(currentAttempt, clientChain)
                val duration = System.currentTimeMillis() - startTime
                SubtitleLogger.logSuccess(jobId, videoId, operationName, currentAttempt, clientChain, duration)
                return Result.success(result)
            } catch (th: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                val failure = if (th is SubtitleFailure) th else SubtitleFailure.fromThrowable(th)

                SubtitleLogger.logFailure(jobId, videoId, operationName, currentAttempt, clientChain, failure, duration)

                if (failure is SubtitleFailure.Canceled) {
                    return Result.failure(failure)
                }

                if (failure is SubtitleFailure.Http429) {
                    RequestCoordinator.triggerRateLimitCooldown()
                }

                val decision = RetryPolicy.evaluate(failure, currentAttempt)

                if (!decision.shouldRetry || currentAttempt >= decision.maxRetries) {
                    Log.w(TAG, "Operation '$operationName' on $videoId permanently failed after $currentAttempt attempts: ${failure.userFriendlyMessage}")
                    return Result.failure(failure)
                }

                // Check for client strategy rotation
                val alternateStrategy = YoutubeClientStrategy.getNextStrategyForFailure(currentAttempt, failure)
                if (alternateStrategy != null) {
                    clientChain = alternateStrategy
                }

                Log.d(TAG, "Retrying '$operationName' (attempt $currentAttempt -> ${currentAttempt + 1}) after ${decision.delayMs}ms. Action: ${decision.nextAction}")
                if (decision.delayMs > 0) {
                    delay(decision.delayMs)
                }
                currentAttempt++
            }
        }
    }
}
