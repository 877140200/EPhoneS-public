package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 购物车商品项领域模型
 * 
 * 用于在应用层传递购物车数据
 */
@Parcelize
data class CartItem(
    /**
     * 购物车项ID
     */
    val id: Long,
    
    /**
     * 商品ID
     */
    val productId: Long,
    
    /**
     * 商品信息(关联查询得到)
     */
    val product: ShoppingProduct,
    
    /**
     * 商品数量
     */
    val quantity: Int,
    
    /**
     * 选中的款式索引
     * null表示未选择款式
     */
    val selectedVariationIndex: Int?,
    
    /**
     * 加入购物车的时间戳(毫秒)
     */
    val timestamp: Long
) : Parcelable {
    
    /**
     * 获取实际价格(根据是否选择款式)
     */
    fun getActualPrice(): Double {
        return if (selectedVariationIndex != null) {
            product.getVariation(selectedVariationIndex)?.price ?: product.price
        } else {
            product.price
        }
    }
    
    /**
     * 获取实际图片URL(根据是否选择款式)
     */
    fun getActualImageUrl(): String {
        return if (selectedVariationIndex != null) {
            product.getVariation(selectedVariationIndex)?.imageUrl ?: product.imageUrl
        } else {
            product.imageUrl
        }
    }
    
    /**
     * 获取款式名称(如果有)
     */
    fun getVariationName(): String? {
        return if (selectedVariationIndex != null) {
            product.getVariation(selectedVariationIndex)?.name
        } else {
            null
        }
    }
    
    /**
     * 获取单价(实际价格)
     */
    fun getUnitPrice(): Double {
        return getActualPrice()
    }
    
    /**
     * 计算小计(价格 × 数量)
     */
    fun getSubtotal(): Double {
        return getActualPrice() * quantity
    }
}