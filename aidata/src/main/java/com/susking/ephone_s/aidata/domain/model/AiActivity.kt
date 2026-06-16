package com.susking.ephone_s.aidata.domain.model

/**
 * 封装一次 AI 活动的数据模型
 *
 * @param id 唯一标识
 * @param description 活动的简短描述, e.g., "AI 正在回复"
 * @param prompt 发送的完整提示词
 * @param rawResponse 收到的原始回复
 * @param timestamp 活动发生的时间戳
 * @param status 活动状态
 * @param activityChainId 用于关联同一系列活动（如 PENDING -> SUCCESS）的 ID
 * @param isBackgroundTask 标记是否为后台任务（如图片生成），后台任务会集中在"后台任务"条目下显示
 */
data class AiActivity(
    val id: Long = 0, // id 现在是自增的，默认为 0
    val activityChainId: String,
    val description: String,
    val prompt: String,
    val rawResponse: String,
    val timestamp: Long,
    val status: AiActivityStatus,
    val isRead: Boolean = false, // 新增：标记是否已读
    val hasVibrated: Boolean = false, // 新增：标记是否已振动
    val isBackgroundTask: Boolean = false // 新增：标记是否为后台任务
)