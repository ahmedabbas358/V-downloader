package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.builder.OutputTemplateBuilder
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputTemplateBuilderTest {

    @Test
    fun testPlaylistNumberingPrefix() {
        val prefs = DownloadPreferences.EMPTY.copy(
            downloadPlaylist = true,
            playlistNumbering = true,
        )
        val template = OutputTemplateBuilder.buildOutputTemplate(
            preferences = prefs,
            playlistItem = 5,
        )
        assertTrue("Playlist numbered template should start with 005 - ", template.startsWith("005 - "))
    }

    @Test
    fun testPlaylistNumberingDisabled() {
        val prefs = DownloadPreferences.EMPTY.copy(
            downloadPlaylist = true,
            playlistNumbering = false,
            skipDownload = true,
            downloadSubtitle = true,
        )
        val template = OutputTemplateBuilder.buildOutputTemplate(
            preferences = prefs,
            playlistItem = 5,
        )
        assertTrue("When playlistNumbering is false, template should NOT start with digits", !template.startsWith("005 - "))
    }

    @Test
    fun testSubtitlePlaylistDirectoryPrefix() {
        val prefs = DownloadPreferences.EMPTY.copy(
            skipDownload = true,
            downloadSubtitle = true,
        )
        val prefix = OutputTemplateBuilder.buildPlaylistSubdirectoryPrefix(
            preferences = prefs,
            playlistItem = 1,
            fallbackPlaylistTitle = "My Playlist",
        )
        assertEquals("[Subtitles] My Playlist/", prefix)
    }
}
