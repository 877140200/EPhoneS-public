package com.susking.ephone_s.aidata.data.repository


import com.susking.ephone_s.aidata.data.local.dao.WorldBookEntryDao
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity
import com.susking.ephone_s.aidata.domain.repository.WorldBookEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WorldBookEntryRepositoryImpl(private val worldBookEntryDao: WorldBookEntryDao) : WorldBookEntryRepository {

    override fun getEntriesForWorldBook(worldBookId: Long): Flow<List<WorldBookEntryEntity>> {
        // 从DAO获取特定世界书的所有内容条目
        return worldBookEntryDao.getEntriesForWorldBook(worldBookId)
    }

    override suspend fun insertEntry(entry: WorldBookEntryEntity) {
        // 插入新的内容条目
        worldBookEntryDao.insertEntry(entry)
    }

    override suspend fun updateEntry(entry: WorldBookEntryEntity) {
        // 更新现有的内容条目
        worldBookEntryDao.updateEntry(entry)
    }

    override suspend fun deleteEntry(entry: WorldBookEntryEntity) {
        // 删除内容条目
        worldBookEntryDao.deleteEntry(entry)
    }

    override suspend fun updateEntries(entries: List<WorldBookEntryEntity>) {
        // 批量更新内容条目，用于拖拽排序
        worldBookEntryDao.updateEntries(entries)
    }

    override suspend fun getEntryByWorldBookIdAndName(worldBookId: Long, name: String): WorldBookEntryEntity? {
        return worldBookEntryDao.getEntryByWorldBookIdAndName(worldBookId, name)
    }
    
    override suspend fun getEnabledWorldBookPrompts(): List<String> = withContext(Dispatchers.IO) {
        val entries = worldBookEntryDao.getAllEnabledEntries()
        // 过滤掉系统世界书条目,只返回用户创建的条目内容
        entries.filter { entry -> !entry.isSystemEntry }
            .map { entry -> entry.content }
    }
}