package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.builder.FormatSelectorBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSelectorBuilderTest {

    @Test
    fun testAudioMergeGuarantee() {
        // Bare video format ID MUST be augmented with +bestaudio/best
        val bareFormat = "137"
        val augmented = FormatSelectorBuilder.ensureAudioMerged(bareFormat)
        assertEquals("137+bestaudio/best", augmented)

        // Multiple bare video format IDs
        val multiBare = "137+616"
        val augmentedMulti = FormatSelectorBuilder.ensureAudioMerged(multiBare)
        assertEquals("137+616+bestaudio/best", augmentedMulti)

        // Formats that already specify audio should remain untouched
        assertEquals("137+140", FormatSelectorBuilder.ensureAudioMerged("137+140"))
        assertEquals("137+bestaudio", FormatSelectorBuilder.ensureAudioMerged("137+bestaudio"))
        assertEquals("bv*+ba/b", FormatSelectorBuilder.ensureAudioMerged("bv*+ba/b"))
        assertEquals("137+ba", FormatSelectorBuilder.ensureAudioMerged("137+ba"))
        assertEquals("bestvideo+bestaudio/best", FormatSelectorBuilder.ensureAudioMerged("bestvideo+bestaudio/best"))
    }

    @Test
    fun testResolutionSelectors() {
        // 4K (2160p)
        val res4k = FormatSelectorBuilder.buildResolutionSelector(1)
        assertTrue("4K selector must include height constraint and audio merge", res4k.contains("height<=2160") && res4k.contains("+ba"))

        // 1080p
        val res1080p = FormatSelectorBuilder.buildResolutionSelector(3)
        assertTrue("1080p selector must include height constraint and audio merge", res1080p.contains("height<=1080") && res1080p.contains("+ba"))

        // 720p
        val res720p = FormatSelectorBuilder.buildResolutionSelector(4)
        assertTrue("720p selector must include height constraint and audio merge", res720p.contains("height<=720") && res720p.contains("+ba"))

        // Default (Best)
        val resBest = FormatSelectorBuilder.buildResolutionSelector(0)
        assertEquals("bv*+ba/b", resBest)
    }

    @Test
    fun testPlaylistFormatSelector() {
        val playlistSelector1080 = FormatSelectorBuilder.buildPlaylistFormatSelector(1080)
        assertEquals("bestvideo[height<=1080]+bestaudio/bestvideo+bestaudio/best", playlistSelector1080)

        val playlistSelectorBest = FormatSelectorBuilder.buildPlaylistFormatSelector(0)
        assertEquals("bestvideo+bestaudio/best", playlistSelectorBest)
    }
}
