package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 商品分类数据访问对象
 * 
 * 提供商品分类的增删查改操作
 */
@Dao
interface ShoppingCategoryDao {
    
    /**
     * 获取所有商品分类
     * 按名称排序
     */
    @Query("SELECT * FROM shopping_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<ShoppingCategoryEntity>>

    /**
     * 同步获取所有商品分类(用于导出备份)
     * 按名称排序
     */
    @Query("SELECT * FROM shopping_categories ORDER BY name ASC")
    suspend fun getAllCategoriesList(): List<ShoppingCategoryEntity>

    /**
     * 根据联系人ID获取分类列表
     * 按名称排序
     */
    @Query("SELECT * FROM shopping_categories WHERE contactId = :contactId ORDER BY name ASC")
    fun getCategoriesByContactId(contactId: String): Flow<List<ShoppingCategoryEntity>>
    
    /**
     * 根据ID获取分类
     */
    @Query("SELECT * FROM shopping_categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): ShoppingCategoryEntity?
    
    /**
     * 根据名称查找分类(不区分大小写)
     */
    @Query("SELECT * FROM shopping_categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findCategoryByName(name: String): ShoppingCategoryEntity?
    
    /**
     * 根据名称和联系人ID查找分类(不区分大小写)
     */
    @Query("SELECT * FROM shopping_categories WHERE LOWER(name) = LOWER(:name) AND contactId = :contactId LIMIT 1")
    suspend fun findCategoryByNameAndContactId(name: String, contactId: String): ShoppingCategoryEntity?
    
    /**
     * 插入新分类
     * 如果名称冲突则忽略
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: ShoppingCategoryEntity): Long
    
    /**
     * 批量插入分类
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<ShoppingCategoryEntity>): List<Long>
    
    /**
     * 更新分类
     */
    @androidx.room.Update
    suspend fun updateCategory(category: ShoppingCategoryEntity)
    
    /**
     * 删除分类
     */
    @Query("DELETE FROM shopping_categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: Long)
    
    /**
     * 删除指定联系人的所有分类
     */
    @Query("DELETE FROM shopping_categories WHERE contactId = :contactId")
    suspend fun deleteCategoriesByContactId(contactId: String)
    
    /**
     * 清空所有分类
     */
    @Query("DELETE FROM shopping_categories")
    suspend fun deleteAllCategories()
}