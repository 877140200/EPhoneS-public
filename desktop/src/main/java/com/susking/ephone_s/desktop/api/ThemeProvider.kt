package com.susking.ephone_s.desktop.api

import kotlinx.coroutines.flow.Flow

/**
 * 主题提供者接口
 * desktop 模块通过此接口获取主题数据,由 app 模块实现
 */
interface ThemeProvider {
    /**
     * 获取当前主题的图标路径映射
     * @return Flow<Map<String, String>> 图标名称到路径的映射
     */
    fun getIconPaths(): Flow<Map<String, String>>
    
    /**
     * 获取当前主题的壁纸URI
     * @return Flow<String?> 壁纸的URI字符串
     */
    fun getWallpaperUri(): Flow<String?>

    /**
     * 获取 Dock 背景颜色。
     */
    fun getDockBackgroundColor(): Flow<Int>

    /**
     * 获取 Dock 背景透明度。
     */
    fun getDockBackgroundAlpha(): Flow<Int>

    /**
     * 获取 Dock 圆角半径，单位 dp。
     */
    fun getDockCornerRadiusDp(): Flow<Float>

    /**
     * 获取桌面应用名称文字颜色。
     */
    fun getAppLabelColor(): Flow<Int>

    /**
     * 获取桌面应用名称阴影颜色。
     */
    fun getAppLabelShadowColor(): Flow<Int>

    /**
     * 获取是否启用桌面应用名称阴影。
     */
    fun isAppLabelShadowEnabled(): Flow<Boolean>
}