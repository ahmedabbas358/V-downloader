package com.junkfood.seal.download.engine.subtitle

import com.junkfood.seal.download.engine.builder.SubtitleOptionBuilder
import com.junkfood.seal.download.engine.subtitle.discovery.LanguageMatcher
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDiscoveryResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.model.SubtitleProgress
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTrack
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTypePolicy
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadKey
import com.junkfood.seal.download.engine.subtitle.runtime.SubtitleTaskRegistry
import com.junkfood.seal.download.engine.subtitle.provider.SubtitleProvider
import com.junkfood.seal.download.engine.subtitle.provider.YouTubeSubtitleProvider
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeCompatibility
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.SUBTITLE_LANGUAGE
import com.junkfood.seal.util.VideoInfo
import java.io.File

/**
 * SubtitleOrchestrator is the central coordinator managing the full subtitle lifecycle:
 * Discovery → Language Matching → Target Track Selection → Coordinated Download → Validation → Conversion → Storage.
 */
class SubtitleOrchestrator(
    private val youtubeProvider: SubtitleProvider = YouTubeSubtitleProvider()
) {

    /**
     * Executes the complete subtitle download lifecycle for a given video.
     */
    suspend fun executeSubtitlePipeline(
        url: String,
        videoInfo: VideoInfo? = null,
        preferences: DownloadPreferences,
        destinationDir: File,
        playlistIndex: Int = 0,
        onProgress: (SubtitleProgress) -> Unit = {}
    ): SubtitleDownloadResult {
        val startTime = System.currentTimeMillis()
        val videoId = videoInfo?.id ?: YoutubeCompatibility.extractVideoId(url) ?: "video"
        val resolvedTitle = videoInfo?.title ?: preferences.newTitle

        // Anti-ban pacing for playlist subtitle items (index > 0 means it's a playlist item)
        if (playlistIndex > 0) {
            val delay = com.junkfood.seal.download.engine.subtitle.resilience.RequestCoordinator.getPacingDelayMs(false)
            kotlinx.coroutines.delay(delay)
        }

        // 1. Structured Discovery & Direct Stream Download if VideoInfo is available
        if (videoInfo != null && (videoInfo.subtitles.isNotEmpty() || videoInfo.automaticCaptions.isNotEmpty())) {
            val inventory = com.junkfood.seal.download.engine.subtitle.discovery.SubtitleDiscovery.discoverInventory(videoInfo)
            val rawLang = preferences.subtitleLanguage.ifBlank {
                com.junkfood.seal.util.SUBTITLE_LANGUAGE.getString().ifBlank { "all" }
            }
            val matchedTracks = LanguageMatcher.matchTracks(
                requestedLangs = rawLang,
                availableTracks = inventory.allTracks,
                policy = SubtitleTypePolicy.ANY,
                allowAutoCaptions = true,
                allowTranslatedSubtitles = true
            )
            if (matchedTracks.isNotEmpty()) {
                val providerRes = youtubeProvider.downloadTracks(
                    url = url,
                    videoId = videoId,
                    tracks = matchedTracks,
                    destinationDir = destinationDir,
                    preferences = preferences,
                    videoTitle = resolvedTitle,
                    playlistIndex = playlistIndex,
                    onProgress = onProgress
                )
                if (providerRes is SubtitleDownloadResult.Success && providerRes.downloadedFiles.isNotEmpty()) {
                    return providerRes
                }
            }
        }

        // 2. Direct Fast Execution: single yt-dlp pass with auto-subs and manual subs
        onProgress(SubtitleProgress.Downloading(preferences.subtitleLanguage.ifBlank { "ar,en" }, 0.3f))
        val directRes = com.junkfood.seal.download.engine.subtitle.download.SubtitleDownloader.downloadSubtitlesDirectly(
            url = url,
            videoId = videoId,
            title = resolvedTitle,
            destinationDir = destinationDir,
            preferences = preferences,
            playlistIndex = playlistIndex,
            onProgress = onProgress
        )

        val directFiles = directRes.getOrNull()
        if (!directFiles.isNullOrEmpty()) {
            return SubtitleDownloadResult.Success(
                downloadedFiles = directFiles,
                tracks = emptyList(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // 3. Check if subtitle files already exist in destination
        val targetFormat = com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat.fromExtension(
            SubtitleOptionBuilder.getConvertSubsValue(preferences.convertSubtitle)
        )
        val existingFiles = com.junkfood.seal.download.engine.subtitle.download.SubtitleDownloader.findExistingSubtitleFiles(
            destinationDir, resolvedTitle, videoId, targetFormat
        )
        if (existingFiles.isNotEmpty()) {
            onProgress(SubtitleProgress.Completed(existingFiles.size))
            return SubtitleDownloadResult.Success(
                downloadedFiles = existingFiles,
                tracks = emptyList(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val err = directRes.exceptionOrNull()
        val failure = if (err is SubtitleFailure) err else (err?.let { SubtitleFailure.fromThrowable(it) } ?: SubtitleFailure.NoSubtitles)
        return SubtitleDownloadResult.Failure(failure)
    }

    /**
     * Discovers all available subtitle tracks for inspection.
     */
    suspend fun discoverTracks(
        url: String,
        preferences: DownloadPreferences,
        videoInfo: VideoInfo? = null
    ): SubtitleDiscoveryResult {
        return youtubeProvider.discover(url, preferences, videoInfo)
    }
}
