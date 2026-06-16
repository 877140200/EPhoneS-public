package com.susking.ephone_s.aidata.domain.model

/**
 * AI 活动的状态枚举
 */
enum class AiActivityStatus {
    WAITING,    // 等待中（排队等待执行）
    PROCESSING, // 处理中
    SUCCESS,    // 成功
    FAILED,     // 失败
    STOP,       // 已暂停（被用户暂停的后台任务）
    CANCELLED   // 已取消（被用户取消的单个任务）
}