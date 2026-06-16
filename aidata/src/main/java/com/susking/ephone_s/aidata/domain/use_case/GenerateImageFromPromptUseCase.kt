package com.susking.ephone_s.aidata.domain.use_case

import com.susking.ephone_s.aidata.domain.model.ImageGenerationParams
import com.susking.ephone_s.aidata.domain.service.ImageGenerationService
import javax.inject.Inject

/**
 * 通用图片生成UseCase
 * 负责协调图片生成和保存流程,可被多个模块复用
 */
class GenerateImageFromPromptUseCase @Inject constructor(
    private val imageGenerationService: ImageGenerationService,
    private val saveImageUseCase: SaveImageFromBase64UseCase
) {
    /**
     * 执行图片生成
     * @param params 图片生成参数
     * @return Result<String> 成功返回图片文件路径,失败返回异常
     */
    suspend operator fun invoke(params: ImageGenerationParams): Result<String> {
        return try {
            // 1. 调用图片生成服务生成图片
            val imageBase64 = imageGenerationService.generateImage(
                prompt = params.prompt,
                personProfile = params.personProfile
            ) ?: return Result.failure(Exception("图片生成失败,返回为空"))

            // 2. 保存图片到本地存储
            val filePath = saveImageUseCase(imageBase64)
                ?: return Result.failure(Exception("图片保存失败"))

            // 3. 返回成功结果
            Result.success(filePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 简化调用版本 - 直接传入提示词和角色配置
     */
    suspend operator fun invoke(
        prompt: String,
        personProfile: com.susking.ephone_s.aidata.domain.model.PersonProfile
    ): Result<String> {
        return invoke(
            ImageGenerationParams(
                prompt = prompt,
                personProfile = personProfile
            )
        )
    }
}