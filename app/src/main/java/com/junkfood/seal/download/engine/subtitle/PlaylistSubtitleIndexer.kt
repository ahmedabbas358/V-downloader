package com.junkfood.seal.download.engine.subtitle

import com.junkfood.seal.util.FileUtil
import java.util.Locale

/**
 * PlaylistSubtitleIndexer
 *
 * Manages directory structures, numbering, and naming conventions for playlist subtitles.
 */
object PlaylistSubtitleIndexer {

    /**
     * Builds the dedicated directory name for playlist subtitles: "[Subtitles] PlaylistTitle"
     */
    fun getSubtitleDirectoryName(playlistTitle: String): String {
        val cleanName = FileUtil.cleanFileName(playlistTitle).ifBlank { "Playlist" }
        return "[Subtitles] $cleanName"
    }

    /**
     * Formats a playlist subtitle item with numerical prefix: "001 - Title"
     */
    fun formatIndexedTitle(index: Int, title: String): String {
        val prefix = String.format(Locale.US, "%03d - ", index)
        val cleanTitle = FileUtil.cleanFileName(title)
        return "$prefix$cleanTitle"
    }
}
