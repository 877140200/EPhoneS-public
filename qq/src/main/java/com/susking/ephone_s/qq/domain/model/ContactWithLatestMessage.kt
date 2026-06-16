package com.susking.ephone_s.qq.domain.model

import com.susking.ephone_s.aidata.domain.model.PersonProfile

/**
 * 联系人及其最新消息的包装类
 * 用于在UI层显示联系人列表时附带最新消息信息
 */
data class ContactWithLatestMessage(
    val profile: PersonProfile,
    val latestMessage: String? = null,
    val latestMessageTime: String? = null,
    val latestMessageType: String? = "text",
    val latestMessageTimestamp: Long = 0L
)