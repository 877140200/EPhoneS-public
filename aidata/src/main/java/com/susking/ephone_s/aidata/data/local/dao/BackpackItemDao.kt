package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.BackpackItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * 背包物品数据访问对象
 * 
 * 提供对背包物品的增删改查操作
 */
@Dao
interface BackpackItemDao {
    
    /**
     * 插入新物品到背包
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: BackpackItemEntity): Long

    /**
     * 批量插入物品(用于导入备份)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<BackpackItemEntity>): List<Long>

    /**
     * 更新物品信息
     */
    @Update
    suspend fun updateItem(item: BackpackItemEntity)
    
    /**
     * 获取所有未丢弃的物品(Flow方式,自动更新)
     * 只显示operationType='normal'的物品
     */
    @Query("SELECT * FROM backpack_items WHERE operationType = 'normal' ORDER BY obtainedTime DESC")
    fun getAllItems(): Flow<List<BackpackItemEntity>>

    /**
     * 同步获取所有物品(用于导出备份,包括所有状态)
     */
    @Query("SELECT * FROM backpack_items ORDER BY obtainedTime DESC")
    suspend fun getAllItemsList(): List<BackpackItemEntity>

    /**
     * 获取所有历史记录(已丢弃/赠送/卖出的物品)
     * operationType != 'normal'的物品
     */
    @Query("SELECT * FROM backpack_items WHERE operationType != 'normal' ORDER BY operationTime DESC")
    fun getAllHistoryItems(): Flow<List<BackpackItemEntity>>
    
    /**
     * 根据ID获取物品
     */
    @Query("SELECT * FROM backpack_items WHERE id = :itemId")
    suspend fun getItemById(itemId: Long): BackpackItemEntity?
    
    /**
     * 根据订单ID获取物品
     */
    @Query("SELECT * FROM backpack_items WHERE orderId = :orderId AND isDiscarded = 0")
    suspend fun getItemsByOrderId(orderId: Long): List<BackpackItemEntity>
    
    /**
     * 标记物品为已丢弃
     * @deprecated 使用recordOperation代替
     */
    @Query("UPDATE backpack_items SET isDiscarded = 1 WHERE id = :itemId")
    suspend fun discardItem(itemId: Long)
    
    /**
     * 记录物品操作(丢弃/赠送/卖出)
     * @param itemId 物品ID
     * @param operationType 操作类型: "discarded", "gifted", "sold"
     * @param operationTime 操作时间戳
     */
    @Query("UPDATE backpack_items SET operationType = :operationType, operationTime = :operationTime, isDiscarded = 1 WHERE id = :itemId")
    suspend fun recordOperation(itemId: Long, operationType: String, operationTime: Long)
    
    /**
     * 记录物品赠送操作
     * @param itemId 物品ID
     * @param operationTime 操作时间戳
     * @param recipient 赠送目标名称
     */
    @Query("UPDATE backpack_items SET operationType = 'gifted', operationTime = :operationTime, isDiscarded = 1, giftRecipient = :recipient WHERE id = :itemId")
    suspend fun recordGiftOperation(itemId: Long, operationTime: Long, recipient: String)
    
    /**
     * 删除物品(物理删除)
     */
    @Query("DELETE FROM backpack_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)
    
    /**
     * 获取背包中物品总数(未丢弃的)
     */
    @Query("SELECT COUNT(*) FROM backpack_items WHERE isDiscarded = 0")
    suspend fun getItemCount(): Int
    
    /**
     * 清空所有已丢弃的物品
     * @deprecated 使用clearAllHistory代替
     */
    @Query("DELETE FROM backpack_items WHERE isDiscarded = 1")
    suspend fun clearDiscardedItems()
    
    /**
     * 删除历史记录
     * @param itemId 要删除的历史记录ID
     */
    @Query("DELETE FROM backpack_items WHERE id = :itemId AND operationType != 'normal'")
    suspend fun deleteHistoryItem(itemId: Long)
    
    /**
     * 清空所有历史记录
     */
    @Query("DELETE FROM backpack_items WHERE operationType != 'normal'")
    suspend fun clearAllHistory()
}