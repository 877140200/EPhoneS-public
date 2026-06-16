package com.susking.ephone_s.qq.domain.use_case.transfer

import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ActionRepository
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import java.math.BigDecimal
import javax.inject.Inject

/**
 * 接受转账的UseCase
 * 处理转账接收和消息状态更新
 */
class AcceptTransferUseCase @Inject constructor(
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
            
            val transferAmount = message.amount?.let { BigDecimal(it) } ?: BigDecimal.ZERO
            alipayRepository.performTransaction(
                transferAmount,
                "收款",
                "收到 ${aiName} 的转账"
            )

            val newContentMap = mapOf(
                "type" to "accept_transfer",
                "for_timestamp" to message.timestamp
            )
            val newContentJson = gson.toJson(newContentMap)

            val updatedMessage = message.copy(
                type = "accept_transfer",
                content = newContentJson,
                status = "accepted",
                timestamp = message.timestamp
            )
            chatRepository.updateMessage(updatedMessage)

            // 添加系统消息通知用户
            val systemMessage = ChatMessage(
                contactId = message.contactId,
                type = "pat_message",
                content = "你已收款",
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
