package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.BackpackItemDao
import com.susking.ephone_s.aidata.data.local.entity.BackpackItemEntity
import com.susking.ephone_s.aidata.domain.repository.BackpackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 背包Repository实现类
 * 
 * 实现背包数据的具体操作
 */
@Singleton
class BackpackRepositoryImpl @Inject constructor(
    private val backpackItemDao: BackpackItemDao
) : BackpackRepository {
    
    override suspend fun addItem(item: BackpackItemEntity): Long {
        return backpackItemDao.insertItem(item)
    }
    
    override suspend fun updateItem(item: BackpackItemEntity) {
        backpackItemDao.updateItem(item)
    }
    
    override fun getAllItems(): Flow<List<BackpackItemEntity>> {
        return backpackItemDao.getAllItems()
    }
    
    override suspend fun getItemById(itemId: Long): BackpackItemEntity? {
        return backpackItemDao.getItemById(itemId)
    }
    
    override suspend fun getItemsByOrderId(orderId: Long): List<BackpackItemEntity> {
        return backpackItemDao.getItemsByOrderId(orderId)
    }
    
    override suspend fun discardItem(itemId: Long) {
        backpackItemDao.discardItem(itemId)
    }
    
    override suspend fun recordItemOperation(itemId: Long, operationType: String) {
        val operationTime = System.currentTimeMillis()
        backpackItemDao.recordOperation(itemId, operationType, operationTime)
    }
    
    override suspend fun recordGiftOperation(itemId: Long, recipientName: String) {
        val operationTime = System.currentTimeMillis()
        backpackItemDao.recordGiftOperation(itemId, operationTime, recipientName)
    }
    
    override fun getAllHistoryItems(): Flow<List<BackpackItemEntity>> {
        return backpackItemDao.getAllHistoryItems()
    }
    
    override suspend fun deleteItem(itemId: Long) {
        backpackItemDao.deleteItem(itemId)
    }
    
    override suspend fun getItemCount(): Int {
        return backpackItemDao.getItemCount()
    }
    
    override suspend fun clearDiscardedItems() {
        backpackItemDao.clearDiscardedItems()
    }
    
    override suspend fun deleteHistoryItem(itemId: Long) {
        backpackItemDao.deleteHistoryItem(itemId)
    }
    
    override suspend fun clearAllHistory() {
        backpackItemDao.clearAllHistory()
    }
}