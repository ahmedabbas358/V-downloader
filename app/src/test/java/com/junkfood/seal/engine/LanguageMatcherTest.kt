package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.subtitle.discovery.LanguageMatcher
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTrack
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTypePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageMatcherTest {

    private val sampleTracks = listOf(
        SubtitleTrack(languageCode = "ar", languageName = "Arabic", source = SubtitleSource.MANUAL),
        SubtitleTrack(languageCode = "ar-EG", languageName = "Arabic (Egypt)", source = SubtitleSource.MANUAL),
        SubtitleTrack(languageCode = "ar-auto", languageName = "Arabic", source = SubtitleSource.AUTO_GENERATED, isAutomatic = true),
        SubtitleTrack(languageCode = "en", languageName = "English", source = SubtitleSource.MANUAL, isOriginal = true),
        SubtitleTrack(languageCode = "en-US", languageName = "English (US)", source = SubtitleSource.MANUAL),
        SubtitleTrack(languageCode = "fr", languageName = "French", source = SubtitleSource.AUTO_GENERATED, isAutomatic = true),
        SubtitleTrack(languageCode = "es-trans", languageName = "Spanish", source = SubtitleSource.TRANSLATED, isTranslated = true),
        SubtitleTrack(languageCode = "zh-Hans", languageName = "Chinese (Simplified)", source = SubtitleSource.MANUAL),
        SubtitleTrack(languageCode = "pt-BR", languageName = "Portuguese (Brazil)", source = SubtitleSource.MANUAL)
    )

    @Test
    fun testExactLanguageMatch() {
        val matches = LanguageMatcher.matchTracks("ar", sampleTracks)
        assertTrue(matches.any { it.languageCode == "ar" })
    }

    @Test
    fun testBaseLanguageMatch() {
        val matches = LanguageMatcher.matchTracks("zh", sampleTracks)
        assertEquals(1, matches.size)
        assertEquals("zh-Hans", matches.first().languageCode)

        val ptMatches = LanguageMatcher.matchTracks("pt", sampleTracks)
        assertEquals(1, ptMatches.size)
        assertEquals("pt-BR", ptMatches.first().languageCode)
    }

    @Test
    fun testRegionalFallback() {
        // Requested ar-SA (Saudi Arabia) which is not in sample, should fall back to base "ar"
        val matches = LanguageMatcher.matchTracks("ar-SA", sampleTracks)
        assertTrue(matches.isNotEmpty())
        assertEquals("ar", matches.first().languageCode)
    }

    @Test
    fun testMultiLanguageMatch() {
        val matches = LanguageMatcher.matchTracks("ar,en", sampleTracks)
        assertTrue(matches.any { it.languageCode.startsWith("ar") })
        assertTrue(matches.any { it.languageCode.startsWith("en") })
    }

    @Test
    fun testAutoCaptionsPolicyFiltering() {
        // Disallowing auto-captions should exclude French (auto)
        val withoutAuto = LanguageMatcher.matchTracks(
            requestedLangs = "fr",
            availableTracks = sampleTracks,
            allowAutoCaptions = false
        )
        assertTrue(withoutAuto.isEmpty())

        // Allowing auto-captions should find French (auto)
        val withAuto = LanguageMatcher.matchTracks(
            requestedLangs = "fr",
            availableTracks = sampleTracks,
            allowAutoCaptions = true
        )
        assertEquals(1, withAuto.size)
        assertEquals("fr", withAuto.first().languageCode)
    }

    @Test
    fun testManualPriorityOverAuto() {
        val matches = LanguageMatcher.matchTracks("ar", sampleTracks, policy = SubtitleTypePolicy.ANY)
        // Manual Arabic must precede Auto Arabic
        assertEquals(SubtitleSource.MANUAL, matches.first().source)
    }

    @Test
    fun testTranslatedFiltering() {
        val withoutTrans = LanguageMatcher.matchTracks("es", sampleTracks, allowTranslatedSubtitles = false)
        assertTrue(withoutTrans.isEmpty())

        val withTrans = LanguageMatcher.matchTracks("es", sampleTracks, allowTranslatedSubtitles = true)
        assertEquals(1, withTrans.size)
        assertEquals("es-trans", withTrans.first().languageCode)
    }
}
