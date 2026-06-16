package com.susking.ephone_s.aidata.domain.model.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 记忆召回调试记录。
 * 一条记录对应一次结构化记忆召回，后续可通过 activityChainId 关联大脑活动。
 */
@Entity(
    tableName = "memory_recall_debug_records",
    indices = [
        Index(value = ["activityChainId"]),
        Index(value = ["contactId", "createdAt"]),
        Index(value = ["createdAt"])
    ]
)
data class MemoryRecallDebugRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val activityChainId: String? = null,
    val contactId: String,
    val sceneType: MemoryRecallSceneType,
    val recallPurpose: MemoryRecallPurpose,
    val currentMessage: String,
    val recentMessagesText: String,
    val semanticStateText: String = "",
    val maxTokenBudget: Int,
    val estimatedTokenCount: Int,
    val topK: Int,
    val candidateCount: Int,
    val injectedCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 记忆召回调试条目。
 * 保存候选记忆在召回中的来源、各项得分和是否注入提示词。
 */
@Entity(
    tableName = "memory_recall_debug_entries",
    foreignKeys = [
        ForeignKey(
            entity = MemoryRecallDebugRecord::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recordId", "rank"]),
        Index(value = ["objectType", "objectId"]),
        Index(value = ["isInjected"])
    ]
)
data class MemoryRecallDebugEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val recordId: String,
    val rank: Int,
    val objectType: MemoryIndexedObjectType,
    val objectId: String,
    val sourceTypesText: String,
    val semanticScore: Float,
    val keywordScore: Float,
    val graphScore: Float,
    val importanceScore: Float,
    val recencyScore: Float,
    val confidenceScore: Float,
    val stateScore: Float,
    val finalScore: Float,
    val isInjected: Boolean,
    val snapshotTitle: String,
    val snapshotText: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class MemoryRecallDebugRecordWithEntries(
    val record: MemoryRecallDebugRecord,
    val entries: List<MemoryRecallDebugEntry>
)
