package com.susking.ephone_s.aidata.domain.repository

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
import com.susking.ephone_s.aidata.domain.model.schedule.ScheduleDashboard
import kotlinx.coroutines.flow.Flow

/**
 * 课程表与校园动态仓库契约。
 * schedule UI、导入导出和提示词摘要都通过该接口访问数据。
 */
interface ScheduleRepository {
    fun observeDashboard(): Flow<ScheduleDashboard>
    fun observeCourses(): Flow<List<ScheduleCourseEntity>>
    fun observeCourseRules(): Flow<List<ScheduleCourseRuleEntity>>
    fun observeAssignments(): Flow<List<ScheduleAssignmentEntity>>
    fun observeExams(): Flow<List<ScheduleExamEntity>>
    fun observeCampusEvents(): Flow<List<CampusEventEntity>>
    fun observeAiPolicy(): Flow<ScheduleAiPolicyEntity>
    fun observeSemesters(): Flow<List<ScheduleSemesterEntity>>
    fun observeSectionTemplates(): Flow<List<ScheduleSectionTemplateEntity>>
    fun observeImportDrafts(): Flow<List<ScheduleImportDraftEntity>>
    fun observeReminderRules(): Flow<List<ScheduleReminderRuleEntity>>
    fun observeReminderRecords(): Flow<List<ScheduleReminderRecordEntity>>
    fun observeCareCandidates(): Flow<List<ScheduleCareCandidateEntity>>
    fun observeWidgetState(): Flow<ScheduleWidgetStateEntity>

    suspend fun getAllCourses(): List<ScheduleCourseEntity>
    suspend fun getAllCourseRules(): List<ScheduleCourseRuleEntity>
    suspend fun getAllAdjustments(): List<ScheduleAdjustmentEntity>
    suspend fun getAllAssignments(): List<ScheduleAssignmentEntity>
    suspend fun getAllExams(): List<ScheduleExamEntity>
    suspend fun getAllCampusEvents(): List<CampusEventEntity>
    suspend fun getAiPolicy(): ScheduleAiPolicyEntity
    suspend fun getAllSemesters(): List<ScheduleSemesterEntity>
    suspend fun getAllSectionTemplates(): List<ScheduleSectionTemplateEntity>
    suspend fun getAllImportDrafts(): List<ScheduleImportDraftEntity>
    suspend fun getAllReminderRules(): List<ScheduleReminderRuleEntity>
    suspend fun getAllReminderRecords(): List<ScheduleReminderRecordEntity>
    suspend fun getAllCareCandidates(): List<ScheduleCareCandidateEntity>
    suspend fun getWidgetState(): ScheduleWidgetStateEntity

    suspend fun saveCourse(course: ScheduleCourseEntity): Unit
    suspend fun saveCourseRule(rule: ScheduleCourseRuleEntity): Unit
    suspend fun deleteCourseWithRules(courseId: String): Unit
    suspend fun deleteCourseOnlyWithRules(courseId: String): Unit
    suspend fun deleteSectionsAtOrAfter(sectionIndex: Int): Unit
    suspend fun saveAdjustment(adjustment: ScheduleAdjustmentEntity): Unit
    suspend fun saveAssignment(assignment: ScheduleAssignmentEntity): Unit
    suspend fun saveExam(exam: ScheduleExamEntity): Unit
    suspend fun saveCampusEvent(event: CampusEventEntity): Unit
    suspend fun saveAiPolicy(policy: ScheduleAiPolicyEntity): Unit
    suspend fun saveSemester(semester: ScheduleSemesterEntity): Unit
    suspend fun deleteSemesterWithSchedule(semester: ScheduleSemesterEntity): Unit
    suspend fun deleteSectionTemplatesAtOrAfter(semesterId: String, sectionIndex: Int): Unit
    suspend fun saveSectionTemplate(template: ScheduleSectionTemplateEntity): Unit
    suspend fun saveImportDraft(draft: ScheduleImportDraftEntity): Unit
    suspend fun saveReminderRule(rule: ScheduleReminderRuleEntity): Unit
    suspend fun saveReminderRecord(record: ScheduleReminderRecordEntity): Unit
    suspend fun saveCareCandidate(candidate: ScheduleCareCandidateEntity): Unit
    suspend fun saveWidgetState(state: ScheduleWidgetStateEntity): Unit

    suspend fun importScheduleData(
        courses: List<ScheduleCourseEntity>,
        courseRules: List<ScheduleCourseRuleEntity>,
        adjustments: List<ScheduleAdjustmentEntity>,
        assignments: List<ScheduleAssignmentEntity>,
        exams: List<ScheduleExamEntity>,
        campusEvents: List<CampusEventEntity>,
        aiPolicy: ScheduleAiPolicyEntity?,
        shouldClearExisting: Boolean,
        semesters: List<ScheduleSemesterEntity> = emptyList(),
        sectionTemplates: List<ScheduleSectionTemplateEntity> = emptyList(),
        importDrafts: List<ScheduleImportDraftEntity> = emptyList(),
        reminderRules: List<ScheduleReminderRuleEntity> = emptyList(),
        reminderRecords: List<ScheduleReminderRecordEntity> = emptyList(),
        careCandidates: List<ScheduleCareCandidateEntity> = emptyList(),
        widgetState: ScheduleWidgetStateEntity? = null
    ): Unit
}
