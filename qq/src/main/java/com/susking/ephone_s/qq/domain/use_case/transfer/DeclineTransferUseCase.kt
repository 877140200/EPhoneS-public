package com.susking.ephone_s.qq.domain.use_case.transfer

import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ActionRepository
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import java.math.BigDecimal
import javax.inject.Inject

/**
 * 拒绝转账的UseCase
 * 处理转账退款和消息状态更新
 */
class DeclineTransferUseCase @Inject constructor(
    private val alipayRepository: AlipayRepository,
    private val chatRepository: ChatRepository,
    private val actionRepository: ActionRepository,
    private val gson: Gson
) {
    suspend operator fun invoke(message: ChatMessage): Result<Unit> {
        return try {
            // 获取AI的个人信息
            val personProfile = actionRepository.getPersonProfile(message.contactId)
            val aiName = personProfile?.remarkName ?: personProfile?.realName ?: "对方"
            
            // AI给用户转账,用户退款时不需要做任何金额操作
            // 因为用户从未收到这笔钱(只是pending状态)
            
            val newContentMap = mapOf(
                "type" to "decline_transfer",
                "for_timestamp" to message.timestamp
            )
            val newContentJson = gson.toJson(newContentMap)

            val updatedMessage = message.copy(
                type = "decline_transfer",
                content = newContentJson,
                status = "declined",
                timestamp = message.timestamp
            )
            chatRepository.updateMessage(updatedMessage)

            // 添加系统消息通知用户
            val systemMessage = ChatMessage(
                contactId = message.contactId,
                type = "pat_message",
                content = "你已退还转账",
                role = "user",
                timestamp = System.currentTimeMillis()
            )
            chatRepository.insertMessage(systemMessage)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
