package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus

/**
 * AI 活动的数据库实体。
 * @param id 活动的唯一标识符，自增主键。
 * @param timestamp 活动发生的时间戳。
 * @param description 活动的简短描述。
 * @param prompt 发送给 AI 的完整提示词。
 * @param rawResponse AI 返回的原始回复。
 * @param status 活动的当前状态 (e.g., PENDING, SUCCESS, FAILED)。
 * @param isRead 标志该条目是否已被用户查看。
 * @param activityChainId 用于关联同一系列活动的 ID
 * @param isBackgroundTask 标记是否为后台任务
 */
@Entity(tableName = "ai_activities")
data class AiActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityChainId: String,
    val timestamp: Long,
    val description: String,
    val prompt: String,
    val rawResponse: String,
    val status: AiActivityStatus,
    val isRead: Boolean = false,
    val hasVibrated: Boolean = false,
    val isBackgroundTask: Boolean = false
)

/**
 * 将数据库实体 AiActivityEntity 转换为领域模型 AiActivity。
 */
fun AiActivityEntity.toDomainModel(): AiActivity {
    return AiActivity(
        id = id,
        activityChainId = activityChainId,
        timestamp = timestamp,
        description = description,
        prompt = prompt,
        rawResponse = rawResponse,
        status = status,
        isRead = isRead,
        hasVibrated = hasVibrated,
        isBackgroundTask = isBackgroundTask
    )
}

/**
 * 将领域模型 AiActivity 转换为数据库实体 AiActivityEntity。
 */
fun AiActivity.toEntity(): AiActivityEntity {
    return AiActivityEntity(
        id = if (id == 0L) 0L else id, // 保留 id 以便更新
        activityChainId = activityChainId,
        timestamp = timestamp,
        description = description,
        prompt = prompt,
        rawResponse = rawResponse,
        status = status,
        isRead = isRead,
        hasVibrated = hasVibrated,
        isBackgroundTask = isBackgroundTask
    )
}