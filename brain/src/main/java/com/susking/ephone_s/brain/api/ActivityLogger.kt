package com.susking.ephone_s.brain.api

import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus

/**
 * AI活动日志记录接口，供外部模块调用。
 * 这是brain模块对外暴露的核心API之一。
 */
interface ActivityLogger {
    
    /**
     * 记录一个AI活动。
     * @param activityChainId 活动链ID，用于关联同一系列活动
     * @param description 活动描述
     * @param prompt 发送的提示词
     * @param rawResponse AI的原始回复
     * @param status 活动状态
     */
    suspend fun log(
        activityChainId: String,
        description: String,
        prompt: String,
        rawResponse: String,
        status: AiActivityStatus
    )
    
    /**
     * 记录一个AI活动（使用AiActivity对象）。
     */
    suspend fun log(activity: AiActivity)
}