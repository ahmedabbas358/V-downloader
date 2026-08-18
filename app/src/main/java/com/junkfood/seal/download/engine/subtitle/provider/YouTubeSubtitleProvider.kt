package com.junkfood.seal.download.engine.subtitle.provider

import com.junkfood.seal.download.engine.builder.DownloadCommandBuilder
import com.junkfood.seal.download.engine.subtitle.cache.SubtitleCache
import com.junkfood.seal.download.engine.subtitle.discovery.SubtitleDiscovery
import com.junkfood.seal.download.engine.subtitle.download.SubtitleDownloader
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDiscoveryResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.model.SubtitleInventory
import com.junkfood.seal.download.engine.subtitle.model.SubtitleProgress
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTrack
import com.junkfood.seal.download.engine.subtitle.resilience.RequestCoordinator
import com.junkfood.seal.download.engine.subtitle.resilience.SubtitleRecoveryManager
import com.junkfood.seal.download.engine.subtitle.runtime.YtDlpRuntimeManager
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeCompatibility
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.serialization.json.Json
import java.io.File

/**
 * YouTubeSubtitleProvider implements [SubtitleProvider] for YouTube using yt-dlp
 * with structured JSON discovery, failure recovery, and coordinated concurrency.
 */
class YouTubeSubtitleProvider : SubtitleProvider {

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override suspend fun discover(
        url: String,
        preferences: DownloadPreferences,
        videoInfo: VideoInfo?
    ): SubtitleDiscoveryResult {
        val videoId = videoInfo?.id ?: YoutubeCompatibility.extractVideoId(url) ?: url

        // 1. Check cache
        val cached = SubtitleCache.get(videoId)
        if (cached != null && cached.isNotEmpty()) {
            return SubtitleDiscoveryResult.Success(cached)
        }

        // 2. If VideoInfo was already provided and contains subtitles/automaticCaptions, discover directly
        if (videoInfo != null && (videoInfo.subtitles.isNotEmpty() || videoInfo.automaticCaptions.isNotEmpty())) {
            val inventory = SubtitleDiscovery.discoverInventory(videoInfo)
            SubtitleCache.put(videoId, inventory)
            return SubtitleDiscoveryResult.Success(inventory)
        }

        // 3. Otherwise, fetch structured JSON via coordinated extraction
        val jobId = "discover_$videoId"
        val recoveryResult = SubtitleRecoveryManager.executeWithRecovery(
            jobId = jobId,
            videoId = videoId,
            operationName = "DiscoverSubtitles"
        ) { _, clientChain ->
            RequestCoordinator.withCoordinatedRequest {
                val request = DownloadCommandBuilder.buildInfoFetchRequest(
                    url = url,
                    preferences = preferences,
                    playlistIndex = null,
                    isFlatPlaylist = false
                )

                val response = YoutubeDL.getInstance().execute(request, jobId, null)
                val fetchedInfo = jsonFormat.decodeFromString<VideoInfo>(response.out)
                val ytDlpVersion = YtDlpRuntimeManager.getVersion()
                val inventory = SubtitleDiscovery.discoverInventory(fetchedInfo, ytDlpVersion)

                if (inventory.isEmpty()) {
                    throw SubtitleFailure.NoSubtitles
                }
                inventory
            }
        }

        return recoveryResult.fold(
            onSuccess = { inv ->
                SubtitleCache.put(videoId, inv)
                SubtitleDiscoveryResult.Success(inv)
            },
            onFailure = { th ->
                val failure = if (th is SubtitleFailure) th else SubtitleFailure.fromThrowable(th)
                SubtitleDiscoveryResult.Failure(failure)
            }
        )
    }

    override suspend fun downloadTracks(
        url: String,
        videoId: String,
        tracks: List<SubtitleTrack>,
        destinationDir: File,
        preferences: DownloadPreferences,
        onProgress: (SubtitleProgress) -> Unit
    ): SubtitleDownloadResult {
        if (tracks.isEmpty()) {
            return SubtitleDownloadResult.Failure(SubtitleFailure.NoSubtitles)
        }

        val startTime = System.currentTimeMillis()
        val jobId = "download_sub_$videoId"

        val recoveryResult = SubtitleRecoveryManager.executeWithRecovery(
            jobId = jobId,
            videoId = videoId,
            operationName = "DownloadSubtitles"
        ) { _, clientChain ->
            RequestCoordinator.withCoordinatedRequest {
                SubtitleDownloader.downloadSelectedTracks(
                    url = url,
                    videoId = videoId,
                    title = tracks.firstOrNull()?.languageName ?: "Video_$videoId",
                    tracks = tracks,
                    destinationDir = destinationDir,
                    preferences = preferences,
                    clientChain = clientChain,
                    onProgress = onProgress
                ).getOrThrow()
            }
        }

        return recoveryResult.fold(
            onSuccess = { files ->
                SubtitleDownloadResult.Success(
                    downloadedFiles = files,
                    tracks = tracks,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            },
            onFailure = { th ->
                val failure = if (th is SubtitleFailure) th else SubtitleFailure.fromThrowable(th)
                SubtitleDownloadResult.Failure(failure)
            }
        )
    }
}
