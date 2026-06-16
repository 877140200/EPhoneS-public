package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * 支付宝钱包实体
 */
@Entity(tableName = "alipay_wallets")
data class AlipayWalletEntity(
    @PrimaryKey val userId: String = "user_main",
    val balance: BigDecimal
)