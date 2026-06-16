package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 商品领域模型
 * 
 * 用于在应用层传递商品数据
 */
@Parcelize
data class ShoppingProduct(
    /**
     * 商品ID
     */
    val id: Long,
    
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
    val description: String,
    
    /**
     * 商品主图URL
     */
    val imageUrl: String,
    
    /**
     * 所属分类ID
     * null表示未分类
     */
    val categoryId: Long?,
    
    /**
     * 商品款式列表
     */
    val variations: List<ProductVariation>,
    
    /**
     * 关联的联系人ID
     * 用于区分不同联系人的商品库
     */
    val contactId: String? = null,
    
    /**
     * 创建时间戳(毫秒)
     */
    val timestamp: Long
) : Parcelable {
    
    /**
     * 检查商品是否有款式
     */
    fun hasVariations(): Boolean = variations.isNotEmpty()
    
    /**
     * 获取指定索引的款式
     */
    fun getVariation(index: Int): ProductVariation? {
        return variations.getOrNull(index)
    }
}