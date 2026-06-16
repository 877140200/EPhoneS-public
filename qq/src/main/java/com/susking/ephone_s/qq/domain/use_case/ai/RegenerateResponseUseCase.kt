package com.susking.ephone_s.qq.domain.use_case.ai

import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 重说AI响应的UseCase
 * 找到最后一个用户消息,重新生成AI回复
 */
class RegenerateResponseUseCase @Inject constructor(
    private val personProfileRepository: PersonProfileRepository,
    private val chatRepository: ChatRepository
) {
    data class RegenerateRequest(
        val prompt: AiPromptRequest,
        val contactId: String,
        val messageIdToUpdate: String?,
        val isOfflineMode: Boolean
    )

    suspend operator fun invoke(contactId: String): Result<RegenerateRequest> {
        return try {
            val contact = personProfileRepository.getPersonProfileById(contactId)
                ?: return Result.failure(Exception("联系人不存在"))

            val messageHistory = chatRepository.getMessagesForContact(contactId).first()

            // 找到最后一个用户消息
            val lastUserMessageIndex = messageHistory.indexOfLast { it.role == "user" }
            if (lastUserMessageIndex == -1) {
                return Result.failure(Exception("未找到用户消息"))
            }

            // 【核心修复】截取历史到最后一个用户消息,不包含之后的AI回复
            val historyForPrompt = messageHistory.subList(0, lastUserMessageIndex + 1)

            // 【语义回退】最后一个用户消息之后的所有 AI 轮次都将被本次重说丢弃。
            // 在构建提示词(会读取语义账本快照)之前,先把这些被丢弃轮造成的语义更新回退一步,
            // 否则新提示词会带上"要丢弃那轮"写进账本的当前互动语义/锚点/线索/关键词,
            // 表现为重说用的是被丢弃轮的结果而非之前的状态。
            val discardedAiTurnIds: Set<String> = messageHistory
                .drop(lastUserMessageIndex + 1)
                .mapNotNull { message -> message.aiTurnId?.takeIf { it.isNotBlank() } }
                .toSet()
            if (discardedAiTurnIds.isNotEmpty()) {
                val reverted: Boolean = AiDataApi.getContactSemanticStateRepository()
                    .revertSemanticStateForTurns(contactId, discardedAiTurnIds)
                android.util.Log.d("RegenerateUseCase", "语义回退: 被丢弃轮=${discardedAiTurnIds.size}, 是否回退=$reverted")
            }

            android.util.Log.d("RegenerateUseCase", "完整历史: ${messageHistory.size} 条")
            android.util.Log.d("RegenerateUseCase", "用于提示词的历史: ${historyForPrompt.size} 条")

            // 确定需要被"重说"的AI消息ID
            // 【关键修复】判定口径必须与 UI 显示对齐：跳过隐藏消息(isHidden=true)。
            // 原因：AI 执行"创建倒计时/回忆"等动作时会追加 role="assistant" 且 isHidden=true 的隐藏系统消息,
            // 它继承了那一轮的 aiTurnId。若直接取 user 后紧邻的消息,会把这条用户看不见的隐藏消息当成"上一次 AI 回复",
            // 导致误入 handleRegenerate 按 turnId 删整轮,把用户能看到的旧回复一起吞掉。
            // 因此这里从 user 之后只找第一条"可见"消息,它是 assistant 才当作要重说的回复;
            // 若可见侧最后一条是 user(只剩隐藏消息),则保持 null,走 handleNewMessages 兜底新生成。
            var messageIdToUpdate: String? = null
            val nextVisibleMessage = messageHistory
                .drop(lastUserMessageIndex + 1)
                .firstOrNull { message -> !message.isHidden }
            if (nextVisibleMessage != null && nextVisibleMessage.role == "assistant") {
                messageIdToUpdate = nextVisibleMessage.id
            }

            // 【修复】构建提示词,传入截取后的历史
            val isOffline = contact.offlineModeEnabled
            val aiPromptService = AiDataApi.getAiPromptService()

            val prompt = if (isOffline) {
                val novelAiEnabled = AiDataApi.getSettingsRepository().isNovelAiEnabled()
                aiPromptService.buildOfflinePrompt(contactId, novelAiEnabled, historyForPrompt)
            } else {
                aiPromptService.buildConversationalPrompt(contactId, isPropel = false, historyForPrompt)
            }

            Result.success(RegenerateRequest(prompt, contactId, messageIdToUpdate, isOffline))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
