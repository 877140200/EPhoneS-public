package com.susking.ephone_s.aidata.domain.model

data class Notification(
    val id: Long,
    val senderNickname: String,
    val senderAvatarUrl: String,
    val content: String,
    val originalFeedContent: String,
    val timestamp: String,
    val type: NotificationType,
    val feedId: Long,
    val commentId: Long? = null,
    val isRead: Boolean = false
)

enum class NotificationType {
    LIKE,
    COMMENT
}