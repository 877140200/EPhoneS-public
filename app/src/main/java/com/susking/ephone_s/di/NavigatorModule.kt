package com.susking.ephone_s.di

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.susking.ephone_s.R
import com.susking.ephone_s.desktop.api.FragmentProvider
import com.susking.ephone_s.desktop.api.ThemeProvider
import com.susking.ephone_s.desktop.di.MainContainerId
import com.susking.ephone_s.desktop.FragmentProviderImpl
import com.susking.ephone_s.desktop.ThemeProviderImpl
import com.susking.ephone_s.features.theme.domain.repository.ThemeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 导航模块的依赖注入配置
 * 提供 FragmentManager、主容器 ID 和 FragmentProvider
 *
 * 注意: DesktopNavigator 的实现已经移到 desktop 模块的 NavigatorModule 中
 */
@Module
@InstallIn(ActivityComponent::class)
object NavigatorModule {

    /**
     * 提供 FragmentManager
     * 从 FragmentActivity 获取
     */
    @Provides
    @ActivityScoped
    fun provideFragmentManager(
        activity: FragmentActivity
    ): FragmentManager {
        return activity.supportFragmentManager
    }

    /**
     * 提供主 Fragment 容器的资源 ID
     * 这个 ID 对应 activity_main.xml 中的 FrameLayout
     */
    @Provides
    @MainContainerId
    fun provideMainContainerId(): Int {
        return R.id.main_fragment_container
    }

    /**
     * 提供 FragmentProvider 实现
     * 用于向 desktop 模块提供未模块化的 Fragment
     */
    @Provides
    @ActivityScoped
    fun provideFragmentProvider(): FragmentProvider {
        return FragmentProviderImpl()
    }
}

/**
 * 单例组件的依赖注入配置
 * 提供需要在整个应用生命周期中存在的依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object SingletonModule {

    /**
     * 提供 ThemeProvider 实现
     * 将 app 模块的 ThemeRepository 适配为 desktop 模块的 ThemeProvider 接口
     * 作用域为 Singleton,因为 DesktopViewModel 使用 @HiltViewModel
     */
    @Provides
    @Singleton
    fun provideThemeProvider(
        themeRepository: ThemeRepository
    ): ThemeProvider {
        return ThemeProviderImpl(themeRepository)
    }
}