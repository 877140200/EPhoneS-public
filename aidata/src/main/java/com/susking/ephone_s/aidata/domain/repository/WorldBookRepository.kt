package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import kotlinx.coroutines.flow.Flow

interface WorldBookRepository {
    fun getAllWorldBooks(): Flow<List<WorldBookEntity>>
    suspend fun insertWorldBook(worldBook: WorldBookEntity): Long
    suspend fun updateWorldBook(worldBook: WorldBookEntity)

    suspend fun updateWorldBookOrder(worldBookId: Long, newOrder: Int)

    suspend fun deleteWorldBook(worldBook: WorldBookEntity)
    suspend fun getWorldBookById(worldBookId: Long): WorldBookEntity?
    suspend fun getWorldBookById(category: String): WorldBookEntity? // 根据分类名称获取世界书
    suspend fun getSystemWorldBook(): WorldBookEntity?
    suspend fun getAllWorldBookIds(): List<Long>
}