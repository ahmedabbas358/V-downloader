package com.junkfood.seal.download.engine.subtitle.model

import java.io.File

/**
 * Result of subtitle discovery operation.
 */
sealed interface SubtitleDiscoveryResult {
    data class Success(val inventory: SubtitleInventory) : SubtitleDiscoveryResult
    data class Failure(val error: SubtitleFailure) : SubtitleDiscoveryResult
}

/**
 * Result of subtitle download operation.
 */
sealed interface SubtitleDownloadResult {
    data class Success(
        val downloadedFiles: List<File>,
        val tracks: List<SubtitleTrack>,
        val executionTimeMs: Long
    ) : SubtitleDownloadResult

    data class Failure(
        val error: SubtitleFailure,
        val partiallyDownloaded: List<File> = emptyList()
    ) : SubtitleDownloadResult
}

/**
 * Real-time progress updates during subtitle lifecycle.
 */
sealed class SubtitleProgress(val progress: Float, val statusMessage: String) {
    data class Discovering(val message: String = "Discovering subtitle tracks...") :
        SubtitleProgress(0.1f, message)

    data class Selecting(val message: String = "Selecting target subtitle tracks...") :
        SubtitleProgress(0.3f, message)

    data class Downloading(val currentLang: String, val stepProgress: Float = 0.5f) :
        SubtitleProgress(stepProgress, "Downloading subtitles ($currentLang)...")

    data class Validating(val message: String = "Validating subtitle integrity...") :
        SubtitleProgress(0.85f, message)

    data class Converting(val targetFormat: String) :
        SubtitleProgress(0.92f, "Converting subtitles to $targetFormat...")

    data class Completed(val fileCount: Int) :
        SubtitleProgress(1.0f, "Completed ($fileCount subtitle files ready)")
}

/**
 * User preference policy for selecting subtitles.
 */
enum class SubtitleTypePolicy {
    ORIGINAL,
    MANUAL,
    AUTOMATIC,
    TRANSLATED,
    ANY
}
