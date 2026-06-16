package com.susking.ephone_s.desktop

import com.susking.ephone_s.desktop.api.ThemeProvider
import com.susking.ephone_s.features.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * ThemeProvider 的实现类
 * 将 ThemeRepository 的数据适配为 desktop 模块需要的格式
 */
class ThemeProviderImpl @Inject constructor(
    private val themeRepository: ThemeRepository
) : ThemeProvider {
    
    override fun getIconPaths(): Flow<Map<String, String>> {
        return themeRepository.getCurrentTheme().map { theme ->
            theme.iconPack.icons
        }
    }
    
    override fun getWallpaperUri(): Flow<String?> {
        return themeRepository.getCurrentTheme().map { theme ->
            theme.wallpaperUri
        }
    }

    override fun getDockBackgroundColor(): Flow<Int> {
        return themeRepository.getCurrentTheme().map { theme ->
            theme.desktopStyle.dockBackgroundColor
        }
    }

    override fun getDockBackgroundAlpha(): Flow<Int> {
        return themeRepository.getCurrentTheme().map { theme ->
            theme.desktopStyle.dockBackgroundAlpha
        }
    }

    override fun getDockCornerRadiusDp(): Flow<Float> {
        return themeRepository.getCurrentTheme().map { theme ->
            theme.desktopStyle.dockCornerRadiusDp
        }
    }

    override fun getAppLabelColor(): Flow<Int> {
        return themeRepository.getCurrentTheme().map { theme ->
            theme.desktopStyle.appLabelColor
        }
    }

    override fun getAppLabelShadowColor(): Flow<Int> {
        return themeRepository.getCurrentTheme().map { theme ->
            theme.desktopStyle.appLabelShadowColor
        }
    }

    override fun isAppLabelShadowEnabled(): Flow<Boolean> {
        return themeRepository.getCurrentTheme().map { theme ->
            theme.desktopStyle.isAppLabelShadowEnabled
        }
    }
}