package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 购物订单数据表Entity
 * 
 * 用于存储用户的购物订单历史(主要用于礼物记录)
 */
@Entity(
    tableName = "shopping_orders",
    indices = [Index(value = ["chatId"])]
)
data class ShoppingOrderEntity(
    /**
     * 主键ID,自动生成
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 关联的聊天ID
     */
    val chatId: String,
    
    /**
     * 订单商品列表(JSON格式存储)
     * 存储格式: [{"name":"商品名","price":99.99,"imageUrl":"...","quantity":2}]
     */
    val items: String,
    
    /**
     * 订单总价(元)
     */
    val total: Double,
    
    /**
     * 收礼人列表(JSON格式存储)
     * 存储格式: ["收礼人A的originalName","收礼人B的originalName"]
     * null表示单聊(无需指定收礼人)
     */
    val recipients: String? = null,
    
    /**
     * 订单备注信息(可选)
     */
    val note: String? = null,
    
    /**
     * 订单创建时间戳(毫秒)
     */
    val timestamp: Long = System.currentTimeMillis()
)