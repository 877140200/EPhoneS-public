package com.susking.ephone_s.aidata.domain.model.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import java.util.UUID

/**
 * 记忆图谱节点实体
 * 代表一个具体的人、事、物、地点等
 */
@Entity(
    tableName = "memory_graph_nodes",
    indices = [
        Index(value = ["contactId", "entityType", "name"], unique = true),
        Index(value = ["contactId", "entityType", "normalizedName"])
    ]
)
data class MemoryGraphNode(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // 关联的联系人ID
    val contactId: String,

    // 实体类型
    val entityType: String, // 例如: "Person", "Location", "Organization", "Item"

    // 实体名称
    val name: String,

    // 规范化实体名称，用于跨批次识别同一实体
    val normalizedName: String = name,

    // 实体的别名或曾用名 (JSON格式的字符串列表)
    val aliases: String? = null,

    // 记录创建时间
    val createdAt: Long = System.currentTimeMillis(),

    // 记录更新时间
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 记忆图谱关系实体
 * 代表两个节点之间的关系
 */
@Entity(
    tableName = "memory_graph_relations",
    foreignKeys = [
        ForeignKey(
            entity = MemoryGraphNode::class,
            parentColumns = ["id"],
            childColumns = ["fromNodeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryGraphNode::class,
            parentColumns = ["id"],
            childColumns = ["toNodeId"],
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
        Index(value = ["fromNodeId"]),
        Index(value = ["toNodeId"]),
        Index(value = ["contactId", "relationType"]),
        Index(value = ["contactId", "status", "eventTime"]),
        Index(value = ["contactId", "status", "effectiveFrom", "effectiveTo"]),
        Index(value = ["endpointKey"]),
        Index(value = ["relationKey"], unique = true),
        Index(value = ["supersededByRelationId"]),
        Index(value = ["invalidatedByRelationId"]),
        Index(value = ["previousRelationId"]),
        Index(value = ["extractionBatchId"])
    ]
)
data class MemoryGraphRelation(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // 关联的联系人ID
    val contactId: String,

    // 起始节点ID
    val fromNodeId: String,

    // 目标节点ID
    val toNodeId: String,

    // 关系类型
    val relationType: String, // 例如: "is_friend_of", "works_at", "likes", "dislikes"

    // 同一对节点同一关系类型的生命周期链查询键，不用于自动互斥推断
    val endpointKey: String = "",

    // 稳定关系声明去重键，用于跨批次合并同一条证据声明
    val relationKey: String,

    // 关联的证据来源记忆ID（可选）
    val evidenceMemoryId: String?,

    // 关系建立、结束或变化声明发生的时间
    val eventTime: Long,

    // 关系生效开始时间
    val effectiveFrom: Long = eventTime,

    // 关系生效结束时间，空值表示没有证据证明已结束
    val effectiveTo: Long? = null,

    // 系统确认关系失效的记录时间，空值表示未确认失效
    val invalidatedAt: Long? = null,

    // 由哪条新关系声明触发失效
    val invalidatedByRelationId: String? = null,

    // 模型从原文抽取出的关系变化动作，服务端只执行明确动作
    val changeAction: RelationshipChangeAction = RelationshipChangeAction.ASSERT_ACTIVE,

    // 关系变化原因，必须来自证据文本或模型对证据的结构化解释
    val changeReason: String? = null,

    // 生效区间来源说明，例如 explicit、inferred_from_memory_time、unknown
    val validitySource: String? = null,

    // 模型明确指向的上一条关系ID或上一关系描述，无法定位时仅记录不执行关闭
    val previousRelationId: String? = null,

    // AI抽取的证据可靠度分数 (0.0 - 1.0)，不表示关系强弱
    val confidenceScore: Float,

    // 关系当前状态，用于过滤失效或被替代的关系边
    val status: MemoryEventStatus = MemoryEventStatus.ACTIVE,

    // 状态变化原因
    val statusReason: String? = null,

    // 被哪条新关系替代
    val supersededByRelationId: String? = null,

    // 抽取批次ID，用于调试、回滚和统计
    val extractionBatchId: String? = null,

    // 记录创建时间
    val createdAt: Long = System.currentTimeMillis(),

    // 记录更新时间
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 关系变化动作。
 * 完全证据驱动：服务端不根据社会常识或关系类型自动关闭旧关系。
 */
enum class RelationshipChangeAction {
    ASSERT_ACTIVE,
    ASSERT_ENDED,
    TRANSITION_FROM,
    CORRECT_PREVIOUS,
    UNCLEAR
}
