package com.junkfood.seal.download.engine.subtitle.logging

import android.util.Log
import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeClient

/**
 * SubtitleLogger provides structured diagnostic logging.
 *
 * Strictly sanitized: Never logs cookies, auth headers, PO tokens, or sensitive personal data.
 */
object SubtitleLogger {

    private const val TAG = "SubtitleEngine"

    fun logInfo(jobId: String, videoId: String, message: String) {
        Log.i(TAG, "[$jobId | $videoId] $message")
    }

    fun logSuccess(
        jobId: String,
        videoId: String,
        operation: String,
        attempt: Int,
        clientChain: List<YoutubeClient>,
        durationMs: Long
    ) {
        val clientStr = clientChain.joinToString(",") { it.identifier }
        Log.d(TAG, "[$jobId | $videoId] SUCCESS: $operation (attempt $attempt, clients=[$clientStr], time=${durationMs}ms)")
    }

    fun logFailure(
        jobId: String,
        videoId: String,
        operation: String,
        attempt: Int,
        clientChain: List<YoutubeClient>,
        failure: SubtitleFailure,
        durationMs: Long
    ) {
        val clientStr = clientChain.joinToString(",") { it.identifier }
        val failureClass = failure::class.java.simpleName
        Log.w(
            TAG,
            "[$jobId | $videoId] FAILED: $operation (attempt $attempt, clients=[$clientStr], error=$failureClass: ${failure.userFriendlyMessage}, time=${durationMs}ms)"
        )
    }

    fun logProgress(jobId: String, videoId: String, stage: String, progress: Float) {
        Log.v(TAG, "[$jobId | $videoId] Progress ${(progress * 100).toInt()}% -> $stage")
    }
}
