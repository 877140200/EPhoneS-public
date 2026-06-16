package com.susking.ephone_s.brain.api

import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus
import com.susking.ephone_s.brain.domain.repository.BrainRepository

/**
 * ActivityLogger接口的实现类。
 */
class ActivityLoggerImpl(
    private val repository: BrainRepository
) : ActivityLogger {
    
    override suspend fun log(
        activityChainId: String,
        description: String,
        prompt: String,
        rawResponse: String,
        status: AiActivityStatus
    ) {
        val activity = AiActivity(
            activityChainId = activityChainId,
            description = description,
            prompt = prompt,
            rawResponse = rawResponse,
            timestamp = System.currentTimeMillis(),
            status = status
        )
        repository.logActivity(activity)
    }
    
    override suspend fun log(activity: AiActivity) {
        repository.logActivity(activity)
    }
}