package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 购物app已授权账号实体
 * 
 * 存储已经授权用户查看其购物商品的联系人ID列表
 */
@Entity(tableName = "shopping_authorized_accounts")
data class ShoppingAuthorizedAccountEntity(
    /**
     * 联系人ID(主键)
     */
    @PrimaryKey
    val contactId: String,
    
    /**
     * 授权时间戳
     */
    val authorizedTimestamp: Long,
    
    /**
     * 备注(可选,用于记录授权来源等信息)
     */
    val note: String? = null
)