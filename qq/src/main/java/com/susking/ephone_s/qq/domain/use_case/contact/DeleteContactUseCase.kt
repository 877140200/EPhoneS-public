package com.susking.ephone_s.qq.domain.use_case.contact

import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import javax.inject.Inject

/**
 * 删除联系人的UseCase。
 * 旧原子事件已改为只读纪念记录，删除联系人时不再删除旧记忆数据。
 */
class DeleteContactUseCase @Inject constructor(
    private val personProfileRepository: PersonProfileRepository,
    private val chatRepository: ChatRepository,
    private val heartbeatRepository: HeartbeatRepository,
    private val jottingRepository: JottingRepository,
    private val feedRepository: FeedRepository
) {
    suspend operator fun invoke(contactId: String): Result<Unit> {
        return try {
            // 删除联系人
            personProfileRepository.deletePersonProfile(contactId)

            // 删除关联数据；旧原子事件只读保留，不随联系人删除流程清除。
            chatRepository.deleteMessagesForContact(contactId)
            heartbeatRepository.deleteHeartbeatsForContact(contactId)
            jottingRepository.deleteJottingsForContact(contactId)
            feedRepository.deleteFeedsForContact(contactId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
