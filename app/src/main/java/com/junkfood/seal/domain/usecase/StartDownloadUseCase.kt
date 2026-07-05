package com.junkfood.seal.domain.usecase

import com.junkfood.seal.domain.repository.DownloadRepository
import com.junkfood.seal.engine.DownloadRequest
import com.junkfood.seal.engine.DownloadProgress
import kotlinx.coroutines.flow.Flow

class StartDownloadUseCase(
    private val repository: DownloadRepository
) {
    operator fun invoke(request: DownloadRequest): Flow<DownloadProgress> {
        return repository.startDownload(request)
    }
}
