package com.susking.ephone_s.brain.domain.repository

import com.susking.ephone_s.aidata.domain.model.AiActivity
import kotlinx.coroutines.flow.Flow

/**
 * Brain模块的仓库接口，定义AI活动数据的操作契约。
 */
interface BrainRepository {
    
    /**
     * 获取所有AI活动的Flow。
     */
    val allActivities: Flow<List<AiActivity>>
    
    /**
     * 记录一个AI活动。
     */
    suspend fun logActivity(activity: AiActivity)
    
    /**
     * 将所有AI活动标记为已读。
     */
    suspend fun markAllAsRead()
    
    /**
     * 清空所有AI活动。
     */
    suspend fun clearAll()
    
    /**
     * 将指定的AI活动标记为已振动。
     */
    suspend fun markAsVibrated(id: Long)
    
    /**
     * 保存悬浮球的位置。
     */
    fun saveFabPosition(x: Float, y: Float)
    
    /**
     * 获取保存的悬浮球位置。
     */
    fun getFabPosition(): Pair<Float, Float>?
    
    /**
     * 清除保存的悬浮球位置。
     */
    fun clearFabPosition()
    
    /**
     * 暂停所有后台任务（将WAITING状态改为STOP）。
     */
    suspend fun pauseWaitingBackgroundTasks()
    
    /**
     * 恢复所有后台任务（将STOP状态改为WAITING）。
     */
    suspend fun resumeStoppedBackgroundTasks()
    
    /**
     * 取消单个任务（将指定activityChainId的任务状态改为CANCELLED）。
     */
    suspend fun cancelTask(activityChainId: String)
    
    /**
     * 取消所有后台任务（将所有后台任务状态改为CANCELLED）。
     */
    suspend fun cancelAllBackgroundTasks()
}