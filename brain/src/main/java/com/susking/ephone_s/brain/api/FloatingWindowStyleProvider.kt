package com.susking.ephone_s.brain.api

/**
 * 悬浮窗样式提供者接口，用于解耦brain模块与主题系统的依赖。
 * app模块需要实现此接口并注入到brain模块。
 */
interface FloatingWindowStyleProvider {
    
    /**
     * 获取悬浮球默认状态图片。
     */
    fun getDefaultImageUri(): String

    /**
     * 获取悬浮球拖拽状态图片。
     */
    fun getDraggingImageUri(): String

    /**
     * 获取悬浮球贴边停靠状态图片。
     */
    fun getDockedImageUri(): String

    /**
     * 获取悬浮窗背景颜色。
     */
    fun getBackgroundColor(): Int
    
    /**
     * 获取悬浮窗文字颜色。
     */
    fun getTextColor(): Int
    
    /**
     * 获取悬浮窗强调色。
     */
    fun getAccentColor(): Int
    
    /**
     * 获取悬浮窗卡片背景颜色。
     */
    fun getCardBackgroundColor(): Int
}