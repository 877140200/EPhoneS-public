package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.PromptContext
import com.susking.ephone_s.aidata.domain.model.UserProfile

/**
 * 提示词上下文 Repository 接口
 * 这是 aidata 模块最核心的接口
 * brain 模块每次发起 AI 请求时调用此接口获取完整的上下文数据
 */
interface PromptContextRepository {
    
    /**
     * 构建完整的提示词上下文
     * 
     * @param contactId 联系人ID
     * @param userProfile 用户档案
     * @param isPropel 是否推进模式
     * @param lastCallFailureReason 上次通话失败理由
     * @param enableNovelAi 是否启用NovelAI
     * @return 包含所有AI需要数据的PromptContext
     */
    suspend fun buildPromptContext(
        contactId: String,
        userProfile: UserProfile,
        isPropel: Boolean = false,
        lastCallFailureReason: String? = null,
        enableNovelAi: Boolean = false
    ): PromptContext
}