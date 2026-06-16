package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 世界观设定 Repository 接口
 */
interface WorldSettingRepository {
    
    /**
     * 获取所有世界书
     */
    fun getAllWorldBooks(): Flow<List<WorldBookEntity>>
    
    /**
     * 根据ID获取世界书
     */
    suspend fun getWorldBookById(id: Long): WorldBookEntity?
    
    /**
     * 获取系统世界书
     */
    suspend fun getSystemWorldBook(): WorldBookEntity?
    
    /**
     * 获取所有启用的世界书提示词
     * 返回所有启用条目的 content 列表
     */
    suspend fun getEnabledWorldBookPrompts(): List<String>
    
    /**
     * 获取指定世界书的所有条目
     */
    fun getEntriesForWorldBook(worldBookId: Long): Flow<List<WorldBookEntryEntity>>
    
    /**
     * 插入世界书
     */
    suspend fun insertWorldBook(worldBook: WorldBookEntity): Long
    
    /**
     * 更新世界书
     */
    suspend fun updateWorldBook(worldBook: WorldBookEntity)
    
    /**
     * 删除世界书
     */
    suspend fun deleteWorldBook(worldBook: WorldBookEntity)
    
    /**
     * 插入世界书条目
     */
    suspend fun insertWorldBookEntry(entry: WorldBookEntryEntity): Long
    
    /**
     * 更新世界书条目
     */
    suspend fun updateWorldBookEntry(entry: WorldBookEntryEntity)
    
    /**
     * 删除世界书条目
     */
    suspend fun deleteWorldBookEntry(entry: WorldBookEntryEntity)
}