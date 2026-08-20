package com.junkfood.seal.download.engine.builder

import android.content.Context
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.database.objects.CommandTemplate
import com.junkfood.seal.util.CONVERT_M4A
import com.junkfood.seal.util.CONVERT_MP3
import com.junkfood.seal.util.CONVERT_OPUS
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.FileUtil.getArchiveFile
import com.junkfood.seal.util.FileUtil.getConfigFile
import com.junkfood.seal.util.FileUtil.getSdcardTempDir
import com.junkfood.seal.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.util.Locale

/**
 * DownloadCommandBuilder
 *
 * Master orchestrator for building yt-dlp commands. Coordinates specialized sub-builders:
 * - [FormatSelectorBuilder]: Audio/video format selectors with guaranteed audio merging
 * - [SubtitleOptionBuilder]: Subtitle formats, languages, auto-captions
 * - [OutputTemplateBuilder]: Output paths, directory prefixes, playlist numbering
 * - [NetworkOptionBuilder]: Cookies, proxy, aria2c, network resilience
 */
object DownloadCommandBuilder {

    private const val CROP_ARTWORK_COMMAND =
        """--ppa "ffmpeg: -c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"""""

    /**
     * Builds a yt-dlp request for fetching video or playlist information.
     */
    fun buildInfoFetchRequest(
        url: String,
        preferences: DownloadPreferences,
        playlistIndex: Int? = null,
        isFlatPlaylist: Boolean = false,
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
        with(request) {
            addOption("-o", OutputTemplateBuilder.BASENAME)
            if (isFlatPlaylist) {
                addOption("--flat-playlist")
                addOption("--dump-single-json")
            } else if (playlistIndex != null) {
                addOption("--playlist-items", playlistIndex)
                addOption("--dump-json")
            } else {
                addOption("--dump-single-json")
                addOption("--no-playlist")
            }

            if (preferences.restrictFilenames) {
                addOption("--restrict-filenames")
            }

            if (preferences.cookies) {
                NetworkOptionBuilder.applyCookies(this, preferences.userAgentString)
            }
            if (preferences.proxy) {
                NetworkOptionBuilder.applyProxy(this, preferences.proxyUrl)
            }
            NetworkOptionBuilder.applyNetworkResilience(this, preferences.forceIpv4, preferences.debug)
        }
        return request
    }

    /**
     * Builds the complete yt-dlp request for executing a download.
     */
    fun buildDownloadRequest(
        url: String,
        videoInfo: VideoInfo,
        preferences: DownloadPreferences,
        isAudioDownload: Boolean,
        playlistItem: Int = 0,
        playlistUrl: String = "",
        fallbackPlaylistTitle: String = "",
        isFallback: Boolean = false,
        appContext: Context = context,
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
        val pathBuilder = StringBuilder()
        val outputBuilder = StringBuilder()

        with(request) {
            addOption("--no-mtime")
            addOption("--no-part")
            // CRITICAL: --newline forces yt-dlp to output one line per callback call,
            // enabling reliable "Destination:" path discovery in the progress callback.
            addOption("--newline")
            // NOTE: --force-overwrites intentionally removed — it silently bypasses existing
            // files and confuses path discovery. Playlist numbering handles conflicts instead.

            if (preferences.skipDownload) {
                addOption("--skip-download")
            }

            // Network & Cookies
            if (preferences.cookies) {
                NetworkOptionBuilder.applyCookies(this, preferences.userAgentString, appContext)
            }
            if (preferences.proxy) {
                NetworkOptionBuilder.applyProxy(this, preferences.proxyUrl)
            }
            NetworkOptionBuilder.applyNetworkResilience(this, preferences.forceIpv4, preferences.debug)
            NetworkOptionBuilder.applyDownloaderAcceleration(this, preferences.aria2c, preferences.concurrentFragments)

            if (preferences.restrictFilenames) {
                addOption("--restrict-filenames")
            }

            if (preferences.useDownloadArchive) {
                addOption("--download-archive", appContext.getArchiveFile().absolutePath)
            }

            if (preferences.rateLimit && preferences.maxDownloadRate.isNotBlank()) {
                addOption("-r", "${preferences.maxDownloadRate}K")
            }

            // Playlist / single video setup
            addOption("--no-playlist")
            val playlistPrefix = OutputTemplateBuilder.buildPlaylistSubdirectoryPrefix(
                preferences = preferences,
                playlistItem = playlistItem,
                fallbackPlaylistTitle = fallbackPlaylistTitle,
                videoPlaylistTitle = videoInfo.playlist
            )
            outputBuilder.append(playlistPrefix)

            // Base download directory
            val basePath = OutputTemplateBuilder.resolveBaseDirectory(preferences, isAudioDownload)
            pathBuilder.append(basePath)

            if (preferences.subdirectoryExtractor) {
                pathBuilder.append("/${videoInfo.extractorKey}")
            }

            if (preferences.sdcard) {
                val tempDir = appContext.getSdcardTempDir(videoInfo.id)
                tempDir.mkdirs()
                addOption("-P", tempDir.absolutePath)
            } else {
                val downloadDir = java.io.File(pathBuilder.toString())
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                addOption("-P", pathBuilder.toString())
            }

            // Media-specific options
            if (isAudioDownload) {
                applyAudioOptions(this, preferences, videoInfo.id, playlistUrl, appContext)
            } else {
                applyVideoOptions(this, preferences)
            }

            if (!preferences.skipDownload && preferences.sponsorBlock) {
                addOption("--sponsorblock-remove", preferences.sponsorBlockCategory)
            }

            if (!preferences.skipDownload && preferences.createThumbnail) {
                addOption("--write-thumbnail")
                addOption("--convert-thumbnails", "png")
            }

            if (!preferences.skipDownload) {
                preferences.videoClips.forEach {
                    addOption(
                        "--download-sections",
                        "*%d-%d".format(locale = Locale.US, it.start, it.end),
                    )
                }
            }

            if (preferences.newTitle.isNotEmpty()) {
                addCommands(listOf("--replace-in-metadata", "title", ".+", preferences.newTitle))
            }

            // Output template
            val output = OutputTemplateBuilder.buildOutputTemplate(
                preferences = preferences,
                playlistItem = playlistItem,
                isFallback = isFallback
            )
            addOption("-o", outputBuilder.append(output).toString())
        }

        return request
    }

    /**
     * Applies all video-specific options to the request.
     */
    fun applyVideoOptions(
        request: YoutubeDLRequest,
        preferences: DownloadPreferences,
    ): YoutubeDLRequest = request.apply {
        if (preferences.skipDownload) {
            addOption("--skip-download")
            val subOpts = SubtitleOptionBuilder.buildForSubtitleOnlyDownload(
                subtitleLanguage = preferences.subtitleLanguage,
                convertSubtitle = preferences.convertSubtitle,
                autoSubtitle = preferences.autoSubtitle,
                autoTranslatedSubtitles = preferences.autoTranslatedSubtitles,
            )
            applySubtitleOptions(this, subOpts)
            return@apply
        }

        preferences.run {
            addOption("--add-metadata")
            addOption("--no-embed-info-json")

            // Format selection with guaranteed audio merge
            val formatSelector = FormatSelectorBuilder.buildFormatSelector(preferences)
            if (formatSelector.isNotEmpty()) {
                addOption("-f", formatSelector)
                if (mergeAudioStream) {
                    addOption("--audio-multistreams")
                }
            }
            val sorter = FormatSelectorBuilder.toFormatSorter(preferences)
            applyFormatSorter(request, preferences, sorter)

            // Subtitle options
            if (downloadSubtitle) {
                val subOpts = SubtitleOptionBuilder.buildForMediaWithSubtitles(
                    subtitleLanguage = subtitleLanguage,
                    convertSubtitle = convertSubtitle,
                    autoSubtitle = autoSubtitle,
                    autoTranslatedSubtitles = autoTranslatedSubtitles,
                    embedSubtitle = embedSubtitle,
                )
                applySubtitleOptions(this@apply, subOpts)
            }

            // Container format
            if (mergeToMkv) {
                addOption("--remux-video", "mkv")
                addOption("--merge-output-format", "mkv")
            } else {
                addOption("--merge-output-format", "mp4/mkv")
            }

            if (embedThumbnail) {
                addOption("--embed-thumbnail")
            }

            if (videoClips.isEmpty()) {
                addOption("--embed-chapters")
            }
        }
    }

    /**
     * Applies all audio-specific options to the request.
     */
    fun applyAudioOptions(
        request: YoutubeDLRequest,
        preferences: DownloadPreferences,
        id: String = "",
        playlistUrl: String = "",
        appContext: Context = context,
    ): YoutubeDLRequest = request.apply {
        if (preferences.skipDownload) {
            addOption("--skip-download")
            val subOpts = SubtitleOptionBuilder.buildForSubtitleOnlyDownload(
                subtitleLanguage = preferences.subtitleLanguage,
                convertSubtitle = preferences.convertSubtitle,
                autoSubtitle = preferences.autoSubtitle,
                autoTranslatedSubtitles = preferences.autoTranslatedSubtitles,
            )
            applySubtitleOptions(this, subOpts)
            return@apply
        }

        preferences.run {
            addOption("-x")

            if (downloadSubtitle) {
                val subOpts = SubtitleOptionBuilder.buildForMediaWithSubtitles(
                    subtitleLanguage = subtitleLanguage,
                    convertSubtitle = convertSubtitle,
                    autoSubtitle = autoSubtitle,
                    autoTranslatedSubtitles = autoTranslatedSubtitles,
                    embedSubtitle = false,
                )
                applySubtitleOptions(this@apply, subOpts)
            }

            if (formatIdString.isNotEmpty()) {
                addOption("-f", formatIdString)
                if (mergeAudioStream) {
                    addOption("--audio-multistreams")
                }
            } else if (convertAudio) {
                when (audioConvertFormat) {
                    CONVERT_MP3 -> addOption("--audio-format", "mp3")
                    CONVERT_M4A -> addOption("--audio-format", "m4a")
                    CONVERT_OPUS -> addOption("--audio-format", "opus")
                }
            } else {
                val audioSorter = FormatSelectorBuilder.toAudioFormatSorter(preferences)
                applyFormatSorter(this@apply, preferences, audioSorter)
            }

            if (embedMetadata) {
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")

                if (cropArtwork && id.isNotEmpty()) {
                    val configFile = appContext.getConfigFile(id)
                    FileUtil.writeContentToFile(CROP_ARTWORK_COMMAND, configFile)
                    addOption("--config", configFile.absolutePath)
                }
            }

            addOption("--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s")
            if (playlistUrl.isNotEmpty()) {
                addOption("--parse-metadata", "%(album,playlist,title)s:%(meta_album)s")
                addOption("--parse-metadata", "%(track_number,playlist_index)d:%(meta_track)s")
            } else {
                addOption("--parse-metadata", "%(album,title)s:%(meta_album)s")
            }
        }
    }

    /**
     * Builds request for custom command templates.
     */
    fun buildCustomCommandRequest(
        urlList: List<String>,
        template: CommandTemplate,
        preferences: DownloadPreferences,
        appContext: Context = context,
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(urlList)
        with(request) {
            preferences.commandDirectory.takeIf { it.isNotEmpty() }?.let { addOption("-P", it) }
            addOption("--newline")
            if (preferences.aria2c) {
                NetworkOptionBuilder.applyDownloaderAcceleration(this, true, preferences.concurrentFragments)
            }
            if (preferences.useDownloadArchive) {
                addOption("--download-archive", appContext.getArchiveFile().absolutePath)
            }
            if (preferences.restrictFilenames) {
                addOption("--restrict-filenames")
            }
            addOption(
                "--config-locations",
                FileUtil.writeContentToFile(template.template, appContext.getConfigFile()).absolutePath,
            )
            if (preferences.cookies) {
                NetworkOptionBuilder.applyCookies(this, preferences.userAgentString, appContext)
            }
        }
        return request
    }

    private fun applySubtitleOptions(
        request: YoutubeDLRequest,
        options: SubtitleOptionBuilder.SubtitleOptions,
    ) {
        if (options.writeSubs) request.addOption("--write-subs")
        if (options.writeAutoSubs) request.addOption("--write-auto-subs")
        if (options.subLangs.isNotEmpty()) request.addOption("--sub-langs", options.subLangs)
        if (options.subFormat.isNotEmpty()) request.addOption("--sub-format", options.subFormat)
        if (options.embedSubs) {
            request.addOption("--embed-subs")
            // NOTE: no-keep-subs removed -- it deletes external .srt/.vtt even when embedding fails.
        }
        if (options.convertSubs.isNotEmpty()) {
            request.addOption("--convert-subs", options.convertSubs)
        } else if (options.embedSubs) {
            request.addOption("--convert-subs", "srt")
        }
        request.addOption("--ignore-errors")
    }

    private fun applyFormatSorter(
        request: YoutubeDLRequest,
        preferences: DownloadPreferences,
        sorter: String,
    ) {
        if (preferences.formatSorting && preferences.sortingFields.isNotEmpty()) {
            request.addOption("-S", preferences.sortingFields)
        } else if (sorter.isNotEmpty()) {
            request.addOption("-S", sorter)
        }
    }
}
