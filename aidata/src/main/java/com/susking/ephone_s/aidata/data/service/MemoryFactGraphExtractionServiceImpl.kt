package com.susking.ephone_s.aidata.data.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.susking.ephone_s.aidata.data.local.dao.LongTermMemoryDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEvidenceDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryGraphDao
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.MemoryType
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventEvidence
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphNode
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphRelation
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRelationEvidence
import com.susking.ephone_s.aidata.domain.model.memory.RelationshipChangeAction
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.service.MemoryFactGraphExtractionService
import com.susking.ephone_s.aidata.domain.service.MemoryVectorizationService
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 事实图谱保存服务。
 * 旧原子事件二次抽取入口已删除，仅保留聊天总结结果写入事实图谱的链条。
 */
@Singleton
class MemoryFactGraphExtractionServiceImpl @Inject constructor(
    private val memoryEventDao: MemoryEventDao,
    private val memoryEvidenceDao: MemoryEvidenceDao,
    private val memoryGraphDao: MemoryGraphDao,
    private val longTermMemoryDao: LongTermMemoryDao,
    private val personProfileRepository: PersonProfileRepository,
    private val memoryVectorizationService: MemoryVectorizationService
) : MemoryFactGraphExtractionService {
    private val gson: Gson = Gson()

    override suspend fun saveChatFactGraphResponse(contactId: String, response: String): Result<MemoryFactGraphExtractionService.ExtractionResult> {
        return runCatching {
            val profile: PersonProfile = personProfileRepository.getPersonProfileById(contactId) ?: error("联系人不存在: $contactId")
            saveExtractionResult(profile.id, emptyList(), response, ExtractionSource.Chat)
        }
    }

    override suspend fun saveVideoCallFactGraphResponse(
        contactId: String,
        videoCallId: Long?,
        transcript: String,
        response: String
    ): Result<MemoryFactGraphExtractionService.ExtractionResult> {
        return runCatching {
            val profile: PersonProfile = personProfileRepository.getPersonProfileById(contactId) ?: error("联系人不存在: $contactId")
            val sourceMemory: LongTermMemory = buildVideoCallSourceMemory(profile.id, videoCallId, transcript)
            longTermMemoryDao.insert(sourceMemory)
            val result: MemoryFactGraphExtractionService.ExtractionResult = saveExtractionResult(profile.id, listOf(sourceMemory), response, ExtractionSource.VideoCall(videoCallId))
            if (result.eventCount <= 0) error("视频通话结构化抽取结果没有可保存事件")
            result
        }
    }

    private suspend fun saveExtractionResult(
        contactId: String,
        memories: List<LongTermMemory>,
        response: String,
        source: ExtractionSource
    ): MemoryFactGraphExtractionService.ExtractionResult {
        val root: JsonObject = parseRootObject(response)
        val memoryMap: Map<String, LongTermMemory> = memories.associateBy { memory: LongTermMemory -> memory.id }
        val extractionBatchId: String = UUID.randomUUID().toString()
        val stats: ExtractionSaveStats = ExtractionSaveStats(contactId = contactId, extractionBatchId = extractionBatchId)
        val nodeCount: Int = saveNodes(contactId, root.getAsJsonArrayOrEmpty("nodes"), stats)
        val eventCount: Int = saveEvents(contactId, memoryMap, root.getAsJsonArrayOrEmpty("events"), extractionBatchId, stats, source)
        val relationCount: Int = saveRelations(contactId, memoryMap, root.getAsJsonArrayOrEmpty("relations"), extractionBatchId, stats, source)
        Log.d(TAG, stats.toLogText())
        return MemoryFactGraphExtractionService.ExtractionResult(contactId, eventCount, nodeCount, relationCount)
    }

    private suspend fun saveNodes(contactId: String, nodes: JsonArray, stats: ExtractionSaveStats): Int {
        var count = 0
        nodes.forEach { element: JsonElement ->
            if (!element.isJsonObject) {
                stats.skippedNodeCount++
                return@forEach
            }
            val item: JsonObject = element.asJsonObject
            val name: String = item.getStringOrNull("name") ?: run {
                stats.skippedNodeCount++
                return@forEach
            }
            val type: String = normalizeEntityType(item.getStringOrNull("entityType"))
            val normalizedName: String = normalizeEntityName(name)
            val aliasesText: String? = item.getAliasesTextOrNull()
            val existingNode: MemoryGraphNode? = memoryGraphDao.findNodeByNormalizedName(contactId, normalizedName, type)
                ?: memoryGraphDao.findNode(contactId, name.trim(), type)
            if (existingNode == null) {
                memoryGraphDao.insertNode(MemoryGraphNode(contactId = contactId, entityType = type, name = name.trim(), normalizedName = normalizedName, aliases = aliasesText))
                stats.insertedNodeCount++
                count++
            } else if (aliasesText != null && existingNode.aliases == null) {
                memoryGraphDao.updateNode(existingNode.copy(aliases = aliasesText, updatedAt = System.currentTimeMillis()))
                stats.updatedNodeCount++
                count++
            } else {
                stats.skippedNodeCount++
            }
        }
        return count
    }

    private suspend fun saveEvents(contactId: String, memoryMap: Map<String, LongTermMemory>, events: JsonArray, extractionBatchId: String, stats: ExtractionSaveStats, source: ExtractionSource): Int {
        var count = 0
        val parsedEvents: List<MemoryEvent> = events.mapNotNull { element: JsonElement ->
            if (element.isJsonObject) {
                val confidenceScore: Float = element.asJsonObject.getFloatOrNull("confidenceScore")?.coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE) ?: DEFAULT_CONFIDENCE_SCORE
                if (confidenceScore < MIN_EVENT_CONFIDENCE_SCORE) stats.lowConfidenceEventCount++
            }
            parseEventItem(contactId, memoryMap, element, extractionBatchId, source)
        }.dedupeEvents()
        parsedEvents.forEach { event: MemoryEvent ->
            when (saveEventIdempotently(event, extractionBatchId)) {
                SaveOutcome.INSERTED -> {
                    stats.insertedEventCount++
                    stats.insertedEventEvidenceCount++
                    val compatibleMemoryId: String = findOrCreateEventCompatibleMemory(event, memoryMap, source)
                    vectorizeEventSafely(event, compatibleMemoryId)
                    count++
                }
                SaveOutcome.REPLACED -> {
                    stats.replacedEventCount++
                    stats.insertedEventEvidenceCount++
                    val compatibleMemoryId: String = findOrCreateEventCompatibleMemory(event, memoryMap, source)
                    vectorizeEventSafely(event, compatibleMemoryId)
                    count++
                }
                SaveOutcome.SKIPPED -> stats.skippedEventCount++
                SaveOutcome.UPDATED -> {
                    stats.updatedEventCount++
                    stats.insertedEventEvidenceCount++
                }
            }
        }
        return count
    }

    private fun parseEventItem(contactId: String, memoryMap: Map<String, LongTermMemory>, element: JsonElement, extractionBatchId: String, source: ExtractionSource): MemoryEvent? {
        if (!element.isJsonObject) return null
        val item: JsonObject = element.asJsonObject
        val content: String = item.getStringOrNull("content") ?: return null
        val confidenceScore: Float = item.getFloatOrNull("confidenceScore")?.coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE) ?: DEFAULT_CONFIDENCE_SCORE
        if (confidenceScore < MIN_EVENT_CONFIDENCE_SCORE) return null
        val eventType: MemoryEventType = parseEventType(item.getStringOrNull("eventType"))
        val evidenceMemoryId: String? = item.getStringOrNull("evidenceMemoryId")?.takeIf { id: String -> memoryMap.containsKey(id) }
        val sourceMemory: LongTermMemory? = memoryMap[evidenceMemoryId]
        val rawEvidenceText: String? = item.getStringOrNull("rawEvidenceText")?.takeIf { text: String ->
            memoryMap.isEmpty() || sourceMemory?.memoryText?.contains(text) == true
        }
        val dedupeKey: String = buildDedupeKey(eventType, content, item.getStringOrNull("dedupeKey"))
        val nowTimestamp: Long = System.currentTimeMillis()
        return MemoryEvent(
            contactId = contactId,
            evidenceMemoryId = evidenceMemoryId,
            eventType = eventType,
            title = buildSourceAwareEventTitle(item.getStringOrNull("title") ?: "事实事件", source),
            content = buildSourceAwareEventContent(content, source),
            eventTime = item.getLongOrNull("eventTime") ?: sourceMemory?.timestamp ?: nowTimestamp,
            importanceScore = item.getIntOrNull("importanceScore")?.coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE) ?: DEFAULT_IMPORTANCE_SCORE,
            confidenceScore = confidenceScore,
            sourceModule = source.sourceModule,
            status = parseEventStatus(item.getStringOrNull("status"), eventType),
            statusReason = item.getStringOrNull("statusReason"),
            sourceTextHash = buildStableHash("$dedupeKey|${rawEvidenceText ?: sourceMemory?.memoryText ?: content}"),
            dedupeKey = dedupeKey,
            rawEvidenceText = rawEvidenceText,
            extractedAt = nowTimestamp
        )
    }

    private fun List<MemoryEvent>.dedupeEvents(): List<MemoryEvent> {
        return groupBy { event: MemoryEvent -> event.dedupeKey ?: event.id }.values.map { duplicatedEvents: List<MemoryEvent> ->
            duplicatedEvents.maxWith(compareBy<MemoryEvent> { event: MemoryEvent -> event.confidenceScore }.thenBy { event: MemoryEvent -> event.importanceScore }.thenBy { event: MemoryEvent -> event.eventTime })
        }
    }

    private suspend fun saveEventIdempotently(event: MemoryEvent, extractionBatchId: String): SaveOutcome {
        val dedupeKey: String = event.dedupeKey ?: return SaveOutcome.SKIPPED
        val sourceTextHash: String = event.sourceTextHash ?: return SaveOutcome.SKIPPED
        if (memoryEvidenceDao.findEventEvidenceBySourceHash(event.contactId, sourceTextHash) != null) return SaveOutcome.SKIPPED
        val existingEvent: MemoryEvent? = memoryEventDao.getActiveEventsByDedupeKey(event.contactId, dedupeKey, ACTIVE_RECALL_STATUSES).firstOrNull()
            ?: memoryEventDao.getLatestEventByDedupeKey(event.contactId, dedupeKey)
        if (existingEvent == null) {
            memoryEventDao.insert(event)
            insertEventEvidence(event, event.id, extractionBatchId, true)
            return SaveOutcome.INSERTED
        }
        if (existingEvent.id == event.id) return SaveOutcome.SKIPPED
        if (isSameOrBetterExistingEvent(existingEvent, event)) {
            insertEventEvidence(event, existingEvent.id, extractionBatchId, false)
            return SaveOutcome.UPDATED
        }
        if (!shouldReplaceExistingEvent(existingEvent, event)) {
            insertEventEvidence(event, existingEvent.id, extractionBatchId, false)
            return SaveOutcome.UPDATED
        }
        memoryEventDao.updateEventStatus(
            eventId = existingEvent.id,
            status = MemoryEventStatus.SUPERSEDED,
            statusReason = "被更新抽取事件替代",
            supersededByEventId = event.id,
            updatedAt = System.currentTimeMillis()
        )
        memoryEventDao.insert(event)
        insertEventEvidence(event, event.id, extractionBatchId, true)
        return SaveOutcome.REPLACED
    }

    private suspend fun insertEventEvidence(event: MemoryEvent, eventId: String, extractionBatchId: String, shouldUpdateEventMetadata: Boolean): Unit {
        val dedupeKey: String = event.dedupeKey ?: return
        val sourceTextHash: String = event.sourceTextHash ?: return
        val evidence = MemoryEventEvidence(
            contactId = event.contactId,
            eventId = eventId,
            dedupeKey = dedupeKey,
            evidenceMemoryId = event.evidenceMemoryId,
            rawEvidenceText = event.rawEvidenceText,
            sourceTextHash = sourceTextHash,
            confidenceScore = event.confidenceScore,
            extractionBatchId = extractionBatchId
        )
        memoryEvidenceDao.insertEventEvidence(evidence)
        if (!shouldUpdateEventMetadata) return
        memoryEventDao.updateEventEvidenceMetadata(
            eventId = eventId,
            confidenceScore = event.confidenceScore,
            importanceScore = event.importanceScore,
            eventTime = event.eventTime,
            rawEvidenceText = event.rawEvidenceText,
            extractedAt = event.extractedAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun isSameOrBetterExistingEvent(existingEvent: MemoryEvent, newEvent: MemoryEvent): Boolean {
        if (existingEvent.sourceTextHash == newEvent.sourceTextHash && existingEvent.confidenceScore >= newEvent.confidenceScore) return true
        if (existingEvent.content == newEvent.content && existingEvent.importanceScore >= newEvent.importanceScore && existingEvent.confidenceScore >= newEvent.confidenceScore) return true
        return false
    }

    private fun shouldReplaceExistingEvent(existingEvent: MemoryEvent, newEvent: MemoryEvent): Boolean {
        if (existingEvent.status in FINAL_EVENT_STATUSES) return false
        if (newEvent.eventTime > existingEvent.eventTime) return true
        if (newEvent.confidenceScore > existingEvent.confidenceScore) return true
        return newEvent.importanceScore > existingEvent.importanceScore
    }

    private suspend fun findOrCreateEventCompatibleMemory(event: MemoryEvent, memoryMap: Map<String, LongTermMemory>, source: ExtractionSource): String {
        val evidenceMemoryId: String? = event.evidenceMemoryId?.takeIf { memoryId: String -> memoryMap.containsKey(memoryId) }
        if (evidenceMemoryId != null) return evidenceMemoryId
        longTermMemoryDao.insert(
            LongTermMemory(
                id = event.id,
                contactId = event.contactId,
                memoryText = buildEventCompatibleMemoryText(event),
                timestamp = event.eventTime,
                memoryType = MemoryType.EVENT,
                importanceScore = event.importanceScore,
                sourceModule = source.compatibleMemorySource,
                isVectorized = true,
                videoCallId = source.videoCallId
            )
        )
        return event.id
    }

    private fun buildEventCompatibleMemoryText(event: MemoryEvent): String {
        return listOf(event.title, event.content).filter { text: String -> text.isNotBlank() }.joinToString(separator = "\n")
    }

    private fun buildVideoCallSourceMemory(contactId: String, videoCallId: Long?, transcript: String): LongTermMemory {
        val sourceMemoryId: String = "video_call_transcript_${videoCallId ?: UUID.randomUUID()}"
        return LongTermMemory(
            id = sourceMemoryId,
            contactId = contactId,
            memoryText = "【视频通话原文】\n$transcript",
            timestamp = System.currentTimeMillis(),
            memoryType = MemoryType.SYSTEM,
            importanceScore = DEFAULT_VIDEO_CALL_SOURCE_IMPORTANCE_SCORE,
            sourceModule = VIDEO_CALL_TRANSCRIPT_SOURCE,
            isVectorized = false,
            videoCallId = videoCallId
        )
    }

    private fun buildSourceAwareEventTitle(title: String, source: ExtractionSource): String {
        if (source !is ExtractionSource.VideoCall) return title
        if (title.startsWith(VIDEO_CALL_EVENT_TITLE_PREFIX)) return title
        return "$VIDEO_CALL_EVENT_TITLE_PREFIX$title"
    }

    private fun buildSourceAwareEventContent(content: String, source: ExtractionSource): String {
        if (source !is ExtractionSource.VideoCall) return content
        if (content.contains(VIDEO_CALL_EVENT_CONTENT_MARK)) return content
        return "$VIDEO_CALL_EVENT_CONTENT_MARK：$content"
    }

    private fun findDefaultSourceMemoryId(memoryMap: Map<String, LongTermMemory>, source: ExtractionSource): String? {
        if (source !is ExtractionSource.VideoCall) return null
        return memoryMap.values.firstOrNull { memory: LongTermMemory ->
            memory.sourceModule == VIDEO_CALL_TRANSCRIPT_SOURCE && memory.videoCallId == source.videoCallId
        }?.id
    }

    private fun buildSourceAwareRelationReason(reason: String?, source: ExtractionSource): String? {
        if (source !is ExtractionSource.VideoCall) return reason
        if (reason.isNullOrBlank()) return "视频通话中明确表达的关系变化或事实关系"
        if (reason.contains(VIDEO_CALL_RELATION_REASON_MARK)) return reason
        return "$VIDEO_CALL_RELATION_REASON_MARK：$reason"
    }

    private suspend fun vectorizeEventSafely(event: MemoryEvent, compatibleMemoryId: String) {
        memoryVectorizationService.vectorizeEvent(event, compatibleMemoryId).onFailure { exception: Throwable ->
            Log.e(TAG, "结构化原子事件向量化失败: eventId=${event.id}, memoryId=$compatibleMemoryId", exception)
        }
    }

    private suspend fun saveRelations(contactId: String, memoryMap: Map<String, LongTermMemory>, relations: JsonArray, extractionBatchId: String, stats: ExtractionSaveStats, source: ExtractionSource): Int {
        var count = 0
        relations.forEach { element: JsonElement ->
            if (!element.isJsonObject) {
                stats.skippedRelationCount++
                return@forEach
            }
            val item: JsonObject = element.asJsonObject
            val fromName: String = item.getStringOrNull("fromName") ?: run {
                stats.skippedRelationCount++
                return@forEach
            }
            val toName: String = item.getStringOrNull("toName") ?: run {
                stats.skippedRelationCount++
                return@forEach
            }
            val confidenceScore: Float = item.getFloatOrNull("confidenceScore")?.coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE) ?: DEFAULT_CONFIDENCE_SCORE
            if (confidenceScore < MIN_RELATION_CONFIDENCE_SCORE) {
                stats.lowConfidenceRelationCount++
                return@forEach
            }
            val fromNode: MemoryGraphNode = findOrCreateNode(contactId, fromName, item.getStringOrNull("fromType") ?: DEFAULT_ENTITY_TYPE)
            val toNode: MemoryGraphNode = findOrCreateNode(contactId, toName, item.getStringOrNull("toType") ?: DEFAULT_ENTITY_TYPE)
            val explicitEvidenceMemoryId: String? = item.getStringOrNull("evidenceMemoryId")?.takeIf { id: String -> memoryMap.containsKey(id) }
            val evidenceMemoryId: String? = explicitEvidenceMemoryId ?: findDefaultSourceMemoryId(memoryMap, source)
            val relationType: String = normalizeRelationType(item.getStringOrNull("relationType"))
            val endpointKey: String = buildEndpointKey(contactId, fromNode.id, relationType, toNode.id)
            val eventTime: Long = item.getLongOrNull("eventTime") ?: memoryMap[evidenceMemoryId]?.timestamp ?: System.currentTimeMillis()
            val effectiveFrom: Long = item.getLongOrNull("effectiveFrom") ?: eventTime
            val changeAction: RelationshipChangeAction = parseRelationshipChangeAction(item.getStringOrNull("changeAction"))
            val relationKey: String = buildRelationKey(
                contactId = contactId,
                fromNodeId = fromNode.id,
                relationType = relationType,
                toNodeId = toNode.id,
                evidenceMemoryId = evidenceMemoryId,
                effectiveFrom = effectiveFrom,
                changeAction = changeAction
            )
            val relation = MemoryGraphRelation(
                contactId = contactId,
                fromNodeId = fromNode.id,
                toNodeId = toNode.id,
                relationType = relationType,
                endpointKey = endpointKey,
                relationKey = relationKey,
                evidenceMemoryId = evidenceMemoryId,
                eventTime = eventTime,
                effectiveFrom = effectiveFrom,
                effectiveTo = item.getLongOrNull("effectiveTo"),
                changeAction = changeAction,
                changeReason = buildSourceAwareRelationReason(item.getStringOrNull("changeReason"), source),
                validitySource = item.getStringOrNull("validitySource") ?: DEFAULT_VALIDITY_SOURCE,
                previousRelationId = item.getStringOrNull("previousRelationId") ?: item.getStringOrNull("previousRelationHint"),
                confidenceScore = confidenceScore,
                extractionBatchId = extractionBatchId
            )
            when (saveRelationIdempotently(relation, memoryMap[evidenceMemoryId]?.memoryText)) {
                SaveOutcome.INSERTED -> {
                    stats.insertedRelationCount++
                    stats.insertedRelationEvidenceCount++
                    count++
                }
                SaveOutcome.UPDATED -> {
                    stats.updatedRelationCount++
                    stats.insertedRelationEvidenceCount++
                    count++
                }
                SaveOutcome.SKIPPED -> stats.skippedRelationCount++
                SaveOutcome.REPLACED -> stats.replacedRelationCount++
            }
        }
        return count
    }

    private suspend fun saveRelationIdempotently(relation: MemoryGraphRelation, sourceMemoryText: String?): SaveOutcome {
        val sourceTextHash: String = buildRelationEvidenceHash(relation, sourceMemoryText)
        if (memoryEvidenceDao.findRelationEvidenceBySourceHash(relation.contactId, sourceTextHash) != null) return SaveOutcome.SKIPPED
        val existingRelation: MemoryGraphRelation? = memoryGraphDao.findRelationByKey(relation.relationKey)
        if (existingRelation != null) {
            insertRelationEvidence(relation, existingRelation.id, sourceTextHash)
            memoryGraphDao.updateRelationEvidenceMetadata(
                relationId = existingRelation.id,
                confidenceScore = maxOf(existingRelation.confidenceScore, relation.confidenceScore),
                eventTime = maxOf(existingRelation.eventTime, relation.eventTime),
                changeReason = relation.changeReason ?: existingRelation.changeReason,
                extractionBatchId = relation.extractionBatchId,
                updatedAt = System.currentTimeMillis()
            )
            return SaveOutcome.UPDATED
        }
        val activeRelation: MemoryGraphRelation? = memoryGraphDao.getCurrentActiveRelationByEndpointKey(relation.contactId, relation.endpointKey, ACTIVE_RECALL_STATUSES)
        if (activeRelation != null && relation.changeAction in RELATION_EVIDENCE_ONLY_ACTIONS) {
            insertRelationEvidence(relation, activeRelation.id, sourceTextHash)
            memoryGraphDao.updateRelationEvidenceMetadata(
                relationId = activeRelation.id,
                confidenceScore = maxOf(activeRelation.confidenceScore, relation.confidenceScore),
                eventTime = maxOf(activeRelation.eventTime, relation.eventTime),
                changeReason = relation.changeReason ?: activeRelation.changeReason,
                extractionBatchId = relation.extractionBatchId,
                updatedAt = System.currentTimeMillis()
            )
            return SaveOutcome.UPDATED
        }
        memoryGraphDao.insertRelation(relation)
        insertRelationEvidence(relation, relation.id, sourceTextHash)
        executeRelationshipChangeAction(relation)
        return SaveOutcome.INSERTED
    }

    private suspend fun insertRelationEvidence(relation: MemoryGraphRelation, relationId: String, sourceTextHash: String): Unit {
        val evidence = MemoryRelationEvidence(
            contactId = relation.contactId,
            relationId = relationId,
            endpointKey = relation.endpointKey,
            evidenceMemoryId = relation.evidenceMemoryId,
            changeAction = relation.changeAction,
            rawEvidenceText = relation.changeReason,
            sourceTextHash = sourceTextHash,
            confidenceScore = relation.confidenceScore,
            extractionBatchId = relation.extractionBatchId ?: UUID.randomUUID().toString()
        )
        memoryEvidenceDao.insertRelationEvidence(evidence)
    }

    private suspend fun executeRelationshipChangeAction(relation: MemoryGraphRelation): Unit {
        if (relation.changeAction !in RELATION_INVALIDATING_ACTIONS) return
        val previousRelation: MemoryGraphRelation = findPreviousRelationForChange(relation) ?: return
        if (previousRelation.id == relation.id) return
        memoryGraphDao.invalidateRelationByEvidence(
            relationId = previousRelation.id,
            status = MemoryEventStatus.SUPERSEDED,
            statusReason = relation.changeReason ?: "关系变化由明确证据触发",
            effectiveTo = relation.effectiveTo ?: relation.effectiveFrom,
            invalidatedAt = System.currentTimeMillis(),
            invalidatedByRelationId = relation.id,
            changeReason = relation.changeReason,
            updatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun findPreviousRelationForChange(relation: MemoryGraphRelation): MemoryGraphRelation? {
        val previousRelationId: String = relation.previousRelationId?.takeIf { value: String -> value.isNotBlank() } ?: return null
        val directMatches: List<MemoryGraphRelation> = memoryGraphDao.getRelationsByKeys(relation.contactId, listOf(previousRelationId))
        if (directMatches.isNotEmpty()) return directMatches.first()
        val endpointHistory: List<MemoryGraphRelation> = memoryGraphDao.getRelationHistoryByEndpointKey(relation.contactId, relation.endpointKey, ACTIVE_RECALL_STATUSES)
        return endpointHistory.firstOrNull { candidate: MemoryGraphRelation ->
            candidate.id == previousRelationId || candidate.relationKey == previousRelationId || candidate.relationType == normalizeRelationType(previousRelationId)
        }
    }

    private suspend fun findOrCreateNode(contactId: String, name: String, type: String): MemoryGraphNode {
        val normalizedType: String = normalizeEntityType(type)
        val normalizedName: String = normalizeEntityName(name)
        val existingNode: MemoryGraphNode? = memoryGraphDao.findNodeByNormalizedName(contactId, normalizedName, normalizedType)
            ?: memoryGraphDao.findNode(contactId, name.trim(), normalizedType)
        if (existingNode != null) return existingNode
        val node: MemoryGraphNode = MemoryGraphNode(contactId = contactId, entityType = normalizedType, name = name.trim(), normalizedName = normalizedName)
        memoryGraphDao.insertNode(node)
        return memoryGraphDao.findNodeByNormalizedName(contactId, normalizedName, normalizedType) ?: node
    }

    private suspend fun buildExistingGraphContext(contactId: String): String {
        val nodes: List<MemoryGraphNode> = memoryGraphDao.getNodesForContact(contactId, EXISTING_GRAPH_CONTEXT_LIMIT)
        val relations: List<MemoryGraphRelation> = memoryGraphDao.getRelationsForContact(contactId, ACTIVE_RECALL_STATUSES, EXISTING_GRAPH_CONTEXT_LIMIT)
        val events: List<MemoryEvent> = memoryEventDao.getEventsByStatuses(contactId, ACTIVE_RECALL_STATUSES, EXISTING_GRAPH_CONTEXT_LIMIT)
        if (nodes.isEmpty() && relations.isEmpty() && events.isEmpty()) return "无既有结构化图谱。"
        val nodeText: String = nodes.joinToString(separator = "\n") { node: MemoryGraphNode ->
            "- node name=${node.name}; normalizedName=${node.normalizedName}; type=${node.entityType}; aliases=${node.aliases.orEmpty()}"
        }.ifBlank { "无" }
        val nodeMap: Map<String, MemoryGraphNode> = nodes.associateBy { node: MemoryGraphNode -> node.id }
        val relationText: String = relations.joinToString(separator = "\n") { relation: MemoryGraphRelation ->
            val fromName: String = nodeMap[relation.fromNodeId]?.name ?: relation.fromNodeId
            val toName: String = nodeMap[relation.toNodeId]?.name ?: relation.toNodeId
            "- relation id=${relation.id}; from=$fromName; type=${relation.relationType}; to=$toName; status=${relation.status.name}; endpointKey=${relation.endpointKey}; relationKey=${relation.relationKey}; changeAction=${relation.changeAction.name}; effectiveFrom=${relation.effectiveFrom}; effectiveTo=${relation.effectiveTo}"
        }.ifBlank { "无" }
        val eventText: String = events.joinToString(separator = "\n") { event: MemoryEvent ->
            "- event id=${event.id}; type=${event.eventType.name}; status=${event.status.name}; dedupeKey=${event.dedupeKey.orEmpty()}; eventTime=${event.eventTime}; content=${event.content}"
        }.ifBlank { "无" }
        return """
既有节点：
$nodeText
既有关系：
$relationText
既有事件：
$eventText
        """.trimIndent()
    }

    private fun normalizeEntityType(value: String?): String {
        val trimmedValue: String = value?.trim().orEmpty()
        if (trimmedValue.isBlank()) return DEFAULT_ENTITY_TYPE
        return when (trimmedValue.lowercase(Locale.ROOT)) {
            "person", "人物", "人", "联系人", "user", "ai" -> "Person"
            "location", "place", "地点", "位置", "城市" -> "Location"
            "organization", "org", "组织", "机构", "公司" -> "Organization"
            "item", "object", "物品", "东西", "商品" -> "Item"
            "concept", "概念", "主题", "事件" -> "Concept"
            else -> trimmedValue.replaceFirstChar { char: Char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
        }
    }

    private fun normalizeEntityName(value: String): String {
        val normalizedText: String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        return normalizedText.lowercase(Locale.ROOT)
            .replace(ENTITY_NAME_DECORATION_REGEX, "")
            .replace(WHITESPACE_REGEX, "")
            .ifBlank { value.trim() }
    }

    private fun normalizeRelationType(value: String?): String {
        val normalizedValue: String = normalizeRelationToken(value ?: DEFAULT_RELATION_TYPE)
        return RELATION_TYPE_ALIASES[normalizedValue] ?: normalizedValue.ifBlank { DEFAULT_RELATION_TYPE }
    }

    private fun normalizeRelationToken(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, "_")
            .replace(RELATION_TYPE_DECORATION_REGEX, "_")
            .replace(MULTIPLE_UNDERSCORE_REGEX, "_")
            .trim('_')
    }

    private fun buildEndpointKey(contactId: String, fromNodeId: String, relationType: String, toNodeId: String): String {
        return listOf(contactId, fromNodeId, relationType, toNodeId).joinToString(separator = "|")
    }

    private fun buildRelationKey(contactId: String, fromNodeId: String, relationType: String, toNodeId: String, evidenceMemoryId: String?, effectiveFrom: Long, changeAction: RelationshipChangeAction): String {
        return listOf(contactId, fromNodeId, relationType, toNodeId, evidenceMemoryId.orEmpty(), effectiveFrom.toString(), changeAction.name).joinToString(separator = "|")
    }

    private fun buildStableHash(text: String): String {
        val digest: ByteArray = MessageDigest.getInstance("SHA-256").digest(text.normalizeForHash().toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte: Byte -> "%02x".format(byte) }
    }

    private fun String.normalizeForHash(): String {
        return Normalizer.normalize(trim(), Normalizer.Form.NFKC).replace(WHITESPACE_REGEX, " ").lowercase(Locale.ROOT)
    }

    private fun parseRootObject(response: String): JsonObject {
        val root: JsonObject = parseJsonObjectFromText(response)
        if (root.has("events") || root.has("nodes") || root.has("relations")) return root
        val assistantContent: String? = root.extractAssistantContentOrNull()
        if (assistantContent != null) return parseJsonObjectFromText(assistantContent)
        return root
    }

    private fun parseJsonObjectFromText(text: String): JsonObject {
        val cleanedText: String = text.replace("```json", "").replace("```", "").trim()
        val startIndex: Int = cleanedText.indexOf('{')
        val endIndex: Int = cleanedText.lastIndexOf('}')
        val jsonText: String = if (startIndex >= 0 && endIndex > startIndex) cleanedText.substring(startIndex, endIndex + 1) else cleanedText
        return gson.fromJson(jsonText, JsonObject::class.java)
    }

    private fun JsonObject.extractAssistantContentOrNull(): String? {
        val choices: JsonArray = if (has("choices") && get("choices").isJsonArray) getAsJsonArray("choices") else return null
        choices.forEach { choiceElement: JsonElement ->
            if (!choiceElement.isJsonObject) return@forEach
            val choice: JsonObject = choiceElement.asJsonObject
            val message: JsonObject = choice.getObjectOrNull("message") ?: return@forEach
            val role: String? = message.getStringOrNull("role")
            val content: String? = message.getStringOrNull("content")
            if (content != null && (role == null || role == "assistant")) return content
        }
        return null
    }

    private fun parseEventType(value: String?): MemoryEventType {
        return runCatching { MemoryEventType.valueOf((value ?: MemoryEventType.OTHER.name).uppercase(Locale.ROOT)) }.getOrDefault(MemoryEventType.OTHER)
    }

    private fun parseEventStatus(value: String?, eventType: MemoryEventType): MemoryEventStatus {
        val parsedStatus: MemoryEventStatus? = runCatching { MemoryEventStatus.valueOf(value.orEmpty().uppercase(Locale.ROOT)) }.getOrNull()
        if (parsedStatus != null) return parsedStatus
        return if (eventType == MemoryEventType.COMMITMENT) MemoryEventStatus.PENDING else MemoryEventStatus.ACTIVE
    }

    private fun parseRelationshipChangeAction(value: String?): RelationshipChangeAction {
        return runCatching { RelationshipChangeAction.valueOf(value.orEmpty().uppercase(Locale.ROOT)) }.getOrDefault(RelationshipChangeAction.ASSERT_ACTIVE)
    }

    private fun buildDedupeKey(eventType: MemoryEventType, content: String, modelDedupeKey: String?): String {
        val normalizedModelKey: String = normalizeDedupeKey(modelDedupeKey)
        if (normalizedModelKey.isNotBlank()) return normalizedModelKey
        val normalizedContent: String = content.normalizeForHash().replace(WHITESPACE_REGEX, "").take(DEDUPE_CONTENT_LIMIT)
        return "${eventType.name}|$normalizedContent"
    }

    private fun normalizeDedupeKey(value: String?): String {
        return Normalizer.normalize(value?.trim().orEmpty(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, "")
            .take(DEDUPE_KEY_LIMIT)
    }

    private fun buildRelationEvidenceHash(relation: MemoryGraphRelation, sourceMemoryText: String?): String {
        val evidenceText: String = listOf(
            relation.contactId,
            relation.endpointKey,
            relation.changeAction.name,
            relation.evidenceMemoryId.orEmpty(),
            relation.changeReason.orEmpty(),
            sourceMemoryText.orEmpty().ifBlank { relation.eventTime.toString() }
        ).joinToString(separator = "|")
        return buildStableHash(evidenceText)
    }

    private fun JsonObject.getAsJsonArrayOrEmpty(name: String): JsonArray {
        return if (has(name) && get(name).isJsonArray) getAsJsonArray(name) else JsonArray()
    }

    private fun JsonObject.getStringOrNull(name: String): String? {
        return if (has(name) && !get(name).isJsonNull && get(name).isJsonPrimitive) get(name).asString.takeIf { value: String -> value.isNotBlank() } else null
    }

    private fun JsonObject.getObjectOrNull(name: String): JsonObject? {
        return if (has(name) && get(name).isJsonObject) getAsJsonObject(name) else null
    }

    private fun JsonObject.getAliasesTextOrNull(): String? {
        val aliases: JsonElement = if (has("aliases") && !get("aliases").isJsonNull) get("aliases") else return null
        if (aliases.isJsonArray) return gson.toJson(aliases)
        if (aliases.isJsonPrimitive) return aliases.asString.takeIf { value: String -> value.isNotBlank() }
        return null
    }

    private fun JsonObject.getLongOrNull(name: String): Long? {
        return runCatching { if (has(name) && !get(name).isJsonNull) get(name).asLong else null }.getOrNull()
    }

    private fun JsonObject.getIntOrNull(name: String): Int? {
        return runCatching { if (has(name) && !get(name).isJsonNull) get(name).asInt else null }.getOrNull()
    }

    private fun JsonObject.getFloatOrNull(name: String): Float? {
        return runCatching { if (has(name) && !get(name).isJsonNull) get(name).asFloat else null }.getOrNull()
    }

    private sealed class ExtractionSource(
        val sourceModule: String,
        val compatibleMemorySource: String,
        val videoCallId: Long?
    ) {
        data object Chat : ExtractionSource(
            sourceModule = SOURCE_MODULE,
            compatibleMemorySource = STRUCTURED_EVENT_COMPATIBLE_MEMORY_SOURCE,
            videoCallId = null
        )

        data class VideoCall(private val callId: Long?) : ExtractionSource(
            sourceModule = VIDEO_CALL_SOURCE_MODULE,
            compatibleMemorySource = VIDEO_CALL_STRUCTURED_EVENT_COMPATIBLE_MEMORY_SOURCE,
            videoCallId = callId
        )
    }

    private enum class SaveOutcome {
        INSERTED,
        UPDATED,
        SKIPPED,
        REPLACED
    }

    private data class ExtractionSaveStats(
        val contactId: String,
        val extractionBatchId: String,
        var insertedNodeCount: Int = 0,
        var updatedNodeCount: Int = 0,
        var skippedNodeCount: Int = 0,
        var insertedEventCount: Int = 0,
        var updatedEventCount: Int = 0,
        var skippedEventCount: Int = 0,
        var replacedEventCount: Int = 0,
        var lowConfidenceEventCount: Int = 0,
        var insertedRelationCount: Int = 0,
        var updatedRelationCount: Int = 0,
        var skippedRelationCount: Int = 0,
        var replacedRelationCount: Int = 0,
        var lowConfidenceRelationCount: Int = 0,
        var insertedEventEvidenceCount: Int = 0,
        var insertedRelationEvidenceCount: Int = 0
    ) {
        fun toLogText(): String {
            return "结构化图谱保存统计: contactId=$contactId, batchId=$extractionBatchId, " +
                "nodes(insert=$insertedNodeCount, update=$updatedNodeCount, skip=$skippedNodeCount), " +
                "events(insert=$insertedEventCount, update=$updatedEventCount, replace=$replacedEventCount, skip=$skippedEventCount, lowConfidence=$lowConfidenceEventCount, evidence=$insertedEventEvidenceCount), " +
                "relations(insert=$insertedRelationCount, update=$updatedRelationCount, replace=$replacedRelationCount, skip=$skippedRelationCount, lowConfidence=$lowConfidenceRelationCount, evidence=$insertedRelationEvidenceCount)"
        }
    }

    private companion object {
        private const val TAG: String = "MemoryFactGraph"
        private const val EXISTING_GRAPH_CONTEXT_LIMIT: Int = 40
        private const val MIN_IMPORTANCE_SCORE: Int = 1
        private const val MAX_IMPORTANCE_SCORE: Int = 10
        private const val DEFAULT_IMPORTANCE_SCORE: Int = 5
        private const val MIN_CONFIDENCE_SCORE: Float = 0.0f
        private const val MAX_CONFIDENCE_SCORE: Float = 1.0f
        private const val DEFAULT_CONFIDENCE_SCORE: Float = 0.7f
        private const val MIN_EVENT_CONFIDENCE_SCORE: Float = 0.45f
        private const val MIN_RELATION_CONFIDENCE_SCORE: Float = 0.4f
        private const val DEDUPE_CONTENT_LIMIT: Int = 80
        private const val DEDUPE_KEY_LIMIT: Int = 160
        private const val DEFAULT_ENTITY_TYPE: String = "Concept"
        private const val DEFAULT_RELATION_TYPE: String = "related_to"
        private const val DEFAULT_VALIDITY_SOURCE: String = "memory_time"
        private const val SOURCE_MODULE: String = "LongTermMemory"
        private const val STRUCTURED_EVENT_COMPATIBLE_MEMORY_SOURCE: String = "LongTermMemoryStructuredEventCompatibility"
        private const val VIDEO_CALL_SOURCE_MODULE: String = "VideoCall"
        private const val VIDEO_CALL_TRANSCRIPT_SOURCE: String = "VideoCallTranscript"
        private const val VIDEO_CALL_STRUCTURED_EVENT_COMPATIBLE_MEMORY_SOURCE: String = "VideoCallStructuredEventCompatibility"
        private const val VIDEO_CALL_EVENT_TITLE_PREFIX: String = "视频通话-"
        private const val VIDEO_CALL_EVENT_CONTENT_MARK: String = "从视频通话中得知"
        private const val VIDEO_CALL_RELATION_REASON_MARK: String = "视频通话中"
        private const val DEFAULT_VIDEO_CALL_SOURCE_IMPORTANCE_SCORE: Int = 1
        private val WHITESPACE_REGEX: Regex = Regex("\\s+")
        private val ENTITY_NAME_DECORATION_REGEX: Regex = Regex("[\\p{Punct}，。！？、；：\\\"“”‘’（）《》【】—…·]")
        private val RELATION_TYPE_DECORATION_REGEX: Regex = Regex("[^a-z0-9_\\u4e00-\\u9fa5]+")
        private val MULTIPLE_UNDERSCORE_REGEX: Regex = Regex("_+")
        private val ACTIVE_RECALL_STATUSES: List<MemoryEventStatus> = listOf(MemoryEventStatus.ACTIVE, MemoryEventStatus.PENDING)
        private val RELATION_EVIDENCE_ONLY_ACTIONS: List<RelationshipChangeAction> = listOf(
            RelationshipChangeAction.ASSERT_ACTIVE,
            RelationshipChangeAction.UNCLEAR
        )
        private val RELATION_INVALIDATING_ACTIONS: List<RelationshipChangeAction> = listOf(
            RelationshipChangeAction.ASSERT_ENDED,
            RelationshipChangeAction.TRANSITION_FROM,
            RelationshipChangeAction.CORRECT_PREVIOUS
        )
        private val RELATION_TYPE_ALIASES: Map<String, String> = mapOf(
            "like" to "likes",
            "likes" to "likes",
            "喜欢" to "likes",
            "dislike" to "dislikes",
            "dislikes" to "dislikes",
            "讨厌" to "dislikes",
            "friend" to "is_friend_of",
            "friends" to "is_friend_of",
            "is_friend_of" to "is_friend_of",
            "朋友" to "is_friend_of",
            "works_at" to "works_at",
            "工作于" to "works_at",
            "located_at" to "located_at",
            "位于" to "located_at",
            "related_to" to "related_to",
            "有关" to "related_to"
        )
        private val FINAL_EVENT_STATUSES: List<MemoryEventStatus> = listOf(
            MemoryEventStatus.RESOLVED,
            MemoryEventStatus.CANCELLED,
            MemoryEventStatus.EXPIRED,
            MemoryEventStatus.SUPERSEDED
        )
    }
}
