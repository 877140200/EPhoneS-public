package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.OrderProduct
import com.susking.ephone_s.aidata.domain.model.Recipient
import com.susking.ephone_s.aidata.domain.model.ShoppingOrder
import kotlinx.coroutines.flow.Flow

/**
 * 订单仓库接口
 * 
 * 定义订单管理的业务操作
 */
interface ShoppingOrderRepository {
    
    /**
     * 获取所有订单
     * 按时间倒序排列
     */
    fun getAllOrders(): Flow<List<ShoppingOrder>>
    
    /**
     * 根据聊天ID获取订单
     */
    fun getOrdersByChatId(chatId: String): Flow<List<ShoppingOrder>>
    
    /**
     * 根据ID获取订单
     */
    suspend fun getOrderById(orderId: Long): ShoppingOrder?
    
    /**
     * 创建新订单
     * @return 新订单的ID
     */
    suspend fun createOrder(
        chatId: String,
        products: List<OrderProduct>,
        totalAmount: Double,
        recipient: Recipient,
        note: String? = null
    ): Long
    
    /**
     * 删除订单
     */
    suspend fun deleteOrder(orderId: Long)
    
    /**
     * 删除指定聊天的所有订单
     */
    suspend fun deleteOrdersByChatId(chatId: String)
    
    /**
     * 清空所有订单
     */
    suspend fun deleteAllOrders()
}