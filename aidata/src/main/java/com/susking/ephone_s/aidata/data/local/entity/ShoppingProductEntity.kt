package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 商品数据表Entity
 * 
 * 用于存储虚拟购物系统的商品信息,包括商品基本信息和款式(variations)
 */
@Entity(
    tableName = "shopping_products",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class ShoppingProductEntity(
    /**
     * 主键ID,自动生成
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 商品名称
     */
    val name: String,
    
    /**
     * 默认价格(元)
     */
    val price: Double,
    
    /**
     * 商品描述
     */
    val description: String = "",
    
    /**
     * 商品主图URL
     */
    val imageUrl: String,
    
    /**
     * 所属分类ID,可为null表示未分类
     */
    val categoryId: Long? = null,
    
    /**
     * 商品款式列表(JSON格式存储)
     * 存储格式: [{"name":"红色","price":99.99,"imageUrl":"..."}]
     */
    val variations: String? = null,
    
    /**
     * 关联的联系人ID
     * 用于区分不同联系人的商品库
     */
    val contactId: String? = null,
    
    /**
     * 创建时间戳(毫秒)
     */
    val timestamp: Long = System.currentTimeMillis()
)