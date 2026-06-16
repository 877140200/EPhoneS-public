package com.susking.ephone_s.settings.di

import com.susking.ephone_s.settings.api.DefaultSettingsNavigator
import com.susking.ephone_s.settings.api.SettingsNavigator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Settings 模块的 Hilt 依赖注入模块
 *
 * 注意：SettingsRepository已在AiDataModule中提供，此处不再重复提供
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    
    /**
     * 提供 SettingsNavigator 的默认实现
     * app 模块可以通过 @Binds 覆盖此实现
     */
    @Provides
    fun provideSettingsNavigator(): SettingsNavigator {
        return DefaultSettingsNavigator()
    }
}