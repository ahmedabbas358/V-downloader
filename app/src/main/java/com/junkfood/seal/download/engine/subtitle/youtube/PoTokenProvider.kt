package com.junkfood.seal.download.engine.subtitle.youtube

/**
 * Purpose of PO Token acquisition.
 */
enum class TokenPurpose {
    METADATA_EXTRACTION,
    SUBTITLE_STREAM,
    MEDIA_DOWNLOAD
}

/**
 * Result of PO Token acquisition.
 */
sealed interface PoTokenResult {
    data class Success(
        val token: String,
        val visitorData: String? = null,
        val expirationTimestamp: Long = System.currentTimeMillis() + 3600_000L
    ) : PoTokenResult

    data object NotRequired : PoTokenResult

    data class Failed(
        val reason: String,
        val cause: Throwable? = null
    ) : PoTokenResult
}

/**
 * PoTokenProvider abstraction for acquiring video-bound Proof of Origin (PO) tokens.
 *
 * Designed to be modular, per-video context bound, and non-global.
 */
interface PoTokenProvider {
    suspend fun getToken(
        videoId: String,
        client: YoutubeClient,
        purpose: TokenPurpose
    ): PoTokenResult

    fun isTokenRequired(client: YoutubeClient, videoId: String): Boolean
}

/**
 * Default implementation of [PoTokenProvider].
 * Detects requirement based on client & error conditions and provides extensible token hook.
 */
class DefaultPoTokenProvider : PoTokenProvider {

    // In-memory short-lived cache keyed by videoId + client
    private val tokenCache = mutableMapOf<String, PoTokenResult.Success>()

    override suspend fun getToken(
        videoId: String,
        client: YoutubeClient,
        purpose: TokenPurpose
    ): PoTokenResult {
        val cacheKey = "${videoId}_${client.identifier}"
        val cached = tokenCache[cacheKey]

        if (cached != null && cached.expirationTimestamp > System.currentTimeMillis()) {
            return cached
        }

        // Under standard execution without specialized headless WebView provider,
        // tokens are returned as NotRequired unless triggered by a challenge.
        return PoTokenResult.NotRequired
    }

    override fun isTokenRequired(client: YoutubeClient, videoId: String): Boolean {
        // Modern Web client often requires PO token for some protected streams,
        // Android and iOS clients typically use device attestation
        return client == YoutubeClient.WEB
    }

    fun storeToken(videoId: String, client: YoutubeClient, token: String, visitorData: String?) {
        val cacheKey = "${videoId}_${client.identifier}"
        tokenCache[cacheKey] = PoTokenResult.Success(
            token = token,
            visitorData = visitorData
        )
    }

    fun clearCache() {
        tokenCache.clear()
    }
}
