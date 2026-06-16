package com.susking.ephone_s.brain.ui

/**
 * 大脑自动计划展示项。
 *
 * 这里只承载展示数据，不保存任何新状态，避免影响现有导入导出与数据库迁移。
 */
data class AutoPlanItem(
    val id: String,
    val type: AutoPlanType,
    val contactName: String,
    val title: String,
    val description: String,
    val isEnabled: Boolean
)

/**
 * 自动计划类型。
 */
enum class AutoPlanType(
    val displayName: String
) {
    AUTO_DIARY("自动日记"),
    BACKGROUND_ACTIVITY("独立后台活动"),
    EMPTY("暂无计划")
}
