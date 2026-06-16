package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 商品款式领域模型
 * 
 * 表示商品的不同款式(如颜色、规格等)
 */
@Parcelize
data class ProductVariation(
    /**
     * 款式名称
     */
    val name: String,
    
    /**
     * 款式价格(元)
     */
    val price: Double,
    
    /**
     * 款式图片URL(可选)
     * 如果为null,使用商品主图
     */
    val imageUrl: String? = null
) : Parcelable