package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.susking.ephone_s.aidata.data.local.entity.ShoppingOrderEntity
import kotlinx.coroutines.flow.Flow

/**
 * 购物订单数据访问对象
 * 
 * 提供订单的增删查操作
 */
@Dao
interface ShoppingOrderDao {
    
    /**
     * 获取所有订单
     * 按时间倒序排列
     */
    @Query("SELECT * FROM shopping_orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<ShoppingOrderEntity>>

    /**
     * 同步获取所有订单(用于导出备份)
     * 按时间倒序排列
     */
    @Query("SELECT * FROM shopping_orders ORDER BY timestamp DESC")
    suspend fun getAllOrdersList(): List<ShoppingOrderEntity>

    /**
     * 根据聊天ID获取订单
     */
    @Query("SELECT * FROM shopping_orders WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getOrdersByChatId(chatId: String): Flow<List<ShoppingOrderEntity>>
    
    /**
     * 根据ID获取订单
     */
    @Query("SELECT * FROM shopping_orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: Long): ShoppingOrderEntity?
    
    /**
     * 插入新订单
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: ShoppingOrderEntity): Long

    /**
     * 批量插入订单(用于导入备份)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<ShoppingOrderEntity>): List<Long>

    /**
     * 删除订单
     */
    @Query("DELETE FROM shopping_orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Long)
    
    /**
     * 删除指定聊天的所有订单
     */
    @Query("DELETE FROM shopping_orders WHERE chatId = :chatId")
    suspend fun deleteOrdersByChatId(chatId: String)
    
    /**
     * 清空所有订单
     */
    @Query("DELETE FROM shopping_orders")
    suspend fun deleteAllOrders()
}