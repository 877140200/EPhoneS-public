package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.BackpackItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * 背包Repository接口
 * 
 * 定义背包数据的操作规范
 */
interface BackpackRepository {
    
    /**
     * 添加物品到背包
     */
    suspend fun addItem(item: BackpackItemEntity): Long
    
    /**
     * 更新物品信息
     */
    suspend fun updateItem(item: BackpackItemEntity)
    
    /**
     * 获取所有未丢弃的物品
     */
    fun getAllItems(): Flow<List<BackpackItemEntity>>
    
    /**
     * 根据ID获取物品
     */
    suspend fun getItemById(itemId: Long): BackpackItemEntity?
    
    /**
     * 根据订单ID获取物品
     */
    suspend fun getItemsByOrderId(orderId: Long): List<BackpackItemEntity>
    
    /**
     * 标记物品为已丢弃
     * @deprecated 使用recordItemOperation代替
     */
    suspend fun discardItem(itemId: Long)
    
    /**
     * 记录物品操作(丢弃/赠送/卖出)
     * @param itemId 物品ID
     * @param operationType 操作类型: "discarded"(丢弃), "gifted"(赠送), "sold"(卖出)
     */
    suspend fun recordItemOperation(itemId: Long, operationType: String)
    
    /**
     * 记录物品赠送操作
     * @param itemId 物品ID
     * @param recipientName 赠送目标名称
     */
    suspend fun recordGiftOperation(itemId: Long, recipientName: String)
    
    /**
     * 获取所有历史记录
     */
    fun getAllHistoryItems(): Flow<List<BackpackItemEntity>>
    
    /**
     * 删除物品
     */
    suspend fun deleteItem(itemId: Long)
    
    /**
     * 获取背包中物品总数
     */
    suspend fun getItemCount(): Int
    
    /**
     * 清空所有已丢弃的物品
     * @deprecated 使用clearAllHistory代替
     */
    suspend fun clearDiscardedItems()
    
    /**
     * 删除单条历史记录
     */
    suspend fun deleteHistoryItem(itemId: Long)
    
    /**
     * 清空所有历史记录
     */
    suspend fun clearAllHistory()
}