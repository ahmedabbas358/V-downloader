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
                // Only apply playlist numbering to actual playlist items (playlistItem > 0)
                if (playlistItem > 0 && (playlistNumbering || isFallback)) {
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
     * Resolves the target directory for a download task, accurately creating a dedicated
     * folder named after the playlist for Video, Audio, and Subtitle downloads.
     * Standalone single videos are NEVER placed into playlist subdirectories.
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
        val isSubtitleOnly = preferences.skipDownload && preferences.downloadSubtitle
        val isAudio = isAudioDownload || (preferences.extractAudio && !isSubtitleOnly)
        val basePath = resolveBaseDirectory(preferences, isAudio)

        val rawPlaylistName = fallbackPlaylistTitle
            .ifBlank { videoPlaylistTitle.orEmpty() }
            .ifBlank { videoInfo?.playlistTitle.orEmpty() }
            .ifBlank { videoInfo?.playlist.orEmpty() }
            .removePrefix("[Subtitles] ")
            .removePrefix("[Subtitle] ")
            .replace(Regex("^#\\d+\\s*"), "")
            .trim()

        val allUrlsToCheck = listOf(
            taskUrl,
            videoInfo?.webpageUrl.orEmpty(),
            videoInfo?.originalUrl.orEmpty()
        )

        val listId = allUrlsToCheck.firstNotNullOfOrNull { u ->
            Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(u)?.groupValues?.get(1)
        }

        val isPlaylist = playlistItem > 0 ||
                preferences.downloadPlaylist ||
                listId != null ||
                allUrlsToCheck.any { it.contains("/playlist", ignoreCase = true) } ||
                (rawPlaylistName.isNotBlank() && !rawPlaylistName.equals("NA", ignoreCase = true) && !rawPlaylistName.equals("Playlist", ignoreCase = true) && !rawPlaylistName.equals(videoInfo?.title, ignoreCase = true)) ||
                !videoPlaylistTitle.isNullOrBlank() ||
                !videoInfo?.playlist.isNullOrBlank() ||
                !videoInfo?.playlistTitle.isNullOrBlank()

        if (!isPlaylist && rawPlaylistName.isBlank()) {
            return if (preferences.commandDirectory.isNotBlank()) {
                File(preferences.commandDirectory)
            } else {
                File(basePath)
            }
        }

        val cleanPlaylistName = FileUtil.cleanFileName(rawPlaylistName).trim()
            .ifBlank {
                if (!listId.isNullOrBlank()) "Playlist_$listId" else "Playlist"
            }

        val folderName = if (isSubtitleOnly) "[Subtitles] $cleanPlaylistName" else cleanPlaylistName

        val targetDir = if (preferences.commandDirectory.isNotBlank()) {
            val cmdDir = File(preferences.commandDirectory)
            if (cmdDir.name.equals(folderName, ignoreCase = true) || cmdDir.name.equals(cleanPlaylistName, ignoreCase = true)) {
                cmdDir
            } else if (preferences.subdirectoryPlaylistTitle || isSubtitleOnly || isPlaylist) {
                File(cmdDir, folderName)
            } else {
                cmdDir
            }
        } else {
            File(basePath, folderName)
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
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
