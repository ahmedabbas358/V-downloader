package com.junkfood.seal.download.engine.subtitle.runtime

import com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadKey
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SubtitleTaskRegistry prevents concurrent duplicate subtitle downloads.
 * It tracks in-flight download keys globally to ensure Level 2 (Queue) deduplication.
 */
object SubtitleTaskRegistry {
    
    private val inFlightTasks = ConcurrentHashMap<SubtitleDownloadKey, Boolean>()
    private val mutex = Mutex()

    /**
     * Attempts to register a task. Returns true if successful (no existing task).
     * Returns false if the task is already running.
     */
    suspend fun registerTask(key: SubtitleDownloadKey): Boolean {
        return mutex.withLock {
            if (inFlightTasks.containsKey(key)) {
                false
            } else {
                inFlightTasks[key] = true
                true
            }
        }
    }

    /**
     * Unregisters a task once it completes or fails.
     */
    suspend fun unregisterTask(key: SubtitleDownloadKey) {
        mutex.withLock {
            inFlightTasks.remove(key)
        }
    }
    
    /**
     * Checks if a task is currently in flight.
     */
    fun isTaskInFlight(key: SubtitleDownloadKey): Boolean {
        return inFlightTasks.containsKey(key)
    }
}
