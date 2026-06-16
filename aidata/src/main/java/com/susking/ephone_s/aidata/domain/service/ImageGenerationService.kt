package com.susking.ephone_s.aidata.domain.service

import com.susking.ephone_s.aidata.domain.model.PersonProfile

/**
 * 图片生成服务接口
 * 用于解耦具体的图片生成实现(NovelAI、StableDiffusion等)
 */
interface ImageGenerationService {
    /**
     * 根据提示词生成图片
     * @param prompt 图片生成提示词
     * @param personProfile 角色配置信息(用于获取API配置等)
     * @return Base64编码的图片数据,失败返回null
     */
    suspend fun generateImage(
        prompt: String,
        personProfile: PersonProfile
    ): String?
}