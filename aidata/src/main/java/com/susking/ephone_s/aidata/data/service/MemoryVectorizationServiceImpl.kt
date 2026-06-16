package com.susking.ephone_s.aidata.data.service

import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEmbedding
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.service.MemoryVectorizationService
import com.susking.ephone_s.aidata.domain.service.OnlineEmbeddingService
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 结构化记忆向量化服务实现。
 * 原子事件已改为只读纪念记录，只保留数据，不再允许作为独立对象向量化。
 */
@Singleton
class MemoryVectorizationServiceImpl @Inject constructor(
    private val onlineEmbeddingService: OnlineEmbeddingService,
    private val memoryEmbeddingDao: MemoryEmbeddingDao
) : MemoryVectorizationService {
    override suspend fun vectorizeMemory(memory: LongTermMemory): Result<MemoryVectorizationService.VectorizationResult> {
        return Result.failure(UnsupportedOperationException("原子事件已改为只读纪念记录，不再允许向量化"))
    }

    override suspend fun vectorizeEvent(event: MemoryEvent, compatibleMemoryId: String): Result<MemoryVectorizationService.VectorizationResult> {
        val text: String = buildEventEmbeddingText(event)
        val objectType: MemoryIndexedObjectType = event.eventType.toIndexedObjectType()
        return vectorizeIndexedObject(
            objectType = objectType,
            objectId = event.id,
            contactId = event.contactId,
            text = text,
            compatibleMemoryId = compatibleMemoryId
        )
    }

    override suspend fun vectorizeSummary(summary: MemorySummary, compatibleMemoryId: String): Result<MemoryVectorizationService.VectorizationResult> {
        val text: String = buildSummaryEmbeddingText(summary)
        return vectorizeIndexedObject(
            objectType = MemoryIndexedObjectType.SUMMARY,
            objectId = summary.id,
            contactId = summary.contactId,
            text = text,
            compatibleMemoryId = compatibleMemoryId
        )
    }

    override suspend fun vectorizeIndexedObject(
        objectType: MemoryIndexedObjectType,
        objectId: String,
        contactId: String,
        text: String,
        compatibleMemoryId: String
    ): Result<MemoryVectorizationService.VectorizationResult> {
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("索引对象内容为空，无法向量化"))
        }
        val existingEmbedding: MemoryEmbedding? = memoryEmbeddingDao.getActiveEmbeddingForIndexedObject(objectType, objectId)
        if (existingEmbedding != null) {
            return Result.success(
                MemoryVectorizationService.VectorizationResult(
                    memoryId = existingEmbedding.memoryId,
                    contactId = existingEmbedding.contactId,
                    objectType = existingEmbedding.indexedObjectType,
                    objectId = existingEmbedding.indexedObjectId,
                    dimension = existingEmbedding.dimension,
                    modelName = existingEmbedding.modelName,
                    modelVersion = existingEmbedding.modelVersion
                )
            )
        }
        return onlineEmbeddingService.generateEmbedding(text).mapCatching { embeddingResult: OnlineEmbeddingService.EmbeddingResult ->
            val memoryEmbedding = MemoryEmbedding(
                memoryId = compatibleMemoryId,
                indexedObjectType = objectType,
                indexedObjectId = objectId,
                contactId = contactId,
                dimension = embeddingResult.dimension,
                embeddingBlob = embeddingResult.embedding.toByteArray(),
                embeddingHash = embeddingResult.embedding.contentHashCode().toString(),
                modelName = embeddingResult.modelName,
                modelVersion = embeddingResult.modelVersion
            )
            memoryEmbeddingDao.insert(memoryEmbedding)
            MemoryVectorizationService.VectorizationResult(
                memoryId = compatibleMemoryId,
                contactId = contactId,
                objectType = objectType,
                objectId = objectId,
                dimension = embeddingResult.dimension,
                modelName = embeddingResult.modelName,
                modelVersion = embeddingResult.modelVersion
            )
        }
    }

    private fun buildEventEmbeddingText(event: MemoryEvent): String {
        return """
            标题：${event.title}
            类型：${event.eventType.name}
            状态：${event.status.name}
            内容：${event.content}
        """.trimIndent()
    }

    private fun buildSummaryEmbeddingText(summary: MemorySummary): String {
        return """
            摘要层级：${summary.summaryLevel.name}
            时间范围：${summary.startTimestamp}-${summary.endTimestamp}
            重要度：${summary.importanceScore}/10
            内容：${summary.summaryText}
        """.trimIndent()
    }

    private fun MemoryEventType.toIndexedObjectType(): MemoryIndexedObjectType {
        return when (this) {
            MemoryEventType.COMMITMENT -> MemoryIndexedObjectType.COMMITMENT
            MemoryEventType.FACT,
            MemoryEventType.PREFERENCE,
            MemoryEventType.PROHIBITION,
            MemoryEventType.RELATIONSHIP -> MemoryIndexedObjectType.FACT
            MemoryEventType.ANNIVERSARY,
            MemoryEventType.OPINION,
            MemoryEventType.OTHER -> MemoryIndexedObjectType.EVENT
        }
    }

    private fun FloatArray.toByteArray(): ByteArray {
        val buffer: ByteBuffer = ByteBuffer.allocate(size * FLOAT_BYTE_SIZE)
        forEach { value: Float -> buffer.putFloat(value) }
        return buffer.array()
    }

    private companion object {
        private const val FLOAT_BYTE_SIZE: Int = 4
    }
}
