package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程基础信息。
 * 这里保存长期存在的课程本体，具体每周上课时间放在 ScheduleCourseRuleEntity 中。
 */
@Entity(
    tableName = "schedule_courses",
    indices = [Index(value = ["semesterName", "isEnabled"])]
)
data class ScheduleCourseEntity(
    @PrimaryKey val courseId: String,
    val courseName: String,
    val teacherName: String = "",
    val classroom: String = "",
    val courseColor: String = "#7C4DFF",
    val semesterName: String = "默认学期",
    val note: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 课程重复时间规则。
 * 用于描述固定课表，例如每周一第 1-2 节高数。
 */
@Entity(
    tableName = "schedule_course_rules",
    indices = [
        Index(value = ["courseId"]),
        Index(value = ["dayOfWeek", "startSection", "endSection"]),
        Index(value = ["startWeek", "endWeek", "weekMode"])
    ]
)
data class ScheduleCourseRuleEntity(
    @PrimaryKey val ruleId: String,
    val courseId: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val startTimeText: String = "",
    val endTimeText: String = "",
    val startWeek: Int = 1,
    val endWeek: Int = 20,
    val weekMode: String = "EVERY_WEEK",
    val classroomOverride: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 调课、停课、补课记录。
 * 摘要构建时这张表优先级高于固定课表，避免 ai 看到过期安排。
 */
@Entity(
    tableName = "schedule_adjustments",
    indices = [
        Index(value = ["courseId"]),
        Index(value = ["originalDate"]),
        Index(value = ["adjustedDate"]),
        Index(value = ["adjustmentType"])
    ]
)
data class ScheduleAdjustmentEntity(
    @PrimaryKey val adjustmentId: String,
    val courseId: String? = null,
    val originalDate: Long = 0L,
    val originalStartSection: Int = 0,
    val originalEndSection: Int = 0,
    val adjustedDate: Long? = null,
    val adjustedStartSection: Int? = null,
    val adjustedEndSection: Int? = null,
    val originalClassroom: String = "",
    val adjustedClassroom: String = "",
    val adjustmentType: String = "CHANGE",
    val reason: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 作业任务。
 * ai 只需要看到未完成和临近截止的作业，已完成作业默认不进入摘要。
 */
@Entity(
    tableName = "schedule_assignments",
    indices = [
        Index(value = ["courseId"]),
        Index(value = ["dueAt"]),
        Index(value = ["status"]),
        Index(value = ["priority"])
    ]
)
data class ScheduleAssignmentEntity(
    @PrimaryKey val assignmentId: String,
    val courseId: String? = null,
    val title: String,
    val content: String = "",
    val dueAt: Long = 0L,
    val priority: Int = 1,
    val status: String = "TODO",
    val completedAt: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 考试安排。
 * 用于倒计时、复习状态管理和 ai 对近期考试的自然关心。
 */
@Entity(
    tableName = "schedule_exams",
    indices = [
        Index(value = ["courseId"]),
        Index(value = ["examAt"]),
        Index(value = ["reviewStatus"]),
        Index(value = ["importance"])
    ]
)
data class ScheduleExamEntity(
    @PrimaryKey val examId: String,
    val courseId: String? = null,
    val examName: String,
    val examAt: Long,
    val classroom: String = "",
    val scopeText: String = "",
    val examType: String = "OTHER",
    val reviewStatus: String = "NOT_STARTED",
    val importance: Int = 1,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 校园动态。
 * 承载讲座、社团、实验、请假、答辩、课程群通知等课程以外事件。
 */
@Entity(
    tableName = "campus_events",
    indices = [
        Index(value = ["eventType"]),
        Index(value = ["startAt"]),
        Index(value = ["endAt"]),
        Index(value = ["isCompleted"])
    ]
)
data class CampusEventEntity(
    @PrimaryKey val eventId: String,
    val title: String,
    val content: String = "",
    val eventType: String = "OTHER",
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val location: String = "",
    val importance: Int = 1,
    val isCompleted: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * AI 关心策略。
 * 默认主键使用 default，控制校园状态是否允许进入提示词以及主动关心强度。
 */
@Entity(tableName = "schedule_ai_policies")
data class ScheduleAiPolicyEntity(
    @PrimaryKey val policyId: String = "default",
    val isAiVisible: Boolean = true,
    val canAiProactivelyCare: Boolean = true,
    val careIntensity: String = "NORMAL",
    val visibleRange: String = "WEEK",
    val includeTomorrowCourses: Boolean = true,
    val includeCompletedAssignments: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 学期配置。
 * 用于计算当前第几周，并作为固定课表、导入和提醒的时间基础。
 */
@Entity(
    tableName = "schedule_semesters",
    indices = [Index(value = ["isActive", "startDateMillis"])]
)
data class ScheduleSemesterEntity(
    @PrimaryKey val semesterId: String,
    val semesterName: String,
    val startDateMillis: Long,
    val totalWeeks: Int = 20,
    val isActive: Boolean = true,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 节次模板。
 * 同一学期可以拥有多节课模板，用于周课表展示和上课前提醒。
 */
@Entity(
    tableName = "schedule_section_templates",
    indices = [
        Index(value = ["semesterId"]),
        Index(value = ["sectionIndex"])
    ]
)
data class ScheduleSectionTemplateEntity(
    @PrimaryKey val sectionTemplateId: String,
    val semesterId: String,
    val sectionIndex: Int,
    val displayName: String,
    val startTimeText: String,
    val endTimeText: String,
    val dayPart: String = "MORNING",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 课程表导入草稿。
 * 文本或后续截图识别结果先保存为草稿，用户确认后才写入事实表。
 */
@Entity(
    tableName = "schedule_import_drafts",
    indices = [
        Index(value = ["sourceType"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class ScheduleImportDraftEntity(
    @PrimaryKey val draftId: String,
    val sourceType: String = "TEXT",
    val rawContent: String,
    val previewJson: String = "",
    val status: String = "PREVIEW",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 本地提醒规则。
 * 用于控制上课、作业、考试等本地通知提前多久触发。
 */
@Entity(
    tableName = "schedule_reminder_rules",
    indices = [
        Index(value = ["targetType", "targetId"]),
        Index(value = ["isEnabled"])
    ]
)
data class ScheduleReminderRuleEntity(
    @PrimaryKey val reminderRuleId: String,
    val targetType: String,
    val targetId: String,
    val minutesBefore: Int = 30,
    val isEnabled: Boolean = true,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 本地提醒记录。
 * 用于避免同一课程、作业或考试在同一触发点重复提醒。
 */
@Entity(
    tableName = "schedule_reminder_records",
    indices = [
        Index(value = ["reminderRuleId"]),
        Index(value = ["targetType", "targetId"]),
        Index(value = ["triggerAt"])
    ]
)
data class ScheduleReminderRecordEntity(
    @PrimaryKey val reminderRecordId: String,
    val reminderRuleId: String,
    val targetType: String,
    val targetId: String,
    val triggerAt: Long,
    val deliveredAt: Long? = null,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * AI 主动关心候选事件。
 * 这里只保存候选事实和冷却信息，真正请求外部模型仍必须交给 brain 展示并转发。
 */
@Entity(
    tableName = "schedule_care_candidates",
    indices = [
        Index(value = ["targetType", "targetId"]),
        Index(value = ["status"]),
        Index(value = ["nextEligibleAt"])
    ]
)
data class ScheduleCareCandidateEntity(
    @PrimaryKey val careCandidateId: String,
    val targetType: String,
    val targetId: String,
    val title: String,
    val reason: String,
    val priority: Int = 1,
    val nextEligibleAt: Long = 0L,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 课程表桌面小组件状态。
 * 先保存轻量展示摘要，后续真实桌面组件或小手机桌面卡片都可以复用。
 */
@Entity(tableName = "schedule_widget_states")
data class ScheduleWidgetStateEntity(
    @PrimaryKey val widgetId: String = "default",
    val nextCourseText: String = "",
    val examCountdownText: String = "",
    val pendingAssignmentText: String = "",
    val accentColor: String = "#7C4DFF",
    val updatedAt: Long = System.currentTimeMillis()
)

/** 单双周模式常量。集中定义，避免各处用字符串字面量造成不一致。 */
object ScheduleWeekMode {
    const val EVERY_WEEK: String = "EVERY_WEEK"
    const val ODD_WEEK: String = "ODD_WEEK"
    const val EVEN_WEEK: String = "EVEN_WEEK"
}

/**
 * 判断该课程时间规则在指定教学周是否实际上课(单一真相源)。
 *
 * 这是课程表「某周是否有这门课」的唯一权威判定，首页渲染、冲突检测、提示词摘要、
 * 提醒 Worker 都必须经由此函数，避免出现「有的地方算单双周、有的地方不算」的不一致。
 *
 * 判定规则：先看周次是否落在 [startWeek, endWeek] 区间内，再按单双周模式过滤奇偶周。
 *
 * @param week 目标教学周(从 1 开始)。
 * @return 该周是否实际上课。
 */
fun ScheduleCourseRuleEntity.isActiveInWeek(week: Int): Boolean {
    if (week < startWeek || week > endWeek) return false
    return when (weekMode) {
        ScheduleWeekMode.ODD_WEEK -> week % 2 == 1
        ScheduleWeekMode.EVEN_WEEK -> week % 2 == 0
        else -> true
    }
}

/**
 * 判断两条课程时间规则是否存在「真正会同时上课」的冲突。
 *
 * 相比仅比较周次区间是否重叠，这里进一步要求在重叠的周次区间内至少存在一个
 * 两条规则都实际上课的周(即单双周也吻合)。例如一门单周课和一门双周课即使周次
 * 区间完全重叠，也永远不会在同一周上课，因此不算冲突。
 *
 * 注意：本函数只比较周次维度。调用方仍需自行判断星期(dayOfWeek)与节次(section)是否重叠，
 * 三者同时成立才构成真实冲突。
 *
 * @param other 另一条课程时间规则。
 * @return 周次维度上两条规则是否存在共同上课周。
 */
fun ScheduleCourseRuleEntity.hasWeekConflictWith(other: ScheduleCourseRuleEntity): Boolean {
    val overlapStart: Int = maxOf(startWeek, other.startWeek)
    val overlapEnd: Int = minOf(endWeek, other.endWeek)
    if (overlapStart > overlapEnd) return false
    // 在重叠区间内逐周检查是否存在两条规则都上课的周。
    return (overlapStart..overlapEnd).any { week: Int -> isActiveInWeek(week) && other.isActiveInWeek(week) }
}
