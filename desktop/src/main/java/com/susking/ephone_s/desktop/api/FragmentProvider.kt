package com.susking.ephone_s.desktop.api

import androidx.fragment.app.Fragment

/**
 * Fragment 提供者接口
 * 用于 app 模块向 desktop 模块提供未模块化的 Fragment
 */
interface FragmentProvider {
    /**
     * 创建 QQ Fragment
     */
    fun createQqFragment(): Fragment
    
    /**
     * 创建世界书 Fragment
     */
    fun createWorldBookFragment(): Fragment
    
    /**
     * 创建主题 Fragment
     */
    fun createThemeFragment(): Fragment
    
    /**
     * 创建设置 Fragment
     */
    fun createSettingsFragment(): Fragment
    
    /**
     * 创建预设 Fragment (预留)
     */
    fun createPresetFragment(): Fragment?
    
    /**
     * 创建商城 Fragment
     */
    fun createShoppingFragment(): Fragment
    
    /**
     * 创建支付宝 Fragment
     */
    fun createAlipayFragment(): Fragment

    /**
     * 创建课程表 Fragment
     */
    fun createScheduleFragment(): Fragment
}