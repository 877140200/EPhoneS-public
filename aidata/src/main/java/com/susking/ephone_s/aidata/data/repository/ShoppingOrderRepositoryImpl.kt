package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.ShoppingOrderDao
import com.susking.ephone_s.aidata.data.mapper.ShoppingMapper
import com.susking.ephone_s.aidata.domain.model.OrderProduct
import com.susking.ephone_s.aidata.domain.model.Recipient
import com.susking.ephone_s.aidata.domain.model.ShoppingOrder
import com.susking.ephone_s.aidata.domain.repository.ShoppingOrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 订单仓库实现
 * 
 * 实现订单管理的业务逻辑
 */
class ShoppingOrderRepositoryImpl @Inject constructor(
    private val orderDao: ShoppingOrderDao
) : ShoppingOrderRepository {
    
    override fun getAllOrders(): Flow<List<ShoppingOrder>> {
        return orderDao.getAllOrders()
            .map { entities -> ShoppingMapper.orderListToDomain(entities) }
    }
    
    override fun getOrdersByChatId(chatId: String): Flow<List<ShoppingOrder>> {
        return orderDao.getOrdersByChatId(chatId)
            .map { entities -> ShoppingMapper.orderListToDomain(entities) }
    }
    
    override suspend fun getOrderById(orderId: Long): ShoppingOrder? {
        val entity = orderDao.getOrderById(orderId)
        return entity?.let { ShoppingMapper.toDomain(it) }
    }
    
    override suspend fun createOrder(
        chatId: String,
        products: List<OrderProduct>,
        totalAmount: Double,
        recipient: Recipient,
        note: String?
    ): Long {
        // 验证商品列表不为空
        require(products.isNotEmpty()) { "订单商品列表不能为空" }
        
        // 验证金额
        require(totalAmount > 0) { "订单金额必须大于0" }
        
        // 创建Domain模型
        val domainOrder = ShoppingOrder(
            id = 0, // Room会自动生成ID
            chatId = chatId,
            products = products,
            totalAmount = totalAmount,
            recipient = recipient,
            note = note,
            timestamp = System.currentTimeMillis()
        )
        
        // 使用Mapper转换为Entity
        val entity = ShoppingMapper.toEntity(domainOrder)
        
        return orderDao.insertOrder(entity)
    }
    
    override suspend fun deleteOrder(orderId: Long) {
        orderDao.deleteOrder(orderId)
    }
    
    override suspend fun deleteOrdersByChatId(chatId: String) {
        orderDao.deleteOrdersByChatId(chatId)
    }
    
    override suspend fun deleteAllOrders() {
        orderDao.deleteAllOrders()
    }
}