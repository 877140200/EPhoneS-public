package com.susking.ephone_s.aidata.domain.service

/**
 * 事实图谱抽取服务。
 */
interface MemoryFactGraphExtractionService {
    /**
     * 保存从聊天记录直接抽取出的结构化事件、节点和关系。
     */
    suspend fun saveChatFactGraphResponse(contactId: String, response: String): Result<ExtractionResult>

    /**
     * 保存从视频通话记录直接抽取出的结构化事件、节点和关系。
     *
     * 视频通话抽取结果必须至少包含一个事件，避免把空结果写入长期记忆链路。
     */
    suspend fun saveVideoCallFactGraphResponse(
        contactId: String,
        videoCallId: Long?,
        transcript: String,
        response: String
    ): Result<ExtractionResult>

    /**
     * 图谱抽取结果。
     */
    data class ExtractionResult(
        val contactId: String,
        val eventCount: Int,
        val nodeCount: Int,
        val relationCount: Int
    )
}
