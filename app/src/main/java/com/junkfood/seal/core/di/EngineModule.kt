package com.junkfood.seal.core.di

import com.junkfood.seal.engine.DownloadEngine
import com.junkfood.seal.domain.repository.DownloadRepository
import com.junkfood.seal.domain.repository.DownloadRepositoryImpl
import com.junkfood.seal.engine.YtDlpEngineImpl
import com.junkfood.seal.domain.usecase.StartDownloadUseCase
import org.koin.dsl.module

val engineModule = module {
    single<DownloadEngine> { YtDlpEngineImpl() }
    single<DownloadRepository> { DownloadRepositoryImpl(get()) }
    factory { StartDownloadUseCase(get()) }
}
