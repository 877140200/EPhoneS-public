package com.susking.ephone_s.aidata.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "long_term_memories")
data class LongTermMemory(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // 关联的联系人ID
    val contactId: String,

    // 记忆原文
    val memoryText: String,

    // 记忆产生的时间戳
    val timestamp: Long = System.currentTimeMillis(),

    // --- 新增字段 ---

    // 记忆类型
    val memoryType: MemoryType = MemoryType.CHAT,

    // AI评估的重要性分数 (1-10)，默认为3
    val importanceScore: Int = 3,

    // 记忆来源模块
    val sourceModule: String = "Chat", // 例如: "Chat", "Call", "QZone", "Album"

    // 是否已经完成向量化。旧原子事件只读保留，不再允许手动向量化；结构化事件可通过兼容记录关联向量。
    val isVectorized: Boolean = false,

    // 向量模型版本，用于追踪和迁移
    val embeddingVersion: String? = null,

    // 最近一次被召回的时间
    val lastRetrievedAt: Long? = null,

    // 总共被召回的次数
    val retrievalCount: Int = 0,

    // --- 旧有字段，保持兼容 ---

    // 是否是视频总结 (后续可由 memoryType 和 sourceModule 替代)
    val isVideoSummary: Boolean = false,

    // 关联的视频通话ID (后续可由事件图谱替代)
    val videoCallId: Long? = null
)

/**
 * 原始记忆类型枚举
 */
enum class MemoryType {
    CHAT,            // 普通聊天内容
    SUMMARY,         // AI生成的总结
    EVENT,           // 结构化事件
    USER_MARKED,     // 用户手动标记的重要记忆
    SYSTEM           // 系统级记忆（如核心设定）
}