package com.susking.ephone_s.di

import android.content.Context
import com.google.gson.Gson
import com.susking.ephone_s.desktop.data.DesktopRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Desktop模块的依赖注入配置
 * 提供DesktopRepository用于桌面布局的持久化存储
 */
@Module
@InstallIn(SingletonComponent::class)
object DesktopModule {

    /**
     * 提供DesktopRepository单例
     * @param context 应用上下文，用于访问DataStore
     * @param gson Gson实例，用于序列化和反序列化数据
     * @return DesktopRepository实例
     */
    @Provides
    @Singleton
    fun provideDesktopRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): DesktopRepository {
        return DesktopRepository(context, gson)
    }
}