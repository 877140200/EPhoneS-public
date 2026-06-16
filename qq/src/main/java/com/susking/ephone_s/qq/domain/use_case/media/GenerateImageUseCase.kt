package com.susking.ephone_s.qq.domain.use_case.media

import com.google.gson.Gson
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.brain.service.NovelAiService
import javax.inject.Inject

class GenerateImageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val gson: Gson
) {
    sealed class GenerateResult {
        data class Success(val imageUrl: String) : GenerateResult()
        data class Failure(val error: String) : GenerateResult()
    }

    suspend operator fun invoke(
        prompt: String,
        contactId: String,
        messageId: String? = null
    ): Result<GenerateResult> {
        return try {
            val contact = personProfileRepository.getPersonProfileById(contactId)
                ?: return Result.failure(Exception("联系人不存在"))

            // 生成图片(返回Base64)
            val imageBase64 = NovelAiService.generateImage(prompt, contact, gson)

            // 立即转换为文件路径
            val saveImageUseCase = AiDataApi.getSaveImageUseCase()
            val filePath = if (imageBase64 != null) {
                saveImageUseCase(imageBase64)
            } else {
                null
            }

            // 如果提供了messageId,则更新对应的消息
            if (messageId != null && filePath != null) {
                updateMessageWithImage(messageId, contactId, filePath)
            }

            // 确保返回非空的filePath
            if (filePath != null) {
                Result.success(GenerateResult.Success(filePath))
            } else {
                Result.success(GenerateResult.Failure("图片生成或保存失败"))
            }
        } catch (e: Exception) {
            Result.success(GenerateResult.Failure(e.message ?: "生成失败"))
        }
    }

    private suspend fun updateMessageWithImage(
        messageId: String,
        contactId: String,
        imageUrl: String
    ) {
        // 此逻辑将在ViewModel中实现,UseCase只负责生成
    }
}
