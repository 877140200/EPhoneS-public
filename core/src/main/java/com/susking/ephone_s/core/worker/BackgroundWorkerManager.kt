package com.susking.ephone_s.core.worker

/**
 * 后台工作器管理器接口
 * 
 * 用于管理应用的后台任务，特别是AI相关的周期性后台活动。
 * 这个接口定义在 core 模块，允许其他模块（如 settings）通过依赖注入使用，
 * 而不需要直接依赖 app 模块。
 */
interface BackgroundWorkerManager {
    
    /**
     * 配置AI后台活动工作器
     * 
     * 根据当前设置状态，启动、停止或重新配置后台AI任务。
     * - 如果后台活动已启用，将根据设置的间隔时间配置周期性任务
     * - 如果后台活动已禁用，将取消所有后台任务
     * - 使用 REPLACE 策略，每次调用都会重置计时器
     */
    fun setupAiBackgroundWorker()
}