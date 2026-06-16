package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.CartItem
import kotlinx.coroutines.flow.Flow

/**
 * 购物车仓库接口
 * 
 * 定义购物车管理的业务操作
 */
interface ShoppingCartRepository {
    
    /**
     * 获取购物车中的所有商品
     * 按加入时间倒序排列
     */
    fun getAllCartItems(): Flow<List<CartItem>>
    
    /**
     * 根据ID获取购物车项
     */
    suspend fun getCartItemById(itemId: Long): CartItem?
    
    /**
     * 添加商品到购物车
     * 如果商品已存在(相同productId和variationIndex),则增加数量
     * @return 购物车项ID
     */
    suspend fun addToCart(
        productId: Long,
        quantity: Int = 1,
        selectedVariationIndex: Int? = null
    ): Long
    
    /**
     * 更新购物车项数量
     */
    suspend fun updateQuantity(itemId: Long, quantity: Int)
    
    /**
     * 删除购物车项
     */
    suspend fun removeFromCart(itemId: Long)
    
    /**
     * 批量删除购物车项
     */
    suspend fun removeMultipleFromCart(itemIds: List<Long>)
    
    /**
     * 清空购物车
     */
    suspend fun clearCart()
    
    /**
     * 获取购物车商品总数
     */
    fun getCartItemCount(): Flow<Int>
    
    /**
     * 计算购物车总金额
     */
    suspend fun calculateTotalAmount(): Double
}