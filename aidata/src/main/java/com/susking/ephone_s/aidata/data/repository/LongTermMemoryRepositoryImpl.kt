package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.repository.LongTermMemoryRepository
import kotlinx.coroutines.flow.Flow

/**
 * 长期记忆 Repository 实现
 */
class LongTermMemoryRepositoryImpl(
    private val memoryDao: com.susking.ephone_s.aidata.data.local.dao.LongTermMemoryDao
) : LongTermMemoryRepository {

    override fun getMemories(contactId: String): Flow<List<LongTermMemory>> {
        return memoryDao.getMemoriesForContact(contactId)
    }

    override fun getAllMemories(): Flow<List<LongTermMemory>> {
        return memoryDao.getAllMemories()
    }

    override suspend fun addMemory(memory: LongTermMemory) {
        memoryDao.insert(memory)
    }

    override suspend fun updateMemory(memory: LongTermMemory) {
        memoryDao.update(memory)
    }

    override suspend fun deleteMemory(memory: LongTermMemory) {
        memoryDao.delete(memory)
    }

    override suspend fun deleteMemoriesForContact(contactId: String) {
        memoryDao.deleteMemoriesForContact(contactId)
    }
    
    override suspend fun getLatestMemoryTimestamp(contactId: String): Long? {
        return memoryDao.getLatestMemoryTimestamp(contactId)
    }
}