package com.susking.ephone_s.cphone.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.dao.LongTermMemoryDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemorySummaryDao
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEmbedding
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import com.susking.ephone_s.aidata.domain.service.MemorySummarizationService
import com.susking.ephone_s.aidata.domain.service.MemoryVectorizationService
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分层摘要可视化ViewModel。
 */
@HiltViewModel
class CPhoneMemorySummaryViewModel @Inject constructor(
    private val longTermMemoryDao: LongTermMemoryDao,
    private val memorySummaryDao: MemorySummaryDao,
    private val memoryEmbeddingDao: MemoryEmbeddingDao,
    private val memorySummarizationService: MemorySummarizationService,
    private val memoryVectorizationService: MemoryVectorizationService
) : ViewModel() {
    fun getSummaryItems(contactId: String): Flow<List<CPhoneMemorySummaryItem>> {
        return combine(
            memorySummaryDao.getSummariesForContact(contactId),
            memoryEmbeddingDao.getActiveEmbeddingsForContact(contactId)
        ) { summaries: List<MemorySummary>, embeddings: List<MemoryEmbedding> ->
            val vectorizedSummaryIds: Set<String> = embeddings
                .filter { embedding: MemoryEmbedding -> embedding.indexedObjectType == MemoryIndexedObjectType.SUMMARY }
                .map { embedding: MemoryEmbedding -> embedding.indexedObjectId }
                .toSet()
            summaries.map { summary: MemorySummary ->
                CPhoneMemorySummaryItem(
                    summary = summary,
                    isVectorized = summary.id in vectorizedSummaryIds
                )
            }
        }
    }

    fun generateSummary(contactId: String, level: SummaryLevel, windowStart: Long, windowEnd: Long, onCompleted: (Boolean) -> Unit): Unit {
        viewModelScope.launch {
            val generatedSummary: MemorySummary? = memorySummarizationService.generateSummaryWindow(contactId, level, windowStart, windowEnd)
            onCompleted(generatedSummary != null)
        }
    }

    fun buildSummaryPrompt(contactId: String, level: SummaryLevel, windowStart: Long, windowEnd: Long, onCompleted: (AiPromptRequest?) -> Unit): Unit {
        viewModelScope.launch {
            val promptRequest: AiPromptRequest? = memorySummarizationService.buildSummaryWindowPrompt(contactId, level, windowStart, windowEnd)
            onCompleted(promptRequest)
        }
    }

    fun updateSummary(summary: MemorySummary, editInput: CPhoneMemorySummaryEditInput, onCompleted: (Boolean) -> Unit): Unit {
        viewModelScope.launch {
            val trimmedText: String = editInput.summaryText.trim()
            if (trimmedText.isBlank()) {
                onCompleted(false)
                return@launch
            }
            val updatedSummary: MemorySummary = summary.copy(
                summaryText = trimmedText,
                sourceMemoryCount = editInput.sourceMemoryCount.coerceAtLeast(MIN_SOURCE_MEMORY_COUNT),
                importanceScore = editInput.importanceScore.coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE),
                updatedAt = System.currentTimeMillis()
            )
            memorySummaryDao.insert(updatedSummary)
            memoryEmbeddingDao.deactivateEmbeddingsForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)
            onCompleted(true)
        }
    }

    fun deleteSummary(summary: MemorySummary, onCompleted: (Boolean) -> Unit): Unit {
        viewModelScope.launch {
            memorySummaryDao.deleteSummaryById(summary.id)
            memoryEmbeddingDao.deactivateEmbeddingsForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)
            onCompleted(true)
        }
    }

    fun regenerateSummary(summary: MemorySummary, onCompleted: (Boolean) -> Unit): Unit {
        viewModelScope.launch {
            memoryEmbeddingDao.deactivateEmbeddingsForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)
            val generatedSummary: MemorySummary? = memorySummarizationService.regenerateSummaryWindow(summary)
            onCompleted(generatedSummary != null)
        }
    }

    fun revectorizeSummary(summary: MemorySummary, onCompleted: (Boolean) -> Unit): Unit {
        viewModelScope.launch {
            val compatibleMemoryId: String? = findCompatibleMemoryId(summary)
            if (compatibleMemoryId == null) {
                onCompleted(false)
                return@launch
            }
            memoryEmbeddingDao.deactivateEmbeddingsForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)
            val result: Result<MemoryVectorizationService.VectorizationResult> = memoryVectorizationService.vectorizeSummary(summary, compatibleMemoryId)
            onCompleted(result.isSuccess)
        }
    }

    fun deleteAllSummaries(contactId: String, onCompleted: (Boolean) -> Unit): Unit {
        viewModelScope.launch {
            val summaries: List<MemorySummary> = memorySummaryDao.getSummaryListForContact(contactId)
            summaries.forEach { summary: MemorySummary ->
                memoryEmbeddingDao.deactivateEmbeddingsForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)
                memorySummaryDao.deleteSummaryById(summary.id)
            }
            onCompleted(true)
        }
    }

    fun repairDefaultImportanceSummaries(contactId: String, onCompleted: (Int?) -> Unit): Unit {
        viewModelScope.launch {
            val repairedCount: Int? = runCatching {
                memorySummarizationService.repairDefaultImportanceSummaries(contactId)
            }.getOrNull()
            onCompleted(repairedCount)
        }
    }

    private suspend fun findCompatibleMemoryId(summary: MemorySummary): String? {
        val memories: List<LongTermMemory> = longTermMemoryDao.getMemoriesForContact(summary.contactId).first()
        return memories.filter { memory: LongTermMemory -> memory.timestamp in summary.startTimestamp..summary.endTimestamp }
            .maxWithOrNull(compareBy<LongTermMemory> { memory: LongTermMemory -> memory.importanceScore }.thenBy { memory: LongTermMemory -> memory.timestamp })
            ?.id
            ?: memories.maxByOrNull { memory: LongTermMemory -> memory.timestamp }?.id
    }
}

data class CPhoneMemorySummaryItem(
    val summary: MemorySummary,
    val isVectorized: Boolean
)

data class CPhoneMemorySummaryEditInput(
    val summaryText: String,
    val sourceMemoryCount: Int,
    val importanceScore: Int
)

private const val MIN_SOURCE_MEMORY_COUNT: Int = 0
private const val MIN_IMPORTANCE_SCORE: Int = 1
private const val MAX_IMPORTANCE_SCORE: Int = 10
