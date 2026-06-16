package com.susking.ephone_s.di

import android.app.Application
import com.susking.ephone_s.EPhoneSApplication
import com.susking.ephone_s.core.worker.BackgroundWorkerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块：提供后台工作器管理器
 * 
 * 这个模块将 EPhoneSApplication 的 setupAiBackgroundWorker 方法
 * 包装成 BackgroundWorkerManager 接口实现，供其他模块通过依赖注入使用。
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {
    
    /**
     * 提供 BackgroundWorkerManager 实现
     * 
     * @param application 应用实例，由 Hilt 自动注入
     * @return BackgroundWorkerManager 接口实现
     */
    @Provides
    @Singleton
    fun provideBackgroundWorkerManager(application: Application): BackgroundWorkerManager {
        return object : BackgroundWorkerManager {
            override fun setupAiBackgroundWorker() {
                // 将 Application 转换为 EPhoneSApplication 并调用其方法
                (application as EPhoneSApplication).setupAiBackgroundWorker()
            }
        }
    }
}