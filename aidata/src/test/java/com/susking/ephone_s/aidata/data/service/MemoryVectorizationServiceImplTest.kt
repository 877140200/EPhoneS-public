package com.susking.ephone_s.aidata.data.service

import com.google.common.truth.Truth.assertThat
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEmbedding
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import com.susking.ephone_s.aidata.domain.service.OnlineEmbeddingService
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class MemoryVectorizationServiceImplTest {
    private val onlineEmbeddingService: OnlineEmbeddingService = mock()
    private val memoryEmbeddingDao: MemoryEmbeddingDao = mock()
    private val service: MemoryVectorizationServiceImpl = MemoryVectorizationServiceImpl(
        onlineEmbeddingService = onlineEmbeddingService,
        memoryEmbeddingDao = memoryEmbeddingDao
    )

    @Test
    fun `vectorizeMemory returns failure as it is deprecated`() = runTest {
        val legacyMemory = com.susking.ephone_s.aidata.domain.model.LongTermMemory(
            id = "legacy-1",
            contactId = CONTACT_ID,
            memoryText = "This is a legacy memory and should not be vectorized."
        )

        val result = service.vectorizeMemory(legacyMemory)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(UnsupportedOperationException::class.java)
        verify(onlineEmbeddingService, never()).generateEmbedding(any())
        verify(memoryEmbeddingDao, never()).insert(any())
    }

    @Test
    fun `vectorizeEvent writes commitment embedding with compatible memory id`() = runTest {
        val event: MemoryEvent = createMemoryEvent(
            id = "event-commitment",
            eventType = MemoryEventType.COMMITMENT,
            status = MemoryEventStatus.PENDING
        )
        val compatibleMemoryId: String = "memory-source"
        whenever(memoryEmbeddingDao.getActiveEmbeddingForIndexedObject(MemoryIndexedObjectType.COMMITMENT, event.id)).thenReturn(null)
        whenever(onlineEmbeddingService.generateEmbedding(any())).thenReturn(Result.success(createEmbeddingResult()))
        val actualResult = service.vectorizeEvent(event, compatibleMemoryId).getOrThrow()
        assertThat(actualResult.memoryId).isEqualTo(compatibleMemoryId)
        assertThat(actualResult.objectType).isEqualTo(MemoryIndexedObjectType.COMMITMENT)
        assertThat(actualResult.objectId).isEqualTo(event.id)
        verify(onlineEmbeddingService).generateEmbedding(argThat { contains(event.title) && contains(event.content) && contains(event.status.name) })
        verify(memoryEmbeddingDao).insert(argThat { memoryId == compatibleMemoryId && indexedObjectType == MemoryIndexedObjectType.COMMITMENT && indexedObjectId == event.id })
    }

    @Test
    fun `vectorizeSummary writes summary embedding`() = runTest {
        val summary: MemorySummary = createSummary(id = "summary-daily")
        val compatibleMemoryId: String = "memory-important"
        whenever(memoryEmbeddingDao.getActiveEmbeddingForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)).thenReturn(null)
        whenever(onlineEmbeddingService.generateEmbedding(any())).thenReturn(Result.success(createEmbeddingResult()))
        val actualResult = service.vectorizeSummary(summary, compatibleMemoryId).getOrThrow()
        assertThat(actualResult.memoryId).isEqualTo(compatibleMemoryId)
        assertThat(actualResult.objectType).isEqualTo(MemoryIndexedObjectType.SUMMARY)
        assertThat(actualResult.objectId).isEqualTo(summary.id)
        verify(onlineEmbeddingService).generateEmbedding(argThat { contains(summary.summaryText) && contains(summary.summaryLevel.name) && contains("重要度：${summary.importanceScore}/10") })
        verify(memoryEmbeddingDao).insert(argThat { memoryId == compatibleMemoryId && indexedObjectType == MemoryIndexedObjectType.SUMMARY && indexedObjectId == summary.id })
    }

    @Test
    fun `vectorizeIndexedObject skips embedding request when active index already exists`() = runTest {
        val existingEmbedding: MemoryEmbedding = createEmbedding(
            memoryId = "memory-existing",
            objectType = MemoryIndexedObjectType.FACT,
            objectId = "event-existing"
        )
        whenever(memoryEmbeddingDao.getActiveEmbeddingForIndexedObject(MemoryIndexedObjectType.FACT, existingEmbedding.indexedObjectId)).thenReturn(existingEmbedding)
        val actualResult = service.vectorizeIndexedObject(
            objectType = MemoryIndexedObjectType.FACT,
            objectId = existingEmbedding.indexedObjectId,
            contactId = existingEmbedding.contactId,
            text = "已有索引的事实内容",
            compatibleMemoryId = existingEmbedding.memoryId
        ).getOrThrow()
        assertThat(actualResult.memoryId).isEqualTo(existingEmbedding.memoryId)
        assertThat(actualResult.objectType).isEqualTo(existingEmbedding.indexedObjectType)
        assertThat(actualResult.objectId).isEqualTo(existingEmbedding.indexedObjectId)
        verify(onlineEmbeddingService, never()).generateEmbedding(any())
        verify(memoryEmbeddingDao, never()).insert(any())
    }

    private fun createMemoryEvent(
        id: String,
        eventType: MemoryEventType,
        status: MemoryEventStatus
    ): MemoryEvent {
        return MemoryEvent(
            id = id,
            contactId = CONTACT_ID,
            evidenceMemoryId = "memory-source",
            eventType = eventType,
            title = "记得买牛奶",
            content = "我答应小北晚上买牛奶",
            eventTime = EVENT_TIME,
            importanceScore = DEFAULT_IMPORTANCE_SCORE,
            confidenceScore = DEFAULT_CONFIDENCE_SCORE,
            sourceModule = "test",
            status = status
        )
    }

    private fun createSummary(id: String): MemorySummary {
        return MemorySummary(
            id = id,
            contactId = CONTACT_ID,
            summaryLevel = SummaryLevel.DAILY,
            startTimestamp = EVENT_TIME,
            endTimestamp = EVENT_TIME + DAY_MILLISECONDS,
            summaryText = "今天我和小北确认了买牛奶的约定。",
            sourceMemoryCount = 2,
            importanceScore = DEFAULT_IMPORTANCE_SCORE,
            modelVersion = "test-model"
        )
    }

    private fun createEmbedding(
        memoryId: String,
        objectType: MemoryIndexedObjectType,
        objectId: String
    ): MemoryEmbedding {
        val embedding: FloatArray = floatArrayOf(1.0f, 0.0f)
        return MemoryEmbedding(
            id = "embedding-$objectId",
            memoryId = memoryId,
            indexedObjectType = objectType,
            indexedObjectId = objectId,
            contactId = CONTACT_ID,
            dimension = embedding.size,
            embeddingBlob = embedding.toByteArray(),
            embeddingHash = embedding.contentHashCode().toString(),
            modelName = "test-model",
            modelVersion = "test-version"
        )
    }

    private fun createEmbeddingResult(): OnlineEmbeddingService.EmbeddingResult {
        val embedding: FloatArray = floatArrayOf(1.0f, 0.0f)
        return OnlineEmbeddingService.EmbeddingResult(
            embedding = embedding,
            dimension = embedding.size,
            modelName = "test-model",
            modelVersion = "test-version",
            totalTokens = 1
        )
    }

    private fun FloatArray.toByteArray(): ByteArray {
        val buffer: ByteBuffer = ByteBuffer.allocate(size * FLOAT_BYTE_SIZE)
        forEach { value: Float -> buffer.putFloat(value) }
        return buffer.array()
    }

    private companion object {
        private const val CONTACT_ID: String = "contact-1"
        private const val FLOAT_BYTE_SIZE: Int = 4
        private const val EVENT_TIME: Long = 1_700_000_000_000L
        private const val DAY_MILLISECONDS: Long = 86_400_000L
        private const val DEFAULT_IMPORTANCE_SCORE: Int = 5
        private const val DEFAULT_CONFIDENCE_SCORE: Float = 0.9f
    }
}
