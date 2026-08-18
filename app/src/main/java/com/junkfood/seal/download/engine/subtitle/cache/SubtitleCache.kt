package com.junkfood.seal.download.engine.subtitle.cache

import com.junkfood.seal.download.engine.subtitle.model.SubtitleInventory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SubtitleCache caches [SubtitleInventory] instances in memory with TTL.
 *
 * Excludes transient temporary video/subtitle download URLs to avoid expired stream access.
 */
object SubtitleCache {

    private const val DEFAULT_TTL_MS = 5 * 60 * 1000L // 5 minutes
    private const val MAX_CACHE_SIZE = 100

    private data class CacheEntry(
        val inventory: SubtitleInventory,
        val expiryTimestamp: Long
    )

    private val cacheMap = LinkedHashMap<String, CacheEntry>(MAX_CACHE_SIZE, 0.75f, true)
    private val mutex = Mutex()

    /**
     * Gets a cached [SubtitleInventory] if present and not expired.
     */
    suspend fun get(videoId: String): SubtitleInventory? = mutex.withLock {
        val entry = cacheMap[videoId] ?: return@withLock null
        if (System.currentTimeMillis() > entry.expiryTimestamp) {
            cacheMap.remove(videoId)
            return@withLock null
        }
        entry.inventory
    }

    /**
     * Stores a [SubtitleInventory] in the cache.
     */
    suspend fun put(videoId: String, inventory: SubtitleInventory, ttlMs: Long = DEFAULT_TTL_MS) = mutex.withLock {
        if (cacheMap.size >= MAX_CACHE_SIZE) {
            val eldestKey = cacheMap.keys.firstOrNull()
            if (eldestKey != null) {
                cacheMap.remove(eldestKey)
            }
        }
        cacheMap[videoId] = CacheEntry(
            inventory = inventory,
            expiryTimestamp = System.currentTimeMillis() + ttlMs
        )
    }

    /**
     * Invalidates cache for a given video or clears all.
     */
    suspend fun invalidate(videoId: String? = null) = mutex.withLock {
        if (videoId != null) {
            cacheMap.remove(videoId)
        } else {
            cacheMap.clear()
        }
    }
}
