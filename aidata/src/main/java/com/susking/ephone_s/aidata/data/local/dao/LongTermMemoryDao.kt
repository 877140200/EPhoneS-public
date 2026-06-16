package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import kotlinx.coroutines.flow.Flow

@Dao
interface LongTermMemoryDao {
    @Query("""
        SELECT * FROM long_term_memories
        WHERE contactId = :contactId
            AND NOT (
                memoryType = 'EVENT'
                AND isVectorized = 1
                AND sourceModule IN (
                    'LongTermMemoryStructuredEventCompatibility',
                    'StructuredMemoryEventManual',
                    'StructuredMemoryEventExtraction',
                    'AutoStructuredEventExtraction',
                    'LongTermMemory'
                )
            )
        ORDER BY timestamp DESC
    """)
    fun getMemoriesForContact(contactId: String): Flow<List<LongTermMemory>>

    @Query("SELECT * FROM long_term_memories WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: String): LongTermMemory?

    @Query("SELECT * FROM long_term_memories WHERE contactId = :contactId AND id IN (:ids) ORDER BY timestamp DESC")
    suspend fun getMemoriesByIds(contactId: String, ids: List<String>): List<LongTermMemory>

    @Query("SELECT * FROM long_term_memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<LongTermMemory>>

    @Query("SELECT * FROM long_term_memories WHERE contactId = :contactId AND timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp ASC")
    suspend fun getMemoriesInTimeRange(contactId: String, startTime: Long, endTime: Long): List<LongTermMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: LongTermMemory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<LongTermMemory>)

    @Update
    suspend fun update(memory: LongTermMemory)

    @Delete
    suspend fun delete(memory: LongTermMemory)

    @Query("DELETE FROM long_term_memories WHERE contactId = :contactId")
    suspend fun deleteMemoriesForContact(contactId: String)

    @Query("DELETE FROM long_term_memories")
    suspend fun clearAll()
    
    /**
     * 获取指定联系人的最新旧原子事件纪念时间戳。
     * 结构化事件的向量外键兼容记录会写入 long_term_memories，必须排除，避免结构化提取成功后改变原子事件纪念视图和下次提取起点。
     */
    @Query("""
        SELECT MAX(timestamp) FROM long_term_memories
        WHERE contactId = :contactId
            AND NOT (
                memoryType = 'EVENT'
                AND isVectorized = 1
                AND sourceModule IN (
                    'LongTermMemoryStructuredEventCompatibility',
                    'StructuredMemoryEventManual',
                    'StructuredMemoryEventExtraction',
                    'AutoStructuredEventExtraction',
                    'LongTermMemory'
                )
            )
    """)
    suspend fun getLatestMemoryTimestamp(contactId: String): Long?

    /**
     * 使用 FTS 表进行全文搜索，返回匹配的记忆ID列表。
     *
     * @param query 搜索查询字符串。FTS5 支持丰富的查询语法。
     * @return 匹配的 LongTermMemory 的 ID 列表。
     */
    @Query("""
        SELECT long_term_memories.id FROM long_term_memories
        INNER JOIN long_term_memories_fts ON long_term_memories.rowid = long_term_memories_fts.rowid
        WHERE long_term_memories.contactId = :contactId AND long_term_memories_fts MATCH :query
        ORDER BY long_term_memories.timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchMemoriesByContent(contactId: String, query: String, limit: Int): List<String>

    @Query("SELECT * FROM long_term_memories WHERE contactId = :contactId AND isVectorized = 0 ORDER BY importanceScore DESC, timestamp DESC")
    suspend fun getUnvectorizedMemoriesForContact(contactId: String): List<LongTermMemory>

    @Query("SELECT DISTINCT contactId FROM long_term_memories")
    suspend fun getContactIdsWithMemories(): List<String>
}