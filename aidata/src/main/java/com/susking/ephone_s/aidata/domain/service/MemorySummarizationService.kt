package com.susking.ephone_s.aidata.domain.service

import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import com.susking.ephone_s.aidata.prompt.AiPromptRequest

/**
 * 分层记忆摘要服务。
 * 负责将聊天原文与下级摘要逐层整理为日、周、月、年摘要。
 */
interface MemorySummarizationService {
    suspend fun generateSummaryWindow(contactId: String, level: SummaryLevel, windowStart: Long, windowEnd: Long): MemorySummary?
    suspend fun buildSummaryWindowPrompt(contactId: String, level: SummaryLevel, windowStart: Long, windowEnd: Long): AiPromptRequest?
    suspend fun regenerateSummaryWindow(summary: MemorySummary): MemorySummary?
    suspend fun repairDefaultImportanceSummaries(contactId: String): Int

    /**
     * 为外部已落库的分层摘要补建向量索引。
     *
     * 自动每日日记路径会自己生成并保存摘要，但不走 [generateSummaryWindow]，
     * 因此需要这个入口复用统一的向量化逻辑，避免摘要进库却进不了向量索引。
     */
    suspend fun vectorizeStoredSummary(summary: MemorySummary)
}
