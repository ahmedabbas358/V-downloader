package com.junkfood.seal.download

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistReconciliationTest {

    @Test
    fun testNormalizeText() {
        val verifier = PlaylistVerifier

        // Arabic normalizations
        assertEquals("ا", verifier.normalizeText("أ"))
        assertEquals("ا", verifier.normalizeText("إ"))
        assertEquals("ا", verifier.normalizeText("آ"))
        
        assertEquals("ه", verifier.normalizeText("ة"))
        assertEquals("ي", verifier.normalizeText("ى"))
        assertEquals("ي", verifier.normalizeText("ی"))
        assertEquals("ك", verifier.normalizeText("ک"))

        // Punctuation and symbols stripping
        assertEquals("hello world", verifier.normalizeText("Hello, World!"))
        assertEquals("test video", verifier.normalizeText("Test... Video?"))

        // Tashkeel removal
        assertEquals("محمد", verifier.normalizeText("مُحَمَّدٌ"))
    }

    @Test
    fun testCleanFileNameForMatching() {
        val verifier = PlaylistVerifier

        // Extension stripping
        assertEquals("video_title", verifier.cleanFileNameForMatching("video_title.mp4"))
        
        // Resolution stripping
        assertEquals("My Video", verifier.cleanFileNameForMatching("My Video [1080p].mkv"))
        assertEquals("My Video", verifier.cleanFileNameForMatching("My Video (720p).webm"))
        assertEquals("My Video", verifier.cleanFileNameForMatching("My Video 4k.mp4"))
        
        // Subtitle language stripping
        assertEquals("video_title", verifier.cleanFileNameForMatching("video_title.ar.srt"))
        assertEquals("video_title", verifier.cleanFileNameForMatching("video_title.en-US.vtt"))
        assertEquals("video_title", verifier.cleanFileNameForMatching("video_title.auto.srt"))
        
        // Common descriptor stripping
        assertEquals("Music Track", verifier.cleanFileNameForMatching("Music Track (Official Video)"))
        assertEquals("Music Track", verifier.cleanFileNameForMatching("Music Track [Lyrics]"))
        assertEquals("مقطع", verifier.cleanFileNameForMatching("مقطع (مترجم)"))
    }
}
