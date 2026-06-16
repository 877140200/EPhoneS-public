package com.susking.ephone_s.aidata.domain.alipay

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 账单记录领域模型
 */
data class BillRecord(
    val id: Long,                            // 账单ID
    val timestamp: Long,                     // 时间戳
    val amount: BigDecimal,                  // 金额
    val type: String,                        // 交易类型
    val description: String,                 // 描述
    val relatedContactId: String? = null     // 关联联系人ID
) {
    /**
     * 判断是否为收入
     */
    fun isIncome(): Boolean = amount > BigDecimal.ZERO
    
    /**
     * 判断是否为支出
     */
    fun isExpense(): Boolean = amount < BigDecimal.ZERO
    
    /**
     * 获取金额绝对值
     */
    fun getAbsAmount(): BigDecimal = amount.abs()
    
    /**
     * 格式化金额显示
     */
    fun getFormattedAmount(): String {
        val prefix = if (isIncome()) "+" else ""
        return String.format("%s¥%,.2f", prefix, amount)
    }
    
    /**
     * 格式化时间显示
     */
    fun getFormattedTime(): String {
        val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * 格式化日期显示(年月日)
     */
    fun getFormattedDate(): String {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * 获取交易类型的显示文本
     */
    fun getTypeDisplayText(): String {
        return when (type) {
            "transfer_in" -> "转账收入"
            "transfer_out" -> "转账支出"
            "shopping" -> "购物消费"
            "red_packet_in" -> "红包收入"
            "red_packet_out" -> "红包支出"
            "refund" -> "退款"
            "recharge" -> "充值"
            else -> type
        }
    }
}