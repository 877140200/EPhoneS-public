package com.susking.ephone_s.qq.domain.use_case.message

import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * 删除消息的UseCase
 * 处理单条消息和拍一拍消息组的删除逻辑
 */
class DeleteMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(message: ChatMessage): Result<Unit> {
        return try {
            if (message.type == "pat_message" && message.actionId != null) {
                // 删除所有具有相同actionId的消息
                chatRepository.deleteMessagesByActionId(message.actionId!!)
            } else {
                chatRepository.deleteMessage(message)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
