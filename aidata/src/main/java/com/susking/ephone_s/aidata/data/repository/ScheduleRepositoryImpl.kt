package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.ScheduleDao
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
import com.susking.ephone_s.aidata.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 课程表与校园动态仓库实现。
 * 这里不保存 UI 状态，只负责 Room 数据读写与默认策略补齐。
 */
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ScheduleRepository {

    override fun observeDashboard(): Flow<ScheduleDashboard> {
        return combine(
            combine(observeCourses(), observeCourseRules(), scheduleDao.observeAdjustments()) { courses: List<ScheduleCourseEntity>, rules: List<ScheduleCourseRuleEntity>, adjustments: List<ScheduleAdjustmentEntity> ->
                ScheduleCourseBundle(courses, rules, adjustments)
            },
            combine(observeAssignments(), observeExams(), observeCampusEvents(), observeAiPolicy()) { assignments: List<ScheduleAssignmentEntity>, exams: List<ScheduleExamEntity>, events: List<CampusEventEntity>, policy: ScheduleAiPolicyEntity ->
                ScheduleTaskBundle(assignments, exams, events, policy)
            },
            combine(observeSemesters(), observeSectionTemplates(), observeImportDrafts()) { semesters: List<ScheduleSemesterEntity>, templates: List<ScheduleSectionTemplateEntity>, drafts: List<ScheduleImportDraftEntity> ->
                ScheduleAcademicBundle(semesters, templates, drafts)
            },
            combine(observeReminderRules(), observeReminderRecords(), observeCareCandidates(), observeWidgetState()) { rules: List<ScheduleReminderRuleEntity>, records: List<ScheduleReminderRecordEntity>, candidates: List<ScheduleCareCandidateEntity>, state: ScheduleWidgetStateEntity ->
                ScheduleReminderBundle(rules, records, candidates, state)
            }
        ) { courseBundle: ScheduleCourseBundle, taskBundle: ScheduleTaskBundle, academicBundle: ScheduleAcademicBundle, reminderBundle: ScheduleReminderBundle ->
            ScheduleDashboard(
                courses = courseBundle.courses,
                courseRules = courseBundle.courseRules,
                adjustments = courseBundle.adjustments,
                assignments = taskBundle.assignments,
                exams = taskBundle.exams,
                campusEvents = taskBundle.campusEvents,
                aiPolicy = taskBundle.aiPolicy,
                semesters = academicBundle.semesters,
                sectionTemplates = academicBundle.sectionTemplates,
                importDrafts = academicBundle.importDrafts,
                reminderRules = reminderBundle.reminderRules,
                reminderRecords = reminderBundle.reminderRecords,
                careCandidates = reminderBundle.careCandidates,
                widgetState = reminderBundle.widgetState
            )
        }
    }

    override fun observeCourses(): Flow<List<ScheduleCourseEntity>> = scheduleDao.observeAllCourses()

    override fun observeCourseRules(): Flow<List<ScheduleCourseRuleEntity>> = scheduleDao.observeEnabledCourseRules()

    override fun observeAssignments(): Flow<List<ScheduleAssignmentEntity>> = scheduleDao.observeAssignments()

    override fun observeExams(): Flow<List<ScheduleExamEntity>> = scheduleDao.observeExams()

    override fun observeCampusEvents(): Flow<List<CampusEventEntity>> = scheduleDao.observeCampusEvents()

    override fun observeAiPolicy(): Flow<ScheduleAiPolicyEntity> {
        return scheduleDao.observeAiPolicy().map { policy: ScheduleAiPolicyEntity? -> policy ?: ScheduleAiPolicyEntity() }
    }

    override fun observeSemesters(): Flow<List<ScheduleSemesterEntity>> = scheduleDao.observeSemesters()

    override fun observeSectionTemplates(): Flow<List<ScheduleSectionTemplateEntity>> = scheduleDao.observeSectionTemplates()

    override fun observeImportDrafts(): Flow<List<ScheduleImportDraftEntity>> = scheduleDao.observeImportDrafts()

    override fun observeReminderRules(): Flow<List<ScheduleReminderRuleEntity>> = scheduleDao.observeReminderRules()

    override fun observeReminderRecords(): Flow<List<ScheduleReminderRecordEntity>> = scheduleDao.observeReminderRecords()

    override fun observeCareCandidates(): Flow<List<ScheduleCareCandidateEntity>> = scheduleDao.observeCareCandidates()

    override fun observeWidgetState(): Flow<ScheduleWidgetStateEntity> {
        return scheduleDao.observeWidgetState().map { state: ScheduleWidgetStateEntity? -> state ?: ScheduleWidgetStateEntity() }
    }

    override suspend fun getAllCourses(): List<ScheduleCourseEntity> = scheduleDao.getAllCourses()

    override suspend fun getAllCourseRules(): List<ScheduleCourseRuleEntity> = scheduleDao.getAllCourseRules()

    override suspend fun getAllAdjustments(): List<ScheduleAdjustmentEntity> = scheduleDao.getAllAdjustments()

    override suspend fun getAllAssignments(): List<ScheduleAssignmentEntity> = scheduleDao.getAllAssignments()

    override suspend fun getAllExams(): List<ScheduleExamEntity> = scheduleDao.getAllExams()

    override suspend fun getAllCampusEvents(): List<CampusEventEntity> = scheduleDao.getAllCampusEvents()

    override suspend fun getAiPolicy(): ScheduleAiPolicyEntity = scheduleDao.getAiPolicy() ?: ScheduleAiPolicyEntity()

    override suspend fun getAllSemesters(): List<ScheduleSemesterEntity> = scheduleDao.getAllSemesters()

    override suspend fun getAllSectionTemplates(): List<ScheduleSectionTemplateEntity> = scheduleDao.getAllSectionTemplates()

    override suspend fun getAllImportDrafts(): List<ScheduleImportDraftEntity> = scheduleDao.getAllImportDrafts()

    override suspend fun getAllReminderRules(): List<ScheduleReminderRuleEntity> = scheduleDao.getAllReminderRules()

    override suspend fun getAllReminderRecords(): List<ScheduleReminderRecordEntity> = scheduleDao.getAllReminderRecords()

    override suspend fun getAllCareCandidates(): List<ScheduleCareCandidateEntity> = scheduleDao.getAllCareCandidates()

    override suspend fun getWidgetState(): ScheduleWidgetStateEntity = scheduleDao.getWidgetState() ?: ScheduleWidgetStateEntity()

    override suspend fun saveCourse(course: ScheduleCourseEntity): Unit = scheduleDao.upsertCourse(course)

    override suspend fun saveCourseRule(rule: ScheduleCourseRuleEntity): Unit = scheduleDao.upsertCourseRule(rule)

    override suspend fun deleteCourseWithRules(courseId: String): Unit {
        deleteCourseRelations(courseId)
        scheduleDao.deleteCourse(courseId)
    }

    override suspend fun deleteCourseOnlyWithRules(courseId: String): Unit {
        scheduleDao.deleteCourseRulesByCourseId(courseId)
        scheduleDao.deleteCourse(courseId)
    }

    override suspend fun deleteSectionsAtOrAfter(sectionIndex: Int): Unit {
        val affectedCourseIds: List<String> = scheduleDao.getCourseIdsAtOrAfterSection(sectionIndex)
        affectedCourseIds.forEach { courseId: String ->
            deleteCourseRelations(courseId)
            scheduleDao.deleteCourse(courseId)
        }
    }

    override suspend fun deleteSemesterWithSchedule(semester: ScheduleSemesterEntity): Unit {
        val affectedCourses: List<ScheduleCourseEntity> = scheduleDao.getCoursesBySemesterName(semester.semesterName)
        affectedCourses.forEach { course: ScheduleCourseEntity ->
            deleteCourseRelations(course.courseId)
            scheduleDao.deleteCourse(course.courseId)
        }
        scheduleDao.deleteSectionTemplatesBySemesterId(semester.semesterId)
        scheduleDao.deleteSemester(semester.semesterId)
    }

    override suspend fun deleteSectionTemplatesAtOrAfter(semesterId: String, sectionIndex: Int): Unit {
        scheduleDao.deleteSectionTemplatesAtOrAfter(semesterId, sectionIndex)
    }

    private suspend fun deleteCourseRelations(courseId: String): Unit {
        val assignmentIds: List<String> = scheduleDao.getAssignmentsByCourseId(courseId)
            .map { assignment: ScheduleAssignmentEntity -> assignment.assignmentId }
        val examIds: List<String> = scheduleDao.getExamsByCourseId(courseId)
            .map { exam: ScheduleExamEntity -> exam.examId }
        deleteReminderRelations(TARGET_TYPE_COURSE, listOf(courseId))
        deleteReminderRelations(TARGET_TYPE_ASSIGNMENT, assignmentIds)
        deleteReminderRelations(TARGET_TYPE_EXAM, examIds)
        scheduleDao.deleteAdjustmentsByCourseId(courseId)
        scheduleDao.deleteAssignmentsByCourseId(courseId)
        scheduleDao.deleteExamsByCourseId(courseId)
        scheduleDao.deleteCourseRulesByCourseId(courseId)
    }

    private suspend fun deleteReminderRelations(targetType: String, targetIds: List<String>): Unit {
        if (targetIds.isEmpty()) return
        val reminderRuleIds: List<String> = scheduleDao.getReminderRuleIdsByTargetIds(targetType, targetIds)
        if (reminderRuleIds.isNotEmpty()) {
            scheduleDao.deleteReminderRecordsByRuleIds(reminderRuleIds)
        }
        scheduleDao.deleteReminderRecordsByTargetIds(targetType, targetIds)
        scheduleDao.deleteReminderRulesByTargetIds(targetType, targetIds)
        scheduleDao.deleteCareCandidatesByTargetIds(targetType, targetIds)
    }

    override suspend fun saveAdjustment(adjustment: ScheduleAdjustmentEntity): Unit = scheduleDao.upsertAdjustment(adjustment)

    override suspend fun saveAssignment(assignment: ScheduleAssignmentEntity): Unit = scheduleDao.upsertAssignment(assignment)

    override suspend fun saveExam(exam: ScheduleExamEntity): Unit = scheduleDao.upsertExam(exam)

    override suspend fun saveCampusEvent(event: CampusEventEntity): Unit = scheduleDao.upsertCampusEvent(event)

    override suspend fun saveAiPolicy(policy: ScheduleAiPolicyEntity): Unit = scheduleDao.upsertAiPolicy(policy)

    override suspend fun saveSemester(semester: ScheduleSemesterEntity): Unit = scheduleDao.upsertSemester(semester)

    override suspend fun saveSectionTemplate(template: ScheduleSectionTemplateEntity): Unit = scheduleDao.upsertSectionTemplate(template)

    override suspend fun saveImportDraft(draft: ScheduleImportDraftEntity): Unit = scheduleDao.upsertImportDraft(draft)

    override suspend fun saveReminderRule(rule: ScheduleReminderRuleEntity): Unit = scheduleDao.upsertReminderRule(rule)

    override suspend fun saveReminderRecord(record: ScheduleReminderRecordEntity): Unit = scheduleDao.upsertReminderRecord(record)

    override suspend fun saveCareCandidate(candidate: ScheduleCareCandidateEntity): Unit = scheduleDao.upsertCareCandidate(candidate)

    override suspend fun saveWidgetState(state: ScheduleWidgetStateEntity): Unit = scheduleDao.upsertWidgetState(state)

    override suspend fun importScheduleData(
        courses: List<ScheduleCourseEntity>,
        courseRules: List<ScheduleCourseRuleEntity>,
        adjustments: List<ScheduleAdjustmentEntity>,
        assignments: List<ScheduleAssignmentEntity>,
        exams: List<ScheduleExamEntity>,
        campusEvents: List<CampusEventEntity>,
        aiPolicy: ScheduleAiPolicyEntity?,
        shouldClearExisting: Boolean,
        semesters: List<ScheduleSemesterEntity>,
        sectionTemplates: List<ScheduleSectionTemplateEntity>,
        importDrafts: List<ScheduleImportDraftEntity>,
        reminderRules: List<ScheduleReminderRuleEntity>,
        reminderRecords: List<ScheduleReminderRecordEntity>,
        careCandidates: List<ScheduleCareCandidateEntity>,
        widgetState: ScheduleWidgetStateEntity?
    ): Unit {
        if (shouldClearExisting) {
            clearScheduleTables()
        }
        scheduleDao.upsertCourses(courses)
        scheduleDao.upsertCourseRules(courseRules)
        scheduleDao.upsertAdjustments(adjustments)
        scheduleDao.upsertAssignments(assignments)
        scheduleDao.upsertExams(exams)
        scheduleDao.upsertCampusEvents(campusEvents)
        scheduleDao.upsertSemesters(semesters)
        scheduleDao.upsertSectionTemplates(sectionTemplates)
        scheduleDao.upsertImportDrafts(importDrafts)
        scheduleDao.upsertReminderRules(reminderRules)
        scheduleDao.upsertReminderRecords(reminderRecords)
        scheduleDao.upsertCareCandidates(careCandidates)
        aiPolicy?.let { policy: ScheduleAiPolicyEntity -> scheduleDao.upsertAiPolicy(policy) }
        widgetState?.let { state: ScheduleWidgetStateEntity -> scheduleDao.upsertWidgetState(state) }
    }

    private suspend fun clearScheduleTables(): Unit {
        scheduleDao.clearCourseRules()
        scheduleDao.clearAdjustments()
        scheduleDao.clearAssignments()
        scheduleDao.clearExams()
        scheduleDao.clearCampusEvents()
        scheduleDao.clearAiPolicies()
        scheduleDao.clearSectionTemplates()
        scheduleDao.clearImportDrafts()
        scheduleDao.clearReminderRecords()
        scheduleDao.clearReminderRules()
        scheduleDao.clearCareCandidates()
        scheduleDao.clearWidgetStates()
        scheduleDao.clearSemesters()
        scheduleDao.clearCourses()
    }

    private companion object {
        private const val TARGET_TYPE_COURSE: String = "COURSE"
        private const val TARGET_TYPE_ASSIGNMENT: String = "ASSIGNMENT"
        private const val TARGET_TYPE_EXAM: String = "EXAM"
    }

    private data class ScheduleCourseBundle(
        val courses: List<ScheduleCourseEntity>,
        val courseRules: List<ScheduleCourseRuleEntity>,
        val adjustments: List<ScheduleAdjustmentEntity>
    )

    private data class ScheduleTaskBundle(
        val assignments: List<ScheduleAssignmentEntity>,
        val exams: List<ScheduleExamEntity>,
        val campusEvents: List<CampusEventEntity>,
        val aiPolicy: ScheduleAiPolicyEntity
    )

    private data class ScheduleAcademicBundle(
        val semesters: List<ScheduleSemesterEntity>,
        val sectionTemplates: List<ScheduleSectionTemplateEntity>,
        val importDrafts: List<ScheduleImportDraftEntity>
    )

    private data class ScheduleReminderBundle(
        val reminderRules: List<ScheduleReminderRuleEntity>,
        val reminderRecords: List<ScheduleReminderRecordEntity>,
        val careCandidates: List<ScheduleCareCandidateEntity>,
        val widgetState: ScheduleWidgetStateEntity
    )
}
