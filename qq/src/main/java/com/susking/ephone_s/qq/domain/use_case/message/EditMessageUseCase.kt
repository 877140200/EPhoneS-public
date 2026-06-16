package com.susking.ephone_s.qq.domain.use_case.message

import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 编辑消息的UseCase
 * 更新消息内容并同步相关数据
 */
class EditMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        messageId: String,
        contactId: String,
        newText: String
    ): Result<ChatMessage> {
        return try {
            val chatHistory = chatRepository.getMessagesForContact(contactId).first()
            val originalMessage = chatHistory.find { it.id == messageId }
                ?: return Result.failure(Exception("消息不存在"))

            val updatedMessage = originalMessage.copy(content = newText)
            chatRepository.updateMessage(updatedMessage)

            Result.success(updatedMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更新表情消息的URL
     */
    suspend fun updateStickerUrl(
        messageId: String,
        contactId: String,
        newUrl: String,
        newName: String
    ): Result<ChatMessage> {
        return try {
            val chatHistory = chatRepository.getMessagesForContact(contactId).first()
            val originalMessage = chatHistory.find { it.id == messageId }
                ?: return Result.failure(Exception("消息不存在"))

            val updatedMessage = originalMessage.copy(
                stickerUrl = newUrl,
                stickerName = newName,
                content = "[表情：$newName]"
            )

            chatRepository.updateMessage(updatedMessage)
            Result.success(updatedMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
