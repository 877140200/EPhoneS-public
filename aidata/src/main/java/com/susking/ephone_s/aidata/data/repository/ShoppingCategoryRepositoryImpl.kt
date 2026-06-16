package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.ShoppingCategoryDao
import com.susking.ephone_s.aidata.data.local.dao.ShoppingProductDao
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCategoryEntity
import com.susking.ephone_s.aidata.data.mapper.ShoppingMapper
import com.susking.ephone_s.aidata.domain.model.ShoppingCategory
import com.susking.ephone_s.aidata.domain.repository.ShoppingCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 商品分类仓库实现
 * 
 * 实现分类管理的业务逻辑
 */
class ShoppingCategoryRepositoryImpl @Inject constructor(
    private val categoryDao: ShoppingCategoryDao,
    private val productDao: ShoppingProductDao
) : ShoppingCategoryRepository {
    
    override fun getAllCategories(): Flow<List<ShoppingCategory>> {
        return categoryDao.getAllCategories()
            .map { entities -> ShoppingMapper.categoryListToDomain(entities) }
    }
    
    override fun getCategoriesByContactId(contactId: String): Flow<List<ShoppingCategory>> {
        return categoryDao.getCategoriesByContactId(contactId)
            .map { entities -> ShoppingMapper.categoryListToDomain(entities) }
    }
    
    override suspend fun getCategoryById(categoryId: Long): ShoppingCategory? {
        val entity = categoryDao.getCategoryById(categoryId)
        return entity?.let { ShoppingMapper.toDomain(it) }
    }
    
    override suspend fun createCategory(name: String, contactId: String): Long {
        // 检查同一联系人下分类名称是否已存在
        val existing = categoryDao.findCategoryByNameAndContactId(name, contactId)
        if (existing != null) {
            return -1 // 名称已存在
        }
        
        // 创建新分类
        val category = ShoppingCategoryEntity(
            id = 0, // Room会自动生成ID
            name = name,
            contactId = contactId,
            timestamp = System.currentTimeMillis()
        )
        
        return categoryDao.insertCategory(category)
    }

    override suspend fun updateCategory(category: ShoppingCategory) {
        val entity = ShoppingMapper.toEntity(category)
        categoryDao.updateCategory(entity)
    }
    
    override suspend fun deleteCategory(categoryId: Long) {
        // 先将该分类下的所有商品设为未分类
        productDao.setCategoryToNull(categoryId)
        
        // 再删除分类
        categoryDao.deleteCategory(categoryId)
    }
    
    override suspend fun deleteCategoriesByContactId(contactId: String) {
        categoryDao.deleteCategoriesByContactId(contactId)
    }
    
    override suspend fun deleteAllCategories() {
        // 先将所有商品设为未分类
        val categories = categoryDao.getAllCategories()
        categories.collect { list ->
            list.forEach { category ->
                productDao.setCategoryToNull(category.id)
            }
        }
        
        // 再删除所有分类
        categoryDao.deleteAllCategories()
    }
}