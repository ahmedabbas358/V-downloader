package com.junkfood.seal.engine

import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YtDlpEngineImpl : DownloadEngine {

    override suspend fun init() {
        withContext(Dispatchers.IO) {
            // YoutubeDL.init(context) is already called in App.kt, but we can manage updates here in the future
        }
    }

    override suspend fun fetchMetadata(url: String): EngineResult<VideoMetadata> = withContext(Dispatchers.IO) {
        try {
            // Dummy implementation for now to establish architecture
            // Eventually we map YoutubeDL.getInstance().getInfo(request) to VideoMetadata
            EngineResult.Success(
                VideoMetadata(
                    id = "dummy_id",
                    title = "Extracted Title",
                    uploader = "Uploader",
                    duration = 120,
                    formats = emptyList()
                )
            )
        } catch (e: Exception) {
            EngineResult.Failure(e)
        }
    }

    override fun startDownload(request: DownloadRequest): Flow<DownloadProgress> = flow {
        // Emit progress periodically
        // We will migrate the YoutubeDL.execute with DownloadProgressCallback here
    }

    override suspend fun cancelDownload(taskId: String) {
        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        }
    }
}
