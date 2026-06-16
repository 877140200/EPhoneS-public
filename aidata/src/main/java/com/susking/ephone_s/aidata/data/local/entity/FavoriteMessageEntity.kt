package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "QQfavorites")
data class FavoriteMessageEntity(
    @PrimaryKey val messageId: String,
    val contactId: String, // 新增：关联的联系人ID
    val text: String?,
    val content: String? = null,
    val senderName: String, // 消息发送者的名字
    val senderAvatar: String? = null, // 发送者头像
    val source: String? = null, // 收藏来源
    val timestamp: Long, // 原始消息的时间戳
    val imageUrl: String? = null,
    val type: String, // 消息类型, e.g., "text", "image", "sticker"
    val stickerUrl: String? = null,
    val stickerName: String? = null,
    val amount: Double? = null,
    val productInfo: String? = null,
    val notes: String? = null,
    val status: String? = null,
    val greeting: String? = null,
    val recipientName: String? = null
)