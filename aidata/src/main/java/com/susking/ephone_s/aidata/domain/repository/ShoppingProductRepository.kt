package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.ProductVariation
import com.susking.ephone_s.aidata.domain.model.ShoppingProduct
import kotlinx.coroutines.flow.Flow

/**
 * 商品仓库接口
 * 
 * 定义商品管理的业务操作
 */
interface ShoppingProductRepository {
    
    /**
     * 获取所有商品
     * 按时间倒序排列
     */
    fun getAllProducts(): Flow<List<ShoppingProduct>>
    
    /**
     * 根据联系人ID获取所有商品
     */
    fun getProductsByContactId(contactId: String): Flow<List<ShoppingProduct>>
    
    /**
     * 根据分类ID获取商品
     */
    fun getProductsByCategoryId(categoryId: Long): Flow<List<ShoppingProduct>>
    
    /**
     * 根据联系人ID和分类ID获取商品
     */
    fun getProductsByContactIdAndCategoryId(contactId: String, categoryId: Long): Flow<List<ShoppingProduct>>
    
    /**
     * 获取未分类的商品
     */
    fun getUncategorizedProducts(): Flow<List<ShoppingProduct>>
    
    /**
     * 根据ID获取商品
     */
    suspend fun getProductById(productId: Long): ShoppingProduct?
    
    /**
     * 创建新商品
     * @return 新商品的ID
     */
    suspend fun createProduct(
        name: String,
        price: Double,
        description: String,
        imageUrl: String,
        categoryId: Long?,
        variations: List<ProductVariation> = emptyList(),
        contactId: String? = null
    ): Long
    
    /**
     * 更新商品信息
     */
    suspend fun updateProduct(product: ShoppingProduct)
    
    /**
     * 删除商品
     */
    suspend fun deleteProduct(productId: Long)
    
    /**
     * 删除指定联系人的所有商品
     */
    suspend fun deleteProductsByContactId(contactId: String)
    
    /**
     * 清空所有商品
     */
    suspend fun deleteAllProducts()
    
    /**
     * 搜索商品(按名称或描述)
     */
    fun searchProducts(keyword: String): Flow<List<ShoppingProduct>>
}