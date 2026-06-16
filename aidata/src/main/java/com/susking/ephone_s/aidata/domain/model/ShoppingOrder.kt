package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 购物订单领域模型
 * 
 * 用于在应用层传递订单数据
 */
@Parcelize
data class ShoppingOrder(
    /**
     * 订单ID
     */
    val id: Long,
    
    /**
     * 关联的聊天ID
     * 表示这个订单是在哪个AI对话中产生的
     */
    val chatId: String,
    
    /**
     * 订单商品列表
     */
    val products: List<OrderProduct>,
    
    /**
     * 订单总金额(元)
     */
    val totalAmount: Double,
    
    /**
     * 收礼人信息
     */
    val recipient: Recipient,
    
    /**
     * 订单备注(可选)
     */
    val note: String?,
    
    /**
     * 订单创建时间戳(毫秒)
     */
    val timestamp: Long
) : Parcelable {
    
    /**
     * 获取订单商品总数
     */
    fun getTotalQuantity(): Int {
        return products.sumOf { it.quantity }
    }
    
    /**
     * 验证订单金额是否正确
     */
    fun validateTotalAmount(): Boolean {
        val calculatedTotal = products.sumOf { it.getSubtotal() }
        return Math.abs(calculatedTotal - totalAmount) < 0.01 // 容忍0.01元的浮点误差
    }
}