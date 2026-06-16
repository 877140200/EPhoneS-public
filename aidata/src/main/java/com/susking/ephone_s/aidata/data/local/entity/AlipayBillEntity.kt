package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * 支付宝账单实体
 */
@Entity(tableName = "alipay_bills")
data class AlipayBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val amount: BigDecimal,
    val type: String,
    val description: String,
    val relatedContactId: String? = null
)