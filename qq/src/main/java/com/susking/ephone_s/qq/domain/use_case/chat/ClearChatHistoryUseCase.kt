package com.susking.ephone_s.qq.domain.use_case.chat

import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import javax.inject.Inject

/**
 * 清空聊天记录的UseCase。
 * 旧原子事件已改为只读纪念记录，清空聊天记录时不再删除旧记忆数据。
 */
class ClearChatHistoryUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val heartbeatRepository: HeartbeatRepository,
    private val jottingRepository: JottingRepository,
    private val personProfileRepository: PersonProfileRepository
) {
    suspend operator fun invoke(contactId: String): Result<Unit> {
        return try {
            // 清除聊天消息
            chatRepository.deleteMessagesForContact(contactId)

            // 清除心声和散记；旧原子事件只读保留，不随聊天记录清空删除。
            heartbeatRepository.deleteHeartbeatsForContact(contactId)
            jottingRepository.deleteJottingsForContact(contactId)

            // 重置联系人状态
            val contact = personProfileRepository.getPersonProfileById(contactId)
            if (contact != null) {
                val updatedContact = contact.copy(status = "在线")
                personProfileRepository.updatePersonProfile(updatedContact)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
