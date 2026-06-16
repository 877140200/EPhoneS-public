package com.susking.ephone_s.desktop.api

import android.content.Context

/**
 * Desktop 导航接口
 * 用于解耦 Desktop 模块与其他功能模块之间的导航依赖
 */
interface DesktopNavigator {
    /**
     * 启动 QQ 应用
     */
    fun launchQq()

    /**
     * 启动世界书集应用
     */
    fun launchWorldBook()

    /**
     * 启动相册应用
     */
    fun launchAlbum()

    /**
     * 启动主题应用
     */
    fun launchTheme()

    /**
     * 启动设置应用
     */
    fun launchSettings()

    /**
     * 启动酒馆记录应用
     * @param context Android 上下文
     */
    fun launchCloudDreams(context: Context)

    /**
     * 启动 CPhone 应用
     */
    fun launchCPhone()

    /**
     * 启动预设应用
     */
    fun launchPreset()

    /**
     * 启动商城应用
     */
    fun launchShopping()
    
    /**
     * 启动支付宝应用
     */
    fun launchAlipay()

    /**
     * 启动关系图应用
     */
    fun launchEventGraph()

    /**
     * 启动课程表应用
     */
    fun launchSchedule()

    /**
     * 启动酒馆应用（SillyTavern WebView 入口）
     */
    fun launchTavern()

    /**
     * 启动健康应用（AI 健康关怀，读取 Health Connect 数据）
     */
    fun launchHealth()
}