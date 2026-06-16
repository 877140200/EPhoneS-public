package com.susking.ephone_s.aidata.domain.model.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import java.util.UUID

/**
 * 结构化记忆事件实体
 * 用于存储从对话中抽取的具体事实，如承诺、偏好、纪念日等
 */
@Entity(
    tableName = "memory_events",
    foreignKeys = [
        ForeignKey(
            entity = LongTermMemory::class,
            parentColumns = ["id"],
            childColumns = ["evidenceMemoryId"],
            onDelete = ForeignKey.SET_NULL // 即使原始记忆被删除，事件本身也可能需要保留
        )
    ],
    indices = [
        Index(value = ["contactId", "eventType"]),
        Index(value = ["contactId", "status", "eventTime"]),
        Index(value = ["contactId", "eventType", "status"]),
        Index(value = ["contactId", "eventType", "status", "dedupeKey"]),
        Index(value = ["evidenceMemoryId"]),
        Index(value = ["supersededByEventId"]),
        Index(value = ["sourceTextHash"]),
        Index(value = ["dedupeKey"])
    ]
)
data class MemoryEvent(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // 关联的联系人ID
    val contactId: String,

    // 关联的证据来源记忆ID（可选）
    val evidenceMemoryId: String?,

    // 事件类型
    val eventType: MemoryEventType,

    // 事件标题或简短描述
    val title: String,

    // 事件的详细内容（例如，承诺的具体内容，偏好的具体描述）
    val content: String,

    // 事件发生的时间戳
    val eventTime: Long,

    // AI评估的重要性分数 (1-10)
    val importanceScore: Int,

    // AI抽取的置信度分数 (0.0 - 1.0)
    val confidenceScore: Float,

    // 事件来源模块
    val sourceModule: String, // 例如: "Chat", "Call", "QZone"

    // 事件当前状态，用于区分仍然有效、已完成、已失效或被新事件替代
    val status: MemoryEventStatus = MemoryEventStatus.ACTIVE,

    // 状态变化原因，用于解释完成、取消、过期或被替代的依据
    val statusReason: String? = null,

    // 事件生效开始时间，未设置时默认等同于事件发生时间
    val validFrom: Long? = null,

    // 事件有效截止时间，用于约定、限时偏好和阶段性事实
    val validUntil: Long? = null,

    // 被哪个新事件替代，用于冲突解决和关系状态更新
    val supersededByEventId: String? = null,

    // 承诺或待办完成、取消、失效的时间
    val resolvedAt: Long? = null,

    // 来源文本哈希，用于抽取去重和避免重复写入相同原子事件
    val sourceTextHash: String? = null,

    // 稳定去重键，用于识别同一事实、承诺、偏好或关系状态
    val dedupeKey: String? = null,

    // 原始证据片段，只保存来自来源记忆的文本
    val rawEvidenceText: String? = null,

    // 抽取时间，用于调试和索引重建
    val extractedAt: Long = System.currentTimeMillis(),

    // 记录创建时间
    val createdAt: Long = System.currentTimeMillis(),

    // 记录更新时间
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 记忆事件类型枚举
 */
enum class MemoryEventType {
    COMMITMENT,      // 承诺或约定
    PREFERENCE,      // 用户偏好
    PROHIBITION,     // 用户禁忌
    ANNIVERSARY,     // 纪念日
    RELATIONSHIP,    // 关系变化
    FACT,            // 客观事实
    OPINION,         // 观点或感受
    OTHER            // 其他
}

/**
 * 记忆事件状态枚举
 */
enum class MemoryEventStatus {
    ACTIVE,       // 当前有效
    PENDING,      // 待完成，主要用于承诺或约定
    RESOLVED,     // 已完成或已解决
    CANCELLED,    // 已取消
    EXPIRED,      // 已过期
    SUPERSEDED,   // 已被新事件替代
    ARCHIVED      // 仅保留历史，不参与强状态判断
}
