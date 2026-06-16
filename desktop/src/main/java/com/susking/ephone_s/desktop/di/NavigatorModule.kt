package com.susking.ephone_s.desktop.di

import androidx.fragment.app.FragmentManager
import com.susking.ephone_s.desktop.api.DesktopNavigator
import com.susking.ephone_s.desktop.api.FragmentProvider
import com.susking.ephone_s.desktop.navigation.DesktopNavigatorImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

/**
 * Desktop 导航模块的依赖注入配置
 * 提供 DesktopNavigator 实现
 */
@Module
@InstallIn(ActivityComponent::class)
object NavigatorModule {

    /**
     * 提供 DesktopNavigator 实现
     * @param fragmentManager Activity 的 FragmentManager
     * @param mainContainerId 主容器的资源 ID (由 app 模块提供)
     * @param fragmentProvider Fragment 提供者 (由 app 模块提供)
     */
    @Provides
    @ActivityScoped
    fun provideDesktopNavigator(
        fragmentManager: FragmentManager,
        @MainContainerId mainContainerId: Int,
        fragmentProvider: FragmentProvider
    ): DesktopNavigator {
        return DesktopNavigatorImpl(fragmentManager, mainContainerId, fragmentProvider)
    }
}

/**
 * 主容器 ID 的限定符注解
 * 用于标识主 Fragment 容器的资源 ID
 */
@Retention(AnnotationRetention.BINARY)
@javax.inject.Qualifier
annotation class MainContainerId