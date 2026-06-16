package com.susking.ephone_s.qq.domain.use_case.contact

import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * 拒绝好友申请的UseCase
 * 更新消息状态并记录系统消息
 */
class DeclineFriendRequestUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(message: ChatMessage): Result<Unit> {
        return try {
            val updatedMessage = message.copy(status = "declined")
            chatRepository.updateMessage(updatedMessage)

            val systemMessage = ChatMessage(
                contactId = message.contactId,
                type = "system",
                content = "你拒绝了对方的好友申请。",
                role = "user"
            )
            chatRepository.insertMessage(systemMessage)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
