package com.susking.ephone_s.aidata.domain.alipay

import java.math.BigDecimal

/**
 * 钱包信息领域模型
 * 用于在UI层展示钱包相关数据
 */
data class WalletInfo(
    val userId: String,                      // 用户ID
    val balance: BigDecimal,                 // 余额
    val lastUpdateTime: Long = System.currentTimeMillis()  // 最后更新时间
) {
    /**
     * 格式化余额显示
     * @return 格式化后的余额字符串,如"1,234.56"
     */
    fun getFormattedBalance(): String {
        return String.format("%,.2f", balance)
    }
}