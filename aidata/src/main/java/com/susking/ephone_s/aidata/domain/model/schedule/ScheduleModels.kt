package com.susking.ephone_s.aidata.domain.model.schedule

import com.susking.ephone_s.aidata.data.local.entity.CampusEventEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAdjustmentEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAiPolicyEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAssignmentEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCareCandidateEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseRuleEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleExamEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleImportDraftEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleReminderRecordEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleReminderRuleEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSectionTemplateEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSemesterEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleWidgetStateEntity

/**
 * 课程表首页所需状态。
 * UI 只读取聚合后的轻量数据，避免 Fragment 中直接拼接多张表。
 */
data class ScheduleDashboard(
    val courses: List<ScheduleCourseEntity>,
    val courseRules: List<ScheduleCourseRuleEntity>,
    val adjustments: List<ScheduleAdjustmentEntity>,
    val assignments: List<ScheduleAssignmentEntity>,
    val exams: List<ScheduleExamEntity>,
    val campusEvents: List<CampusEventEntity>,
    val aiPolicy: ScheduleAiPolicyEntity,
    val semesters: List<ScheduleSemesterEntity> = emptyList(),
    val sectionTemplates: List<ScheduleSectionTemplateEntity> = emptyList(),
    val importDrafts: List<ScheduleImportDraftEntity> = emptyList(),
    val reminderRules: List<ScheduleReminderRuleEntity> = emptyList(),
    val reminderRecords: List<ScheduleReminderRecordEntity> = emptyList(),
    val careCandidates: List<ScheduleCareCandidateEntity> = emptyList(),
    val widgetState: ScheduleWidgetStateEntity = ScheduleWidgetStateEntity()
)

/**
 * ai 可见的校园状态摘要。
 * content 为空时不注入提示词。
 */
data class SchedulePromptSummary(
    val content: String,
    val generatedAt: Long = System.currentTimeMillis()
)
