package com.susking.ephone_s.aidata.domain.service

import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary

/**
 * 结构化记忆向量化服务。
 * 只负责为结构化事件、分层摘要和统一索引对象建立向量索引。
 */
interface MemoryVectorizationService {
    /**
     * 旧原子事件兼容接口。
     * 原子事件已改为只读纪念记录，实现层必须拒绝独立向量化。
     */
    suspend fun vectorizeMemory(memory: LongTermMemory): Result<VectorizationResult>

    /**
     * 向量化结构化事件。
     * compatibleMemoryId 用于保持旧 memory_embeddings.memoryId 兼容关系。
     */
    suspend fun vectorizeEvent(event: MemoryEvent, compatibleMemoryId: String): Result<VectorizationResult>

    /**
     * 向量化分层摘要。
     * compatibleMemoryId 用于保持旧 memory_embeddings.memoryId 兼容关系。
     */
    suspend fun vectorizeSummary(summary: MemorySummary, compatibleMemoryId: String): Result<VectorizationResult>

    /**
     * 向量化统一索引对象。
     * 第一版仍要求 compatibleMemoryId 指向兼容记忆记录，避免破坏旧外键逻辑。
     */
    suspend fun vectorizeIndexedObject(
        objectType: MemoryIndexedObjectType,
        objectId: String,
        contactId: String,
        text: String,
        compatibleMemoryId: String
    ): Result<VectorizationResult>

    /**
     * 向量化结果。
     */
    data class VectorizationResult(
        val memoryId: String,
        val contactId: String,
        val objectType: MemoryIndexedObjectType = MemoryIndexedObjectType.EVENT,
        val objectId: String = memoryId,
        val dimension: Int,
        val modelName: String,
        val modelVersion: String
    )
}
