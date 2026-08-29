package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeClient
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeClientStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeClientStrategyTest {

    @Test
    fun testDefaultClientChain() {
        val chain = YoutubeClientStrategy.getClientChainForAttempt(1)
        assertTrue(chain.contains(YoutubeClient.WEB))
        val args = YoutubeClientStrategy.buildExtractorArgs(clientChain = chain)
        assertEquals("youtube:player_client=web,mweb,android,default", args)
    }

    @Test
    fun testClientRotationOn403() {
        val nextChain = YoutubeClientStrategy.getNextStrategyForFailure(
            currentAttempt = 1,
            failure = SubtitleFailure.Http403()
        )
        assertNotNull(nextChain)
        assertTrue(nextChain!!.contains(YoutubeClient.WEB))
    }

    @Test
    fun testClientRotationOnPoToken() {
        val nextChain = YoutubeClientStrategy.getNextStrategyForFailure(
            currentAttempt = 1,
            failure = SubtitleFailure.PoTokenRequired("test_vid")
        )
        assertNotNull(nextChain)
        assertTrue(nextChain!!.contains(YoutubeClient.MWEB))
    }

    @Test
    fun testClientRotationOnPrivateVideo() {
        val nextChain = YoutubeClientStrategy.getNextStrategyForFailure(
            currentAttempt = 1,
            failure = SubtitleFailure.PrivateVideo
        )
        assertNotNull(nextChain)
        assertTrue(nextChain!!.contains(YoutubeClient.TV_EMBED))
    }
}
