package com.susking.ephone_s.qq.domain.use_case.contact

import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import javax.inject.Inject

/**
 * 切换联系人拉黑状态的UseCase
 */
class ToggleBlockStatusUseCase @Inject constructor(
    private val personProfileRepository: PersonProfileRepository,
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(contactId: String): Result<Unit> {
        return try {
            val contact = personProfileRepository.getPersonProfileById(contactId)
                ?: return Result.failure(Exception("联系人不存在"))

            val updatedContact = if (!contact.isBlocked) {
                val cooldownHours = AiDataApi.getSettingsRepository().getAiCooldownPeriod().toDouble()
                contact.copy(
                    isBlocked = true,
                    blockTimestamp = System.currentTimeMillis(),
                    blockCooldownHours = cooldownHours
                )
            } else {
                contact.copy(
                    isBlocked = false,
                    blockTimestamp = null,
                    blockCooldownHours = 0.0
                )
            }

            personProfileRepository.updatePersonProfile(updatedContact)

            // 插入系统消息
            val systemMessage = ChatMessage(
                contactId = contactId,
                content = if (updatedContact.isBlocked) "你已将对方拉黑" else "你已将对方解除拉黑",
                type = "system",
                role = "user"
            )
            chatRepository.insertMessage(systemMessage)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
