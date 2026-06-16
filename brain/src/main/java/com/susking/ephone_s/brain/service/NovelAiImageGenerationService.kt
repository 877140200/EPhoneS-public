package com.susking.ephone_s.brain.service

import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.service.ImageGenerationService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NovelAI图片生成服务实现类
 * 实现了ImageGenerationService接口,提供基于NovelAI的图片生成能力
 */
@Singleton
class NovelAiImageGenerationService @Inject constructor(
    private val gson: Gson
) : ImageGenerationService {
    
    /**
     * 使用NovelAI API生成图片
     * @param prompt 图片生成提示词
     * @param personProfile 角色配置信息
     * @return Base64编码的图片数据,失败返回null
     */
    override suspend fun generateImage(
        prompt: String,
        personProfile: PersonProfile
    ): String? {
        return NovelAiService.generateImage(prompt, personProfile, gson)
    }
}