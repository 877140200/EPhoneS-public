package com.susking.ephone_s.aidata.domain.model.memory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 分层摘要实体
 * 用于存储日、周、月、年等不同层级的记忆摘要
 */
@Entity(
    tableName = "memory_summaries",
    indices = [Index(value = ["contactId", "summaryLevel", "startTimestamp", "endTimestamp"])]
)
data class MemorySummary(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // 关联的联系人ID
    val contactId: String,

    // 摘要层级
    val summaryLevel: SummaryLevel,

    // 摘要覆盖的起始时间戳
    val startTimestamp: Long,

    // 摘要覆盖的结束时间戳
    val endTimestamp: Long,

    // 摘要文本内容
    val summaryText: String,

    // 该摘要所包含的原始记忆或下一级摘要的数量
    val sourceMemoryCount: Int,

    // AI评估的重要性分数 (1-10)
    val importanceScore: Int,

    // 生成该摘要的AI模型版本
    val modelVersion: String,

    // 记录创建时间
    val createdAt: Long = System.currentTimeMillis(),

    // 记录更新时间
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 摘要层级枚举
 */
enum class SummaryLevel {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
