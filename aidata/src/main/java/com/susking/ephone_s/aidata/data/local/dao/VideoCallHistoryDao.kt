package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 视频通话历史记录DAO
 * 提供对视频通话历史记录的数据库操作
 */
@Dao
interface VideoCallHistoryDao {
    
    /**
     * 插入新的视频通话历史记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoCallHistory(history: VideoCallHistoryEntity): Long
    
    /**
     * 批量插入视频通话历史记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<VideoCallHistoryEntity>)
    
    /**
     * 更新视频通话历史记录
     */
    @Update
    suspend fun updateVideoCallHistory(history: VideoCallHistoryEntity)
    
    /**
     * 删除视频通话历史记录
     */
    @Delete
    suspend fun deleteVideoCallHistory(history: VideoCallHistoryEntity)
    
    /**
     * 根据ID删除视频通话历史记录
     */
    @Query("DELETE FROM video_call_history WHERE id = :historyId")
    suspend fun deleteVideoCallHistoryById(historyId: Long)
    
    /**
     * 根据联系人ID删除所有视频通话历史记录
     */
    @Query("DELETE FROM video_call_history WHERE contactId = :contactId")
    suspend fun deleteAllVideoCallHistoryByContactId(contactId: String)
    
    /**
     * 删除所有视频通话历史记录
     */
    @Query("DELETE FROM video_call_history")
    suspend fun deleteAllVideoCallHistory()
    
    /**
     * 根据ID获取视频通话历史记录
     */
    @Query("SELECT * FROM video_call_history WHERE id = :historyId")
    suspend fun getVideoCallHistoryById(historyId: Long): VideoCallHistoryEntity?
    
    /**
     * 获取指定联系人的所有视频通话历史记录（响应式）
     */
    @Query("SELECT * FROM video_call_history WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getVideoCallHistoryByContactId(contactId: String): Flow<List<VideoCallHistoryEntity>>
    
    /**
     * 获取指定联系人的所有视频通话历史记录（非响应式）
     */
    @Query("SELECT * FROM video_call_history WHERE contactId = :contactId ORDER BY timestamp DESC")
    suspend fun getVideoCallHistoryByContactIdSuspend(contactId: String): List<VideoCallHistoryEntity>
    
    /**
     * 获取所有视频通话历史记录（响应式）
     */
    @Query("SELECT * FROM video_call_history ORDER BY timestamp DESC")
    fun getAllVideoCallHistory(): Flow<List<VideoCallHistoryEntity>>
    
    /**
     * 获取所有视频通话历史记录（非响应式）
     */
    @Query("SELECT * FROM video_call_history ORDER BY timestamp DESC")
    suspend fun getAllVideoCallHistorySuspend(): List<VideoCallHistoryEntity>
    
    /**
     * 获取指定联系人的视频通话历史记录数量
     */
    @Query("SELECT COUNT(*) FROM video_call_history WHERE contactId = :contactId")
    suspend fun getVideoCallHistoryCountByContactId(contactId: String): Int
    
    /**
     * 获取所有视频通话历史记录数量
     */
    @Query("SELECT COUNT(*) FROM video_call_history")
    suspend fun getTotalVideoCallHistoryCount(): Int
    
    /**
     * 获取所有进行中的通话记录
     * 用于应用启动时检查是否有未完成的通话需要恢复
     */
    @Query("SELECT * FROM video_call_history WHERE callStatus = 'in_progress' ORDER BY lastUpdateTime DESC")
    suspend fun getInProgressCalls(): List<VideoCallHistoryEntity>
    
    /**
     * 更新通话状态
     * @param id 通话记录ID
     * @param callStatus 新的通话状态
     * @param terminationReason 挂断原因（可选）
     * @param duration 通话时长（可选）
     */
    @Query("UPDATE video_call_history SET callStatus = :callStatus, terminationReason = COALESCE(:terminationReason, terminationReason), duration = COALESCE(:duration, duration) WHERE id = :id")
    suspend fun updateCallStatus(id: Long, callStatus: String, terminationReason: String? = null, duration: Long? = null)
    
    /**
     * 更新通话的完整信息（消息、时长、状态、更新时间）
     * @param id 通话记录ID
     * @param messages 消息列表（JSON字符串格式）
     * @param duration 通话时长
     * @param callStatus 通话状态
     * @param lastUpdateTime 最后更新时间
     */
    @Query("UPDATE video_call_history SET messages = :messages, duration = :duration, callStatus = :callStatus, lastUpdateTime = :lastUpdateTime WHERE id = :id")
    suspend fun updateVideoCallHistoryFields(id: Long, messages: String, duration: Long, callStatus: String, lastUpdateTime: Long)
}