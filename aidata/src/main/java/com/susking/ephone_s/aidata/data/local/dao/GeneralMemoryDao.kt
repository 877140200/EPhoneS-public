package com.susking.ephone_s.aidata.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity

/**
 * 通用回忆数据访问对象
 */
@Dao
interface GeneralMemoryDao {
    
    /**
     * 插入一条回忆记录
     */
    @Insert
    suspend fun insert(memory: GeneralMemoryEntity)

    /**
     * 批量插入回忆记录(用于导入备份)
     */
    @Insert
    suspend fun insertAll(memories: List<GeneralMemoryEntity>)

    /**
     * 更新一条回忆记录
     */
    @Update
    suspend fun update(memory: GeneralMemoryEntity)
    
    /**
     * 删除一条回忆记录
     */
    @Delete
    suspend fun delete(memory: GeneralMemoryEntity)
    
    /**
     * 获取所有回忆记录（LiveData）
     */
    @Query("SELECT * FROM general_memories ORDER BY createdDate DESC")
    fun getAllMemories(): LiveData<List<GeneralMemoryEntity>>

    /**
     * 根据联系人ID获取回忆记录（LiveData）。
     */
    @Query("SELECT * FROM general_memories WHERE contactId = :contactId ORDER BY createdDate DESC")
    fun getMemoriesByContactIdLiveData(contactId: String): LiveData<List<GeneralMemoryEntity>>
    
    /**
     * 获取所有回忆记录（挂起函数）
     */
    @Query("SELECT * FROM general_memories ORDER BY createdDate DESC")
    suspend fun getAllMemoriesSuspend(): List<GeneralMemoryEntity>
    
    /**
     * 根据联系人ID获取回忆记录
     */
    @Query("SELECT * FROM general_memories WHERE contactId = :contactId ORDER BY createdDate DESC")
    suspend fun getMemoriesByContactId(contactId: String): List<GeneralMemoryEntity>
}