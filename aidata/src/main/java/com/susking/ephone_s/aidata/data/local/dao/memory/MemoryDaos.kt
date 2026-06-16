package com.susking.ephone_s.aidata.data.local.dao.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEmbedding
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventEvidence
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphNode
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphRelation
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRelationEvidence
import com.susking.ephone_s.aidata.domain.model.memory.RelationshipChangeAction
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugEntry
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugRecord
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: MemoryEmbedding)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<MemoryEmbedding>)

    @Query("SELECT * FROM memory_embeddings WHERE memoryId = :memoryId LIMIT 1")
    suspend fun getEmbeddingByMemoryId(memoryId: String): MemoryEmbedding?

    @Query("SELECT * FROM memory_embeddings ORDER BY createdAt DESC")
    suspend fun getAllEmbeddingsList(): List<MemoryEmbedding>

    @Query("SELECT * FROM memory_embeddings WHERE memoryId = :memoryId AND isActive = 1 LIMIT 1")
    fun observeActiveEmbeddingForMemory(memoryId: String): Flow<MemoryEmbedding?>

    @Query("SELECT * FROM memory_embeddings WHERE contactId = :contactId AND isActive = 1")
    fun getActiveEmbeddingsForContact(contactId: String): Flow<List<MemoryEmbedding>>

    @Query("SELECT * FROM memory_embeddings WHERE contactId = :contactId AND indexedObjectType = :objectType AND isActive = 1")
    suspend fun getActiveEmbeddingsForObjectType(contactId: String, objectType: MemoryIndexedObjectType): List<MemoryEmbedding>

    @Query("SELECT * FROM memory_embeddings WHERE indexedObjectType = :objectType AND indexedObjectId = :objectId AND isActive = 1 LIMIT 1")
    suspend fun getActiveEmbeddingForIndexedObject(objectType: MemoryIndexedObjectType, objectId: String): MemoryEmbedding?

    @Query("UPDATE memory_embeddings SET isActive = 0 WHERE indexedObjectType = :objectType AND indexedObjectId = :objectId")
    suspend fun deactivateEmbeddingsForIndexedObject(objectType: MemoryIndexedObjectType, objectId: String)

    @Query("DELETE FROM memory_embeddings WHERE indexedObjectId = :objectId")
    suspend fun deleteEmbeddingsForIndexedObjectId(objectId: String)
 
    @Query("UPDATE memory_embeddings SET isActive = 0 WHERE modelVersion != :currentModelVersion")
    suspend fun deactivateOldEmbeddings(currentModelVersion: String)

    @Query("DELETE FROM memory_embeddings")
    suspend fun deleteAllEmbeddings()

    @Query("SELECT DISTINCT contactId FROM memory_embeddings")
    suspend fun getIndexedContactIds(): List<String>

    @Query("SELECT * FROM memory_embeddings WHERE memoryId = :memoryId AND isActive = 1 LIMIT 1")
    suspend fun getActiveEmbeddingForMemory(memoryId: String): MemoryEmbedding?
}

@Dao
interface MemorySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: MemorySummary)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(summaries: List<MemorySummary>)

    @Query("SELECT * FROM memory_summaries WHERE contactId = :contactId AND summaryLevel = :level ORDER BY startTimestamp DESC")
    fun getSummariesByLevel(contactId: String, level: SummaryLevel): Flow<List<MemorySummary>>

    @Query("SELECT * FROM memory_summaries WHERE contactId = :contactId AND startTimestamp >= :startTime AND endTimestamp <= :endTime")
    fun getSummariesInTimeRange(contactId: String, startTime: Long, endTime: Long): Flow<List<MemorySummary>>

    @Query("SELECT * FROM memory_summaries WHERE contactId = :contactId AND summaryLevel = :level AND startTimestamp >= :startTime AND endTimestamp <= :endTime ORDER BY startTimestamp ASC")
    suspend fun getSummariesByLevelInTimeRange(contactId: String, level: SummaryLevel, startTime: Long, endTime: Long): List<MemorySummary>

    @Query("SELECT * FROM memory_summaries WHERE contactId = :contactId AND summaryLevel = :level AND startTimestamp = :startTime AND endTimestamp = :endTime LIMIT 1")
    suspend fun getSummaryForWindow(contactId: String, level: SummaryLevel, startTime: Long, endTime: Long): MemorySummary?

    @Query("SELECT * FROM memory_summaries WHERE contactId = :contactId AND summaryLevel = :level ORDER BY endTimestamp DESC LIMIT 1")
    suspend fun getLatestSummaryByLevel(contactId: String, level: SummaryLevel): MemorySummary?

    @Query("SELECT * FROM memory_summaries WHERE contactId = :contactId ORDER BY summaryLevel ASC, startTimestamp DESC")
    fun getSummariesForContact(contactId: String): Flow<List<MemorySummary>>

    @Query("SELECT * FROM memory_summaries WHERE contactId = :contactId ORDER BY endTimestamp DESC, importanceScore DESC LIMIT :limit")
    suspend fun getLatestSummaries(contactId: String, limit: Int): List<MemorySummary>

    @Query("SELECT * FROM memory_summaries WHERE id = :id LIMIT 1")
    suspend fun getSummaryById(id: String): MemorySummary?

    @Query("DELETE FROM memory_summaries WHERE id = :id")
    suspend fun deleteSummaryById(id: String)
 
    @Query("SELECT * FROM memory_summaries ORDER BY summaryLevel ASC, startTimestamp DESC")
    suspend fun getAllSummaryList(): List<MemorySummary>

    @Query("SELECT * FROM memory_summaries WHERE contactId = :contactId ORDER BY endTimestamp DESC, importanceScore DESC")
    suspend fun getSummaryListForContact(contactId: String): List<MemorySummary>
}

@Dao
interface MemoryEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: MemoryEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<MemoryEvent>)

    @Update
    suspend fun update(event: MemoryEvent)

    @Delete
    suspend fun delete(event: MemoryEvent)

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId ORDER BY eventTime DESC")
    fun getAllEvents(contactId: String): Flow<List<MemoryEvent>>

    @Query("SELECT * FROM memory_events ORDER BY eventTime DESC")
    fun getAllEvents(): Flow<List<MemoryEvent>>

    @Query("SELECT * FROM memory_events ORDER BY eventTime DESC")
    suspend fun getAllEventList(): List<MemoryEvent>

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId AND eventType = :eventType ORDER BY importanceScore DESC, eventTime DESC")
    fun getEventsByType(contactId: String, eventType: String): Flow<List<MemoryEvent>>

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId ORDER BY eventTime DESC LIMIT :limit")
    suspend fun getRecentEvents(contactId: String, limit: Int): List<MemoryEvent>

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId AND eventTime >= :startTime AND eventTime < :endTime ORDER BY importanceScore DESC, eventTime DESC")
    suspend fun getEventsInTimeRange(contactId: String, startTime: Long, endTime: Long): List<MemoryEvent>

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId AND eventType IN (:eventTypes) AND status IN (:statuses) ORDER BY importanceScore DESC, eventTime DESC LIMIT :limit")
    suspend fun getEventsByTypesAndStatuses(contactId: String, eventTypes: List<MemoryEventType>, statuses: List<MemoryEventStatus>, limit: Int): List<MemoryEvent>

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId AND status IN (:statuses) ORDER BY importanceScore DESC, eventTime DESC LIMIT :limit")
    suspend fun getEventsByStatuses(contactId: String, statuses: List<MemoryEventStatus>, limit: Int): List<MemoryEvent>

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId AND status IN (:statuses) ORDER BY importanceScore DESC, eventTime DESC")
    suspend fun getEventsByStatuses(contactId: String, statuses: List<MemoryEventStatus>): List<MemoryEvent>

    @Query("SELECT * FROM memory_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): MemoryEvent?

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId AND dedupeKey = :dedupeKey ORDER BY confidenceScore DESC, importanceScore DESC, eventTime DESC LIMIT 1")
    suspend fun getLatestEventByDedupeKey(contactId: String, dedupeKey: String): MemoryEvent?

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId AND dedupeKey IN (:dedupeKeys) ORDER BY eventTime DESC")
    suspend fun getEventsByDedupeKeys(contactId: String, dedupeKeys: List<String>): List<MemoryEvent>

    @Query("SELECT * FROM memory_events WHERE contactId = :contactId AND dedupeKey = :dedupeKey AND status IN (:statuses) ORDER BY confidenceScore DESC, importanceScore DESC, eventTime DESC")
    suspend fun getActiveEventsByDedupeKey(contactId: String, dedupeKey: String, statuses: List<MemoryEventStatus>): List<MemoryEvent>

    @Query("UPDATE memory_events SET confidenceScore = :confidenceScore, importanceScore = :importanceScore, eventTime = :eventTime, rawEvidenceText = :rawEvidenceText, extractedAt = :extractedAt, updatedAt = :updatedAt WHERE id = :eventId")
    suspend fun updateEventEvidenceMetadata(eventId: String, confidenceScore: Float, importanceScore: Int, eventTime: Long, rawEvidenceText: String?, extractedAt: Long, updatedAt: Long)

    @Query("SELECT DISTINCT evidenceMemoryId FROM memory_events WHERE contactId = :contactId AND evidenceMemoryId IS NOT NULL")
    suspend fun getEventEvidenceMemoryIds(contactId: String): List<String>

    @Query("UPDATE memory_events SET status = :status, statusReason = :statusReason, supersededByEventId = :supersededByEventId, updatedAt = :updatedAt WHERE id = :eventId")
    suspend fun updateEventStatus(eventId: String, status: MemoryEventStatus, statusReason: String?, supersededByEventId: String?, updatedAt: Long)
}

@Dao
interface MemoryEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventEvidence(evidence: MemoryEventEvidence)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventEvidences(evidences: List<MemoryEventEvidence>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelationEvidence(evidence: MemoryRelationEvidence)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelationEvidences(evidences: List<MemoryRelationEvidence>)

    @Query("SELECT * FROM memory_event_evidences ORDER BY createdAt DESC")
    suspend fun getAllEventEvidencesList(): List<MemoryEventEvidence>

    @Query("SELECT * FROM memory_relation_evidences ORDER BY createdAt DESC")
    suspend fun getAllRelationEvidencesList(): List<MemoryRelationEvidence>

    @Query("SELECT * FROM memory_event_evidences WHERE contactId = :contactId AND sourceTextHash = :sourceTextHash LIMIT 1")
    suspend fun findEventEvidenceBySourceHash(contactId: String, sourceTextHash: String): MemoryEventEvidence?

    @Query("SELECT * FROM memory_relation_evidences WHERE contactId = :contactId AND sourceTextHash = :sourceTextHash LIMIT 1")
    suspend fun findRelationEvidenceBySourceHash(contactId: String, sourceTextHash: String): MemoryRelationEvidence?

    @Query("SELECT COUNT(*) FROM memory_event_evidences WHERE contactId = :contactId AND eventId = :eventId")
    suspend fun countEventEvidences(contactId: String, eventId: String): Int

    @Query("SELECT COUNT(*) FROM memory_relation_evidences WHERE contactId = :contactId AND relationId = :relationId")
    suspend fun countRelationEvidences(contactId: String, relationId: String): Int
}

@Dao
interface MemoryRecallDebugDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MemoryRecallDebugRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<MemoryRecallDebugRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<MemoryRecallDebugEntry>)

    @Query("SELECT * FROM memory_recall_debug_records ORDER BY createdAt DESC")
    suspend fun getAllRecordsList(): List<MemoryRecallDebugRecord>

    @Query("SELECT * FROM memory_recall_debug_entries ORDER BY recordId, rank ASC")
    suspend fun getAllEntriesList(): List<MemoryRecallDebugEntry>

    @Query("SELECT * FROM memory_recall_debug_records WHERE contactId = :contactId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRecordForContact(contactId: String): MemoryRecallDebugRecord?

    @Query("SELECT * FROM memory_recall_debug_records WHERE activityChainId = :activityChainId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRecordForActivity(activityChainId: String): MemoryRecallDebugRecord?

    @Query("SELECT * FROM memory_recall_debug_records ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRecord(): MemoryRecallDebugRecord?

    @Query("SELECT * FROM memory_recall_debug_entries WHERE recordId = :recordId ORDER BY rank ASC")
    suspend fun getEntriesForRecord(recordId: String): List<MemoryRecallDebugEntry>

    @Query("DELETE FROM memory_recall_debug_records WHERE id NOT IN (SELECT id FROM memory_recall_debug_records ORDER BY createdAt DESC LIMIT :keepCount)")
    suspend fun deleteOldRecords(keepCount: Int)
}

@Dao
interface MemoryGraphDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNode(node: MemoryGraphNode)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNodes(nodes: List<MemoryGraphNode>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relation: MemoryGraphRelation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(relations: List<MemoryGraphRelation>)

    @Query("SELECT * FROM memory_graph_nodes ORDER BY updatedAt DESC")
    suspend fun getAllNodesList(): List<MemoryGraphNode>

    @Query("SELECT * FROM memory_graph_relations ORDER BY eventTime DESC")
    suspend fun getAllRelationsList(): List<MemoryGraphRelation>

    @Update
    suspend fun updateNode(node: MemoryGraphNode)

    @Update
    suspend fun updateRelation(relation: MemoryGraphRelation)

    @Delete
    suspend fun deleteNode(node: MemoryGraphNode)

    @Delete
    suspend fun deleteRelation(relation: MemoryGraphRelation)

    @Query("SELECT * FROM memory_graph_nodes WHERE contactId = :contactId AND name = :name AND entityType = :type LIMIT 1")
    suspend fun findNode(contactId: String, name: String, type: String): MemoryGraphNode?

    @Query("SELECT * FROM memory_graph_nodes WHERE contactId = :contactId AND normalizedName = :normalizedName AND entityType = :type LIMIT 1")
    suspend fun findNodeByNormalizedName(contactId: String, normalizedName: String, type: String): MemoryGraphNode?

    @Query("SELECT * FROM memory_graph_nodes WHERE contactId = :contactId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getNodesForContact(contactId: String, limit: Int): List<MemoryGraphNode>

    @Query("SELECT * FROM memory_graph_nodes WHERE contactId = :contactId AND id IN (:nodeIds)")
    suspend fun getNodesByIds(contactId: String, nodeIds: List<String>): List<MemoryGraphNode>

    @Query("SELECT * FROM memory_graph_relations WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    fun getRelationsForNode(nodeId: String): Flow<List<MemoryGraphRelation>>

    @Query("SELECT * FROM memory_graph_nodes ORDER BY updatedAt DESC")
    fun getAllNodes(): Flow<List<MemoryGraphNode>>

    @Query("SELECT * FROM memory_graph_relations ORDER BY eventTime DESC")
    fun getAllRelations(): Flow<List<MemoryGraphRelation>>

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND status IN (:statuses) ORDER BY eventTime DESC LIMIT :limit")
    suspend fun getRelationsForContact(contactId: String, statuses: List<MemoryEventStatus>, limit: Int): List<MemoryGraphRelation>

    @Query("SELECT * FROM memory_graph_relations WHERE relationKey = :relationKey LIMIT 1")
    suspend fun findRelationByKey(relationKey: String): MemoryGraphRelation?

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND fromNodeId = :fromNodeId AND relationType = :relationType AND toNodeId = :toNodeId AND ifnull(evidenceMemoryId, '') = ifnull(:evidenceMemoryId, '') ORDER BY confidenceScore DESC, eventTime DESC LIMIT 1")
    suspend fun findRelationByEndpoint(contactId: String, fromNodeId: String, relationType: String, toNodeId: String, evidenceMemoryId: String?): MemoryGraphRelation?

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND endpointKey = :endpointKey AND status IN (:statuses) ORDER BY effectiveFrom DESC, eventTime DESC")
    suspend fun getRelationHistoryByEndpointKey(contactId: String, endpointKey: String, statuses: List<MemoryEventStatus>): List<MemoryGraphRelation>

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND endpointKey = :endpointKey AND status IN (:statuses) AND effectiveTo IS NULL ORDER BY confidenceScore DESC, effectiveFrom ASC, eventTime DESC LIMIT 1")
    suspend fun getCurrentActiveRelationByEndpointKey(contactId: String, endpointKey: String, statuses: List<MemoryEventStatus>): MemoryGraphRelation?

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND previousRelationId = :previousRelationId ORDER BY eventTime DESC")
    suspend fun getRelationsByPreviousRelationId(contactId: String, previousRelationId: String): List<MemoryGraphRelation>

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND status IN (:statuses) AND effectiveFrom <= :targetTime AND (effectiveTo IS NULL OR effectiveTo >= :targetTime) ORDER BY confidenceScore DESC, effectiveFrom DESC LIMIT :limit")
    suspend fun getRelationsEffectiveAt(contactId: String, statuses: List<MemoryEventStatus>, targetTime: Long, limit: Int): List<MemoryGraphRelation>

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND (fromNodeId = :firstNodeId AND toNodeId = :secondNodeId OR fromNodeId = :secondNodeId AND toNodeId = :firstNodeId) AND status IN (:statuses) AND effectiveTo IS NULL ORDER BY confidenceScore DESC, effectiveFrom DESC")
    suspend fun getCurrentRelationsBetweenNodes(contactId: String, firstNodeId: String, secondNodeId: String, statuses: List<MemoryEventStatus>): List<MemoryGraphRelation>

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND relationKey IN (:relationKeys)")
    suspend fun getRelationsByKeys(contactId: String, relationKeys: List<String>): List<MemoryGraphRelation>

    @Query("UPDATE memory_graph_relations SET status = :status, statusReason = :statusReason, supersededByRelationId = :supersededByRelationId, effectiveTo = :effectiveTo, invalidatedAt = :invalidatedAt, invalidatedByRelationId = :invalidatedByRelationId, changeReason = :changeReason, updatedAt = :updatedAt WHERE id = :relationId")
    suspend fun updateRelationStatus(relationId: String, status: MemoryEventStatus, statusReason: String?, supersededByRelationId: String?, effectiveTo: Long?, invalidatedAt: Long?, invalidatedByRelationId: String?, changeReason: String?, updatedAt: Long)

    @Query("UPDATE memory_graph_relations SET status = :status, statusReason = :statusReason, effectiveTo = :effectiveTo, invalidatedAt = :invalidatedAt, invalidatedByRelationId = :invalidatedByRelationId, changeReason = :changeReason, updatedAt = :updatedAt WHERE id = :relationId")
    suspend fun invalidateRelationByEvidence(relationId: String, status: MemoryEventStatus, statusReason: String?, effectiveTo: Long?, invalidatedAt: Long?, invalidatedByRelationId: String?, changeReason: String?, updatedAt: Long)

    @Query("UPDATE memory_graph_relations SET confidenceScore = :confidenceScore, eventTime = :eventTime, changeReason = :changeReason, extractionBatchId = :extractionBatchId, updatedAt = :updatedAt WHERE id = :relationId")
    suspend fun updateRelationEvidenceMetadata(relationId: String, confidenceScore: Float, eventTime: Long, changeReason: String?, extractionBatchId: String?, updatedAt: Long)

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND status IN (:statuses) AND changeAction IN (:changeActions) AND confidenceScore >= :minimumConfidence ORDER BY eventTime DESC LIMIT :limit")
    suspend fun getHighConfidenceRelations(contactId: String, statuses: List<MemoryEventStatus>, changeActions: List<RelationshipChangeAction>, minimumConfidence: Float, limit: Int): List<MemoryGraphRelation>

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND status IN (:statuses) AND confidenceScore >= :minimumConfidence ORDER BY eventTime DESC LIMIT :limit")
    suspend fun getHighConfidenceRelations(contactId: String, statuses: List<MemoryEventStatus>, minimumConfidence: Float, limit: Int): List<MemoryGraphRelation>

    @Query("SELECT * FROM memory_graph_relations WHERE contactId = :contactId AND confidenceScore >= :minimumConfidence ORDER BY eventTime DESC LIMIT :limit")
    suspend fun getHighConfidenceRelations(contactId: String, minimumConfidence: Float, limit: Int): List<MemoryGraphRelation>
}
