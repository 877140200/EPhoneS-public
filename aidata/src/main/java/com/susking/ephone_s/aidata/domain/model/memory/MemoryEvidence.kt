package com.susking.ephone_s.aidata.domain.model.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import java.util.UUID

/**
 * 结构化事件证据。
 * 同一语义事件可以拥有多条证据，避免重复抽取时创建多个等价事件。
 */
@Entity(
    tableName = "memory_event_evidences",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEvent::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LongTermMemory::class,
            parentColumns = ["id"],
            childColumns = ["evidenceMemoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["contactId", "eventId"]),
        Index(value = ["contactId", "dedupeKey"]),
        Index(value = ["contactId", "sourceTextHash"], unique = true),
        Index(value = ["evidenceMemoryId"]),
        Index(value = ["extractionBatchId"])
    ]
)
data class MemoryEventEvidence(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // 关联的联系人ID
    val contactId: String,

    // 被证实的结构化事件ID
    val eventId: String,

    // 事件语义去重键
    val dedupeKey: String,

    // 关联的原子记忆ID
    val evidenceMemoryId: String?,

    // 原文证据片段
    val rawEvidenceText: String?,

    // 原文证据哈希
    val sourceTextHash: String,

    // 证据置信度
    val confidenceScore: Float,

    // 抽取批次ID
    val extractionBatchId: String,

    // 记录创建时间
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 结构化关系证据。
 * 同一关系可以拥有多条证据，重复 ASSERT_ACTIVE 只追加证据，不重复创建活跃关系边。
 */
@Entity(
    tableName = "memory_relation_evidences",
    foreignKeys = [
        ForeignKey(
            entity = MemoryGraphRelation::class,
            parentColumns = ["id"],
            childColumns = ["relationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LongTermMemory::class,
            parentColumns = ["id"],
            childColumns = ["evidenceMemoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["contactId", "relationId"]),
        Index(value = ["contactId", "endpointKey"]),
        Index(value = ["contactId", "sourceTextHash"], unique = true),
        Index(value = ["evidenceMemoryId"]),
        Index(value = ["changeAction"]),
        Index(value = ["extractionBatchId"])
    ]
)
data class MemoryRelationEvidence(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // 关联的联系人ID
    val contactId: String,

    // 被证实的结构化关系ID
    val relationId: String,

    // 同一端点关系生命周期键
    val endpointKey: String,

    // 关联的原子记忆ID
    val evidenceMemoryId: String?,

    // 关系变化动作
    val changeAction: RelationshipChangeAction,

    // 原文证据片段
    val rawEvidenceText: String?,

    // 原文证据哈希
    val sourceTextHash: String,

    // 证据置信度
    val confidenceScore: Float,

    // 抽取批次ID
    val extractionBatchId: String,

    // 记录创建时间
    val createdAt: Long = System.currentTimeMillis()
)
