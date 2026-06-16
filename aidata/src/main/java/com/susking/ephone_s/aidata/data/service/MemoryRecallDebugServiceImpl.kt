package com.susking.ephone_s.aidata.data.service

import com.susking.ephone_s.aidata.data.local.dao.LongTermMemoryDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryRecallDebugDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemorySummaryDao
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugEntry
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugItem
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugRecord
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugRecordWithEntries
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallQuery
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.service.MemoryRecallDebugService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRecallDebugServiceImpl @Inject constructor(
    private val memoryRecallDebugDao: MemoryRecallDebugDao,
    private val longTermMemoryDao: LongTermMemoryDao,
    private val memoryEventDao: MemoryEventDao,
    private val memorySummaryDao: MemorySummaryDao
) : MemoryRecallDebugService {
    override suspend fun saveRecallDebugRecord(query: MemoryRecallQuery, context: MemoryRecallContext): String {
        val record = MemoryRecallDebugRecord(
            activityChainId = query.activityChainId,
            contactId = query.contactId,
            sceneType = query.sceneType,
            recallPurpose = query.recallPurpose,
            currentMessage = query.currentMessage,
            recentMessagesText = query.recentMessagesText,
            semanticStateText = query.semanticStateText,
            maxTokenBudget = query.maxTokenBudget,
            estimatedTokenCount = context.estimatedTokenCount,
            topK = query.topK,
            candidateCount = context.debugItems.size,
            injectedCount = context.debugItems.count { item: MemoryRecallDebugItem -> item.isInjected }
        )
        val entries: List<MemoryRecallDebugEntry> = context.debugItems.mapIndexed { index: Int, item: MemoryRecallDebugItem ->
            val snapshot: DebugSnapshot = buildDebugSnapshot(item)
            MemoryRecallDebugEntry(
                recordId = record.id,
                rank = index + FIRST_RANK,
                objectType = item.objectType,
                objectId = item.objectId,
                sourceTypesText = item.sourceTypes.joinToString(separator = ",") { sourceType -> sourceType.name },
                semanticScore = item.semanticScore,
                keywordScore = item.keywordScore,
                graphScore = item.graphScore,
                importanceScore = item.importanceScore,
                recencyScore = item.recencyScore,
                confidenceScore = item.confidenceScore,
                stateScore = item.stateScore,
                finalScore = item.finalScore,
                isInjected = item.isInjected,
                snapshotTitle = snapshot.title,
                snapshotText = snapshot.text
            )
        }
        memoryRecallDebugDao.insertRecord(record)
        memoryRecallDebugDao.insertEntries(entries)
        memoryRecallDebugDao.deleteOldRecords(MAX_RECORD_COUNT)
        return record.id
    }

    override suspend fun getLatestRecordForContact(contactId: String): MemoryRecallDebugRecordWithEntries? {
        val record: MemoryRecallDebugRecord = memoryRecallDebugDao.getLatestRecordForContact(contactId) ?: return null
        return MemoryRecallDebugRecordWithEntries(record, memoryRecallDebugDao.getEntriesForRecord(record.id))
    }

    override suspend fun getLatestRecordForActivity(activityChainId: String): MemoryRecallDebugRecordWithEntries? {
        val record: MemoryRecallDebugRecord = memoryRecallDebugDao.getLatestRecordForActivity(activityChainId) ?: return null
        return MemoryRecallDebugRecordWithEntries(record, memoryRecallDebugDao.getEntriesForRecord(record.id))
    }

    override suspend fun getLatestRecord(): MemoryRecallDebugRecordWithEntries? {
        val record: MemoryRecallDebugRecord = memoryRecallDebugDao.getLatestRecord() ?: return null
        return MemoryRecallDebugRecordWithEntries(record, memoryRecallDebugDao.getEntriesForRecord(record.id))
    }

    private suspend fun buildDebugSnapshot(item: MemoryRecallDebugItem): DebugSnapshot {
        return when (item.objectType) {
            MemoryIndexedObjectType.EVENT,
            MemoryIndexedObjectType.FACT,
            MemoryIndexedObjectType.COMMITMENT -> buildEventSnapshot(item.objectId)
            MemoryIndexedObjectType.SUMMARY -> buildSummarySnapshot(item.objectId)
            MemoryIndexedObjectType.LEGACY_MEMORY -> buildLegacyMemorySnapshot(item.objectId)
        }
    }

    private suspend fun buildEventSnapshot(objectId: String): DebugSnapshot {
        val event: MemoryEvent = memoryEventDao.getEventById(objectId) ?: return DebugSnapshot("事件已不存在", objectId)
        return DebugSnapshot(event.title, event.content)
    }

    private suspend fun buildSummarySnapshot(objectId: String): DebugSnapshot {
        val summary: MemorySummary = memorySummaryDao.getSummaryById(objectId) ?: return DebugSnapshot("摘要已不存在", objectId)
        return DebugSnapshot("${summary.summaryLevel} 摘要", summary.summaryText)
    }

    private suspend fun buildLegacyMemorySnapshot(objectId: String): DebugSnapshot {
        val memory: LongTermMemory = longTermMemoryDao.getMemoryById(objectId) ?: return DebugSnapshot("旧记忆已不存在", objectId)
        return DebugSnapshot(memory.memoryType.name, memory.memoryText)
    }

    private data class DebugSnapshot(
        val title: String,
        val text: String
    )

    private companion object {
        const val FIRST_RANK: Int = 1
        const val MAX_RECORD_COUNT: Int = 200
    }
}
