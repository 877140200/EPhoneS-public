package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.QuotedMessage
import java.util.UUID

/**
 * 代表数据库中 "chat_messages" 表的实体类。
 * 我们添加了一个基于 contactId 的索引，以加速按联系人查询消息的操作。
 */
@Entity(tableName = "chat_messages", indices = [Index(value = ["contactId"])])
@TypeConverters(ChatMessageConverters::class)
data class ChatMessageEntity(
    @PrimaryKey
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
    val status: String? = null,
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
    
    val quotedMessage: QuotedMessage? = null, // 新增：用于存储引用消息
    val displayedResponseIndex: Int = 0,
    val aiTurnId: String? = null,
    val isHidden: Boolean = false,
    val actionId: String? = null,
    val hasBeenSeenByAi: Boolean = true,
    
    // --- 撤回相关字段 ---
    val isRecalled: Boolean = false,
    val recalledContent: String? = null,
    val recallTimestamp: Long? = null
) {
    /**
     * 提供一个将数据库实体转换为领域模型的方法。
     * 这有助于保持数据层和领域层的分离。
     */
    fun toDomainModel(): ChatMessage {
        return ChatMessage(
            id = id,
            type = type,
            contactId = contactId,
            content = content,
            timestamp = timestamp,
            role = role,
            imageUrl = imageUrl,
            imageDescription = imageDescription,
            stickerUrl = stickerUrl,
            stickerName = stickerName,
            voiceAudioPath = voiceAudioPath,
            voiceDurationMillis = voiceDurationMillis,
            ttsGenerationStatus = ttsGenerationStatus,
            ttsModelId = ttsModelId,
            ttsVoiceId = ttsVoiceId,
            ttsGeneratedAt = ttsGeneratedAt,
            ttsErrorMessage = ttsErrorMessage,
            ttsIsStreaming = ttsIsStreaming,
            productInfo = productInfo,
            amount = amount,
            status = status,
            greeting = greeting,
            senderName = senderName,
            recipientName = recipientName,
            notes = notes,
            giftItemId = giftItemId,
            giftName = giftName,
            giftImageUrl = giftImageUrl,
            giftValue = giftValue,
            giftNote = giftNote,
            offlineLocation = offlineLocation,
            offlineReason = offlineReason,
            quotedMessage = quotedMessage,
            displayedResponseIndex = displayedResponseIndex,
            aiTurnId = aiTurnId,
            isHidden = isHidden,
            actionId = actionId,
            hasBeenSeenByAi = hasBeenSeenByAi,
            isRecalled = isRecalled,
            recalledContent = recalledContent,
            recallTimestamp = recallTimestamp
        )
    }
}

/**
 * 提供一个将领域模型转换为数据库实体的扩展函数。
 * 这使得在仓库层进行数据转换变得更加方便和清晰。
 */
fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        type = type,
        contactId = contactId,
        content = content,
        timestamp = timestamp,
        role = role,
        imageUrl = imageUrl,
        imageDescription = imageDescription,
        stickerUrl = stickerUrl,
        stickerName = stickerName,
        voiceAudioPath = voiceAudioPath,
        voiceDurationMillis = voiceDurationMillis,
        ttsGenerationStatus = ttsGenerationStatus,
        ttsModelId = ttsModelId,
        ttsVoiceId = ttsVoiceId,
        ttsGeneratedAt = ttsGeneratedAt,
        ttsErrorMessage = ttsErrorMessage,
        ttsIsStreaming = ttsIsStreaming,
        productInfo = productInfo,
        amount = amount,
        status = status,
        greeting = greeting,
        senderName = senderName,
        recipientName = recipientName,
        notes = notes,
        giftItemId = giftItemId,
        giftName = giftName,
        giftImageUrl = giftImageUrl,
        giftValue = giftValue,
        giftNote = giftNote,
        offlineLocation = offlineLocation,
        offlineReason = offlineReason,
        quotedMessage = quotedMessage,
        displayedResponseIndex = displayedResponseIndex,
        aiTurnId = aiTurnId,
        isHidden = isHidden,
        actionId = actionId,
        hasBeenSeenByAi = hasBeenSeenByAi,
        isRecalled = isRecalled,
        recalledContent = recalledContent,
        recallTimestamp = recallTimestamp
    )
}