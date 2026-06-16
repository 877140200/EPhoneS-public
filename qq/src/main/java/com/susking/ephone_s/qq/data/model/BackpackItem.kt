package com.susking.ephone_s.qq.data.model

/**
 * 背包物品数据模型
 * 
 * 用于在UI层展示背包物品信息
 */
data class BackpackItem(
    /**
     * 物品ID
     */
    val id: Long,
    
    /**
     * 商品名称
     */
    val productName: String,
    
    /**
     * 商品图片URL
     */
    val imageUrl: String,
    
    /**
     * 商品价值(元)
     */
    val price: Double,
    
    /**
     * 物品来源
     * 例如: "购物商城", "礼物", "活动奖励" 等
     */
    val source: String,
    
    /**
     * 获得时间戳(毫秒)
     */
    val obtainedTime: Long,
    
    /**
     * 关联的订单ID(可选)
     */
    val orderId: Long? = null,
    
    /**
     * 是否已丢弃
     */
    val isDiscarded: Boolean = false,
    
    /**
     * 操作类型
     * "normal": 正常持有中
     * "discarded": 已丢弃
     * "gifted": 已赠送
     * "sold": 已卖出
     */
    val operationType: String = "normal",
    
    /**
     * 操作时间戳(毫秒)
     * 记录丢弃/赠送/卖出的时间
     */
    val operationTime: Long? = null,
    
    /**
     * 赠送目标
     * 当operationType="gifted"时,记录赠送给谁
     */
    val giftRecipient: String? = null
) {
    /**
     * 获取格式化的价格字符串
     */
    fun getFormattedPrice(): String {
        return "¥%.2f".format(price)
    }
    
    /**
     * 获取格式化的获得时间
     */
    fun getFormattedObtainedTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        return sdf.format(java.util.Date(obtainedTime))
    }
    
    /**
     * 获取格式化的操作时间
     */
    fun getFormattedOperationTime(): String {
        return if (operationTime != null) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            sdf.format(java.util.Date(operationTime))
        } else {
            ""
        }
    }
    
    /**
     * 获取操作类型的中文描述
     */
    fun getOperationTypeText(): String {
        return when (operationType) {
            "normal" -> "持有中"
            "discarded" -> "已丢弃"
            "gifted" -> "已赠送"
            "sold" -> "已卖出"
            else -> "未知"
        }
    }
}