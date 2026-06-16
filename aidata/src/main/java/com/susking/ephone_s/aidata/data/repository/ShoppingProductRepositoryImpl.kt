package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.ShoppingProductDao
import com.susking.ephone_s.aidata.data.mapper.ShoppingMapper
import com.susking.ephone_s.aidata.domain.model.ProductVariation
import com.susking.ephone_s.aidata.domain.model.ShoppingProduct
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 商品仓库实现
 * 
 * 实现商品管理的业务逻辑
 */
class ShoppingProductRepositoryImpl @Inject constructor(
    private val productDao: ShoppingProductDao
) : ShoppingProductRepository {
    
    override fun getAllProducts(): Flow<List<ShoppingProduct>> {
        return productDao.getAllProducts()
            .map { entities -> ShoppingMapper.productListToDomain(entities) }
    }
    
    override fun getProductsByContactId(contactId: String): Flow<List<ShoppingProduct>> {
        return productDao.getProductsByContactId(contactId)
            .map { entities -> ShoppingMapper.productListToDomain(entities) }
    }
    
    override fun getProductsByCategoryId(categoryId: Long): Flow<List<ShoppingProduct>> {
        return productDao.getProductsByCategoryId(categoryId)
            .map { entities -> ShoppingMapper.productListToDomain(entities) }
    }
    
    override fun getProductsByContactIdAndCategoryId(contactId: String, categoryId: Long): Flow<List<ShoppingProduct>> {
        return productDao.getProductsByContactIdAndCategoryId(contactId, categoryId)
            .map { entities -> ShoppingMapper.productListToDomain(entities) }
    }
    
    override fun getUncategorizedProducts(): Flow<List<ShoppingProduct>> {
        return productDao.getUncategorizedProducts()
            .map { entities -> ShoppingMapper.productListToDomain(entities) }
    }
    
    override suspend fun getProductById(productId: Long): ShoppingProduct? {
        val entity = productDao.getProductById(productId)
        return entity?.let { ShoppingMapper.toDomain(it) }
    }
    
    override suspend fun createProduct(
        name: String,
        price: Double,
        description: String,
        imageUrl: String,
        categoryId: Long?,
        variations: List<ProductVariation>,
        contactId: String?
    ): Long {
        // 创建Domain模型
        val domainProduct = ShoppingProduct(
            id = 0, // Room会自动生成ID
            name = name,
            price = price,
            description = description,
            imageUrl = imageUrl,
            categoryId = categoryId,
            variations = variations,
            contactId = contactId,
            timestamp = System.currentTimeMillis()
        )
        
        // 使用Mapper转换为Entity
        val entity = ShoppingMapper.toEntity(domainProduct)
        
        return productDao.insertProduct(entity)
    }
    
    override suspend fun updateProduct(product: ShoppingProduct) {
        val entity = ShoppingMapper.toEntity(product)
        productDao.updateProduct(entity)
    }
    
    override suspend fun deleteProduct(productId: Long) {
        productDao.deleteProduct(productId)
    }
    
    override suspend fun deleteProductsByContactId(contactId: String) {
        productDao.deleteProductsByContactId(contactId)
    }
    
    override suspend fun deleteAllProducts() {
        productDao.deleteAllProducts()
    }
    
    override fun searchProducts(keyword: String): Flow<List<ShoppingProduct>> {
        return productDao.searchProducts(keyword)
            .map { entities -> ShoppingMapper.productListToDomain(entities) }
    }
}