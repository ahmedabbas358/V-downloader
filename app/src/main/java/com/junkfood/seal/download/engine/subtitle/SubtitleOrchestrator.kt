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

        // 1. Stage: Discovery
        onProgress(SubtitleProgress.Discovering("Discovering available subtitles..."))
        val discoveryRes = youtubeProvider.discover(url, preferences, videoInfo)

        val inventory = when (discoveryRes) {
            is SubtitleDiscoveryResult.Success -> discoveryRes.inventory
            is SubtitleDiscoveryResult.Failure -> null
        }
        var attempt = 0
        val maxAttempts = 3
        var lastFailure: SubtitleFailure? = null

        while (attempt < maxAttempts) {
            attempt++
            try {
                if (attempt > 1) {
                    kotlinx.coroutines.delay(1000L * attempt)
                    onProgress(SubtitleProgress.Downloading("Retrying subtitle download (Attempt $attempt/$maxAttempts)...", 0.1f * attempt))
                }

                if (inventory != null && inventory.isNotEmpty()) {
                    // 2. Stage: Language & Track Selection
                    val requestedLangs = preferences.subtitleLanguage.ifBlank { "ar,en" }

                    var matchedTracks = LanguageMatcher.matchTracks(
                        requestedLangs = requestedLangs,
                        availableTracks = inventory.allTracks,
                        policy = SubtitleTypePolicy.ANY,
                        allowAutoCaptions = preferences.autoSubtitle,
                        allowTranslatedSubtitles = preferences.autoTranslatedSubtitles
                    )

                    if (matchedTracks.isEmpty()) {
                        // Resilient fallback: match any available track in inventory
                        matchedTracks = LanguageMatcher.matchTracks(
                            requestedLangs = "all",
                            availableTracks = inventory.allTracks,
                            policy = SubtitleTypePolicy.ANY,
                            allowAutoCaptions = true,
                            allowTranslatedSubtitles = true
                        ).take(2)
                    }

                    val targetFormatStr = SubtitleOptionBuilder.getConvertSubsValue(preferences.convertSubtitle)
                    val registeredKeys = mutableListOf<SubtitleDownloadKey>()
                    val uniqueTracks = mutableListOf<SubtitleTrack>()
                    
                    for (track in matchedTracks) {
                        val key = SubtitleDownloadKey(videoId, track.languageCode, track.source, targetFormatStr)
                        if (SubtitleTaskRegistry.registerTask(key)) {
                            registeredKeys.add(key)
                            uniqueTracks.add(track)
                        }
                    }

                    if (uniqueTracks.isNotEmpty()) {
                        // 3. Stage: Coordinated Download & Conversion
                        val downloadRes = try {
                            youtubeProvider.downloadTracks(
                                url = url,
                                videoId = videoId,
                                tracks = uniqueTracks,
                                destinationDir = destinationDir,
                                preferences = preferences,
                                videoTitle = resolvedTitle,
                                playlistIndex = playlistIndex,
                                onProgress = onProgress
                            )
                        } finally {
                            registeredKeys.forEach { SubtitleTaskRegistry.unregisterTask(it) }
                        }

                        if (downloadRes is SubtitleDownloadResult.Success && downloadRes.downloadedFiles.isNotEmpty()) {
                            return downloadRes
                        }
                    }
                }

                // 4. Robust Direct Extraction Fallback (proven high-compatibility yt-dlp execution)
                onProgress(SubtitleProgress.Downloading(preferences.subtitleLanguage.ifBlank { "ar,en" }, 0.4f))
                val directRes = com.junkfood.seal.download.engine.subtitle.download.SubtitleDownloader.downloadSubtitlesDirectly(
                    url = url,
                    videoId = videoId,
                    title = resolvedTitle,
                    destinationDir = destinationDir,
                    preferences = preferences,
                    playlistIndex = playlistIndex,
                    onProgress = onProgress
                )

                val result = directRes.fold(
                    onSuccess = { files ->
                        if (files.isNotEmpty() || playlistIndex > 0) {
                            SubtitleDownloadResult.Success(
                                downloadedFiles = files,
                                tracks = emptyList(),
                                executionTimeMs = System.currentTimeMillis() - startTime
                            )
                        } else {
                            null
                        }
                    },
                    onFailure = { th ->
                        if (playlistIndex > 0) {
                            SubtitleDownloadResult.Success(
                                downloadedFiles = emptyList(),
                                tracks = emptyList(),
                                executionTimeMs = System.currentTimeMillis() - startTime
                            )
                        } else {
                            lastFailure = if (th is SubtitleFailure) th else SubtitleFailure.fromThrowable(th)
                            null
                        }
                    }
                )

                if (result != null) return result
            } catch (e: Exception) {
                lastFailure = SubtitleFailure.NetworkError(e.message ?: "Network error during subtitle download", e)
            }
        }

        return SubtitleDownloadResult.Failure(lastFailure ?: SubtitleFailure.NoSubtitles)
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
