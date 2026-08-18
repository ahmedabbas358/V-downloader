package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.builder.SubtitleOptionBuilder
import com.junkfood.seal.download.engine.subtitle.PlaylistSubtitleIndexer
import com.junkfood.seal.util.CONVERT_ASS
import com.junkfood.seal.util.CONVERT_LRC
import com.junkfood.seal.util.CONVERT_SRT
import com.junkfood.seal.util.CONVERT_VTT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleOptionBuilderTest {

    @Test
    fun testSubLangsExpansion() {
        // Empty or "all" -> "all"
        assertEquals("all", SubtitleOptionBuilder.buildSubLangsOption(""))
        assertEquals("all", SubtitleOptionBuilder.buildSubLangsOption("all"))

        // Single bare language (e.g. "ar")
        val expandedAr = SubtitleOptionBuilder.buildSubLangsOption("ar")
        assertTrue("Expanded Arabic pattern must include variants", expandedAr.contains("ar") && expandedAr.contains("ar-.*"))

        // Multiple languages
        val multi = SubtitleOptionBuilder.buildSubLangsOption("ar,en")
        assertTrue(multi.contains("ar") && multi.contains("en"))
    }

    @Test
    fun testConvertSubsMapping() {
        assertEquals("srt", SubtitleOptionBuilder.getConvertSubsValue(CONVERT_SRT))
        assertEquals("ass", SubtitleOptionBuilder.getConvertSubsValue(CONVERT_ASS))
        assertEquals("vtt", SubtitleOptionBuilder.getConvertSubsValue(CONVERT_VTT))
        assertEquals("lrc", SubtitleOptionBuilder.getConvertSubsValue(CONVERT_LRC))
    }

    @Test
    fun testPlaylistSubtitleIndexer() {
        assertEquals("[Subtitles] Android Course 2026", PlaylistSubtitleIndexer.getSubtitleDirectoryName("Android Course 2026"))
        assertEquals("001 - Introduction", PlaylistSubtitleIndexer.formatIndexedTitle(1, "Introduction"))
        assertEquals("042 - Advanced Kotlin Coroutines", PlaylistSubtitleIndexer.formatIndexedTitle(42, "Advanced Kotlin Coroutines"))
    }

    @Test
    fun testSubtitleManagerAntiBanDelay() {
        val normalDelay = com.junkfood.seal.download.engine.subtitle.SubtitleManager.getAntiBanDelayMs(isRateLimited = false)
        assertTrue("Normal anti-ban delay should be >= 1800ms", normalDelay >= 1800L)

        val rateLimitedDelay = com.junkfood.seal.download.engine.subtitle.SubtitleManager.getAntiBanDelayMs(isRateLimited = true)
        assertTrue("Rate limited cooldown delay should be >= 6000ms", rateLimitedDelay >= 6000L)
    }

    @Test
    fun testSubtitleManagerRetryBackoff() {
        val retry1 = com.junkfood.seal.download.engine.subtitle.SubtitleManager.getRetryBackoffDelayMs(retryAttempt = 1)
        val retry2 = com.junkfood.seal.download.engine.subtitle.SubtitleManager.getRetryBackoffDelayMs(retryAttempt = 2)
        assertTrue("Retry 1 delay should be >= 1500ms", retry1 >= 1500L)
        assertTrue("Retry 2 delay should be >= 2000ms", retry2 >= 2000L)
    }

    @Test
    fun testRateLimitErrorDetection() {
        val err429 = RuntimeException("HTTP Error 429: Too Many Requests")
        val errBot = RuntimeException("Sign in to confirm you're not a bot")
        val errNormal = RuntimeException("Video unavailable")

        assertTrue(com.junkfood.seal.download.engine.subtitle.SubtitleManager.isRateLimitOrBotError(err429))
        assertTrue(com.junkfood.seal.download.engine.subtitle.SubtitleManager.isRateLimitOrBotError(errBot))
        assertFalse(com.junkfood.seal.download.engine.subtitle.SubtitleManager.isRateLimitOrBotError(errNormal))
    }
}
