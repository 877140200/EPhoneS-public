package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCartItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * 购物车数据访问对象
 * 
 * 提供购物车的增删查改操作
 */
@Dao
interface ShoppingCartDao {
    
    /**
     * 获取购物车中的所有商品
     * 按加入时间倒序排列
     */
    @Query("SELECT * FROM shopping_cart_items ORDER BY timestamp DESC")
    fun getAllCartItems(): Flow<List<ShoppingCartItemEntity>>

    /**
     * 同步获取购物车中的所有商品(用于导出备份)
     * 按加入时间倒序排列
     */
    @Query("SELECT * FROM shopping_cart_items ORDER BY timestamp DESC")
    suspend fun getAllCartItemsList(): List<ShoppingCartItemEntity>

    /**
     * 根据ID获取购物车项
     */
    @Query("SELECT * FROM shopping_cart_items WHERE id = :itemId")
    suspend fun getCartItemById(itemId: Long): ShoppingCartItemEntity?
    
    /**
     * 查找购物车中是否存在指定商品和款式
     */
    @Query("SELECT * FROM shopping_cart_items WHERE productId = :productId AND selectedVariationIndex = :variationIndex LIMIT 1")
    suspend fun findCartItem(productId: Long, variationIndex: Int?): ShoppingCartItemEntity?
    
    /**
     * 插入购物车项
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: ShoppingCartItemEntity): Long

    /**
     * 批量插入购物车项(用于导入备份)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItems(items: List<ShoppingCartItemEntity>): List<Long>

    /**
     * 更新购物车项
     */
    @Update
    suspend fun updateCartItem(item: ShoppingCartItemEntity)
    
    /**
     * 更新购物车项数量
     */
    @Query("UPDATE shopping_cart_items SET quantity = :quantity WHERE id = :itemId")
    suspend fun updateQuantity(itemId: Long, quantity: Int)
    
    /**
     * 删除购物车项
     */
    @Query("DELETE FROM shopping_cart_items WHERE id = :itemId")
    suspend fun deleteCartItem(itemId: Long)
    
    /**
     * 批量删除购物车项
     */
    @Query("DELETE FROM shopping_cart_items WHERE id IN (:itemIds)")
    suspend fun deleteCartItems(itemIds: List<Long>)
    
    /**
     * 清空购物车
     */
    @Query("DELETE FROM shopping_cart_items")
    suspend fun clearCart()
    
    /**
     * 获取购物车商品总数
     */
    @Query("SELECT SUM(quantity) FROM shopping_cart_items")
    fun getCartItemCount(): Flow<Int?>
}