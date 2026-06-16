package com.susking.ephone_s.aidata.domain.model.memory

import com.susking.ephone_s.aidata.domain.model.LongTermMemory

/**
 * 统一记忆召回查询。
 * 第一版用于把原子事件、事实、承诺、摘要合并为结构化召回上下文。
 */
data class MemoryRecallQuery(
    val contactId: String,
    val currentMessage: String,
    val recentMessagesText: String = "",
    val semanticStateText: String = "",
    val sceneType: MemoryRecallSceneType = MemoryRecallSceneType.CHAT,
    val recallPurpose: MemoryRecallPurpose = MemoryRecallPurpose.GENERAL_CHAT,
    val nowTimestamp: Long = System.currentTimeMillis(),
    val maxTokenBudget: Int = DEFAULT_MAX_TOKEN_BUDGET,
    val topK: Int = DEFAULT_TOP_K,
    val activityChainId: String? = null
) {
    fun buildEmbeddingQueryText(): String {
        return listOf(currentMessage, sceneType.name, recallPurpose.name)
            .filter { text: String -> text.isNotBlank() }
            .joinToString(separator = "\n")
    }

    companion object {
        const val DEFAULT_MAX_TOKEN_BUDGET: Int = 12000
        const val DEFAULT_TOP_K: Int = 50
    }
}

/**
 * 召回场景。
 */
enum class MemoryRecallSceneType {
    CHAT,
    CALL,
    CPHONE,
    BACKGROUND,
    SHOPPING,
    OTHER
}

/**
 * 召回目的。
 */
enum class MemoryRecallPurpose {
    GENERAL_CHAT,
    FACT_QUERY,
    RELATIONSHIP_CHECK,
    COMMITMENT_CHECK,
    PREFERENCE_CHECK,
    TIMELINE_REVIEW
}

/**
 * 统一向量索引对象类型。
 */
enum class MemoryIndexedObjectType {
    EVENT,
    SUMMARY,
    FACT,
    COMMITMENT,
    LEGACY_MEMORY
}

/**
 * 召回命中来源。
 */
enum class MemoryRecallSourceType {
    RECENT,
    VECTOR,
    KEYWORD,
    GRAPH,
    SUMMARY,
    FALLBACK
}

/**
 * 结构化召回上下文。
 */
data class MemoryRecallContext(
    val relevantEvents: List<MemoryRecallItem> = emptyList(),
    val activeFacts: List<MemoryRecallItem> = emptyList(),
    val pendingCommitments: List<MemoryRecallItem> = emptyList(),
    val relationshipTimelines: List<MemoryRecallItem> = emptyList(),
    val timelineSummaries: List<MemoryRecallItem> = emptyList(),
    val debugItems: List<MemoryRecallDebugItem> = emptyList(),
    val estimatedTokenCount: Int = 0
) {
    fun toCompatibleMemories(): List<LongTermMemory> {
        return (relevantEvents + activeFacts + pendingCommitments)
            .mapNotNull { item: MemoryRecallItem -> item.compatibleMemory }
            .distinctBy { memory: LongTermMemory -> memory.id }
    }
}

/**
 * 可注入提示词的召回条目。
 */
data class MemoryRecallItem(
    val objectType: MemoryIndexedObjectType,
    val objectId: String,
    val title: String,
    val text: String,
    val eventTime: Long? = null,
    val status: MemoryEventStatus? = null,
    val sourceTypes: Set<MemoryRecallSourceType> = emptySet(),
    val finalScore: Float = 0.0f,
    val confidenceScore: Float = 1.0f,
    val compatibleMemory: LongTermMemory? = null,
    val relationshipContext: RelationshipRecallContext? = null
)

/**
 * 关系时间线召回上下文。
 * 用于提示词注入“当时有效关系、历史变化、失效原因和证据可靠度”。
 */
data class RelationshipRecallContext(
    val contactId: String,
    val endpointKey: String,
    val currentRelations: List<RelationshipRecallEntry> = emptyList(),
    val historicalRelations: List<RelationshipRecallEntry> = emptyList(),
    val targetTime: Long
)

/**
 * 关系时间线中的单条关系声明。
 */
data class RelationshipRecallEntry(
    val relationId: String,
    val relationType: String,
    val fromName: String,
    val toName: String,
    val effectiveFrom: Long,
    val effectiveTo: Long? = null,
    val status: MemoryEventStatus,
    val changeAction: RelationshipChangeAction,
    val changeReason: String? = null,
    val evidenceMemoryId: String? = null,
    val confidenceScore: Float = 1.0f
)

/**
 * 候选项，召回服务内部用于合并和排序。
 */
data class MemoryRecallCandidate(
    val objectType: MemoryIndexedObjectType,
    val objectId: String,
    val title: String,
    val text: String,
    val eventTime: Long? = null,
    val status: MemoryEventStatus? = null,
    val sourceTypes: Set<MemoryRecallSourceType> = emptySet(),
    val semanticScore: Float = 0.0f,
    val keywordScore: Float = 0.0f,
    val graphScore: Float = 0.0f,
    val importanceScore: Float = 0.0f,
    val recencyScore: Float = 0.0f,
    val confidenceScore: Float = 1.0f,
    val stateScore: Float = 1.0f,
    val finalScore: Float = 0.0f,
    val compatibleMemory: LongTermMemory? = null,
    val relationshipContext: RelationshipRecallContext? = null
) {
    fun mergeWith(other: MemoryRecallCandidate): MemoryRecallCandidate {
        return copy(
            sourceTypes = sourceTypes + other.sourceTypes,
            semanticScore = maxOf(semanticScore, other.semanticScore),
            keywordScore = maxOf(keywordScore, other.keywordScore),
            graphScore = maxOf(graphScore, other.graphScore),
            importanceScore = maxOf(importanceScore, other.importanceScore),
            recencyScore = maxOf(recencyScore, other.recencyScore),
            confidenceScore = maxOf(confidenceScore, other.confidenceScore),
            stateScore = maxOf(stateScore, other.stateScore),
            finalScore = maxOf(finalScore, other.finalScore),
            compatibleMemory = compatibleMemory ?: other.compatibleMemory,
            relationshipContext = relationshipContext ?: other.relationshipContext
        )
    }

    fun toItem(finalScoreValue: Float = finalScore): MemoryRecallItem {
        return MemoryRecallItem(
            objectType = objectType,
            objectId = objectId,
            title = title,
            text = text,
            eventTime = eventTime,
            status = status,
            sourceTypes = sourceTypes,
            finalScore = finalScoreValue,
            confidenceScore = confidenceScore,
            compatibleMemory = compatibleMemory,
            relationshipContext = relationshipContext
        )
    }
}

/**
 * 召回调试条目。
 */
data class MemoryRecallDebugItem(
    val objectType: MemoryIndexedObjectType,
    val objectId: String,
    val sourceTypes: Set<MemoryRecallSourceType>,
    val semanticScore: Float,
    val keywordScore: Float,
    val graphScore: Float,
    val importanceScore: Float,
    val recencyScore: Float,
    val confidenceScore: Float,
    val stateScore: Float,
    val finalScore: Float,
    val isInjected: Boolean
)
