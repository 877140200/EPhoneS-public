package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.ShoppingCartDao
import com.susking.ephone_s.aidata.data.local.dao.ShoppingProductDao
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCartItemEntity
import com.susking.ephone_s.aidata.data.mapper.ShoppingMapper
import com.susking.ephone_s.aidata.domain.model.CartItem
import com.susking.ephone_s.aidata.domain.repository.ShoppingCartRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 购物车仓库实现
 * 
 * 实现购物车管理的业务逻辑
 */
class ShoppingCartRepositoryImpl @Inject constructor(
    private val cartDao: ShoppingCartDao,
    private val productDao: ShoppingProductDao
) : ShoppingCartRepository {
    
    override fun getAllCartItems(): Flow<List<CartItem>> {
        return cartDao.getAllCartItems()
            .map { entities ->
                // 为每个购物车项关联对应的商品信息
                entities.mapNotNull { entity ->
                    val product = productDao.getProductById(entity.productId)
                    if (product != null) {
                        ShoppingMapper.toDomain(entity, ShoppingMapper.toDomain(product))
                    } else {
                        null // 商品已被删除
                    }
                }
            }
    }
    
    override suspend fun getCartItemById(itemId: Long): CartItem? {
        val entity = cartDao.getCartItemById(itemId) ?: return null
        val product = productDao.getProductById(entity.productId) ?: return null
        return ShoppingMapper.toDomain(entity, ShoppingMapper.toDomain(product))
    }
    
    override suspend fun addToCart(
        productId: Long,
        quantity: Int,
        selectedVariationIndex: Int?
    ): Long {
        // 检查商品是否存在
        val product = productDao.getProductById(productId) 
            ?: throw IllegalArgumentException("商品不存在: $productId")
        
        // 检查是否已经在购物车中
        val existing = cartDao.findCartItem(productId, selectedVariationIndex)
        
        return if (existing != null) {
            // 如果已存在,增加数量
            val newQuantity = existing.quantity + quantity
            cartDao.updateQuantity(existing.id, newQuantity)
            existing.id
        } else {
            // 如果不存在,添加新项
            val cartItem = ShoppingCartItemEntity(
                id = 0, // Room会自动生成ID
                productId = productId,
                quantity = quantity,
                selectedVariationIndex = selectedVariationIndex,
                timestamp = System.currentTimeMillis()
            )
            cartDao.insertCartItem(cartItem)
        }
    }
    
    override suspend fun updateQuantity(itemId: Long, quantity: Int) {
        if (quantity <= 0) {
            // 如果数量为0或负数,删除该项
            cartDao.deleteCartItem(itemId)
        } else {
            cartDao.updateQuantity(itemId, quantity)
        }
    }
    
    override suspend fun removeFromCart(itemId: Long) {
        cartDao.deleteCartItem(itemId)
    }
    
    override suspend fun removeMultipleFromCart(itemIds: List<Long>) {
        if (itemIds.isNotEmpty()) {
            cartDao.deleteCartItems(itemIds)
        }
    }
    
    override suspend fun clearCart() {
        cartDao.clearCart()
    }
    
    override fun getCartItemCount(): Flow<Int> {
        return cartDao.getCartItemCount()
            .map { it ?: 0 }
    }
    
    override suspend fun calculateTotalAmount(): Double {
        val items = getAllCartItems().first()
        return items.sumOf { it.getSubtotal() }
    }
}