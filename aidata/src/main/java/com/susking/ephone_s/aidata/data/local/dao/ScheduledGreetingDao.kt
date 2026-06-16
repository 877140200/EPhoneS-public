package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.*
import com.susking.ephone_s.aidata.data.local.entity.ScheduledGreetingEntity
import kotlinx.coroutines.flow.Flow

/**
 * 预发送祝福数据访问对象
 */
@Dao
interface ScheduledGreetingDao {
    
    /**
     * 插入一条预发送祝福记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGreeting(greeting: ScheduledGreetingEntity): Long

    /**
     * 批量插入预发送祝福记录(用于导入备份)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGreetings(greetings: List<ScheduledGreetingEntity>): List<Long>

    /**
     * 更新预发送祝福记录
     */
    @Update
    suspend fun updateGreeting(greeting: ScheduledGreetingEntity)
    
    /**
     * 删除预发送祝福记录
     */
    @Delete
    suspend fun deleteGreeting(greeting: ScheduledGreetingEntity)
    
    /**
     * 根据ID获取预发送祝福记录
     */
    @Query("SELECT * FROM scheduled_greetings WHERE id = :greetingId")
    suspend fun getGreetingById(greetingId: Long): ScheduledGreetingEntity?
    
    /**
     * 获取某个联系人的所有待发送祝福
     */
    @Query("SELECT * FROM scheduled_greetings WHERE contactId = :contactId AND status = 'pending' ORDER BY scheduledTime ASC")
    fun getPendingGreetingsByContact(contactId: String): Flow<List<ScheduledGreetingEntity>>
    
    /**
     * 获取所有待发送的祝福（用于定时任务检查）
     */
    @Query("SELECT * FROM scheduled_greetings WHERE status = 'pending' ORDER BY scheduledTime ASC")
    suspend fun getAllPendingGreetings(): List<ScheduledGreetingEntity>

    /**
     * 同步获取所有祝福记录(用于导出备份,包括所有状态)
     */
    @Query("SELECT * FROM scheduled_greetings ORDER BY scheduledTime DESC")
    suspend fun getAllGreetingsList(): List<ScheduledGreetingEntity>

    /**
     * 获取指定时间范围内需要发送的祝福
     */
    @Query("SELECT * FROM scheduled_greetings WHERE status = 'pending' AND scheduledTime <= :maxTime ORDER BY scheduledTime ASC")
    suspend fun getGreetingsDueBy(maxTime: Long): List<ScheduledGreetingEntity>
    
    /**
     * 检查某个联系人是否已经有指定类型和时间的祝福
     */
    @Query("SELECT * FROM scheduled_greetings WHERE contactId = :contactId AND greetingType = :greetingType AND festivalYear = :year AND festivalMonth = :month AND festivalDay = :day AND status = 'pending'")
    suspend fun findExistingGreeting(
        contactId: String,
        greetingType: String,
        year: Int,
        month: Int,
        day: Int
    ): ScheduledGreetingEntity?
    
    /**
     * 标记祝福为已发送
     */
    @Query("UPDATE scheduled_greetings SET status = 'sent', sentTime = :sentTime WHERE id = :greetingId")
    suspend fun markAsSent(greetingId: Long, sentTime: Long)
    
    /**
     * 标记祝福为已取消
     */
    @Query("UPDATE scheduled_greetings SET status = 'cancelled' WHERE id = :greetingId")
    suspend fun markAsCancelled(greetingId: Long)
    
    /**
     * 删除已发送超过指定天数的祝福记录（清理历史数据）
     */
    @Query("DELETE FROM scheduled_greetings WHERE status = 'sent' AND sentTime < :beforeTime")
    suspend fun deleteOldSentGreetings(beforeTime: Long)
    
    /**
     * 获取某个联系人的所有祝福历史
     */
    @Query("SELECT * FROM scheduled_greetings WHERE contactId = :contactId ORDER BY scheduledTime DESC")
    fun getAllGreetingsByContact(contactId: String): Flow<List<ScheduledGreetingEntity>>
}