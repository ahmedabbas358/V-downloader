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

    @Test
    fun testExplicitCommandDirectoryDoesNotDoubleNestPlaylistSubfolder() {
        val tempRoot = System.getProperty("java.io.tmpdir")
        val sameFolderPath = java.io.File(tempRoot, "My Playlist").absolutePath
        val prefsSameFolder = DownloadPreferences.EMPTY.copy(
            commandDirectory = sameFolderPath,
            downloadPlaylist = true,
            subdirectoryPlaylistTitle = true,
        )
        val resolvedSame = OutputTemplateBuilder.resolveTargetDirectory(
            preferences = prefsSameFolder,
            isAudioDownload = false,
            playlistItem = 3,
            fallbackPlaylistTitle = "My Playlist",
        )
        assertEquals(sameFolderPath, resolvedSame.absolutePath)

        val customRootPath = java.io.File(tempRoot, "SealCustomRoot").absolutePath
        val prefsCustomRoot = prefsSameFolder.copy(commandDirectory = customRootPath)
        val resolvedNested = OutputTemplateBuilder.resolveTargetDirectory(
            preferences = prefsCustomRoot,
            isAudioDownload = false,
            playlistItem = 3,
            fallbackPlaylistTitle = "My Playlist",
        )
        assertEquals(java.io.File(customRootPath, "My Playlist").absolutePath, resolvedNested.absolutePath)
    }
}
