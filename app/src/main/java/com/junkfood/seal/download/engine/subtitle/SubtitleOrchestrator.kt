package com.junkfood.seal.download.engine.subtitle

import com.junkfood.seal.download.engine.subtitle.discovery.LanguageMatcher
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDiscoveryResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.model.SubtitleProgress
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTrack
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTypePolicy
import com.junkfood.seal.download.engine.subtitle.provider.SubtitleProvider
import com.junkfood.seal.download.engine.subtitle.provider.YouTubeSubtitleProvider
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeCompatibility
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
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
        onProgress: (SubtitleProgress) -> Unit = {}
    ): SubtitleDownloadResult {
        val videoId = videoInfo?.id ?: YoutubeCompatibility.extractVideoId(url) ?: "video"

        // 1. Stage: Discovery
        onProgress(SubtitleProgress.Discovering("Discovering available subtitles..."))
        val discoveryRes = youtubeProvider.discover(url, preferences, videoInfo)

        val inventory = when (discoveryRes) {
            is SubtitleDiscoveryResult.Success -> discoveryRes.inventory
            is SubtitleDiscoveryResult.Failure -> return SubtitleDownloadResult.Failure(discoveryRes.error)
        }

        if (inventory.isEmpty()) {
            return SubtitleDownloadResult.Failure(SubtitleFailure.NoSubtitles)
        }

        // 2. Stage: Language & Track Selection
        onProgress(SubtitleProgress.Selecting("Matching requested languages..."))
        val requestedLangs = preferences.subtitleLanguage.ifBlank { "ar,en" }

        val matchedTracks = LanguageMatcher.matchTracks(
            requestedLangs = requestedLangs,
            availableTracks = inventory.allTracks,
            policy = SubtitleTypePolicy.ANY,
            allowAutoCaptions = preferences.autoSubtitle,
            allowTranslatedSubtitles = preferences.autoTranslatedSubtitles
        )

        if (matchedTracks.isEmpty()) {
            return SubtitleDownloadResult.Failure(SubtitleFailure.LanguageUnavailable(requestedLangs))
        }

        // 3. Stage: Coordinated Download & Conversion
        val downloadRes = youtubeProvider.downloadTracks(
            url = url,
            videoId = videoId,
            tracks = matchedTracks,
            destinationDir = destinationDir,
            preferences = preferences,
            onProgress = onProgress
        )

        return downloadRes
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
