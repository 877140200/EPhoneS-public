package com.susking.ephone_s.qq.domain.use_case.contact

import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import java.util.UUID
import javax.inject.Inject

/**
 * 拍一拍动作的UseCase
 * 创建可见和隐藏的系统消息以触发AI响应
 */
class PerformPatActionUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        contactId: String,
        userNickname: String,
        displayNameForUI: String,
        suffix: String
    ): Result<Unit> {
        return try {
            val patActionId = UUID.randomUUID().toString()

            // 创建可见的系统消息
            val visibleMessage = ChatMessage(
                contactId = contactId,
                role = "user",
                type = "pat_message",
                content = "[系统提示：$userNickname 拍了拍 “$displayNameForUI” ${suffix.trim()}]",
                actionId = patActionId
            )
            chatRepository.insertMessage(visibleMessage)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
