package com.susking.ephone_s.features.theme.domain.model

/**
 * 代表一套完整的小手机主题。
 *
 * 主题是外观系统的聚合根，负责把桌面壁纸、图标包、桌面样式、Brain 悬浮窗样式、
 * Material 主题色与日夜模式组合成一个可保存、可切换、可导入导出的整体。
 */
data class Theme(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val sourceType: ThemeSourceType,
    val previewUri: String,
    val wallpaper: WallpaperConfig,
    val iconPack: IconPack,
    val desktopStyle: DesktopStyle,
    val floatingWindowStyle: FloatingWindowStyle,
    val colorScheme: ThemeColorScheme,
    val nightMode: ThemeNightMode,
    val version: Int,
    val updatedAt: Long
) {
    /**
     * 兼容旧桌面通路的便捷属性，避免调用方直接依赖 WallpaperConfig 的内部结构。
     */
    val wallpaperUri: String
        get() = wallpaper.uri
}

/**
 * 标记主题来源，用于区分内置主题和用户自定义主题。
 */
enum class ThemeSourceType {
    BUILT_IN,
    CUSTOM
}

/**
 * 定义主题要求的小手机日夜模式。
 */
enum class ThemeNightMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK
}

/**
 * 描述桌面壁纸显示方式。
 */
data class WallpaperConfig(
    val uri: String,
    val blurRadius: Int,
    val dimAmount: Float,
    val scaleType: WallpaperScaleType
)

/**
 * 壁纸缩放模式，当前实现优先支持 centerCrop，保留其他枚举方便后续扩展。
 */
enum class WallpaperScaleType {
    CENTER_CROP,
    FIT_CENTER
}

/**
 * 描述桌面 Dock、页面指示器和应用文字的视觉样式。
 */
data class DesktopStyle(
    val dockBackgroundColor: Int,
    val dockBackgroundAlpha: Int,
    val dockCornerRadiusDp: Float,
    val pageIndicatorSelectedColor: Int,
    val pageIndicatorUnselectedColor: Int,
    val appLabelColor: Int,
    val appLabelShadowColor: Int,
    val isAppLabelShadowEnabled: Boolean
)

/**
 * 描述小手机重点界面可复用的主题色。
 */
data class ThemeColorScheme(
    val primaryColor: Int,
    val onPrimaryColor: Int,
    val primaryContainerColor: Int,
    val onPrimaryContainerColor: Int,
    val secondaryColor: Int,
    val backgroundColor: Int,
    val surfaceColor: Int,
    val onSurfaceColor: Int,
    val errorColor: Int
)