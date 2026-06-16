package com.susking.ephone_s.qq.domain.use_case.contact

import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import javax.inject.Inject

/**
 * 同意好友申请的UseCase
 * 更新消息状态并解除拉黑
 */
class AcceptFriendRequestUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personProfileRepository: PersonProfileRepository
) {
    suspend operator fun invoke(message: ChatMessage): Result<Unit> {
        return try {
            // 更新消息状态
            val updatedMessage = message.copy(status = "accepted")
            chatRepository.updateMessage(updatedMessage)

            // 解除拉黑
            val contact = personProfileRepository.getPersonProfileById(message.contactId)
            if (contact != null && contact.isBlocked) {
                val unblockedContact = contact.copy(
                    isBlocked = false,
                    blockTimestamp = null,
                    blockCooldownHours = 0.0,
                    applicationReason = null
                )
                personProfileRepository.updatePersonProfile(unblockedContact)
            }

            // 插入系统消息
            val systemMessage = ChatMessage(
                contactId = message.contactId,
                type = "system",
                content = "你同意了对方的好友申请，现在你们可以开始聊天了。",
                role = "user"
            )
            chatRepository.insertMessage(systemMessage)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
