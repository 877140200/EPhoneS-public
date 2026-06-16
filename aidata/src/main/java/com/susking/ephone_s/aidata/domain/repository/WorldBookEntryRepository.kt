package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity
import kotlinx.coroutines.flow.Flow

interface WorldBookEntryRepository {
    fun getEntriesForWorldBook(worldBookId: Long): Flow<List<WorldBookEntryEntity>>
    suspend fun insertEntry(entry: WorldBookEntryEntity)
    suspend fun updateEntry(entry: WorldBookEntryEntity)
    suspend fun deleteEntry(entry: WorldBookEntryEntity)
    suspend fun updateEntries(entries: List<WorldBookEntryEntity>)
    suspend fun getEntryByWorldBookIdAndName(worldBookId: Long, name: String): WorldBookEntryEntity?
    
    /**
     * 获取所有启用的世界书条目的提示词内容
     * 过滤掉系统条目,仅返回用户创建的条目
     * 用于构建AI提示词上下文
     */
    suspend fun getEnabledWorldBookPrompts(): List<String>
}