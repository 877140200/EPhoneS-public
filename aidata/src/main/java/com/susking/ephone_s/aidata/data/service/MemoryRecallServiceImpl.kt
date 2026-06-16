package com.susking.ephone_s.aidata.data.service

import android.util.Log
import com.susking.ephone_s.aidata.data.local.dao.LongTermMemoryDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryGraphDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemorySummaryDao
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphNode
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphRelation
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallCandidate
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugItem
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallItem
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallQuery
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallSourceType
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugRecordWithEntries
import com.susking.ephone_s.aidata.domain.model.memory.RelationshipRecallContext
import com.susking.ephone_s.aidata.domain.model.memory.RelationshipRecallEntry
import com.susking.ephone_s.aidata.domain.service.MemoryRecallDebugService
import com.susking.ephone_s.aidata.domain.service.MemoryRecallService
import com.susking.ephone_s.aidata.domain.service.OnlineEmbeddingService
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

@Singleton
class MemoryRecallServiceImpl @Inject constructor(
    private val embeddingService: OnlineEmbeddingService,
    private val memoryEmbeddingDao: MemoryEmbeddingDao,
    private val longTermMemoryDao: LongTermMemoryDao,
    private val memoryEventDao: MemoryEventDao,
    private val memorySummaryDao: MemorySummaryDao,
    private val memoryGraphDao: MemoryGraphDao,
    private val memoryRecallDebugService: MemoryRecallDebugService
) : MemoryRecallService {

    override suspend fun recallMemories(query: String, contactId: String, topK: Int): List<MemoryRecallService.RecallResult> {
        val recallQuery = MemoryRecallQuery(contactId = contactId, currentMessage = query, topK = topK)
        val context: MemoryRecallContext = recallMemoryContext(recallQuery)
        val compatibleMemories: List<LongTermMemory> = context.toCompatibleMemories()
        if (compatibleMemories.isNotEmpty()) {
            return compatibleMemories.map { memory: LongTermMemory ->
                val matchedItem: MemoryRecallItem? = (context.relevantEvents + context.activeFacts + context.pendingCommitments)
                    .firstOrNull { item: MemoryRecallItem -> item.compatibleMemory?.id == memory.id }
                MemoryRecallService.RecallResult(
                    memory = memory,
                    relevanceScore = matchedItem?.finalScore ?: 0.0f,
                    finalScore = matchedItem?.finalScore ?: 0.0f
                )
            }.sortedByDescending { result: MemoryRecallService.RecallResult -> result.finalScore }.take(topK)
        }
        return emptyList()
    }

    override suspend fun recallMemoryContext(query: MemoryRecallQuery): MemoryRecallContext {
        val candidateLimit: Int = query.topK * CANDIDATE_MULTIPLIER
        val queryText: String = query.buildEmbeddingQueryText()
        val queryEmbedding: FloatArray? = embeddingService.generateEmbedding(queryText).getOrNull()?.embedding
        Log.d(
            TAG,
            "召回诊断：开始召回 contactId=${query.contactId}, topK=${query.topK}, candidateLimit=$candidateLimit, " +
                "currentMessage=${summarizeText(query.currentMessage)}, queryText=${summarizeText(queryText)}, " +
                "queryEmbedding=${summarizeEmbedding(queryEmbedding)}"
        )
        val candidates: MutableMap<String, MemoryRecallCandidate> = mutableMapOf()
        collectRecentEventCandidates(query, candidateLimit, candidates)
        if (queryEmbedding != null) {
            collectVectorCandidates(query.contactId, queryEmbedding, candidateLimit, candidates)
        }
        collectGraphCandidates(query.contactId, query.nowTimestamp, candidateLimit, candidates)
        Log.d(TAG, "召回诊断：候选数量=${candidates.size}")
        val rankedCandidates: List<MemoryRecallCandidate> = rankCandidates(candidates.values.toList(), query.nowTimestamp)
        Log.d(TAG, "召回诊断：排序后Top候选=${summarizeCandidates(rankedCandidates)}")
        val selectedCandidates: List<MemoryRecallCandidate> = selectCandidatesWithinBudget(rankedCandidates, query.maxTokenBudget, query.topK)
        Log.d(TAG, "召回诊断：注入候选=${summarizeCandidates(selectedCandidates)}")
        val summaries: List<MemoryRecallItem> = collectTimelineSummaries(query.contactId)
        val relationshipTimelines: List<MemoryRecallItem> = buildRelationshipTimelineItems(selectedCandidates, query.nowTimestamp)
        val debugItems: List<MemoryRecallDebugItem> = rankedCandidates.map { candidate: MemoryRecallCandidate ->
            candidate.toDebugItem(selectedCandidates.any { selected: MemoryRecallCandidate -> selected.objectId == candidate.objectId && selected.objectType == candidate.objectType })
        }
        val context = MemoryRecallContext(
            relevantEvents = selectedCandidates.filterRelevantEvents().map { candidate: MemoryRecallCandidate -> candidate.toItem() },
            activeFacts = selectedCandidates.filterActiveFacts().map { candidate: MemoryRecallCandidate -> candidate.toItem() },
            pendingCommitments = selectedCandidates.filterPendingCommitments().map { candidate: MemoryRecallCandidate -> candidate.toItem() },
            relationshipTimelines = relationshipTimelines,
            timelineSummaries = summaries,
            debugItems = debugItems,
            estimatedTokenCount = selectedCandidates.sumOf { candidate: MemoryRecallCandidate -> estimateTokenCount(candidate.text) } + relationshipTimelines.sumOf { item: MemoryRecallItem -> estimateTokenCount(item.text) } + summaries.sumOf { item: MemoryRecallItem -> estimateTokenCount(item.text) }
        )
        memoryRecallDebugService.saveRecallDebugRecord(query, context)
        return context
    }

    override suspend fun getLatestRecallDebugRecord(): MemoryRecallDebugRecordWithEntries? {
        return memoryRecallDebugService.getLatestRecord()
    }

    override suspend fun getLatestRecallDebugRecordForActivity(activityChainId: String): MemoryRecallDebugRecordWithEntries? {
        return memoryRecallDebugService.getLatestRecordForActivity(activityChainId)
    }

    private suspend fun collectRecentEventCandidates(
        query: MemoryRecallQuery,
        candidateLimit: Int,
        candidates: MutableMap<String, MemoryRecallCandidate>
    ): Unit {
        memoryEventDao.getRecentEvents(query.contactId, candidateLimit).forEach { event: MemoryEvent ->
            candidates.mergeCandidate(event.toCandidate(MemoryRecallSourceType.RECENT, recencyScore = calculateRecencyScore(event.eventTime, query.nowTimestamp)))
        }
    }

    private suspend fun collectVectorCandidates(
        contactId: String,
        queryEmbedding: FloatArray,
        candidateLimit: Int,
        candidates: MutableMap<String, MemoryRecallCandidate>
    ) {
        val allMemoryEmbeddings = memoryEmbeddingDao.getActiveEmbeddingsForContact(contactId).first()
        Log.d(TAG, "召回诊断：活跃向量数量=${allMemoryEmbeddings.size}, queryEmbedding=${summarizeEmbedding(queryEmbedding)}")

        val vectorCandidates = allMemoryEmbeddings.mapNotNull { memoryEmbedding ->
            val similarity = cosineSimilarity(queryEmbedding, memoryEmbedding.embeddingBlob.toFloatArray())
            when (memoryEmbedding.indexedObjectType) {
                MemoryIndexedObjectType.EVENT,
                MemoryIndexedObjectType.FACT,
                MemoryIndexedObjectType.COMMITMENT -> {
                    val event = memoryEventDao.getEventById(memoryEmbedding.indexedObjectId)
                    event?.toCandidate(MemoryRecallSourceType.VECTOR, semanticScore = similarity)
                }
                MemoryIndexedObjectType.SUMMARY -> {
                    val summary = memorySummaryDao.getSummaryById(memoryEmbedding.indexedObjectId)
                    summary?.toCandidate(sourceType = MemoryRecallSourceType.VECTOR, semanticScore = similarity)
                }
                MemoryIndexedObjectType.LEGACY_MEMORY -> null
            }
        }

        val sortedVectorCandidates: List<MemoryRecallCandidate> = vectorCandidates.sortedByDescending { candidate: MemoryRecallCandidate -> candidate.semanticScore }
        Log.d(TAG, "召回诊断：向量候选Top=${summarizeCandidates(sortedVectorCandidates)}")
        sortedVectorCandidates
            .take(candidateLimit)
            .forEach { candidates.mergeCandidate(it) }
    }

    private suspend fun collectGraphCandidates(
        contactId: String,
        targetTime: Long,
        candidateLimit: Int,
        candidates: MutableMap<String, MemoryRecallCandidate>
    ): Unit {
        val events: List<MemoryEvent> = memoryEventDao.getEventsByTypesAndStatuses(
            contactId = contactId,
            eventTypes = GRAPH_RECALL_EVENT_TYPES,
            statuses = GRAPH_EVENT_RECALL_STATUSES,
            limit = candidateLimit
        )
        events.forEach { event: MemoryEvent -> candidates.mergeCandidate(event.toCandidate(MemoryRecallSourceType.GRAPH, graphScore = GRAPH_RELEVANCE_SCORE)) }
        memoryGraphDao.getRelationsEffectiveAt(contactId, ACTIVE_RECALL_STATUSES, targetTime, candidateLimit).forEach { relation: MemoryGraphRelation ->
            val relationCandidate: MemoryRecallCandidate = relation.toRelationshipCandidate(targetTime)
            candidates.mergeCandidate(relationCandidate)
        }
    }

    private suspend fun buildRelationshipTimelineItems(selectedCandidates: List<MemoryRecallCandidate>, targetTime: Long): List<MemoryRecallItem> {
        return selectedCandidates
            .filter { candidate: MemoryRecallCandidate -> candidate.relationshipContext != null }
            .map { candidate: MemoryRecallCandidate -> candidate.toItem() }
            .map { item: MemoryRecallItem -> enrichRelationshipTimelineItem(item, targetTime) }
    }

    private suspend fun enrichRelationshipTimelineItem(item: MemoryRecallItem, targetTime: Long): MemoryRecallItem {
        val context: RelationshipRecallContext = item.relationshipContext ?: return item
        val history: List<MemoryGraphRelation> = memoryGraphDao.getRelationHistoryByEndpointKey(context.contactId, context.endpointKey, RELATION_TIMELINE_STATUSES)
        if (history.isEmpty()) return item
        val nodeIds: List<String> = history.flatMap { relation: MemoryGraphRelation -> listOf(relation.fromNodeId, relation.toNodeId) }.distinct()
        val nodes: Map<String, MemoryGraphNode> = memoryGraphDao.getNodesByIds(context.contactId, nodeIds).associateBy { node: MemoryGraphNode -> node.id }
        val currentEntries: List<RelationshipRecallEntry> = history
            .filter { relation: MemoryGraphRelation -> relation.status in ACTIVE_RECALL_STATUSES && relation.effectiveFrom <= targetTime && (relation.effectiveTo == null || relation.effectiveTo >= targetTime) }
            .map { relation: MemoryGraphRelation -> relation.toRelationshipRecallEntry(nodes) }
        val historicalEntries: List<RelationshipRecallEntry> = history
            .filterNot { relation: MemoryGraphRelation -> currentEntries.any { entry: RelationshipRecallEntry -> entry.relationId == relation.id } }
            .take(RELATION_TIMELINE_HISTORY_LIMIT)
            .map { relation: MemoryGraphRelation -> relation.toRelationshipRecallEntry(nodes) }
        return item.copy(
            relationshipContext = context.copy(
                currentRelations = currentEntries,
                historicalRelations = historicalEntries,
                targetTime = targetTime
            ),
            text = buildRelationshipTimelineText(currentEntries, historicalEntries)
        )
    }

    private suspend fun collectTimelineSummaries(contactId: String): List<MemoryRecallItem> {
        return memorySummaryDao.getLatestSummaries(contactId, SUMMARY_RECALL_LIMIT).map { summary: MemorySummary ->
            summary.toRecallItem()
        }
    }

    /**
     * 计算时间衰减得分
     * 使用指数衰减函数: e^(-lambda * t)
     */
    private fun calculateRecencyScore(timestamp: Long, currentTime: Long): Float {
        val hoursSince = (currentTime - timestamp) / (1000.0 * 60.0 * 60.0)
        val lambda = 0.01 // 衰减系数，需要调优
        return exp(-lambda * hoursSince).toFloat()
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) return 0.0f

        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        if (norm1 == 0.0f || norm2 == 0.0f) return 0.0f

        return (dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)))
    }

    // 辅助函数，将ByteArray转为FloatArray
    private fun ByteArray.toFloatArray(): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(this)
        val floatBuffer = buffer.asFloatBuffer()
        val floatArray = FloatArray(floatBuffer.remaining())
        floatBuffer.get(floatArray)
        return floatArray
    }
    private fun MutableMap<String, MemoryRecallCandidate>.mergeCandidate(candidate: MemoryRecallCandidate): Unit {
        val key: String = "${candidate.objectType.name}:${candidate.objectId}"
        val existingCandidate: MemoryRecallCandidate? = this[key]
        this[key] = existingCandidate?.mergeWith(candidate) ?: candidate
    }

    private fun rankCandidates(candidates: List<MemoryRecallCandidate>, nowTimestamp: Long): List<MemoryRecallCandidate> {
        return candidates.map { candidate: MemoryRecallCandidate ->
            val recencyScore: Float = maxOf(candidate.recencyScore, candidate.eventTime?.let { eventTime: Long -> calculateRecencyScore(eventTime, nowTimestamp) } ?: 0.0f)
            val finalScore: Float = ((SEMANTIC_WEIGHT * candidate.semanticScore) +
                (KEYWORD_WEIGHT * candidate.keywordScore) +
                (GRAPH_WEIGHT * candidate.graphScore) +
                (IMPORTANCE_WEIGHT * candidate.importanceScore) +
                (RECENCY_WEIGHT * recencyScore) +
                (CONFIDENCE_WEIGHT * candidate.confidenceScore) +
                (STATE_WEIGHT * candidate.stateScore)).coerceIn(MINIMUM_FINAL_SCORE, MAXIMUM_FINAL_SCORE)
            candidate.copy(recencyScore = recencyScore, finalScore = finalScore)
        }.sortedByDescending { candidate: MemoryRecallCandidate -> candidate.finalScore }
    }

    private fun selectCandidatesWithinBudget(candidates: List<MemoryRecallCandidate>, maxTokenBudget: Int, topK: Int): List<MemoryRecallCandidate> {
        // 1. Hard state check: Filter out invalid candidates first.
        val validCandidates = candidates.filter { candidate: MemoryRecallCandidate ->
            candidate.status == null || candidate.status !in INVALID_RECALL_STATUSES
        }

        // 2. Separate protected candidates (pending commitments and active facts).
        val (protectedCandidates, otherCandidates) = validCandidates.partition {
            (it.objectType == MemoryIndexedObjectType.COMMITMENT && it.status == MemoryEventStatus.PENDING) ||
                    (it.objectType == MemoryIndexedObjectType.FACT && it.status == MemoryEventStatus.ACTIVE)
        }

        val selectedCandidates = mutableListOf<MemoryRecallCandidate>()
        var usedTokens = 0

        // 3. Add protected candidates first, respecting the budget.
        protectedCandidates.sortedByDescending { it.finalScore }.forEach { candidate ->
            val tokenCount = estimateTokenCount(candidate.text)
            if (usedTokens + tokenCount <= maxTokenBudget && selectedCandidates.size < topK) {
                selectedCandidates.add(candidate)
                usedTokens += tokenCount
            }
        }

        // 4. Fill remaining budget with the highest-scored other candidates.
        otherCandidates.forEach { candidate ->
            if (selectedCandidates.size >= topK) return@forEach
            // Avoid adding duplicates
            if (selectedCandidates.any { it.objectId == candidate.objectId && it.objectType == candidate.objectType }) return@forEach

            val tokenCount = estimateTokenCount(candidate.text)
            if (usedTokens + tokenCount <= maxTokenBudget) {
                selectedCandidates.add(candidate)
                usedTokens += tokenCount
            }
        }

        return selectedCandidates
    }

    private fun List<MemoryRecallCandidate>.filterRelevantEvents(): List<MemoryRecallCandidate> {
        return filter { candidate: MemoryRecallCandidate -> candidate.objectType == MemoryIndexedObjectType.EVENT }
    }

    private fun List<MemoryRecallCandidate>.filterActiveFacts(): List<MemoryRecallCandidate> {
        return filter { candidate: MemoryRecallCandidate ->
            candidate.objectType == MemoryIndexedObjectType.FACT && candidate.relationshipContext == null
        }
    }

    private fun List<MemoryRecallCandidate>.filterPendingCommitments(): List<MemoryRecallCandidate> {
        return filter { candidate: MemoryRecallCandidate -> candidate.objectType == MemoryIndexedObjectType.COMMITMENT }
    }

    private fun MemoryEvent.toCandidate(
        sourceType: MemoryRecallSourceType,
        semanticScore: Float = 0.0f,
        graphScore: Float = 0.0f,
        recencyScore: Float = 0.0f
    ): MemoryRecallCandidate {
        val objectType: MemoryIndexedObjectType = when (eventType) {
            MemoryEventType.FACT,
            MemoryEventType.PREFERENCE,
            MemoryEventType.PROHIBITION,
            MemoryEventType.RELATIONSHIP -> MemoryIndexedObjectType.FACT
            MemoryEventType.COMMITMENT -> MemoryIndexedObjectType.COMMITMENT
            MemoryEventType.ANNIVERSARY,
            MemoryEventType.OPINION,
            MemoryEventType.OTHER -> MemoryIndexedObjectType.EVENT
        }
        return MemoryRecallCandidate(
            objectType = objectType,
            objectId = id,
            title = title,
            text = content,
            eventTime = eventTime,
            status = status,
            sourceTypes = setOf(sourceType),
            semanticScore = semanticScore,
            graphScore = graphScore,
            importanceScore = importanceScore / MAX_IMPORTANCE_SCORE,
            recencyScore = recencyScore,
            confidenceScore = confidenceScore,
            stateScore = status.toStateScore()
        )
    }

    private suspend fun MemoryGraphRelation.toRelationshipCandidate(targetTime: Long): MemoryRecallCandidate {
        val nodes: Map<String, MemoryGraphNode> = memoryGraphDao.getNodesByIds(contactId, listOf(fromNodeId, toNodeId)).associateBy { node: MemoryGraphNode -> node.id }
        val currentEntry: RelationshipRecallEntry = toRelationshipRecallEntry(nodes)
        val relationshipContext = RelationshipRecallContext(
            contactId = contactId,
            endpointKey = endpointKey,
            currentRelations = listOf(currentEntry),
            targetTime = targetTime
        )
        return MemoryRecallCandidate(
            objectType = MemoryIndexedObjectType.FACT,
            objectId = id,
            title = "关系时间线",
            text = buildRelationshipTimelineText(listOf(currentEntry), emptyList()),
            eventTime = eventTime,
            status = status,
            sourceTypes = setOf(MemoryRecallSourceType.GRAPH),
            graphScore = GRAPH_RELEVANCE_SCORE,
            importanceScore = confidenceScore.coerceIn(MINIMUM_FINAL_SCORE, 1.0f),
            confidenceScore = confidenceScore,
            stateScore = status.toStateScore(),
            relationshipContext = relationshipContext
        )
    }

    private fun MemoryGraphRelation.toRelationshipRecallEntry(nodes: Map<String, MemoryGraphNode>): RelationshipRecallEntry {
        return RelationshipRecallEntry(
            relationId = id,
            relationType = relationType,
            fromName = nodes[fromNodeId]?.name ?: fromNodeId,
            toName = nodes[toNodeId]?.name ?: toNodeId,
            effectiveFrom = effectiveFrom,
            effectiveTo = effectiveTo,
            status = status,
            changeAction = changeAction,
            changeReason = changeReason ?: statusReason,
            evidenceMemoryId = evidenceMemoryId,
            confidenceScore = confidenceScore
        )
    }

    private fun buildRelationshipTimelineText(currentEntries: List<RelationshipRecallEntry>, historicalEntries: List<RelationshipRecallEntry>): String {
        val currentText: String = currentEntries.joinToString(separator = "；") { entry: RelationshipRecallEntry -> entry.toRelationshipPromptText(isCurrent = true) }
        val historicalText: String = historicalEntries.joinToString(separator = "；") { entry: RelationshipRecallEntry -> entry.toRelationshipPromptText(isCurrent = false) }
        return listOf(
            currentText.takeIf { text: String -> text.isNotBlank() }?.let { text: String -> "当前关系：$text" },
            historicalText.takeIf { text: String -> text.isNotBlank() }?.let { text: String -> "历史变化：$text" }
        ).filterNotNull().joinToString(separator = "\n")
    }

    private fun RelationshipRecallEntry.toRelationshipPromptText(isCurrent: Boolean): String {
        val statusText: String = if (isCurrent) "当前有效" else status.toPromptLabel()
        val effectiveToText: String = effectiveTo?.let { value: Long -> "，失效时间=$value" }.orEmpty()
        val reasonText: String = changeReason?.let { value: String -> "，变化原因=$value" }.orEmpty()
        val evidenceText: String = evidenceMemoryId?.let { value: String -> "，证据=$value" }.orEmpty()
        val confidenceText: String = String.format(Locale.CHINA, "，证据可靠度=%.2f", confidenceScore)
        return "$fromName -[$relationType]-> $toName（$statusText，生效时间=$effectiveFrom$effectiveToText，变化动作=${changeAction.name}$reasonText$evidenceText$confidenceText）"
    }

    private fun MemoryEventStatus.toPromptLabel(): String {
        return when (this) {
            MemoryEventStatus.ACTIVE -> "有效"
            MemoryEventStatus.PENDING -> "待确认"
            MemoryEventStatus.RESOLVED -> "已解决"
            MemoryEventStatus.CANCELLED -> "已取消"
            MemoryEventStatus.EXPIRED -> "已过期"
            MemoryEventStatus.SUPERSEDED -> "已被替代"
            MemoryEventStatus.ARCHIVED -> "已归档"
        }
    }

    private fun MemorySummary.toCandidate(
        sourceType: MemoryRecallSourceType,
        semanticScore: Float = 0.0f
    ): MemoryRecallCandidate {
        return MemoryRecallCandidate(
            objectType = MemoryIndexedObjectType.SUMMARY,
            objectId = id,
            title = summaryLevel.name,
            text = summaryText,
            eventTime = endTimestamp,
            sourceTypes = setOf(sourceType),
            semanticScore = semanticScore,
            importanceScore = importanceScore / MAX_IMPORTANCE_SCORE,
            confidenceScore = 1.0f
        )
    }

    private fun MemorySummary.toRecallItem(): MemoryRecallItem {
        return MemoryRecallItem(
            objectType = MemoryIndexedObjectType.SUMMARY,
            objectId = id,
            title = summaryLevel.name,
            text = summaryText,
            eventTime = endTimestamp,
            sourceTypes = setOf(MemoryRecallSourceType.SUMMARY),
            finalScore = importanceScore / MAX_IMPORTANCE_SCORE,
            confidenceScore = 1.0f
        )
    }

    private fun MemoryRecallCandidate.toDebugItem(isInjected: Boolean): MemoryRecallDebugItem {
        return MemoryRecallDebugItem(
            objectType = objectType,
            objectId = objectId,
            sourceTypes = sourceTypes,
            semanticScore = semanticScore,
            keywordScore = keywordScore,
            graphScore = graphScore,
            importanceScore = importanceScore,
            recencyScore = recencyScore,
            confidenceScore = confidenceScore,
            stateScore = stateScore,
            finalScore = finalScore,
            isInjected = isInjected
        )
    }

    private fun MemoryEventStatus.toStateScore(): Float {
        return when (this) {
            MemoryEventStatus.ACTIVE -> 1.0f
            MemoryEventStatus.PENDING -> 0.95f
            MemoryEventStatus.RESOLVED -> 0.82f
            MemoryEventStatus.ARCHIVED -> 0.62f
            MemoryEventStatus.CANCELLED,
            MemoryEventStatus.EXPIRED,
            MemoryEventStatus.SUPERSEDED -> 0.2f
        }
    }

    private fun estimateTokenCount(text: String): Int {
        return maxOf(1, text.length / CHARS_PER_TOKEN)
    }

    private fun summarizeText(text: String): String {
        val compactText: String = text.replace(Regex("\\s+"), " ").trim()
        val previewText: String = compactText
        return "len=${text.length}, preview=\"$previewText\""
    }

    private fun summarizeEmbedding(embedding: FloatArray?): String {
        if (embedding == null) return "null"
        val previewText: String = embedding.take(DEBUG_EMBEDDING_PREVIEW_SIZE)
            .joinToString(separator = ",") { value: Float -> String.format(Locale.US, "%.4f", value) }
        return "dimension=${embedding.size}, hash=${embedding.contentHashCode()}, first=[$previewText]"
    }

    private fun summarizeCandidates(candidates: List<MemoryRecallCandidate>): String {
        return candidates.take(DEBUG_CANDIDATE_PREVIEW_SIZE).joinToString(separator = " | ") { candidate: MemoryRecallCandidate ->
            "${candidate.objectType}:${candidate.objectId}" +
                " sources=${candidate.sourceTypes.joinToString(separator = ",")}" +
                " final=${formatDebugScore(candidate.finalScore)}" +
                " sem=${formatDebugScore(candidate.semanticScore)}" +
                " key=${formatDebugScore(candidate.keywordScore)}" +
                " graph=${formatDebugScore(candidate.graphScore)}" +
                " imp=${formatDebugScore(candidate.importanceScore)}" +
                " rec=${formatDebugScore(candidate.recencyScore)}"
        }
    }

    private fun formatDebugScore(score: Float): String {
        return String.format(Locale.US, "%.3f", score)
    }

    private companion object {
        private const val TAG: String = "MemoryRecallService"
        private const val CANDIDATE_MULTIPLIER: Int = 3
        private const val KEYWORD_RELEVANCE_SCORE: Float = 0.65f
        private const val GRAPH_RELEVANCE_SCORE: Float = 0.75f
        private const val MINIMUM_GRAPH_CONFIDENCE: Float = 0.6f
        private const val SUMMARY_RECALL_LIMIT: Int = 3
        private const val RELATION_TIMELINE_HISTORY_LIMIT: Int = 6
        private const val TITLE_TEXT_LIMIT: Int = 32
        private const val CHARS_PER_TOKEN: Int = 2
        private const val MAX_IMPORTANCE_SCORE: Float = 10.0f
        private const val SEMANTIC_WEIGHT: Float = 0.32f
        private const val KEYWORD_WEIGHT: Float = 0.14f
        private const val GRAPH_WEIGHT: Float = 0.16f
        private const val IMPORTANCE_WEIGHT: Float = 0.14f
        private const val RECENCY_WEIGHT: Float = 0.10f
        private const val CONFIDENCE_WEIGHT: Float = 0.08f
        private const val STATE_WEIGHT: Float = 0.06f
        private const val MINIMUM_FINAL_SCORE: Float = 0.0f
        private const val MAXIMUM_FINAL_SCORE: Float = 2.0f
        private const val DEBUG_TEXT_LIMIT: Int = 120
        private const val DEBUG_EMBEDDING_PREVIEW_SIZE: Int = 5
        private const val DEBUG_CANDIDATE_PREVIEW_SIZE: Int = 5
        private val ACTIVE_RECALL_STATUSES: List<MemoryEventStatus> = listOf(MemoryEventStatus.ACTIVE, MemoryEventStatus.PENDING)
        private val GRAPH_EVENT_RECALL_STATUSES: List<MemoryEventStatus> = listOf(
            MemoryEventStatus.ACTIVE,
            MemoryEventStatus.PENDING,
            MemoryEventStatus.RESOLVED,
            MemoryEventStatus.ARCHIVED
        )
        private val RELATION_TIMELINE_STATUSES: List<MemoryEventStatus> = listOf(
            MemoryEventStatus.ACTIVE,
            MemoryEventStatus.PENDING,
            MemoryEventStatus.RESOLVED,
            MemoryEventStatus.CANCELLED,
            MemoryEventStatus.EXPIRED,
            MemoryEventStatus.SUPERSEDED,
            MemoryEventStatus.ARCHIVED
        )
        private val INVALID_RECALL_STATUSES: List<MemoryEventStatus> = listOf(
            MemoryEventStatus.CANCELLED,
            MemoryEventStatus.EXPIRED,
            MemoryEventStatus.SUPERSEDED
        )
        private val GRAPH_RECALL_EVENT_TYPES: List<MemoryEventType> = listOf(
            MemoryEventType.COMMITMENT,
            MemoryEventType.PREFERENCE,
            MemoryEventType.PROHIBITION,
            MemoryEventType.ANNIVERSARY,
            MemoryEventType.RELATIONSHIP,
            MemoryEventType.FACT
        )
    }
}
