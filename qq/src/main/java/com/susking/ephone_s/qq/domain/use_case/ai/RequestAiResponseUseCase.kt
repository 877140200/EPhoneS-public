package com.susking.ephone_s.qq.domain.use_case.ai

import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import javax.inject.Inject

/**
 * 请求AI响应的UseCase
 * 负责构建AI提示词并准备请求
 */
class RequestAiResponseUseCase @Inject constructor(
    private val personProfileRepository: PersonProfileRepository
) {
    suspend operator fun invoke(contactId: String, extraInstruction: String? = null): Result<AiPromptRequest> {
        return try {
            val contact = personProfileRepository.getPersonProfileById(contactId)
                ?: return Result.failure(Exception("联系人不存在"))

            val isOffline = contact.offlineModeEnabled
            val aiPromptService = AiDataApi.getAiPromptService()

            val basePrompt = if (isOffline) {
                val novelAiEnabled = AiDataApi.getSettingsRepository().isNovelAiEnabled()
                aiPromptService.buildOfflinePrompt(contactId, novelAiEnabled)
            } else {
                aiPromptService.buildConversationalPrompt(contactId, isPropel = false)
            }
            val prompt = if (extraInstruction.isNullOrBlank()) {
                basePrompt
            } else {
                appendExtraInstruction(basePrompt, extraInstruction)
            }

            Result.success(prompt)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun appendExtraInstruction(prompt: AiPromptRequest, extraInstruction: String): AiPromptRequest {
        val messages = prompt.request.messages.toMutableList()
        messages.add(com.susking.ephone_s.aidata.prompt.ChatMessagePayload(role = "user", content = extraInstruction))
        val request = prompt.request.copy(messages = messages)
        val displayJson = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(request)
        return prompt.copy(request = request, displayPromptJson = displayJson)
    }
}
