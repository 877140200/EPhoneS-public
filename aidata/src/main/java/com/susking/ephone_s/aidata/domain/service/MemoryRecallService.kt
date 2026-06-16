package com.susking.ephone_s.aidata.domain.service

import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallQuery
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugRecordWithEntries

/**
 * 记忆召回服务接口
 * 负责根据查询请求，从记忆库中检索、过滤、排序并返回最相关的记忆
 */
interface MemoryRecallService {

    /**
     * 召回与当前查询最相关的记忆
     *
     * @param query 当前的查询文本（例如，用户最新的一条消息）
     * @param contactId 关联的联系人ID
     * @param topK 需要返回的最相关记忆的数量
     * @return 经过排序和过滤的相关记忆列表
     */
    suspend fun recallMemories(query: String, contactId: String, topK: Int): List<RecallResult>

    /**
     * 召回结构化记忆上下文。
     * 新路径会把原子事件、事实、承诺和时间线摘要合并后再注入提示词。
     */
    suspend fun recallMemoryContext(query: MemoryRecallQuery): MemoryRecallContext

    /**
     * 读取最近一次召回调试记录。
     * 默认实现保持旧实现兼容，具体实现可返回持久化调试数据。
     */
    suspend fun getLatestRecallDebugRecord(): MemoryRecallDebugRecordWithEntries? = null

    /**
     * 按大脑活动链读取最近一次召回调试记录。
     */
    suspend fun getLatestRecallDebugRecordForActivity(activityChainId: String): MemoryRecallDebugRecordWithEntries? = null

    /**
     * 召回结果的数据类
     */
    data class RecallResult(
        val memory: LongTermMemory,
        val relevanceScore: Float, // 语义相关性得分
        val finalScore: Float      // 综合得分（结合了时间、重要性等）
    )
}
