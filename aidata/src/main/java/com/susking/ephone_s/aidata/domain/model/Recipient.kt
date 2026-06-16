package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 收礼人信息领域模型
 * 
 * 表示订单的收礼人联系信息
 */
@Parcelize
data class Recipient(
    /**
     * 收礼人姓名
     */
    val name: String,
    
    /**
     * 收礼人电话
     */
    val phone: String,
    
    /**
     * 收货地址
     */
    val address: String
) : Parcelable