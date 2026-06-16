package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 订单商品领域模型
 * 
 * 表示订单中的单个商品项
 */
@Parcelize
data class OrderProduct(
    /**
     * 商品名称
     */
    val name: String,
    
    /**
     * 商品价格(元)
     */
    val price: Double,
    
    /**
     * 商品数量
     */
    val quantity: Int,
    
    /**
     * 商品图片URL
     */
    val imageUrl: String,
    
    /**
     * 选中的款式名称(可选)
     */
    val variationName: String? = null
) : Parcelable {
    
    /**
     * 计算小计(价格 × 数量)
     */
    fun getSubtotal(): Double {
        return price * quantity
    }
}