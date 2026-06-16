package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * 代表被引用消息的简化数据结构。
 * @param messageId 被引用消息的ID
 * @param senderName 发送者名称
 * @param content 消息内容的预览
 */
@Parcelize
data class QuotedMessage(
    val messageId: String,
    val senderName: String,
    val content: String
) : Parcelable

/**
 * 聊天消息的数据类
 * @param id 消息ID
 * @param contactId 关联的联系人ID
 * @param content 消息内容
 * @param timestamp 消息时间戳
 * @param role 角色: "user", "assistant", or "system"
 */
@Parcelize
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "text",
    val contactId: String,
    val content: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val role: String,
    val imageUrl: String? = null,
    val imageDescription: String? = null, // AI生成的图片描述，用于替代Base64发送
    val stickerUrl: String? = null,
    val stickerName: String? = null,
    val voiceAudioPath: String? = null,
    val voiceDurationMillis: Long? = null,
    val ttsGenerationStatus: String? = null,
    val ttsModelId: String? = null,
    val ttsVoiceId: String? = null,
    val ttsGeneratedAt: Long? = null,
    val ttsErrorMessage: String? = null,
    val ttsIsStreaming: Boolean? = null,
    val productInfo: String? = null,
    val amount: Double? = null,
    var status: String? = null,
    val greeting: String? = null,
    val senderName: String? = null,
    val recipientName: String? = null,
    val notes: String? = null,

    // --- 礼物相关字段 ---
    val giftItemId: Long? = null,
    val giftName: String? = null,
    val giftImageUrl: String? = null,
    val giftValue: Double? = null,
    val giftNote: String? = null,

    // --- 线下见面相关字段 ---
    val offlineLocation: String? = null,
    val offlineReason: String? = null,

    // --- 引用消息字段 ---
    val quotedMessage: QuotedMessage? = null,

    // --- 版本控制字段 ---
    val aiResponseVersions: List<String> = emptyList(),
    val displayedResponseIndex: Int = 0,

    // --- 撤回相关字段 ---
    val isRecalled: Boolean = false,
    val recalledContent: String? = null,
    val recallTimestamp: Long? = null,

    // --- 其他元数据 ---
    val aiTurnId: String? = null,
    val isHidden: Boolean = false,
    val actionId: String? = null,
    val isFavorited: Boolean = false,

    // --- 自动回复相关字段 ---
    // 用户新消息默认未被 AI 看见；旧消息、导入消息、AI 消息和系统消息默认已看见。
    val hasBeenSeenByAi: Boolean = true
) : Parcelable
