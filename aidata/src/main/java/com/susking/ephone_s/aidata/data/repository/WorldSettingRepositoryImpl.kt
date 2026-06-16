package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity
import com.susking.ephone_s.aidata.domain.repository.WorldSettingRepository
import kotlinx.coroutines.flow.Flow

/**
 * 世界观设定 Repository 实现
 */
class WorldSettingRepositoryImpl(
    private val database: AiDataDatabase
) : WorldSettingRepository {
    private val worldBookDao = database.worldBookDao()
    private val entryDao = database.worldBookEntryDao()

    override fun getAllWorldBooks(): Flow<List<WorldBookEntity>> {
        return worldBookDao.getAllWorldBooks()
    }

    override suspend fun getWorldBookById(id: Long): WorldBookEntity? {
        return worldBookDao.getWorldBookById(id)
    }

    override suspend fun getSystemWorldBook(): WorldBookEntity? {
        return worldBookDao.getSystemWorldBook()
    }

    override suspend fun getEnabledWorldBookPrompts(): List<String> {
        val enabledEntries = entryDao.getAllEnabledEntries()
        // 过滤掉系统世界书条目(破限、文风),只返回用户创建的条目内容
        return enabledEntries.filter { !it.isSystemEntry }.map { it.content }
    }

    override fun getEntriesForWorldBook(worldBookId: Long): Flow<List<WorldBookEntryEntity>> {
        return entryDao.getEntriesForWorldBook(worldBookId)
    }

    override suspend fun insertWorldBook(worldBook: WorldBookEntity): Long {
        return worldBookDao.insertWorldBook(worldBook)
    }

    override suspend fun updateWorldBook(worldBook: WorldBookEntity) {
        worldBookDao.updateWorldBook(worldBook)
    }

    override suspend fun deleteWorldBook(worldBook: WorldBookEntity) {
        worldBookDao.deleteWorldBook(worldBook)
    }

    override suspend fun insertWorldBookEntry(entry: WorldBookEntryEntity): Long {
        return entryDao.insertEntry(entry)
    }

    override suspend fun updateWorldBookEntry(entry: WorldBookEntryEntity) {
        entryDao.updateEntry(entry)
    }

    override suspend fun deleteWorldBookEntry(entry: WorldBookEntryEntity) {
        entryDao.deleteEntry(entry)
    }
}