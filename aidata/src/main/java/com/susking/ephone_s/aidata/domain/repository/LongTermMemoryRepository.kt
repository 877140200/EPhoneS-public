package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import kotlinx.coroutines.flow.Flow

/**
 * 长期记忆 Repository 接口
 */
interface LongTermMemoryRepository {
    
    /**
     * 获取指定联系人的所有长期记忆
     */
    fun getMemories(contactId: String): Flow<List<LongTermMemory>>
    
    /**
     * 获取所有长期记忆
     */
    fun getAllMemories(): Flow<List<LongTermMemory>>
    
    /**
     * 插入长期记忆
     */
    suspend fun addMemory(memory: LongTermMemory)

    /**
     * 更新长期记忆
     */
    suspend fun updateMemory(memory: LongTermMemory)
    
    /**
     * 删除长期记忆
     */
    suspend fun deleteMemory(memory: LongTermMemory)
    
    /**
     * 删除指定联系人的所有长期记忆
     */
    suspend fun deleteMemoriesForContact(contactId: String)
    
    /**
     * 获取指定联系人的最新记忆时间戳
     * 用于确定下次总结的起始位置
     * @return 最新记忆的时间戳，如果没有记忆则返回null
     */
    suspend fun getLatestMemoryTimestamp(contactId: String): Long?

}