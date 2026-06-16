package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 商品分类数据表Entity
 *
 * 用于存储虚拟购物系统的商品分类信息
 * 每个分类绑定到特定的联系人(角色)
 */
@Entity(
    tableName = "shopping_categories",
    indices = [
        Index(value = ["name", "contactId"], unique = true),
        Index(value = ["contactId"])
    ]
)
data class ShoppingCategoryEntity(
    /**
     * 主键ID,自动生成
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 分类名称,同一contactId下唯一
     */
    val name: String,
    
    /**
     * 所属联系人ID(角色ID)
     * 分类与角色绑定,不同角色的分类互不影响
     */
    val contactId: String,
    
    /**
     * 创建时间戳(毫秒)
     */
    val timestamp: Long = System.currentTimeMillis()
)