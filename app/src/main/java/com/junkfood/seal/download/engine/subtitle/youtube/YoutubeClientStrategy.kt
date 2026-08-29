package com.junkfood.seal.download.engine.subtitle.youtube

import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure

/**
 * YouTube Player Client types recognized by yt-dlp.
 */
enum class YoutubeClient(val identifier: String) {
    ANDROID("android"),
    IOS("ios"),
    WEB("web"),
    MWEB("mweb"),
    ANDROID_EMBED("android_embedded"),
    TV_EMBED("tv_embedded"),
    DEFAULT("default");

    override fun toString(): String = identifier
}

/**
 * ExtractionContext holds contextual parameters for choosing the optimal client.
 */
data class ExtractionContext(
    val videoId: String? = null,
    val isSubtitleOnly: Boolean = false,
    val hasCookies: Boolean = false,
    val isAgeRestricted: Boolean = false,
    val attemptNumber: Int = 1
)

/**
 * Dynamic YouTube Client Selection & Fallback Strategy.
 *
 * Avoids rigid hardcoding of a single client.
 * Adapts client ordering upon HTTP 403, bot detection, or PO token requirements.
 */
object YoutubeClientStrategy {

    // Default primary client chains for normal operation (Web/mWeb/Android enables full auto-captions and public video access)
    private val DEFAULT_CHAIN = listOf(
        YoutubeClient.WEB,
        YoutubeClient.MWEB,
        YoutubeClient.ANDROID,
        YoutubeClient.DEFAULT
    )

    private val FALLBACK_CHAINS = listOf(
        listOf(YoutubeClient.WEB, YoutubeClient.MWEB, YoutubeClient.ANDROID, YoutubeClient.DEFAULT),
        listOf(YoutubeClient.MWEB, YoutubeClient.WEB, YoutubeClient.DEFAULT),
        listOf(YoutubeClient.ANDROID, YoutubeClient.WEB, YoutubeClient.DEFAULT),
        listOf(YoutubeClient.ANDROID_EMBED, YoutubeClient.TV_EMBED, YoutubeClient.WEB, YoutubeClient.DEFAULT),
        listOf(YoutubeClient.IOS, YoutubeClient.MWEB, YoutubeClient.WEB)
    )

    /**
     * Builds the yt-dlp --extractor-args string for YouTube client strategy.
     */
    fun buildExtractorArgs(
        context: ExtractionContext = ExtractionContext(),
        clientChain: List<YoutubeClient> = getClientChainForAttempt(context.attemptNumber)
    ): String {
        val clientString = clientChain.joinToString(",") { it.identifier }
        return "youtube:player_client=$clientString"
    }

    /**
     * Returns the ordered client chain for a given retry attempt.
     */
    fun getClientChainForAttempt(attempt: Int): List<YoutubeClient> {
        val index = (attempt - 1).coerceIn(0, FALLBACK_CHAINS.lastIndex)
        return FALLBACK_CHAINS[index]
    }

    /**
     * Determines the next client chain strategy if a specific failure occurred.
     */
    fun getNextStrategyForFailure(
        currentAttempt: Int,
        failure: SubtitleFailure
    ): List<YoutubeClient>? {
        val nextAttempt = currentAttempt + 1
        if (nextAttempt > FALLBACK_CHAINS.size) return null

        return when (failure) {
            is SubtitleFailure.PrivateVideo -> {
                listOf(YoutubeClient.WEB, YoutubeClient.MWEB, YoutubeClient.TV_EMBED, YoutubeClient.DEFAULT)
            }
            is SubtitleFailure.Http403 -> {
                listOf(YoutubeClient.WEB, YoutubeClient.MWEB, YoutubeClient.DEFAULT)
            }
            is SubtitleFailure.PoTokenRequired -> {
                listOf(YoutubeClient.MWEB, YoutubeClient.WEB, YoutubeClient.DEFAULT)
            }
            is SubtitleFailure.Http429 -> {
                // Keep same or backoff
                null
            }
            else -> {
                getClientChainForAttempt(nextAttempt)
            }
        }
    }
}
