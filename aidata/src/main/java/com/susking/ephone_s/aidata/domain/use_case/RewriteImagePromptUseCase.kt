package com.susking.ephone_s.aidata.domain.use_case

import android.content.Context
import com.google.gson.GsonBuilder
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import com.susking.ephone_s.aidata.prompt.ChatCompletionRequest
import com.susking.ephone_s.aidata.prompt.OfflinePromptBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * AI重写图片提示词的UseCase
 * 根据场景描述和特殊要求重写提示词
 */
class RewriteImagePromptUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        messageId: String,
        contactId: String,
        specialRequirements: String?,
        includeOriginalPrompt: Boolean
    ): Result<String> {
        return try {
            val chatHistory = chatRepository.getMessagesForContact(contactId).first()
            val imageMessageIndex = chatHistory.indexOfFirst { it.id == messageId }

            if (imageMessageIndex == -1) {
                return Result.failure(Exception("消息不存在"))
            }

            val originalMessage = chatHistory[imageMessageIndex]
            val originalPrompt = originalMessage.content
                ?: return Result.failure(Exception("缺少原始提示词"))

            val sceneDescription = findSceneDescription(chatHistory, imageMessageIndex)
                ?: return Result.failure(Exception("找不到场景描述"))

            val rewrittenPrompt = requestAiRewrite(
                originalPrompt,
                sceneDescription,
                specialRequirements,
                includeOriginalPrompt
            )

            Result.success(rewrittenPrompt)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findSceneDescription(
        chatHistory: List<ChatMessage>,
        imageMessageIndex: Int
    ): String? {
        if (imageMessageIndex > 0) {
            val precedingMessage = chatHistory[imageMessageIndex - 1]
            if (precedingMessage.type == "offline_text" &&
                precedingMessage.aiTurnId == chatHistory[imageMessageIndex].aiTurnId) {
                return precedingMessage.content
            }
        }

        val turnId = chatHistory[imageMessageIndex].aiTurnId
        if (turnId != null) {
            return chatHistory.find {
                it.aiTurnId == turnId && it.type == "offline_text"
            }?.content
        }

        return null
    }

    private suspend fun requestAiRewrite(
        originalPrompt: String,
        sceneDescription: String,
        specialRequirements: String?,
        includeOriginalPrompt: Boolean
    ): String {
        val apiUrl = AiDataApi.getSettingsRepository().getMainApiUrl()
        val model = AiDataApi.getSettingsRepository().getMainModel()
        val temperature = AiDataApi.getSettingsRepository().getApiTemperature()

        val rewriteMessages = OfflinePromptBuilder.buildRerollImagePrompt(
            originalPrompt,
            sceneDescription,
            specialRequirements,
            includeOriginalPrompt
        )

        val requestBody = ChatCompletionRequest(
            model = model,
            messages = rewriteMessages,
            temperature = temperature
        )

        val fullUrl = "$apiUrl/v1/chat/completions"
        val displayJson = GsonBuilder().setPrettyPrinting().create().toJson(requestBody)
        val promptRequest = AiPromptRequest(requestBody, fullUrl, displayJson, System.currentTimeMillis())

        return AiDataApi.getAiRequestService().getChatCompletion(context, promptRequest)?.trim()
            ?: throw Exception("AI未返回重写结果")
    }
}