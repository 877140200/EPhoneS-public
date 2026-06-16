package com.susking.ephone_s.aidata.domain.repository

import androidx.lifecycle.LiveData
import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity

/**
 * 记忆（约会和回忆）Repository 接口
 */
interface MemoriesRepository {

    /**
     * 获取所有约会记录（LiveData）
     */
    val allAppointments: LiveData<List<AppointmentEntity>>

    /**
     * 插入约会记录
     */
    suspend fun insert(appointment: AppointmentEntity)
    
    /**
     * 更新约会记录
     */
    suspend fun update(appointment: AppointmentEntity)
    
    /**
     * 删除约会记录
     */
    suspend fun delete(appointment: AppointmentEntity)

    /**
     * 获取所有约会记录（挂起函数）
     */
    suspend fun getAllAppointmentsSuspend(): List<AppointmentEntity>

    /**
     * 根据联系人ID获取约定倒计时（LiveData）。
     */
    fun getAppointmentsByContactId(contactId: String): LiveData<List<AppointmentEntity>>

    /**
     * 根据联系人ID获取约定倒计时（挂起函数）。
     */
    suspend fun getAppointmentsByContactIdSuspend(contactId: String): List<AppointmentEntity>
    
    /**
     * 获取所有收藏消息（用于导出）
     */
    suspend fun getAllFavoriteMessages(): List<FavoriteMessageEntity>
    
    /**
     * 插入回忆记录
     */
    suspend fun insertMemory(memory: GeneralMemoryEntity)
    
    /**
     * 更新回忆记录
     */
    suspend fun updateMemory(memory: GeneralMemoryEntity)
    
    /**
     * 删除回忆记录
     */
    suspend fun deleteMemory(memory: GeneralMemoryEntity)
    
    /**
     * 获取所有回忆记录（LiveData）
     */
    fun getAllMemories(): LiveData<List<GeneralMemoryEntity>>

    /**
     * 根据联系人ID获取珍藏回忆（LiveData）。
     */
    fun getMemoriesByContactId(contactId: String): LiveData<List<GeneralMemoryEntity>>
    
    /**
     * 获取所有回忆记录（挂起函数）
     */
    suspend fun getAllMemoriesSuspend(): List<GeneralMemoryEntity>

    /**
     * 根据联系人ID获取珍藏回忆（挂起函数）。
     */
    suspend fun getMemoriesByContactIdSuspend(contactId: String): List<GeneralMemoryEntity>
}