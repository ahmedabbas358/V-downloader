package com.junkfood.seal.download.engine.subtitle.provider

import com.junkfood.seal.download.engine.subtitle.model.SubtitleDiscoveryResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleProgress
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTrack
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.VideoInfo
import java.io.File

/**
 * Generic SubtitleProvider interface.
 *
 * Allows plugging in different backends (YouTube, Bilibili, Vimeo, Whisper AI, External APIs)
 * without rewriting UI, ViewModel, or Task logic.
 */
interface SubtitleProvider {

    /**
     * Discovers all available subtitle tracks for a given media URL or pre-fetched [VideoInfo].
     */
    suspend fun discover(
        url: String,
        preferences: DownloadPreferences,
        videoInfo: VideoInfo? = null
    ): SubtitleDiscoveryResult

    /**
     * Downloads one or more specified subtitle tracks.
     */
    suspend fun downloadTracks(
        url: String,
        videoId: String,
        tracks: List<SubtitleTrack>,
        destinationDir: File,
        preferences: DownloadPreferences,
        videoTitle: String = "",
        onProgress: (SubtitleProgress) -> Unit = {}
    ): SubtitleDownloadResult
}
