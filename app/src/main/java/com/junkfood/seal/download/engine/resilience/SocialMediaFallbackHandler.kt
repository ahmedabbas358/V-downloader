package com.junkfood.seal.download.engine.resilience

import android.util.Log
import com.junkfood.seal.util.CobaltEngine
import com.junkfood.seal.util.VideoInfo

/**
 * SocialMediaFallbackHandler
 *
 * Provides emergency fallback extraction for tricky social media platforms
 * (Instagram Reels/Posts, TikTok, Twitter/X) when yt-dlp fails due to platform restrictions,
 * authentication barriers, or anti-bot protections.
 *
 * Uses the embedded [CobaltEngine] as a high-speed fallback provider.
 */
object SocialMediaFallbackHandler {

    private const val TAG = "SocialMediaFallback"

    /**
     * Checks if the given URL belongs to a supported social media platform.
     */
    fun isSocialMediaUrl(url: String): Boolean {
        return url.contains("instagram.com", ignoreCase = true) ||
               url.contains("tiktok.com", ignoreCase = true) ||
               url.contains("twitter.com", ignoreCase = true) ||
               url.contains("x.com", ignoreCase = true) ||
               url.contains("facebook.com", ignoreCase = true) ||
               url.contains("fb.watch", ignoreCase = true)
    }

    /**
     * Attempts to resolve a direct downloadable media stream URL for social platforms
     * using the Cobalt Engine.
     *
     * @param url The original media webpage URL
     * @return A fallback [VideoInfo] object if resolution succeeded, or null.
     */
    suspend fun resolveFallbackVideoInfo(url: String): VideoInfo? {
        if (!isSocialMediaUrl(url)) return null

        Log.d(TAG, "Attempting Cobalt fallback resolution for: $url")
        return try {
            val directMediaUrl = CobaltEngine.fetchVideoUrl(url)
            if (!directMediaUrl.isNullOrBlank()) {
                val extractedId = FileCollisionResolver.extractVideoId(url, fallbackId = url.hashCode().toString())
                val platformName = when {
                    url.contains("instagram", ignoreCase = true) -> "Instagram"
                    url.contains("tiktok", ignoreCase = true) -> "TikTok"
                    url.contains("twitter", ignoreCase = true) || url.contains("x.com", ignoreCase = true) -> "Twitter"
                    else -> "Cobalt"
                }

                VideoInfo(
                    id = extractedId,
                    title = "${platformName}_$extractedId",
                    webpageUrl = directMediaUrl,
                    originalUrl = directMediaUrl,
                    uploader = "$platformName via Cobalt",
                    extractor = platformName,
                    extractorKey = platformName,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "SocialMediaFallbackHandler failed for $url", e)
            null
        }
    }
}
