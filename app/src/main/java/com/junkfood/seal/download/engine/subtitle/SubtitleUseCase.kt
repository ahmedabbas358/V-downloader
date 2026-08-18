package com.junkfood.seal.download.engine.subtitle

import com.junkfood.seal.download.engine.subtitle.conversion.SubtitleConverter
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDiscoveryResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadResult
import com.junkfood.seal.download.engine.subtitle.model.SubtitleProgress
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.VideoInfo
import java.io.File

/**
 * SubtitleUseCase provides a clean, decoupled interface for UI, ViewModels,
 * and DownloadTaskExecutor to interact with the Subtitle Engine.
 */
class SubtitleUseCase(
    private val orchestrator: SubtitleOrchestrator = SubtitleOrchestrator()
) {

    /**
     * Discovers subtitles for a video.
     */
    suspend fun discoverSubtitles(
        url: String,
        preferences: DownloadPreferences,
        videoInfo: VideoInfo? = null
    ): SubtitleDiscoveryResult {
        return orchestrator.discoverTracks(url, preferences, videoInfo)
    }

    /**
     * Executes targeted download of subtitles.
     */
    suspend fun downloadSubtitles(
        url: String,
        videoInfo: VideoInfo? = null,
        preferences: DownloadPreferences,
        destinationDir: File,
        onProgress: (SubtitleProgress) -> Unit = {}
    ): SubtitleDownloadResult {
        return orchestrator.executeSubtitlePipeline(
            url = url,
            videoInfo = videoInfo,
            preferences = preferences,
            destinationDir = destinationDir,
            onProgress = onProgress
        )
    }

    /**
     * Adjusts timing of a subtitle file.
     */
    suspend fun adjustTiming(
        file: File,
        offsetMillis: Long,
        outputFile: File = file
    ): Result<File> {
        return SubtitleConverter.shiftSubtitleTiming(file, offsetMillis, outputFile)
    }
}
