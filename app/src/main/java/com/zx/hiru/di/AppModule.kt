package com.zx.hiru.di

import com.zx.hiru.config.RuntimeConfigRepository
import com.zx.hiru.data.repository.AiRepository
import com.zx.hiru.data.repository.ConfigRepository
import com.zx.hiru.data.repository.MemoryRepository
import com.zx.hiru.data.repository.impl.AiRepositoryImpl
import com.zx.hiru.data.repository.impl.ConfigRepositoryImpl
import com.zx.hiru.data.repository.impl.MemoryRepositoryImpl
import com.zx.hiru.data.service.AsrService
import com.zx.hiru.data.service.TtsService
import com.zx.hiru.data.service.impl.AsrServiceImpl
import com.zx.hiru.data.service.impl.TtsServiceImpl
import com.zx.hiru.domain.usecase.ChatUseCase
import com.zx.hiru.domain.usecase.ConfigUseCase
import com.zx.hiru.domain.usecase.MemoryUseCase
import com.zx.hiru.domain.usecase.MotionUseCase
import com.zx.hiru.ui.main.MainViewModel
import com.zx.hiru.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin 应用模块
 */
val appModule = module {
    // ViewModel
    viewModel { MainViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }

    // UseCase
    factory { ChatUseCase(get(), get()) }
    factory { MemoryUseCase(get()) }
    factory { ConfigUseCase(get()) }
    factory { MotionUseCase() }

    // Repository
    single<AiRepository> { AiRepositoryImpl() }
    single<MemoryRepository> { MemoryRepositoryImpl(androidContext()) }
    single<ConfigRepository> { ConfigRepositoryImpl(get()) }

    // Service - 需要传入 Context
    single<AsrService> { AsrServiceImpl(androidContext()) }
    single<TtsService> { TtsServiceImpl(androidContext()) }

    // Runtime Config Repository
    single { RuntimeConfigRepository() }
}
