package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.ShoppingProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * 商品数据访问对象
 * 
 * 提供商品的增删查改操作
 */
@Dao
interface ShoppingProductDao {
    
    /**
     * 获取所有商品
     * 按时间倒序排列
     */
    @Query("SELECT * FROM shopping_products ORDER BY timestamp DESC")
    fun getAllProducts(): Flow<List<ShoppingProductEntity>>

    /**
     * 同步获取所有商品(用于导出备份)
     * 按时间倒序排列
     */
    @Query("SELECT * FROM shopping_products ORDER BY timestamp DESC")
    suspend fun getAllProductsList(): List<ShoppingProductEntity>

    /**
     * 根据联系人ID获取所有商品
     */
    @Query("SELECT * FROM shopping_products WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getProductsByContactId(contactId: String): Flow<List<ShoppingProductEntity>>
    
    /**
     * 根据分类ID获取商品
     */
    @Query("SELECT * FROM shopping_products WHERE categoryId = :categoryId ORDER BY timestamp DESC")
    fun getProductsByCategoryId(categoryId: Long): Flow<List<ShoppingProductEntity>>
    
    /**
     * 根据联系人ID和分类ID获取商品
     */
    @Query("SELECT * FROM shopping_products WHERE contactId = :contactId AND categoryId = :categoryId ORDER BY timestamp DESC")
    fun getProductsByContactIdAndCategoryId(contactId: String, categoryId: Long): Flow<List<ShoppingProductEntity>>
    
    /**
     * 获取未分类的商品
     */
    @Query("SELECT * FROM shopping_products WHERE categoryId IS NULL ORDER BY timestamp DESC")
    fun getUncategorizedProducts(): Flow<List<ShoppingProductEntity>>
    
    /**
     * 根据ID获取商品
     */
    @Query("SELECT * FROM shopping_products WHERE id = :productId")
    suspend fun getProductById(productId: Long): ShoppingProductEntity?
    
    /**
     * 插入新商品
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ShoppingProductEntity): Long
    
    /**
     * 批量插入商品
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ShoppingProductEntity>): List<Long>
    
    /**
     * 更新商品
     */
    @Update
    suspend fun updateProduct(product: ShoppingProductEntity)
    
    /**
     * 删除商品
     */
    @Query("DELETE FROM shopping_products WHERE id = :productId")
    suspend fun deleteProduct(productId: Long)
    
    /**
     * 将指定分类的所有商品设为未分类
     * 用于删除分类时
     */
    @Query("UPDATE shopping_products SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun setCategoryToNull(categoryId: Long)
    
    /**
     * 删除指定联系人的所有商品
     */
    @Query("DELETE FROM shopping_products WHERE contactId = :contactId")
    suspend fun deleteProductsByContactId(contactId: String)
    
    /**
     * 清空所有商品
     */
    @Query("DELETE FROM shopping_products")
    suspend fun deleteAllProducts()
    
    /**
     * 搜索商品(按名称或描述)
     */
    @Query("SELECT * FROM shopping_products WHERE name LIKE '%' || :keyword || '%' OR description LIKE '%' || :keyword || '%' ORDER BY timestamp DESC")
    fun searchProducts(keyword: String): Flow<List<ShoppingProductEntity>>
}