package com.zx.hiru

import android.app.Application
import com.zx.hiru.cache.CacheManager
import com.zx.hiru.config.AppConfig
import com.zx.hiru.config.RuntimeConfigRepository
import com.zx.hiru.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get

/**
 * 应用程序入口
 */
class HiruApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化缓存
        CacheManager.init(this)

        // 初始化 Koin
        startKoin {
            androidContext(this@HiruApplication)
            modules(appModule)
        }

        // 初始化 AppConfig（必须在 Koin 初始化之后）
        val repository: RuntimeConfigRepository = get(RuntimeConfigRepository::class.java)
        AppConfig.init(repository)
    }
}
