package com.junkfood.seal.engine

import kotlinx.coroutines.flow.Flow

interface DownloadEngine {
    /**
     * Initializes the engine if required.
     */
    suspend fun init()

    /**
     * Fetches metadata for a given URL.
     */
    suspend fun fetchMetadata(url: String): EngineResult<VideoMetadata>

    /**
     * Starts the download and emits progress updates via a Flow.
     */
    fun startDownload(request: DownloadRequest): Flow<DownloadProgress>

    /**
     * Cancels an ongoing download.
     */
    suspend fun cancelDownload(taskId: String)
}

data class VideoMetadata(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: Int,
    val formats: List<VideoFormat>
)

data class VideoFormat(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val fileSize: Long
)

data class DownloadRequest(
    val url: String,
    val formatId: String,
    val outputDir: String,
    val filename: String
)

sealed class DownloadProgress {
    data class Progress(val percent: Float, val speed: String, val eta: String) : DownloadProgress()
    data class Success(val filePath: String) : DownloadProgress()
    data class Error(val exception: Throwable) : DownloadProgress()
}

sealed class EngineResult<out T> {
    data class Success<out T>(val data: T) : EngineResult<T>()
    data class Failure(val error: Throwable) : EngineResult<Nothing>()
}
