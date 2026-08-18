package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.subtitle.discovery.SubtitleDiscovery
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import com.junkfood.seal.util.SubtitleFormat
import com.junkfood.seal.util.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleDiscoveryTest {

    @Test
    fun testManualAndAutoCaptionsDiscovery() {
        val videoInfo = VideoInfo(
            id = "test_vid_123",
            title = "Test Video Title",
            subtitles = mapOf(
                "ar" to listOf(SubtitleFormat(ext = "vtt", url = "https://example.com/ar.vtt", name = "Arabic")),
                "en" to listOf(SubtitleFormat(ext = "vtt", url = "https://example.com/en.vtt", name = "English")),
                "en-orig" to listOf(SubtitleFormat(ext = "vtt", url = "https://example.com/en-orig.vtt", name = "English (original)"))
            ),
            automaticCaptions = mapOf(
                "ar" to listOf(SubtitleFormat(ext = "vtt", url = "https://example.com/auto_ar.vtt", name = "Arabic (auto-generated)")),
                "fr-trans" to listOf(SubtitleFormat(ext = "vtt", url = "https://example.com/trans_fr.vtt", name = "French (auto-translated)"))
            )
        )

        val inventory = SubtitleDiscovery.discoverInventory(videoInfo, "2026.08.01")

        assertEquals("test_vid_123", inventory.videoId)
        assertEquals(3, inventory.manualTracks.size)
        assertEquals(1, inventory.autoTracks.size)
        assertEquals(1, inventory.translatedTracks.size)
        assertEquals(5, inventory.allTracks.size)

        // Verify Arabic manual track
        val arTrack = inventory.manualTracks.first { it.languageCode == "ar" }
        assertEquals(SubtitleSource.MANUAL, arTrack.source)
        assertFalse(arTrack.isAutomatic)
        assertFalse(arTrack.isTranslated)

        // Verify English original track
        val enOrigTrack = inventory.manualTracks.first { it.languageCode == "en-orig" }
        assertTrue(enOrigTrack.isOriginal)

        // Verify French translated track
        val frTrack = inventory.translatedTracks.first { it.languageCode == "fr-trans" }
        assertEquals(SubtitleSource.TRANSLATED, frTrack.source)
        assertTrue(frTrack.isTranslated)
    }

    @Test
    fun testEmptySubtitlesDiscovery() {
        val emptyInfo = VideoInfo(id = "empty_vid", title = "No Subs Video")
        val inventory = SubtitleDiscovery.discoverInventory(emptyInfo)

        assertTrue(inventory.isEmpty())
        assertEquals(0, inventory.allTracks.size)
    }
}
