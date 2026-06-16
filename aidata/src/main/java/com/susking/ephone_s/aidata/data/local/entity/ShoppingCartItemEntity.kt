package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 购物车商品数据表Entity
 * 
 * 用于存储用户购物车中的商品信息
 */
@Entity(
    tableName = "shopping_cart_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId"])]
)
data class ShoppingCartItemEntity(
    /**
     * 主键ID,自动生成
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 商品ID(外键)
     */
    val productId: Long,
    
    /**
     * 商品数量
     */
    val quantity: Int,
    
    /**
     * 选中的款式索引
     * null表示未选择款式(使用商品默认价格和图片)
     */
    val selectedVariationIndex: Int? = null,
    
    /**
     * 加入购物车的时间戳(毫秒)
     */
    val timestamp: Long = System.currentTimeMillis()
)