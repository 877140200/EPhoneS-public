package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
import kotlinx.coroutines.flow.Flow

/**
 * 课程表与校园动态 DAO。
 * 所有查询保持简单，复杂的时间线聚合放在 Repository 或 UseCase 中完成。
 */
@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourse(course: ScheduleCourseEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourses(courses: List<ScheduleCourseEntity>): Unit

    @Update
    suspend fun updateCourse(course: ScheduleCourseEntity): Unit

    @Query("SELECT * FROM schedule_courses WHERE isEnabled = 1 ORDER BY courseName ASC")
    fun observeEnabledCourses(): Flow<List<ScheduleCourseEntity>>

    @Query("SELECT * FROM schedule_courses ORDER BY updatedAt DESC")
    fun observeAllCourses(): Flow<List<ScheduleCourseEntity>>

    @Query("SELECT * FROM schedule_courses ORDER BY updatedAt DESC")
    suspend fun getAllCourses(): List<ScheduleCourseEntity>

    @Query("SELECT * FROM schedule_courses WHERE courseId = :courseId LIMIT 1")
    suspend fun getCourseById(courseId: String): ScheduleCourseEntity?

    @Query("DELETE FROM schedule_courses WHERE courseId = :courseId")
    suspend fun deleteCourse(courseId: String): Unit

    @Query("SELECT * FROM schedule_courses WHERE semesterName = :semesterName ORDER BY updatedAt DESC")
    suspend fun getCoursesBySemesterName(semesterName: String): List<ScheduleCourseEntity>

    @Query("DELETE FROM schedule_courses")
    suspend fun clearCourses(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourseRule(rule: ScheduleCourseRuleEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourseRules(rules: List<ScheduleCourseRuleEntity>): Unit

    @Query("SELECT * FROM schedule_course_rules WHERE isEnabled = 1 ORDER BY dayOfWeek ASC, startSection ASC")
    fun observeEnabledCourseRules(): Flow<List<ScheduleCourseRuleEntity>>

    @Query("SELECT * FROM schedule_course_rules ORDER BY updatedAt DESC")
    suspend fun getAllCourseRules(): List<ScheduleCourseRuleEntity>

    @Query("SELECT * FROM schedule_course_rules WHERE dayOfWeek = :dayOfWeek AND isEnabled = 1 ORDER BY startSection ASC")
    suspend fun getEnabledRulesForDay(dayOfWeek: Int): List<ScheduleCourseRuleEntity>

    @Query("DELETE FROM schedule_course_rules WHERE ruleId = :ruleId")
    suspend fun deleteCourseRule(ruleId: String): Unit

    @Query("SELECT DISTINCT courseId FROM schedule_course_rules WHERE startSection >= :sectionIndex OR endSection >= :sectionIndex")
    suspend fun getCourseIdsAtOrAfterSection(sectionIndex: Int): List<String>

    @Query("DELETE FROM schedule_course_rules WHERE courseId = :courseId")
    suspend fun deleteCourseRulesByCourseId(courseId: String): Unit

    @Query("DELETE FROM schedule_course_rules")
    suspend fun clearCourseRules(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdjustment(adjustment: ScheduleAdjustmentEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdjustments(adjustments: List<ScheduleAdjustmentEntity>): Unit

    @Query("SELECT * FROM schedule_adjustments ORDER BY updatedAt DESC")
    fun observeAdjustments(): Flow<List<ScheduleAdjustmentEntity>>

    @Query("SELECT * FROM schedule_adjustments ORDER BY updatedAt DESC")
    suspend fun getAllAdjustments(): List<ScheduleAdjustmentEntity>

    @Query("SELECT * FROM schedule_adjustments WHERE (originalDate BETWEEN :startAt AND :endAt) OR (adjustedDate BETWEEN :startAt AND :endAt) ORDER BY originalDate ASC")
    suspend fun getAdjustmentsBetween(startAt: Long, endAt: Long): List<ScheduleAdjustmentEntity>

    @Query("DELETE FROM schedule_adjustments WHERE adjustmentId = :adjustmentId")
    suspend fun deleteAdjustment(adjustmentId: String): Unit

    @Query("DELETE FROM schedule_adjustments WHERE courseId = :courseId")
    suspend fun deleteAdjustmentsByCourseId(courseId: String): Unit

    @Query("DELETE FROM schedule_adjustments")
    suspend fun clearAdjustments(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(assignment: ScheduleAssignmentEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignments(assignments: List<ScheduleAssignmentEntity>): Unit

    @Query("SELECT * FROM schedule_assignments ORDER BY dueAt ASC, priority DESC")
    fun observeAssignments(): Flow<List<ScheduleAssignmentEntity>>

    @Query("SELECT * FROM schedule_assignments ORDER BY dueAt ASC, priority DESC")
    suspend fun getAllAssignments(): List<ScheduleAssignmentEntity>

    @Query("SELECT * FROM schedule_assignments WHERE courseId = :courseId ORDER BY dueAt ASC, priority DESC")
    suspend fun getAssignmentsByCourseId(courseId: String): List<ScheduleAssignmentEntity>

    @Query("SELECT * FROM schedule_assignments WHERE status != 'DONE' ORDER BY dueAt ASC, priority DESC LIMIT :limit")
    suspend fun getPendingAssignments(limit: Int): List<ScheduleAssignmentEntity>

    @Query("DELETE FROM schedule_assignments WHERE assignmentId = :assignmentId")
    suspend fun deleteAssignment(assignmentId: String): Unit

    @Query("DELETE FROM schedule_assignments WHERE courseId = :courseId")
    suspend fun deleteAssignmentsByCourseId(courseId: String): Unit

    @Query("DELETE FROM schedule_assignments")
    suspend fun clearAssignments(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExam(exam: ScheduleExamEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExams(exams: List<ScheduleExamEntity>): Unit

    @Query("SELECT * FROM schedule_exams ORDER BY examAt ASC")
    fun observeExams(): Flow<List<ScheduleExamEntity>>

    @Query("SELECT * FROM schedule_exams ORDER BY examAt ASC")
    suspend fun getAllExams(): List<ScheduleExamEntity>

    @Query("SELECT * FROM schedule_exams WHERE courseId = :courseId ORDER BY examAt ASC")
    suspend fun getExamsByCourseId(courseId: String): List<ScheduleExamEntity>

    @Query("SELECT * FROM schedule_exams WHERE examAt >= :fromAt ORDER BY examAt ASC LIMIT :limit")
    suspend fun getUpcomingExams(fromAt: Long, limit: Int): List<ScheduleExamEntity>

    @Query("DELETE FROM schedule_exams WHERE examId = :examId")
    suspend fun deleteExam(examId: String): Unit

    @Query("DELETE FROM schedule_exams WHERE courseId = :courseId")
    suspend fun deleteExamsByCourseId(courseId: String): Unit

    @Query("DELETE FROM schedule_exams")
    suspend fun clearExams(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCampusEvent(event: CampusEventEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCampusEvents(events: List<CampusEventEntity>): Unit

    @Query("SELECT * FROM campus_events ORDER BY startAt ASC, importance DESC")
    fun observeCampusEvents(): Flow<List<CampusEventEntity>>

    @Query("SELECT * FROM campus_events ORDER BY startAt ASC, importance DESC")
    suspend fun getAllCampusEvents(): List<CampusEventEntity>

    @Query("SELECT * FROM campus_events WHERE isCompleted = 0 AND ((startAt BETWEEN :startAt AND :endAt) OR (endAt BETWEEN :startAt AND :endAt)) ORDER BY startAt ASC, importance DESC")
    suspend fun getActiveCampusEventsBetween(startAt: Long, endAt: Long): List<CampusEventEntity>

    @Query("DELETE FROM campus_events WHERE eventId = :eventId")
    suspend fun deleteCampusEvent(eventId: String): Unit

    @Query("DELETE FROM campus_events")
    suspend fun clearCampusEvents(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiPolicy(policy: ScheduleAiPolicyEntity): Unit

    @Query("SELECT * FROM schedule_ai_policies WHERE policyId = 'default' LIMIT 1")
    fun observeAiPolicy(): Flow<ScheduleAiPolicyEntity?>

    @Query("SELECT * FROM schedule_ai_policies WHERE policyId = 'default' LIMIT 1")
    suspend fun getAiPolicy(): ScheduleAiPolicyEntity?

    @Query("DELETE FROM schedule_ai_policies")
    suspend fun clearAiPolicies(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSemester(semester: ScheduleSemesterEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSemesters(semesters: List<ScheduleSemesterEntity>): Unit

    @Query("SELECT * FROM schedule_semesters ORDER BY isActive DESC, startDateMillis DESC")
    fun observeSemesters(): Flow<List<ScheduleSemesterEntity>>

    @Query("SELECT * FROM schedule_semesters ORDER BY isActive DESC, startDateMillis DESC")
    suspend fun getAllSemesters(): List<ScheduleSemesterEntity>

    @Query("SELECT * FROM schedule_semesters WHERE isActive = 1 ORDER BY startDateMillis DESC LIMIT 1")
    suspend fun getActiveSemester(): ScheduleSemesterEntity?

    @Query("DELETE FROM schedule_semesters WHERE semesterId = :semesterId")
    suspend fun deleteSemester(semesterId: String): Unit

    @Query("DELETE FROM schedule_semesters")
    suspend fun clearSemesters(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSectionTemplate(template: ScheduleSectionTemplateEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSectionTemplates(templates: List<ScheduleSectionTemplateEntity>): Unit

    @Query("SELECT * FROM schedule_section_templates ORDER BY sectionIndex ASC")
    fun observeSectionTemplates(): Flow<List<ScheduleSectionTemplateEntity>>

    @Query("SELECT * FROM schedule_section_templates ORDER BY sectionIndex ASC")
    suspend fun getAllSectionTemplates(): List<ScheduleSectionTemplateEntity>

    @Query("DELETE FROM schedule_section_templates WHERE semesterId = :semesterId")
    suspend fun deleteSectionTemplatesBySemesterId(semesterId: String): Unit

    @Query("DELETE FROM schedule_section_templates WHERE semesterId = :semesterId AND sectionIndex >= :sectionIndex")
    suspend fun deleteSectionTemplatesAtOrAfter(semesterId: String, sectionIndex: Int): Unit

    @Query("DELETE FROM schedule_section_templates")
    suspend fun clearSectionTemplates(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportDraft(draft: ScheduleImportDraftEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportDrafts(drafts: List<ScheduleImportDraftEntity>): Unit

    @Query("SELECT * FROM schedule_import_drafts ORDER BY updatedAt DESC")
    fun observeImportDrafts(): Flow<List<ScheduleImportDraftEntity>>

    @Query("SELECT * FROM schedule_import_drafts ORDER BY updatedAt DESC")
    suspend fun getAllImportDrafts(): List<ScheduleImportDraftEntity>

    @Query("DELETE FROM schedule_import_drafts")
    suspend fun clearImportDrafts(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderRule(rule: ScheduleReminderRuleEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderRules(rules: List<ScheduleReminderRuleEntity>): Unit

    @Query("SELECT * FROM schedule_reminder_rules ORDER BY updatedAt DESC")
    fun observeReminderRules(): Flow<List<ScheduleReminderRuleEntity>>

    @Query("SELECT * FROM schedule_reminder_rules ORDER BY updatedAt DESC")
    suspend fun getAllReminderRules(): List<ScheduleReminderRuleEntity>

    @Query("SELECT reminderRuleId FROM schedule_reminder_rules WHERE targetType = :targetType AND targetId IN (:targetIds)")
    suspend fun getReminderRuleIdsByTargetIds(targetType: String, targetIds: List<String>): List<String>

    @Query("DELETE FROM schedule_reminder_rules WHERE targetType = :targetType AND targetId IN (:targetIds)")
    suspend fun deleteReminderRulesByTargetIds(targetType: String, targetIds: List<String>): Unit

    @Query("DELETE FROM schedule_reminder_rules")
    suspend fun clearReminderRules(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderRecord(record: ScheduleReminderRecordEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderRecords(records: List<ScheduleReminderRecordEntity>): Unit

    @Query("SELECT * FROM schedule_reminder_records ORDER BY triggerAt DESC")
    fun observeReminderRecords(): Flow<List<ScheduleReminderRecordEntity>>

    @Query("SELECT * FROM schedule_reminder_records ORDER BY triggerAt DESC")
    suspend fun getAllReminderRecords(): List<ScheduleReminderRecordEntity>

    @Query("DELETE FROM schedule_reminder_records WHERE reminderRuleId IN (:reminderRuleIds)")
    suspend fun deleteReminderRecordsByRuleIds(reminderRuleIds: List<String>): Unit

    @Query("DELETE FROM schedule_reminder_records WHERE targetType = :targetType AND targetId IN (:targetIds)")
    suspend fun deleteReminderRecordsByTargetIds(targetType: String, targetIds: List<String>): Unit

    @Query("DELETE FROM schedule_reminder_records")
    suspend fun clearReminderRecords(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCareCandidate(candidate: ScheduleCareCandidateEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCareCandidates(candidates: List<ScheduleCareCandidateEntity>): Unit

    @Query("SELECT * FROM schedule_care_candidates ORDER BY priority DESC, nextEligibleAt ASC")
    fun observeCareCandidates(): Flow<List<ScheduleCareCandidateEntity>>

    @Query("SELECT * FROM schedule_care_candidates ORDER BY priority DESC, nextEligibleAt ASC")
    suspend fun getAllCareCandidates(): List<ScheduleCareCandidateEntity>

    @Query("DELETE FROM schedule_care_candidates WHERE targetType = :targetType AND targetId IN (:targetIds)")
    suspend fun deleteCareCandidatesByTargetIds(targetType: String, targetIds: List<String>): Unit

    @Query("DELETE FROM schedule_care_candidates")
    suspend fun clearCareCandidates(): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWidgetState(state: ScheduleWidgetStateEntity): Unit

    @Query("SELECT * FROM schedule_widget_states WHERE widgetId = 'default' LIMIT 1")
    fun observeWidgetState(): Flow<ScheduleWidgetStateEntity?>

    @Query("SELECT * FROM schedule_widget_states WHERE widgetId = 'default' LIMIT 1")
    suspend fun getWidgetState(): ScheduleWidgetStateEntity?

    @Query("DELETE FROM schedule_widget_states")
    suspend fun clearWidgetStates(): Unit
}
