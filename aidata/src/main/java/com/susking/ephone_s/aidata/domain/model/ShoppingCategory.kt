package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 商品分类领域模型
 *
 * 用于在应用层传递商品分类数据
 * 每个分类绑定到特定的联系人(角色)
 */
@Parcelize
data class ShoppingCategory(
    /**
     * 分类ID
     */
    val id: Long,
    
    /**
     * 分类名称
     */
    val name: String,
    
    /**
     * 所属联系人ID(角色ID)
     */
    val contactId: String,
    
    /**
     * 创建时间戳(毫秒)
     */
    val timestamp: Long
) : Parcelable