package com.susking.ephone_s.qq.domain.use_case.chat

import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.QuotedMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicyStore
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

/**
 * 发送消息的UseCase
 * 封装了消息发送的所有业务逻辑,包括余额检查、消息创建和持久化
 */
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val alipayRepository: AlipayRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val followUpPolicyStore: FollowUpPolicyStore
) {
    suspend operator fun invoke(
        contactId: String,
        text: String? = null,
        imageUrl: String? = null,
        stickerUrl: String? = null,
        stickerName: String? = null,
        type: String? = null,
        amount: Double? = null,
        notes: String? = null,
        productInfo: String? = null,
        voiceAudioPath: String? = null,
        voiceDurationMillis: Long? = null,
        quotedMessage: QuotedMessage? = null
    ): Result<ChatMessage> {
        return try {
            // 确定消息类型
            val messageType = type ?: when {
                stickerUrl != null -> "sticker"
                imageUrl != null -> if (imageUrl.startsWith("http")) "ai_image" else "image"
                else -> "text"
            }

            // 转账和外卖订单前检查余额并扣款
            if (messageType == "transfer" || messageType == "waimai_order") {
                val wallet = alipayRepository.getWalletInfo("user_main").first()
                val currentBalance = wallet?.balance ?: BigDecimal.ZERO
                val payAmount = amount?.let { BigDecimal(it) } ?: BigDecimal.ZERO

                if (currentBalance < payAmount) {
                    return Result.failure(Exception("余额不足"))
                }

                val recipientName = getRecipientName(contactId)
                val description = if (messageType == "transfer") {
                    "向 $recipientName 转账"
                } else {
                    "为 $recipientName 点单: $productInfo"
                }
                alipayRepository.performTransaction(-payAmount, if (messageType == "transfer") "转账" else "外卖", description)
            }

            // 创建消息
            val message = ChatMessage(
                contactId = contactId,
                type = messageType,
                content = text,
                role = "user",
                imageUrl = imageUrl,
                stickerUrl = stickerUrl,
                stickerName = stickerName,
                amount = amount,
                notes = notes,
                productInfo = productInfo,
                voiceAudioPath = voiceAudioPath,
                voiceDurationMillis = voiceDurationMillis,
                quotedMessage = quotedMessage,
                status = if (messageType == "transfer" || messageType == "waimai_request") "pending" else null,
                hasBeenSeenByAi = false
            )

            chatRepository.insertMessage(message)
            followUpPolicyStore.resetAfterUserMessage(contactId)
            
            // 发送消息时自动取消隐藏联系人
            val contact = personProfileRepository.getPersonProfileById(contactId)
            if (contact?.isHiddenFromChatList == true) {
                personProfileRepository.savePersonProfiles(
                    listOf(contact.copy(isHiddenFromChatList = false))
                )
            }
            
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getRecipientName(contactId: String): String {
        val contact = personProfileRepository.getPersonProfileById(contactId)
        return contact?.remarkName ?: contact?.realName ?: "对方"
    }
}