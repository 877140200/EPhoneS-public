package com.susking.ephone_s.settings.api

/**
 * Settings 模块导航接口
 * 用于处理从 settings 界面跳转到其他模块的需求
 */
interface SettingsNavigator {
    /**
     * 从设置界面返回到主界面
     */
    fun navigateBack()
    
    /**
     * 显示 Toast 消息
     */
    fun showToast(message: String)
    
    /**
     * 显示错误对话框
     */
    fun showError(title: String, message: String)
    
    /**
     * 显示成功对话框
     */
    fun showSuccess(title: String, message: String)
}

/**
 * SettingsNavigator 的默认实现
 * 用于在 settings 模块独立运行时使用
 */
class DefaultSettingsNavigator : SettingsNavigator {
    override fun navigateBack() {
        // 默认实现为空，由使用方决定行为
    }
    
    override fun showToast(message: String) {
        // 默认实现为空，由使用方决定行为
    }
    
    override fun showError(title: String, message: String) {
        // 默认实现为空，由使用方决定行为
    }
    
    override fun showSuccess(title: String, message: String) {
        // 默认实现为空，由使用方决定行为
    }
}