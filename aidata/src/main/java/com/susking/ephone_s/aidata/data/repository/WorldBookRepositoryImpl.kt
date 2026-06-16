package com.susking.ephone_s.aidata.data.repository


import com.susking.ephone_s.aidata.data.local.dao.WorldBookDao
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import kotlinx.coroutines.flow.Flow

class WorldBookRepositoryImpl(private val worldBookDao: WorldBookDao) : WorldBookRepository {

    override fun getAllWorldBooks(): Flow<List<WorldBookEntity>> {
        // 从DAO获取所有世界书列表
        return worldBookDao.getAllWorldBooks()
    }

    override suspend fun insertWorldBook(worldBook: WorldBookEntity): Long {
        // 插入新的世界书到数据库
        return worldBookDao.insertWorldBook(worldBook)
    }

    override suspend fun updateWorldBook(worldBook: WorldBookEntity) {
        // 更新现有世界书
        worldBookDao.updateWorldBook(worldBook)
    }

    override suspend fun deleteWorldBook(worldBook: WorldBookEntity) {
        // 从数据库中删除世界书
        worldBookDao.deleteWorldBook(worldBook)
    }

    override suspend fun getWorldBookById(worldBookId: Long): WorldBookEntity? {
        // 根据ID获取单个世界书
        return worldBookDao.getWorldBookById(worldBookId)
    }

    override suspend fun updateWorldBookOrder(worldBookId: Long, newOrder: Int) {
        worldBookDao.updateWorldBookOrder(worldBookId, newOrder)
    }

    override suspend fun getWorldBookById(category: String): WorldBookEntity? {
        return worldBookDao.getWorldBookByCategory(category)
    }

    override suspend fun getSystemWorldBook(): WorldBookEntity? {
        return worldBookDao.getSystemWorldBook()
    }
    
    override suspend fun getAllWorldBookIds(): List<Long> {
        return worldBookDao.getAllWorldBookIds()
    }
}