package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 背包物品数据表Entity
 * 
 * 用于存储用户背包中的所有物品
 */
@Entity(
    tableName = "backpack_items",
    indices = [Index(value = ["orderId"]), Index(value = ["isDiscarded"]), Index(value = ["operationType"])]
)
data class BackpackItemEntity(
    /**
     * 主键ID,自动生成
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 商品名称
     */
    val productName: String,
    
    /**
     * 商品图片URL
     */
    val imageUrl: String,
    
    /**
     * 商品价值(元)
     */
    val price: Double,
    
    /**
     * 物品来源
     * 例如: "购物商城", "礼物", "活动奖励" 等
     */
    val source: String,
    
    /**
     * 获得时间戳(毫秒)
     */
    val obtainedTime: Long,
    
    /**
     * 关联的订单ID(可选)
     * 如果物品来自购物订单,则记录订单ID
     */
    val orderId: Long? = null,
    
    /**
     * 是否已丢弃
     * true: 已丢弃(不在背包中显示)
     * false: 正常状态
     */
    val isDiscarded: Boolean = false,
    
    /**
     * 操作类型
     * "normal": 正常持有中
     * "discarded": 已丢弃
     * "gifted": 已赠送
     * "sold": 已卖出
     */
    val operationType: String = "normal",
    
    /**
     * 操作时间戳(毫秒)
     * 记录丢弃/赠送/卖出的时间
     * 如果为null,表示还在背包中
     */
    val operationTime: Long? = null,
    
    /**
     * 赠送目标
     * 当operationType="gifted"时,记录赠送给谁
     * 例如: "张三", "李四" 等联系人名称
     */
    val giftRecipient: String? = null
)