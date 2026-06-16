package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 视频通话历史记录Repository接口
 * 提供视频通话历史记录的数据访问方法
 */
interface VideoCallHistoryRepository {
    
    /**
     * 插入新的视频通话历史记录
     * @return 插入记录的ID
     */
    suspend fun insertVideoCallHistory(history: VideoCallHistoryEntity): Long
    
    /**
     * 更新视频通话历史记录
     */
    suspend fun updateVideoCallHistory(history: VideoCallHistoryEntity)
    
    /**
     * 删除视频通话历史记录
     */
    suspend fun deleteVideoCallHistory(history: VideoCallHistoryEntity)
    
    /**
     * 根据ID删除视频通话历史记录
     */
    suspend fun deleteVideoCallHistoryById(historyId: Long)
    
    /**
     * 根据联系人ID删除所有视频通话历史记录
     */
    suspend fun deleteAllVideoCallHistoryByContactId(contactId: String)
    
    /**
     * 删除所有视频通话历史记录
     */
    suspend fun deleteAllVideoCallHistory()
    
    /**
     * 根据ID获取视频通话历史记录
     */
    suspend fun getVideoCallHistoryById(historyId: Long): VideoCallHistoryEntity?
    
    /**
     * 获取指定联系人的所有视频通话历史记录（响应式）
     */
    fun getVideoCallHistoryByContactId(contactId: String): Flow<List<VideoCallHistoryEntity>>
    
    /**
     * 获取指定联系人的所有视频通话历史记录（非响应式）
     */
    suspend fun getVideoCallHistoryByContactIdSuspend(contactId: String): List<VideoCallHistoryEntity>
    
    /**
     * 获取所有视频通话历史记录（响应式）
     */
    fun getAllVideoCallHistory(): Flow<List<VideoCallHistoryEntity>>
    
    /**
     * 获取所有视频通话历史记录（非响应式）
     */
    suspend fun getAllVideoCallHistorySuspend(): List<VideoCallHistoryEntity>
    
    /**
     * 获取指定联系人的视频通话历史记录数量
     */
    suspend fun getVideoCallHistoryCountByContactId(contactId: String): Int
    
    /**
     * 获取所有视频通话历史记录数量
     */
    suspend fun getTotalVideoCallHistoryCount(): Int
    
    /**
     * 获取所有进行中的通话记录
     * 用于应用启动时检查是否有未完成的通话需要恢复
     */
    suspend fun getInProgressCalls(): List<VideoCallHistoryEntity>
    
    /**
     * 更新通话状态
     * @param id 通话记录ID
     * @param callStatus 新的通话状态
     * @param terminationReason 挂断原因（可选）
     * @param duration 通话时长（可选）
     */
    suspend fun updateCallStatus(id: Long, callStatus: String, terminationReason: String? = null, duration: Long? = null)
    
    /**
     * 更新通话的完整信息（消息、时长、状态、更新时间）
     * @param id 通话记录ID
     * @param messages 消息列表
     * @param duration 通话时长
     * @param callStatus 通话状态
     * @param lastUpdateTime 最后更新时间
     */
    suspend fun updateVideoCallHistoryFields(id: Long, messages: List<com.susking.ephone_s.aidata.data.local.entity.VideoCallMessageEntity>, duration: Long, callStatus: String, lastUpdateTime: Long)
}