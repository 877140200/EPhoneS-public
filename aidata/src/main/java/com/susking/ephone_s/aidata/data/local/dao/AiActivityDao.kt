package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.AiActivityEntity
import kotlinx.coroutines.flow.Flow

/**
 * AI 活动日志的数据库访问对象 (DAO)。
 */
@Dao
interface AiActivityDao {

    /**
     * 插入一个新的 AI 活动。
     * @param activity 要插入的活动实体。
     */
    @Insert
    suspend fun insertActivity(activity: AiActivityEntity)

    /**
     * 更新一个已存在的 AI 活动。
     * @param activity 要更新的活动实体。
     */
    @Update
    suspend fun updateActivity(activity: AiActivityEntity)

    /**
     * 根据 activityChainId 查找一个 AI 活动。
     * @param chainId 要查找的 chain ID。
     * @return 匹配的活动实体，如果不存在则为 null。
     */
    @Query("SELECT * FROM ai_activities WHERE activityChainId = :chainId LIMIT 1")
    suspend fun findByChainId(chainId: String): AiActivityEntity?

    /**
     * 获取所有 AI 活动，按时间戳降序排列。
     * 使用 @Transaction 确保查询的原子性，避免游标越界问题。
     * @return 一个 Flow，持续发出最新的活动列表。
     */
    @Transaction
    @Query("SELECT * FROM ai_activities ORDER BY timestamp DESC")
    fun getAllActivities(): Flow<List<AiActivityEntity>>
    
    /**
     * 同步获取所有AI活动(用于恢复时重新调度)
     */
    @Query("SELECT * FROM ai_activities ORDER BY timestamp DESC")
    suspend fun getAllActivitiesSync(): List<AiActivityEntity>

    /**
     * 删除所有 AI 活动。
     */
    @Query("DELETE FROM ai_activities")
    suspend fun clearAll()

    /**
     * 将所有活动条目的 isRead 状态更新为 true。
     */
    @Query("UPDATE ai_activities SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    /**
     * 将单个活动条目的 hasVibrated 状态更新为 true。
     * @param id 要更新的活动的 ID。
     */
    @Query("UPDATE ai_activities SET hasVibrated = 1 WHERE id = :id")
    suspend fun markAsVibrated(id: Long)
    
    /**
     * 删除最旧的活动，直到数量达到指定的限制。
     * @param limit 要保留的最大活动数量。
     */
    @Query("DELETE FROM ai_activities WHERE id NOT IN (SELECT id FROM ai_activities ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trim(limit: Int)
    
    /**
     * 将所有后台任务中WAITING和PROCESSING状态更新为STOP状态
     */
    @Query("UPDATE ai_activities SET status = 'STOP' WHERE isBackgroundTask = 1 AND (status = 'WAITING' OR status = 'PROCESSING')")
    suspend fun pauseWaitingBackgroundTasks()
    
    /**
     * 将所有后台任务中STOP状态更新为WAITING状态
     */
    @Query("UPDATE ai_activities SET status = 'WAITING' WHERE isBackgroundTask = 1 AND status = 'STOP'")
    suspend fun resumeStoppedBackgroundTasks()
    
    /**
     * 取消单个任务（将指定activityChainId的任务状态改为CANCELLED）
     */
    @Query("UPDATE ai_activities SET status = 'CANCELLED' WHERE activityChainId = :activityChainId")
    suspend fun cancelTask(activityChainId: String)
    
    /**
     * 取消所有后台任务（将所有后台任务状态改为CANCELLED）
     */
    @Query("UPDATE ai_activities SET status = 'CANCELLED' WHERE isBackgroundTask = 1 AND status != 'CANCELLED'")
    suspend fun cancelAllBackgroundTasks()
}