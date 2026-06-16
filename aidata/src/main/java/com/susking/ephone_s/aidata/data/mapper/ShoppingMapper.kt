package com.susking.ephone_s.aidata.data.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.local.entity.OrderItemData
import com.susking.ephone_s.aidata.data.local.entity.ProductVariationData
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCartItemEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCategoryEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingOrderEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingProductEntity
import com.susking.ephone_s.aidata.domain.model.CartItem
import com.susking.ephone_s.aidata.domain.model.OrderProduct
import com.susking.ephone_s.aidata.domain.model.ProductVariation
import com.susking.ephone_s.aidata.domain.model.Recipient
import com.susking.ephone_s.aidata.domain.model.ShoppingCategory
import com.susking.ephone_s.aidata.domain.model.ShoppingOrder
import com.susking.ephone_s.aidata.domain.model.ShoppingProduct

/**
 * 购物系统数据映射器
 * 
 * 负责Entity和Domain Model之间的相互转换
 */
object ShoppingMapper {
    
    private val gson = Gson()
    
    // ========== Category 转换 ==========
    
    /**
     * CategoryEntity -> Category
     */
    fun toDomain(entity: ShoppingCategoryEntity): ShoppingCategory {
        return ShoppingCategory(
            id = entity.id,
            name = entity.name,
            contactId = entity.contactId,
            timestamp = entity.timestamp
        )
    }
    
    /**
     * Category -> CategoryEntity
     */
    fun toEntity(category: ShoppingCategory): ShoppingCategoryEntity {
        return ShoppingCategoryEntity(
            id = category.id,
            name = category.name,
            contactId = category.contactId,
            timestamp = category.timestamp
        )
    }
    
    // ========== Product 转换 ==========
    
    /**
     * ProductEntity -> Product
     */
    fun toDomain(entity: ShoppingProductEntity): ShoppingProduct {
        // 将variations JSON字符串转换为List<ProductVariation>
        val variations = if (entity.variations != null) {
            try {
                val type = object : TypeToken<List<ProductVariationData>>() {}.type
                val dataList: List<ProductVariationData> = gson.fromJson(entity.variations, type)
                dataList.map { data ->
                    ProductVariation(
                        name = data.name,
                        price = data.price,
                        imageUrl = data.imageUrl
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        
        return ShoppingProduct(
            id = entity.id,
            name = entity.name,
            price = entity.price,
            description = entity.description,
            imageUrl = entity.imageUrl,
            categoryId = entity.categoryId,
            variations = variations,
            contactId = entity.contactId,
            timestamp = entity.timestamp
        )
    }
    
    /**
     * Product -> ProductEntity
     */
    fun toEntity(product: ShoppingProduct): ShoppingProductEntity {
        // 将List<ProductVariation>转换为JSON字符串
        val variationsJson = if (product.variations.isNotEmpty()) {
            val dataList = product.variations.map { variation ->
                ProductVariationData(
                    name = variation.name,
                    price = variation.price,
                    imageUrl = variation.imageUrl
                )
            }
            gson.toJson(dataList)
        } else {
            null
        }
        
        return ShoppingProductEntity(
            id = product.id,
            name = product.name,
            price = product.price,
            description = product.description,
            imageUrl = product.imageUrl,
            categoryId = product.categoryId,
            variations = variationsJson,
            contactId = product.contactId,
            timestamp = product.timestamp
        )
    }
    
    // ========== CartItem 转换 ==========
    
    /**
     * CartItemEntity + Product -> CartItem
     * 需要同时传入购物车实体和对应的商品信息
     */
    fun toDomain(entity: ShoppingCartItemEntity, product: ShoppingProduct): CartItem {
        return CartItem(
            id = entity.id,
            productId = entity.productId,
            product = product,
            quantity = entity.quantity,
            selectedVariationIndex = entity.selectedVariationIndex,
            timestamp = entity.timestamp
        )
    }
    
    /**
     * CartItem -> CartItemEntity
     */
    fun toEntity(cartItem: CartItem): ShoppingCartItemEntity {
        return ShoppingCartItemEntity(
            id = cartItem.id,
            productId = cartItem.productId,
            quantity = cartItem.quantity,
            selectedVariationIndex = cartItem.selectedVariationIndex,
            timestamp = cartItem.timestamp
        )
    }
    
    // ========== Order 转换 ==========
    
    /**
     * OrderEntity -> Order
     */
    fun toDomain(entity: ShoppingOrderEntity): ShoppingOrder {
        // 将items JSON字符串转换为List<OrderProduct>
        val products = try {
            val type = object : TypeToken<List<OrderItemData>>() {}.type
            val dataList: List<OrderItemData> = gson.fromJson(entity.items, type)
            dataList.map { data ->
                OrderProduct(
                    name = data.name,
                    price = data.price,
                    quantity = data.quantity,
                    imageUrl = data.imageUrl
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        
        // 将recipients JSON字符串转换为Recipient对象(取第一个收礼人)
        val recipient = try {
            if (entity.recipients != null) {
                val type = object : TypeToken<List<String>>() {}.type
                val recipientNames: List<String> = gson.fromJson(entity.recipients, type)
                if (recipientNames.isNotEmpty()) {
                    // 暂时使用默认值,因为Entity中只存储了姓名
                    Recipient(
                        name = recipientNames.first(),
                        phone = "",
                        address = ""
                    )
                } else {
                    Recipient(name = "", phone = "", address = "")
                }
            } else {
                Recipient(name = "", phone = "", address = "")
            }
        } catch (e: Exception) {
            Recipient(name = "", phone = "", address = "")
        }
        
        return ShoppingOrder(
            id = entity.id,
            chatId = entity.chatId,
            products = products,
            totalAmount = entity.total,
            recipient = recipient,
            note = entity.note,
            timestamp = entity.timestamp
        )
    }
    
    /**
     * Order -> OrderEntity
     */
    fun toEntity(order: ShoppingOrder): ShoppingOrderEntity {
        // 将List<OrderProduct>转换为JSON字符串
        val itemsJson = try {
            val dataList = order.products.map { product ->
                OrderItemData(
                    name = product.name,
                    price = product.price,
                    quantity = product.quantity,
                    imageUrl = product.imageUrl
                )
            }
            gson.toJson(dataList)
        } catch (e: Exception) {
            "[]"
        }
        
        // 将Recipient对象转换为JSON字符串(包装为列表)
        val recipientsJson = try {
            if (order.recipient.name.isNotEmpty()) {
                gson.toJson(listOf(order.recipient.name))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        
        return ShoppingOrderEntity(
            id = order.id,
            chatId = order.chatId,
            items = itemsJson,
            total = order.totalAmount,
            recipients = recipientsJson,
            note = order.note,
            timestamp = order.timestamp
        )
    }
    
    // ========== 批量转换 ==========
    
    /**
     * 批量转换分类
     */
    fun categoryListToDomain(entities: List<ShoppingCategoryEntity>): List<ShoppingCategory> {
        return entities.map { toDomain(it) }
    }
    
    /**
     * 批量转换商品
     */
    fun productListToDomain(entities: List<ShoppingProductEntity>): List<ShoppingProduct> {
        return entities.map { toDomain(it) }
    }
    
    /**
     * 批量转换订单
     */
    fun orderListToDomain(entities: List<ShoppingOrderEntity>): List<ShoppingOrder> {
        return entities.map { toDomain(it) }
    }
}