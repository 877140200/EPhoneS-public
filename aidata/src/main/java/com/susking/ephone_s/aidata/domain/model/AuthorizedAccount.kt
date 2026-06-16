package com.susking.ephone_s.aidata.domain.model

/**
 * 已授权账号领域模型
 * 
 * 代表已经授权用户查看其购物商品的联系人
 */
data class AuthorizedAccount(
    /**
     * 联系人ID
     */
    val contactId: String,
    
    /**
     * 授权时间戳
     */
    val authorizedTimestamp: Long,
    
    /**
     * 备注
     */
    val note: String? = null
)