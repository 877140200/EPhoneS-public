package com.susking.ephone_s.cphone.domain.model

/**
 * 模拟QQ对话列表数据模型
 * 
 * @property id 对话唯一标识
 * @property conversationType 对话类型：private（私聊）、group（群聊）
 * @property name 联系人/群组名称
 * @property avatarPrompt 头像提示词
 * @property avatarUrl 头像URL
 * @property messages 消息列表
 * @property lastMessage 最后一条消息摘要
 * @property timestamp 时间戳
 */
data class SimulatedQQConversation(
    val id: String,
    val conversationType: String,
    val name: String,
    val avatarPrompt: String? = null,
    val avatarUrl: String? = null,
    val messages: List<SimulatedQQMessage>,
    val lastMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 模拟QQ消息数据模型
 * 
 * @property id 消息唯一标识
 * @property senderId 发送者ID
 * @property senderName 发送者名称
 * @property content 消息内容
 * @property messageType 消息类型：text、image、sticker等
 * @property timestamp 时间戳
 */
data class SimulatedQQMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val messageType: String = "text",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 模拟QQ群聊成员数据模型
 * 
 * @property id 成员唯一标识
 * @property name 成员名称
 * @property avatarPrompt 头像提示词
 * @property avatarUrl 头像URL
 */
data class SimulatedQQGroupMember(
    val id: String,
    val name: String,
    val avatarPrompt: String? = null,
    val avatarUrl: String? = null
)