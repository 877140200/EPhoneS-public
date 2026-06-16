package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.ShoppingCategory
import kotlinx.coroutines.flow.Flow

/**
 * 商品分类仓库接口
 * 
 * 定义分类管理的业务操作
 */
interface ShoppingCategoryRepository {
    
    /**
     * 获取所有分类
     * 按名称排序
     */
    fun getAllCategories(): Flow<List<ShoppingCategory>>
    
    /**
     * 根据联系人ID获取分类列表
     * 按名称排序
     */
    fun getCategoriesByContactId(contactId: String): Flow<List<ShoppingCategory>>
    
    /**
     * 根据ID获取分类
     */
    suspend fun getCategoryById(categoryId: Long): ShoppingCategory?
    
    /**
     * 创建新分类
     * @param name 分类名称
     * @param contactId 联系人ID
     * @return 新分类的ID,如果名称已存在返回-1
     */
    suspend fun createCategory(name: String, contactId: String): Long

    /**
     * 更新分类
     */
    suspend fun updateCategory(category: ShoppingCategory)
    
    /**
     * 删除分类
     * 注意:该分类下的商品会被设为未分类
     */
    suspend fun deleteCategory(categoryId: Long)
    
    /**
     * 删除指定联系人的所有分类
     */
    suspend fun deleteCategoriesByContactId(contactId: String)
    
    /**
     * 清空所有分类
     */
    suspend fun deleteAllCategories()
}