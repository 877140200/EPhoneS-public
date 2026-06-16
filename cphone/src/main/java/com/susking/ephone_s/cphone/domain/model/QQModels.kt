package com.susking.ephone_s.cphone.domain.model

/**
 * QQ模拟对话领域模型
 * 
 * @property id 对话唯一标识
 * @property isGroup 是否为群聊
 * @property participantName 参与者名称（对于私聊是联系人名，对于群聊是群名）
 * @property participantAvatar 参与者头像URL（可选）
 * @property messages 消息列表
 * @property lastMessagePreview 最后一条消息预览
 * @property timestamp 最后更新时间戳
 */
data class SimulatedConversation(
    val id: String,
    val isGroup: Boolean,
    val participantName: String,
    val participantAvatar: String?,
    val messages: List<SimulatedMessage>,
    val lastMessagePreview: String,
    val timestamp: Long
)

/**
 * QQ模拟消息
 * 
 * @property id 消息唯一标识
 * @property senderId 发送者ID
 * @property senderName 发送者名称
 * @property content 消息内容
 * @property messageType 消息类型（text、image、sticker等）
 * @property timestamp 发送时间戳
 */
data class SimulatedMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val messageType: String,
    val timestamp: Long
)