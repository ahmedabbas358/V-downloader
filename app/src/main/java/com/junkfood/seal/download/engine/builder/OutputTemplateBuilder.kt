package com.junkfood.seal.download.engine.builder

import com.junkfood.seal.App
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import java.util.Locale

/**
 * OutputTemplateBuilder
 *
 * Builds output templates, file paths, directory structures, and playlist numbering
 * for yt-dlp download commands.
 *
 * Design Principles:
 * - Pure formatting functions for yt-dlp -o and -P arguments.
 * - Handles playlist numbering (e.g. "001 - Title").
 * - Handles chapter splitting and clip timestamp templates.
 * - Handles dedicated subdirectory organization for playlists and subtitles.
 */
object OutputTemplateBuilder {

    const val BASENAME = "%(title).200B"
    const val EXTENSION = ".%(ext)s"
    const val ID = "[%(id)s]"
    const val CLIP_TIMESTAMP = "%(section_start)d-%(section_end)d"

    const val OUTPUT_TEMPLATE_DEFAULT = BASENAME + EXTENSION
    const val OUTPUT_TEMPLATE_ID = "$BASENAME $ID$EXTENSION"
    const val OUTPUT_TEMPLATE_CLIPS = "$BASENAME [$CLIP_TIMESTAMP]$EXTENSION"
    const val OUTPUT_TEMPLATE_CHAPTERS =
        "chapter:$BASENAME/%(section_number)d - %(section_title).200B$EXTENSION"
    const val OUTPUT_TEMPLATE_SPLIT = "$BASENAME/$OUTPUT_TEMPLATE_DEFAULT"
    const val PLAYLIST_TITLE_SUBDIRECTORY_PREFIX = "%(playlist_title,playlist,uploader,id).200B/"
    const val PLAYLIST_INDEX_PADDED = "%(playlist_index,playlist_autonumber)03d"

    /**
     * Builds the output template string for the -o option.
     */
    fun buildOutputTemplate(
        preferences: DownloadPreferences,
        playlistItem: Int = 0,
        isFallback: Boolean = false,
    ): String {
        return preferences.run {
            if (splitByChapter) {
                OUTPUT_TEMPLATE_SPLIT
            } else if (videoClips.isEmpty()) {
                val template = outputTemplate.ifEmpty { OUTPUT_TEMPLATE_DEFAULT }
                if ((downloadPlaylist || isFallback || playlistItem != 0) &&
                    playlistNumbering &&
                    playlistItem != 0
                ) {
                    val prefix = String.format(Locale.US, "%03d - ", playlistItem)
                    val fileNameStart = template.lastIndexOf('/').takeIf { it >= 0 }?.plus(1) ?: 0
                    template.replaceRange(fileNameStart, fileNameStart, prefix)
                } else {
                    template
                }
            } else {
                OUTPUT_TEMPLATE_CLIPS
            }
        }
    }

    /**
     * Resolves the base destination directory according to preferences and media type.
     */
    fun resolveBaseDirectory(
        preferences: DownloadPreferences,
        isAudioDownload: Boolean,
    ): String {
        return preferences.run {
            when {
                commandDirectory.isNotBlank() -> commandDirectory
                privateDirectory -> App.privateDownloadDir
                isAudioDownload -> App.audioDownloadDir
                else -> App.videoDownloadDir
            }
        }
    }

    /**
     * Builds the directory prefix for playlists (if subdirectory setting enabled).
     */
    fun buildPlaylistSubdirectoryPrefix(
        preferences: DownloadPreferences,
        playlistItem: Int,
        fallbackPlaylistTitle: String = "",
        videoPlaylistTitle: String? = null,
    ): String {
        if (playlistItem == 0 || preferences.commandDirectory.isNotBlank()) return ""

        val isSubtitleOnly = preferences.skipDownload && preferences.downloadSubtitle
        val playlistName = fallbackPlaylistTitle.ifEmpty { videoPlaylistTitle.orEmpty() }

        return when {
            isSubtitleOnly && playlistName.isNotEmpty() -> {
                "${FileUtil.cleanFileName(playlistName)}/"
            }
            preferences.subdirectoryPlaylistTitle -> {
                if (fallbackPlaylistTitle.isNotEmpty()) {
                    "${FileUtil.cleanFileName(fallbackPlaylistTitle)}/"
                } else if (!videoPlaylistTitle.isNullOrEmpty()) {
                    PLAYLIST_TITLE_SUBDIRECTORY_PREFIX
                } else {
                    ""
                }
            }
            else -> ""
        }
    }
}
