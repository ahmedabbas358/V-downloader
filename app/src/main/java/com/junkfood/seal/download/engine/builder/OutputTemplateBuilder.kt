package com.junkfood.seal.download.engine.builder

import com.junkfood.seal.App
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.VideoInfo
import java.io.File
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
 * - Handles dedicated subdirectory organization for playlists and subtitles named after the playlist.
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
                if ((downloadPlaylist || isFallback || playlistItem != 0) && playlistNumbering) {
                    val prefix = if (playlistItem != 0) {
                        String.format(Locale.US, "%03d - ", playlistItem)
                    } else {
                        "$PLAYLIST_INDEX_PADDED - "
                    }
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
     * Resolves the target directory for a download task, accurately creating a dedicated
     * folder named after the playlist for Video, Audio, and Subtitle downloads.
     */
    fun resolveTargetDirectory(
        preferences: DownloadPreferences,
        isAudioDownload: Boolean,
        playlistItem: Int = 0,
        fallbackPlaylistTitle: String = "",
        videoPlaylistTitle: String? = null,
        videoInfo: VideoInfo? = null,
        taskUrl: String = "",
    ): File {
        if (preferences.commandDirectory.isNotBlank()) {
            return File(preferences.commandDirectory)
        }

        val basePath = resolveBaseDirectory(preferences, isAudioDownload)
        val isPlaylist = preferences.downloadPlaylist ||
                playlistItem > 0 ||
                fallbackPlaylistTitle.isNotBlank() ||
                !videoPlaylistTitle.isNullOrBlank() ||
                videoInfo?.playlist?.isNotBlank() == true ||
                videoInfo?.playlistTitle?.isNotBlank() == true ||
                taskUrl.contains("list=", ignoreCase = true)

        if (!isPlaylist) {
            return File(basePath)
        }

        val rawPlaylistName = fallbackPlaylistTitle
            .ifBlank { videoPlaylistTitle.orEmpty() }
            .ifBlank { videoInfo?.playlistTitle.orEmpty() }
            .ifBlank { videoInfo?.playlist.orEmpty() }
            .ifBlank { preferences.newTitle }
            .removePrefix("[Subtitles] ")
            .removePrefix("[Subtitle] ")
            .replace(Regex("^#\\d+\\s*"), "")
            .trim()

        val cleanPlaylistName = FileUtil.cleanFileName(rawPlaylistName).trim()
            .ifBlank {
                val listIdMatch = Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(taskUrl)
                val listId = listIdMatch?.groupValues?.get(1)
                if (!listId.isNullOrBlank()) "Playlist_$listId" else "Playlist"
            }

        return File(basePath, cleanPlaylistName)
    }

    /**
     * Legacy helper kept for backward compatibility if needed.
     */
    fun buildPlaylistSubdirectoryPrefix(
        preferences: DownloadPreferences,
        playlistItem: Int,
        fallbackPlaylistTitle: String = "",
        videoPlaylistTitle: String? = null,
    ): String {
        return ""
    }
}
