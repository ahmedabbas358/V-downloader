package com.junkfood.seal.domain.repository

import com.junkfood.seal.engine.DownloadEngine
import com.junkfood.seal.engine.DownloadRequest
import com.junkfood.seal.engine.DownloadProgress
import com.junkfood.seal.engine.EngineResult
import com.junkfood.seal.engine.VideoMetadata
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    suspend fun fetchMetadata(url: String): EngineResult<VideoMetadata>
    fun startDownload(request: DownloadRequest): Flow<DownloadProgress>
    suspend fun cancelDownload(taskId: String)
}

class DownloadRepositoryImpl(
    private val engine: DownloadEngine
) : DownloadRepository {

    override suspend fun fetchMetadata(url: String): EngineResult<VideoMetadata> {
        return engine.fetchMetadata(url)
    }

    override fun startDownload(request: DownloadRequest): Flow<DownloadProgress> {
        return engine.startDownload(request)
    }

    override suspend fun cancelDownload(taskId: String) {
        engine.cancelDownload(taskId)
    }
}
