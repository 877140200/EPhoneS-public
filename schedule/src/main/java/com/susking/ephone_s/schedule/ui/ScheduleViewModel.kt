package com.susking.ephone_s.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.entity.CampusEventEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAiPolicyEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAssignmentEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCareCandidateEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseRuleEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleExamEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleImportDraftEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSectionTemplateEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSemesterEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleWidgetStateEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleWeekMode
import com.susking.ephone_s.aidata.data.local.entity.hasWeekConflictWith
import com.susking.ephone_s.aidata.domain.model.schedule.ScheduleDashboard
import com.susking.ephone_s.aidata.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 课程表首页 ViewModel。
 * 这里提供保留阶段的基础闭环：快速新增、学期模板、导入草稿、提醒候选和桌面摘要。
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    val uiState: StateFlow<ScheduleUiState> = scheduleRepository.observeDashboard()
        .map { dashboard: ScheduleDashboard -> dashboard.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ScheduleUiState()
        )

    fun addQuickCourse(): Unit {
        saveCourseFromEditor(
            courseName = "新课程 ${formatShortId(createId("course_name"))}",
            teacherName = "待填写老师",
            classroom = "待填写教室",
            dayOfWeekText = Calendar.getInstance().get(Calendar.DAY_OF_WEEK).toScheduleDayOfWeek().toString(),
            startSectionText = DEFAULT_START_SECTION.toString(),
            endSectionText = DEFAULT_END_SECTION.toString()
        )
    }

    fun addQuickAssignment(): Unit {
        saveAssignmentFromEditor(
            title = "新作业 ${formatShortId(createId("task"))}",
            content = "待填写作业内容",
            priorityText = DEFAULT_PRIORITY.toString()
        )
    }

    fun addQuickExam(): Unit {
        saveExamFromEditor(
            examName = "新考试 ${formatShortId(createId("exam_name"))}",
            classroom = "待填写考场",
            scopeText = "待填写范围",
            importanceText = DEFAULT_PRIORITY.toString()
        )
    }

    fun saveCourseFromEditor(
        courseName: String,
        teacherName: String,
        classroom: String,
        dayOfWeekText: String,
        startSectionText: String,
        endSectionText: String,
        courseId: String? = null,
        ruleId: String? = null,
        startWeekText: String = "",
        endWeekText: String = "",
        onResult: (Boolean) -> Unit = {}
    ): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            val activeSemester: ScheduleSemesterEntity = findActiveSemester(scheduleRepository.getAllSemesters())
                ?: createDefaultSemester(nowMillis)
            val targetCourseId: String = courseId ?: createId("course")
            val existingCourse: ScheduleCourseEntity? = courseId?.let { id: String -> scheduleRepository.getAllCourses().firstOrNull { course: ScheduleCourseEntity -> course.courseId == id } }
            val existingRule: ScheduleCourseRuleEntity? = ruleId?.let { id: String -> scheduleRepository.getAllCourseRules().firstOrNull { rule: ScheduleCourseRuleEntity -> rule.ruleId == id } }
                ?: courseId?.let { id: String -> scheduleRepository.getAllCourseRules().firstOrNull { rule: ScheduleCourseRuleEntity -> rule.courseId == id } }
            val maxSection: Int = getCurrentMaxSection()
            val startSection: Int = parseBoundedInt(startSectionText, existingRule?.startSection ?: DEFAULT_START_SECTION, MIN_SECTION, maxSection)
            val endSection: Int = parseBoundedInt(endSectionText, existingRule?.endSection ?: startSection, startSection, maxSection)
            val startWeek: Int = parseBoundedInt(startWeekText, existingRule?.startWeek ?: MIN_TOTAL_WEEKS, MIN_TOTAL_WEEKS, MAX_TOTAL_WEEKS)
            val endWeek: Int = parseBoundedInt(endWeekText, existingRule?.endWeek ?: startWeek, startWeek, MAX_TOTAL_WEEKS)
            val targetDayOfWeek: Int = parseBoundedInt(dayOfWeekText, existingRule?.dayOfWeek ?: DEFAULT_DAY_OF_WEEK, MIN_DAY_OF_WEEK, MAX_DAY_OF_WEEK)
            // 待保存规则的单双周模式：编辑现有课沿用原值,新建课默认每周。
            // 冲突检测需要它,否则单周课与双周课即使节次周次都重叠也永不同周上课,不应判为冲突。
            val targetWeekMode: String = existingRule?.weekMode ?: ScheduleWeekMode.EVERY_WEEK
            val activeCourseIds: Set<String> = scheduleRepository.getAllCourses()
                .filter { course: ScheduleCourseEntity -> course.semesterName == activeSemester.semesterName }
                .map { course: ScheduleCourseEntity -> course.courseId }
                .toSet()
            val hasConflict: Boolean = scheduleRepository.getAllCourseRules().any { rule: ScheduleCourseRuleEntity ->
                rule.isEnabled && rule.courseId != targetCourseId && rule.courseId in activeCourseIds && rule.dayOfWeek == targetDayOfWeek && hasCourseTimeConflict(startSection, endSection, startWeek, endWeek, targetWeekMode, rule)
            }
            if (hasConflict) {
                onResult(false)
                return@launch
            }
            scheduleRepository.saveCourse(
                (existingCourse ?: ScheduleCourseEntity(courseId = targetCourseId, courseName = normalizeText(courseName, "未命名课程"))).copy(
                    courseName = normalizeText(courseName, existingCourse?.courseName ?: "未命名课程"),
                    teacherName = normalizeText(teacherName, existingCourse?.teacherName ?: ""),
                    classroom = normalizeText(classroom, existingCourse?.classroom ?: ""),
                    courseColor = existingCourse?.courseColor ?: DEFAULT_ACCENT_COLOR,
                    semesterName = existingCourse?.semesterName ?: activeSemester.semesterName,
                    updatedAt = nowMillis
                )
            )
            scheduleRepository.saveCourseRule(
                (existingRule ?: ScheduleCourseRuleEntity(
                    ruleId = createId("rule"),
                    courseId = targetCourseId,
                    dayOfWeek = parseBoundedInt(dayOfWeekText, DEFAULT_DAY_OF_WEEK, MIN_DAY_OF_WEEK, MAX_DAY_OF_WEEK),
                    startSection = startSection,
                    endSection = endSection,
                    startWeek = startWeek,
                    endWeek = endWeek
                )).copy(
                    courseId = targetCourseId,
                    dayOfWeek = targetDayOfWeek,
                    startSection = startSection,
                    endSection = endSection,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    updatedAt = nowMillis
                )
            )
            onResult(true)
        }
    }

    fun deleteCourse(courseId: String): Unit {
        viewModelScope.launch {
            scheduleRepository.deleteCourseWithRules(courseId)
        }
    }

    fun saveAssignmentFromEditor(
        title: String,
        content: String,
        priorityText: String,
        assignmentId: String? = null
    ): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            val existingAssignment: ScheduleAssignmentEntity? = assignmentId?.let { id: String -> scheduleRepository.getAllAssignments().firstOrNull { assignment: ScheduleAssignmentEntity -> assignment.assignmentId == id } }
            scheduleRepository.saveAssignment(
                (existingAssignment ?: ScheduleAssignmentEntity(
                    assignmentId = assignmentId ?: createId("assignment"),
                    title = normalizeText(title, "未命名作业"),
                    dueAt = nowMillis + DEFAULT_ASSIGNMENT_OFFSET_MILLIS
                )).copy(
                    title = normalizeText(title, existingAssignment?.title ?: "未命名作业"),
                    content = normalizeText(content, existingAssignment?.content ?: ""),
                    priority = parseBoundedInt(priorityText, existingAssignment?.priority ?: DEFAULT_PRIORITY, MIN_PRIORITY, MAX_PRIORITY),
                    updatedAt = nowMillis
                )
            )
        }
    }

    fun saveExamFromEditor(
        examName: String,
        classroom: String,
        scopeText: String,
        importanceText: String,
        examId: String? = null
    ): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            val existingExam: ScheduleExamEntity? = examId?.let { id: String -> scheduleRepository.getAllExams().firstOrNull { exam: ScheduleExamEntity -> exam.examId == id } }
            scheduleRepository.saveExam(
                (existingExam ?: ScheduleExamEntity(
                    examId = examId ?: createId("exam"),
                    examName = normalizeText(examName, "未命名考试"),
                    examAt = nowMillis + DEFAULT_EXAM_OFFSET_MILLIS
                )).copy(
                    examName = normalizeText(examName, existingExam?.examName ?: "未命名考试"),
                    classroom = normalizeText(classroom, existingExam?.classroom ?: ""),
                    scopeText = normalizeText(scopeText, existingExam?.scopeText ?: ""),
                    importance = parseBoundedInt(importanceText, existingExam?.importance ?: DEFAULT_PRIORITY, MIN_PRIORITY, MAX_PRIORITY),
                    updatedAt = nowMillis
                )
            )
        }
    }

    /**
     * 切换作业完成状态：TODO ↔ DONE。
     * 与 AI 摘要、首页筛选共用同一 DONE_STATUS 口径，保证三处对「已完成」的判断一致。
     */
    fun toggleAssignmentStatus(assignmentId: String): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            val existingAssignment: ScheduleAssignmentEntity = scheduleRepository.getAllAssignments()
                .firstOrNull { assignment: ScheduleAssignmentEntity -> assignment.assignmentId == assignmentId }
                ?: return@launch
            val nextStatus: String = if (existingAssignment.status == DONE_STATUS) TODO_STATUS else DONE_STATUS
            scheduleRepository.saveAssignment(
                existingAssignment.copy(
                    status = nextStatus,
                    updatedAt = nowMillis
                )
            )
        }
    }

    fun switchSemester(semesterId: String): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            scheduleRepository.getAllSemesters().forEach { semester: ScheduleSemesterEntity ->
                scheduleRepository.saveSemester(
                    semester.copy(
                        isActive = semester.semesterId == semesterId,
                        updatedAt = nowMillis
                    )
                )
            }
        }
    }

    fun createSemester(semesterName: String = ""): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            val existingSemesters: List<ScheduleSemesterEntity> = scheduleRepository.getAllSemesters()
            existingSemesters.forEach { semester: ScheduleSemesterEntity ->
                scheduleRepository.saveSemester(semester.copy(isActive = false, updatedAt = nowMillis))
            }
            val semesterId: String = createId("semester")
            val normalizedName: String = buildUniqueSemesterName(normalizeText(semesterName, "新课表 ${existingSemesters.size + 1}"), existingSemesters)
            val newSemester: ScheduleSemesterEntity = ScheduleSemesterEntity(
                semesterId = semesterId,
                semesterName = normalizedName,
                startDateMillis = nowMillis,
                totalWeeks = DEFAULT_TOTAL_WEEKS,
                isActive = true,
                updatedAt = nowMillis
            )
            scheduleRepository.saveSemester(newSemester)
            DEFAULT_SECTION_TIMES.forEachIndexed { index: Int, pair: Pair<String, String> ->
                scheduleRepository.saveSectionTemplate(createSectionTemplate(semesterId, index + SECTION_INDEX_STEP, pair.first, pair.second, nowMillis))
            }
        }
    }

    fun deleteActiveSemester(): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            val semesters: List<ScheduleSemesterEntity> = scheduleRepository.getAllSemesters()
            val activeSemester: ScheduleSemesterEntity = findActiveSemester(semesters) ?: return@launch
            scheduleRepository.deleteSemesterWithSchedule(activeSemester)
            val remainingSemesters: List<ScheduleSemesterEntity> = scheduleRepository.getAllSemesters()
            val nextSemester: ScheduleSemesterEntity? = remainingSemesters.firstOrNull { semester: ScheduleSemesterEntity -> semester.semesterId != activeSemester.semesterId }
                ?: remainingSemesters.firstOrNull()
            if (nextSemester != null) {
                scheduleRepository.saveSemester(nextSemester.copy(isActive = true, updatedAt = nowMillis))
                return@launch
            }
            val fallbackSemester: ScheduleSemesterEntity = createDefaultSemester(nowMillis)
            DEFAULT_SECTION_TIMES.forEachIndexed { index: Int, pair: Pair<String, String> ->
                scheduleRepository.saveSectionTemplate(createSectionTemplate(fallbackSemester.semesterId, index + SECTION_INDEX_STEP, pair.first, pair.second, nowMillis))
            }
        }
    }

    fun createTextImportDraft(rawContent: String): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            val normalizedRawContent: String = rawContent.trim()
            scheduleRepository.saveImportDraft(
                ScheduleImportDraftEntity(
                    draftId = createId("draft"),
                    sourceType = "TEXT",
                    rawContent = normalizedRawContent,
                    previewJson = parseScheduleTextPreview(normalizedRawContent, getCurrentMaxSection()),
                    updatedAt = nowMillis
                )
            )
        }
    }

    fun prepareLatestImportConflicts(onPrepared: (List<ScheduleImportConflictGroup>) -> Unit): Unit {
        viewModelScope.launch {
            val latestDraft: ScheduleImportDraftEntity = scheduleRepository.getAllImportDrafts().firstOrNull() ?: run {
                onPrepared(emptyList())
                return@launch
            }
            val parsedCourses: List<ParsedScheduleCourse> = parseScheduleText(latestDraft.rawContent, getCurrentMaxSection())
            onPrepared(buildImportConflictGroups(parsedCourses))
        }
    }

    fun commitLatestImportDraft(
        resolvedConflictChoices: Map<Int, Int> = emptyMap(),
        onCommitted: () -> Unit = {}
    ): Unit {
        viewModelScope.launch {
            val latestDraft: ScheduleImportDraftEntity = scheduleRepository.getAllImportDrafts().firstOrNull() ?: return@launch
            val nowMillis: Long = System.currentTimeMillis()
            val activeSemester: ScheduleSemesterEntity = findActiveSemester(scheduleRepository.getAllSemesters())
                ?: createDefaultSemester(nowMillis)
            val parsedCourses: List<ParsedScheduleCourse> = resolveImportedCourseConflicts(parseScheduleText(latestDraft.rawContent, getCurrentMaxSection()), resolvedConflictChoices)
            deleteActiveSemesterCourses(activeSemester)
            saveImportedSectionTimes(parsedCourses)
            parsedCourses.forEach { parsedCourse: ParsedScheduleCourse ->
                val courseId: String = createId("course")
                scheduleRepository.saveCourse(
                    ScheduleCourseEntity(
                        courseId = courseId,
                        courseName = parsedCourse.courseName,
                        teacherName = parsedCourse.teacherName,
                        classroom = parsedCourse.classroom,
                        semesterName = activeSemester.semesterName,
                        updatedAt = nowMillis
                    )
                )
                scheduleRepository.saveCourseRule(
                    ScheduleCourseRuleEntity(
                        ruleId = createId("rule"),
                        courseId = courseId,
                        dayOfWeek = parsedCourse.dayOfWeek,
                        startSection = parsedCourse.startSection,
                        endSection = parsedCourse.endSection,
                        startWeek = parsedCourse.startWeek,
                        endWeek = parsedCourse.endWeek,
                        weekMode = parsedCourse.weekMode,
                        classroomOverride = parsedCourse.classroom,
                        updatedAt = nowMillis
                    )
                )
            }
            scheduleRepository.saveImportDraft(
                latestDraft.copy(
                    status = "COMMITTED",
                    updatedAt = System.currentTimeMillis()
                )
            )
            onCommitted()
        }
    }

    private suspend fun deleteActiveSemesterCourses(activeSemester: ScheduleSemesterEntity): Unit {
        scheduleRepository.getAllCourses()
            .filter { course: ScheduleCourseEntity -> course.semesterName == activeSemester.semesterName }
            .forEach { course: ScheduleCourseEntity ->
                // 重新导入学期课表＝重建该学期，需连带清理该课程挂着的作业/考试/调课/提醒，避免孤儿数据
                scheduleRepository.deleteCourseWithRules(course.courseId)
            }
    }

    fun seedDefaultAcademicTemplates(): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            val activeSemester: ScheduleSemesterEntity = findActiveSemester(scheduleRepository.getAllSemesters())
                ?: createDefaultSemester(nowMillis)
            scheduleRepository.deleteSectionTemplatesAtOrAfter(activeSemester.semesterId, DEFAULT_SECTION_TIMES.size + SECTION_INDEX_STEP)
            ensureDefaultAcademicTemplates(shouldOverwriteExistingSections = true)
        }
    }

    fun ensureAcademicTemplatesInitialized(): Unit {
        viewModelScope.launch {
            val hasSemester: Boolean = scheduleRepository.getAllSemesters().isNotEmpty()
            val hasSectionTemplates: Boolean = scheduleRepository.getAllSectionTemplates().isNotEmpty()
            if (hasSemester && hasSectionTemplates) return@launch
            ensureDefaultAcademicTemplates(shouldOverwriteExistingSections = false)
        }
    }

    private suspend fun ensureDefaultAcademicTemplates(shouldOverwriteExistingSections: Boolean): Unit {
        val nowMillis: Long = System.currentTimeMillis()
        val activeSemester: ScheduleSemesterEntity = findActiveSemester(scheduleRepository.getAllSemesters())
            ?: createDefaultSemester(nowMillis)
        val semesterId: String = activeSemester.semesterId
        val existingSectionIndexes: Set<Int> = scheduleRepository.getAllSectionTemplates()
            .filter { template: ScheduleSectionTemplateEntity -> template.semesterId == semesterId }
            .map { template: ScheduleSectionTemplateEntity -> template.sectionIndex }
            .toSet()
        DEFAULT_SECTION_TIMES.forEachIndexed { index: Int, pair: Pair<String, String> ->
            val sectionIndex: Int = index + 1
            if (!shouldOverwriteExistingSections && sectionIndex in existingSectionIndexes) return@forEachIndexed
            scheduleRepository.saveSectionTemplate(
                createSectionTemplate(semesterId, sectionIndex, pair.first, pair.second, nowMillis)
            )
        }
    }

    fun saveAcademicSettings(
        semesterName: String,
        semesterStartDateText: String,
        totalWeeksText: String,
        note: String,
        sectionTimesText: String
    ): Unit {
        viewModelScope.launch {
            val nowMillis: Long = System.currentTimeMillis()
            val existingSemester: ScheduleSemesterEntity? = findActiveSemester(scheduleRepository.getAllSemesters())
            val semesterId: String = existingSemester?.semesterId ?: DEFAULT_SEMESTER_ID
            val totalWeeks: Int = parseBoundedInt(totalWeeksText, existingSemester?.totalWeeks ?: DEFAULT_TOTAL_WEEKS, MIN_TOTAL_WEEKS, MAX_TOTAL_WEEKS)
            val startDateMillis: Long = parseDateMillis(semesterStartDateText, existingSemester?.startDateMillis ?: nowMillis)
            val templates: List<ScheduleSectionTemplateEntity> = parseSectionTimeLines(sectionTimesText, semesterId, nowMillis)
            val firstRemovedSectionIndex: Int = templates.size + SECTION_INDEX_STEP
            val existingSectionCount: Int = scheduleRepository.getAllSectionTemplates()
                .count { template: ScheduleSectionTemplateEntity -> template.semesterId == semesterId }
            scheduleRepository.saveSemester(
                (existingSemester ?: ScheduleSemesterEntity(
                    semesterId = semesterId,
                    semesterName = normalizeText(semesterName, "默认学期"),
                    startDateMillis = nowMillis,
                    totalWeeks = totalWeeks
                )).copy(
                    semesterName = normalizeText(semesterName, existingSemester?.semesterName ?: "默认学期"),
                    startDateMillis = startDateMillis,
                    totalWeeks = totalWeeks,
                    note = note.trim(),
                    updatedAt = nowMillis
                )
            )
            updateCourseSemesterNamesIfNeeded(existingSemester, normalizeText(semesterName, existingSemester?.semesterName ?: "默认学期"), nowMillis)
            if (existingSectionCount >= firstRemovedSectionIndex) {
                scheduleRepository.deleteSectionTemplatesAtOrAfter(semesterId, firstRemovedSectionIndex)
            }
            templates.forEach { template: ScheduleSectionTemplateEntity ->
                scheduleRepository.saveSectionTemplate(template)
            }
        }
    }

    fun deleteSectionsAtOrAfter(sectionIndex: Int): Unit {
        viewModelScope.launch {
            val activeSemester: ScheduleSemesterEntity = findActiveSemester(scheduleRepository.getAllSemesters()) ?: return@launch
            scheduleRepository.deleteSectionTemplatesAtOrAfter(activeSemester.semesterId, sectionIndex)
            scheduleRepository.deleteSectionsAtOrAfter(sectionIndex)
        }
    }

    fun toggleAiVisibility(): Unit {
        viewModelScope.launch {
            val policy: ScheduleAiPolicyEntity = scheduleRepository.getAiPolicy()
            scheduleRepository.saveAiPolicy(
                policy.copy(
                    isAiVisible = !policy.isAiVisible,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleAiProactiveCare(): Unit {
        viewModelScope.launch {
            val policy: ScheduleAiPolicyEntity = scheduleRepository.getAiPolicy()
            scheduleRepository.saveAiPolicy(
                policy.copy(
                    canAiProactivelyCare = !policy.canAiProactivelyCare,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun cycleAiCareIntensity(): Unit {
        viewModelScope.launch {
            val policy: ScheduleAiPolicyEntity = scheduleRepository.getAiPolicy()
            val nextIntensity: String = when (policy.careIntensity) {
                CARE_INTENSITY_LOW -> CARE_INTENSITY_NORMAL
                CARE_INTENSITY_NORMAL -> CARE_INTENSITY_HIGH
                else -> CARE_INTENSITY_LOW
            }
            scheduleRepository.saveAiPolicy(
                policy.copy(
                    careIntensity = nextIntensity,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun ScheduleDashboard.toUiState(): ScheduleUiState {
        val activeSemester: ScheduleSemesterEntity? = findActiveSemester(semesters)
        val activeCourses: List<ScheduleCourseEntity> = activeSemester?.let { semester: ScheduleSemesterEntity ->
            courses.filter { course: ScheduleCourseEntity -> course.semesterName == semester.semesterName }
        } ?: emptyList()
        val activeCourseIds: Set<String> = activeCourses.map { course: ScheduleCourseEntity -> course.courseId }.toSet()
        val activeRules: List<ScheduleCourseRuleEntity> = courseRules.filter { rule: ScheduleCourseRuleEntity -> rule.courseId in activeCourseIds }
        val activeAssignments: List<ScheduleAssignmentEntity> = assignments.filter { assignment: ScheduleAssignmentEntity -> assignment.courseId == null || assignment.courseId in activeCourseIds }
        val activeExams: List<ScheduleExamEntity> = exams.filter { exam: ScheduleExamEntity -> exam.courseId == null || exam.courseId in activeCourseIds }
        val activeSectionTemplates: List<ScheduleSectionTemplateEntity> = activeSemester?.let { semester: ScheduleSemesterEntity ->
            sectionTemplates.filter { template: ScheduleSectionTemplateEntity -> template.semesterId == semester.semesterId }
        } ?: emptyList()
        val courseMap: Map<String, ScheduleCourseEntity> = activeCourses.associateBy { course: ScheduleCourseEntity -> course.courseId }
        return ScheduleUiState(
            isLoading = false,
            todayCourses = activeCourses.take(COURSE_PREVIEW_LIMIT),
            pendingAssignments = activeAssignments.filter { assignment: ScheduleAssignmentEntity -> assignment.status != DONE_STATUS }.take(ASSIGNMENT_PREVIEW_LIMIT),
            upcomingExams = activeExams.take(EXAM_PREVIEW_LIMIT),
            campusEvents = campusEvents.filter { event: CampusEventEntity -> !event.isCompleted }.take(EVENT_PREVIEW_LIMIT),
            allCourses = activeCourses,
            allCourseRules = activeRules,
            allAssignments = activeAssignments,
            allExams = activeExams,
            allCampusEvents = campusEvents,
            aiPolicy = aiPolicy,
            semesters = semesters,
            sectionTemplates = activeSectionTemplates,
            importDrafts = importDrafts,
            importPreviewText = importDrafts.firstOrNull()?.previewJson.orEmpty(),
            weekScheduleLines = buildWeekScheduleLines(activeRules, courseMap),
            timelineLines = buildTimelineLines(activeCourses, activeAssignments, activeExams, campusEvents),
            reminderRuleCount = reminderRules.size,
            careCandidateCount = careCandidates.count { candidate: ScheduleCareCandidateEntity -> candidate.status == "PENDING" },
            widgetState = widgetState
        )
    }

    private fun buildWeekScheduleLines(
        rules: List<ScheduleCourseRuleEntity>,
        courseMap: Map<String, ScheduleCourseEntity>
    ): List<String> {
        return rules.sortedWith(compareBy<ScheduleCourseRuleEntity> { rule: ScheduleCourseRuleEntity -> rule.dayOfWeek }.thenBy { rule: ScheduleCourseRuleEntity -> rule.startSection })
            .take(WEEK_SCHEDULE_LIMIT)
            .mapNotNull { rule: ScheduleCourseRuleEntity ->
                val course: ScheduleCourseEntity = courseMap[rule.courseId] ?: return@mapNotNull null
                "${rule.dayOfWeek.toWeekdayName()} 第${rule.startSection}-${rule.endSection}节 ${course.courseName}${course.classroom.takeIf { value: String -> value.isNotBlank() }?.let { value: String -> "｜$value" }.orEmpty()}"
            }
    }

    private fun buildTimelineLines(
        courses: List<ScheduleCourseEntity>,
        assignments: List<ScheduleAssignmentEntity>,
        exams: List<ScheduleExamEntity>,
        campusEvents: List<CampusEventEntity>
    ): List<String> {
        val courseLines: List<String> = courses.take(TIMELINE_COURSE_LIMIT).map { course: ScheduleCourseEntity -> "课程｜${course.courseName}" }
        val assignmentLines: List<String> = assignments.filter { assignment: ScheduleAssignmentEntity -> assignment.status != DONE_STATUS }.take(TIMELINE_ITEM_LIMIT).map { assignment: ScheduleAssignmentEntity -> "作业｜${assignment.title}" }
        val examLines: List<String> = exams.take(TIMELINE_ITEM_LIMIT).map { exam: ScheduleExamEntity -> "考试｜${exam.examName}" }
        val eventLines: List<String> = campusEvents.filter { event: CampusEventEntity -> !event.isCompleted }.take(TIMELINE_ITEM_LIMIT).map { event: CampusEventEntity -> "校园｜${event.title}" }
        return (courseLines + assignmentLines + examLines + eventLines).take(TIMELINE_TOTAL_LIMIT)
    }

    private suspend fun createDefaultSemester(nowMillis: Long): ScheduleSemesterEntity {
        val defaultSemester: ScheduleSemesterEntity = ScheduleSemesterEntity(
            semesterId = DEFAULT_SEMESTER_ID,
            semesterName = "默认学期",
            startDateMillis = nowMillis,
            totalWeeks = DEFAULT_TOTAL_WEEKS,
            isActive = true,
            updatedAt = nowMillis
        )
        scheduleRepository.saveSemester(defaultSemester)
        return defaultSemester
    }

    private fun findActiveSemester(semesters: List<ScheduleSemesterEntity>): ScheduleSemesterEntity? {
        return semesters.firstOrNull { semester: ScheduleSemesterEntity -> semester.isActive }
            ?: semesters.firstOrNull()
    }

    private suspend fun updateCourseSemesterNamesIfNeeded(
        existingSemester: ScheduleSemesterEntity?,
        nextSemesterName: String,
        nowMillis: Long
    ): Unit {
        val previousSemesterName: String = existingSemester?.semesterName ?: return
        if (previousSemesterName == nextSemesterName) return
        scheduleRepository.getAllCourses()
            .filter { course: ScheduleCourseEntity -> course.semesterName == previousSemesterName }
            .forEach { course: ScheduleCourseEntity ->
                scheduleRepository.saveCourse(course.copy(semesterName = nextSemesterName, updatedAt = nowMillis))
            }
    }

    private fun buildUniqueSemesterName(baseName: String, existingSemesters: List<ScheduleSemesterEntity>): String {
        val existingNames: Set<String> = existingSemesters.map { semester: ScheduleSemesterEntity -> semester.semesterName }.toSet()
        if (baseName !in existingNames) return baseName
        var suffix: Int = UNIQUE_NAME_START_SUFFIX
        var candidateName: String = "$baseName $suffix"
        while (candidateName in existingNames) {
            suffix += UNIQUE_NAME_STEP
            candidateName = "$baseName $suffix"
        }
        return candidateName
    }

    private fun normalizeText(value: String, fallback: String): String {
        return value.trim().ifBlank { fallback }
    }

    private fun parseBoundedInt(value: String, fallback: Int, minValue: Int, maxValue: Int): Int {
        return value.trim().toIntOrNull()?.coerceIn(minValue, maxValue) ?: fallback
    }

    private fun parseDateMillis(value: String, fallback: Long): Long {
        return runCatching {
            val dateFormat: java.text.SimpleDateFormat = java.text.SimpleDateFormat(DATE_PATTERN, java.util.Locale.CHINA)
            dateFormat.parse(value.trim())?.time
        }.getOrNull() ?: fallback
    }

    private fun createId(prefix: String): String {
        return "$prefix-${UUID.randomUUID()}"
    }

    private fun formatShortId(id: String): String {
        return id.takeLast(SHORT_ID_LENGTH)
    }

    private fun Int.toScheduleDayOfWeek(): Int {
        return if (this == Calendar.SUNDAY) WEEK_END_DAY else this - 1
    }

    private fun parseScheduleTextPreview(rawContent: String, maxSection: Int): String {
        val parsedCourses: List<ParsedScheduleCourse> = parseScheduleText(rawContent, maxSection)
        if (parsedCourses.isEmpty()) return "未识别到课程，请检查文本里是否包含星期、节次和课程名。"
        val weekdayPreviewText: String = buildImportPreviewWeekdayText(parsedCourses)
        val sectionTemplateText: String = buildImportPreviewSectionTemplateText(parsedCourses)
        return listOf(
            "识别到 ${parsedCourses.size} 门课程",
            "课程预览\n$weekdayPreviewText",
            sectionTemplateText
        ).filter { text: String -> text.isNotBlank() }.joinToString(separator = "\n\n")
    }

    private fun buildImportPreviewWeekdayText(parsedCourses: List<ParsedScheduleCourse>): String {
        val coursesByDay: Map<Int, List<ParsedScheduleCourse>> = parsedCourses
            .sortedWith(
                compareBy<ParsedScheduleCourse> { parsedCourse: ParsedScheduleCourse -> parsedCourse.dayOfWeek }
                    .thenBy { parsedCourse: ParsedScheduleCourse -> parsedCourse.startSection }
                    .thenBy { parsedCourse: ParsedScheduleCourse -> parsedCourse.endSection }
            )
            .groupBy { parsedCourse: ParsedScheduleCourse -> parsedCourse.dayOfWeek }
        return (MIN_DAY_OF_WEEK..MAX_DAY_OF_WEEK).joinToString(separator = "\n\n") { dayOfWeek: Int ->
            val courseLines: List<String> = coursesByDay[dayOfWeek].orEmpty().mapIndexed { index: Int, parsedCourse: ParsedScheduleCourse ->
                buildImportPreviewCourseLine(index + PREVIEW_INDEX_OFFSET, parsedCourse)
            }
            buildImportPreviewWeekdayBlock(dayOfWeek, courseLines)
        }
    }

    private fun buildImportPreviewWeekdayBlock(dayOfWeek: Int, courseLines: List<String>): String {
        if (courseLines.isEmpty()) return "【${dayOfWeek.toWeekdayName()}】\n   无课程"
        return "【${dayOfWeek.toWeekdayName()}】\n${courseLines.joinToString(separator = "\n\n") }"
    }

    private fun buildImportPreviewCourseLine(index: Int, parsedCourse: ParsedScheduleCourse): String {
        val teacherText: String = parsedCourse.teacherName.ifBlank { "未识别" }
        val classroomText: String = parsedCourse.classroom.ifBlank { "未识别" }
        val sectionTimeText: String = buildImportPreviewSectionTimeText(parsedCourse)
        val weekText: String = buildImportPreviewWeekText(parsedCourse)
        return "$index. ${parsedCourse.courseName}\n   时间：第${parsedCourse.startSection}-${parsedCourse.endSection}节$sectionTimeText\n   周次：$weekText\n   教师：$teacherText\n   教室：$classroomText"
    }

    private fun buildImportPreviewSectionTemplateText(parsedCourses: List<ParsedScheduleCourse>): String {
        val sectionTimes: List<ParsedSectionTime> = parsedCourses
            .flatMap { parsedCourse: ParsedScheduleCourse -> parsedCourse.sectionTimes }
            .distinctBy { sectionTime: ParsedSectionTime -> sectionTime.sectionIndex }
            .sortedBy { sectionTime: ParsedSectionTime -> sectionTime.sectionIndex }
        if (sectionTimes.isEmpty()) return "节次模板\n未识别到节次起止时间"
        val sectionLines: List<String> = sectionTimes.map { sectionTime: ParsedSectionTime ->
            "第${sectionTime.sectionIndex}节：${sectionTime.startTimeText}-${sectionTime.endTimeText}"
        }
        return "节次模板\n${sectionLines.joinToString(separator = "\n") }"
    }

    private fun parseScheduleText(rawContent: String, maxSection: Int): List<ParsedScheduleCourse> {
        return mergeAdjacentSameCourses(parseHtmlScheduleText(rawContent, maxSection))
    }

    private fun mergeAdjacentSameCourses(courses: List<ParsedScheduleCourse>): List<ParsedScheduleCourse> {
        if (courses.isEmpty()) return emptyList()
        return courses
            .sortedWith(
                compareBy<ParsedScheduleCourse> { course: ParsedScheduleCourse -> course.dayOfWeek }
                    .thenBy { course: ParsedScheduleCourse -> course.startSection }
                    .thenBy { course: ParsedScheduleCourse -> course.endSection }
            )
            .fold(emptyList()) { mergedCourses: List<ParsedScheduleCourse>, course: ParsedScheduleCourse ->
                val previousCourse: ParsedScheduleCourse = mergedCourses.lastOrNull() ?: return@fold listOf(course)
                if (!canMergeAdjacentCourses(previousCourse, course)) return@fold mergedCourses + course
                mergedCourses.dropLast(1) + previousCourse.copy(
                    endSection = course.endSection,
                    sectionTimes = mergeSectionTimes(previousCourse.sectionTimes, course.sectionTimes)
                )
            }
    }

    private fun mergeSectionTimes(previousTimes: List<ParsedSectionTime>, nextTimes: List<ParsedSectionTime>): List<ParsedSectionTime> {
        return (previousTimes + nextTimes)
            .distinctBy { sectionTime: ParsedSectionTime -> sectionTime.sectionIndex }
            .sortedBy { sectionTime: ParsedSectionTime -> sectionTime.sectionIndex }
    }

    private fun canMergeAdjacentCourses(previousCourse: ParsedScheduleCourse, nextCourse: ParsedScheduleCourse): Boolean {
        return previousCourse.dayOfWeek == nextCourse.dayOfWeek &&
            previousCourse.endSection + SECTION_INDEX_STEP == nextCourse.startSection &&
            previousCourse.courseName == nextCourse.courseName &&
            previousCourse.teacherName == nextCourse.teacherName &&
            previousCourse.classroom == nextCourse.classroom &&
            previousCourse.startWeek == nextCourse.startWeek &&
            previousCourse.endWeek == nextCourse.endWeek &&
            previousCourse.weekMode == nextCourse.weekMode
    }

    private suspend fun saveImportedSectionTimes(parsedCourses: List<ParsedScheduleCourse>): Unit {
        val nowMillis: Long = System.currentTimeMillis()
        val activeSemester: ScheduleSemesterEntity = findActiveSemester(scheduleRepository.getAllSemesters()) ?: createDefaultSemester(nowMillis)
        parsedCourses.flatMap { parsedCourse: ParsedScheduleCourse -> parsedCourse.sectionTimes }
            .distinctBy { sectionTime: ParsedSectionTime -> sectionTime.sectionIndex }
            .filter { sectionTime: ParsedSectionTime -> sectionTime.startTimeText.isNotBlank() && sectionTime.endTimeText.isNotBlank() }
            .forEach { sectionTime: ParsedSectionTime ->
                scheduleRepository.saveSectionTemplate(
                    createSectionTemplate(
                        semesterId = activeSemester.semesterId,
                        sectionIndex = sectionTime.sectionIndex,
                        startTimeText = sectionTime.startTimeText,
                        endTimeText = sectionTime.endTimeText,
                        nowMillis = nowMillis
                    )
                )
            }
    }

    private fun resolveImportedCourseConflicts(
        parsedCourses: List<ParsedScheduleCourse>,
        resolvedConflictChoices: Map<Int, Int>
    ): List<ParsedScheduleCourse> {
        val conflictGroups: List<ScheduleImportConflictGroup> = buildImportConflictGroups(parsedCourses)
        val removedIndexes: Set<Int> = conflictGroups.flatMap { group: ScheduleImportConflictGroup ->
            val selectedIndex: Int = resolvedConflictChoices[group.groupIndex] ?: group.options.firstOrNull()?.courseIndex ?: return@flatMap emptyList<Int>()
            group.options.map { option: ScheduleImportConflictOption -> option.courseIndex }.filter { courseIndex: Int -> courseIndex != selectedIndex }
        }.toSet()
        return parsedCourses.filterIndexed { index: Int, _: ParsedScheduleCourse -> index !in removedIndexes }
    }

    private fun buildImportConflictGroups(parsedCourses: List<ParsedScheduleCourse>): List<ScheduleImportConflictGroup> {
        val conflictGroups: MutableList<ScheduleImportConflictGroup> = mutableListOf()
        val usedIndexes: MutableSet<Int> = mutableSetOf()
        parsedCourses.forEachIndexed { index: Int, parsedCourse: ParsedScheduleCourse ->
            if (index in usedIndexes) return@forEachIndexed
            val conflictIndexes: List<Int> = parsedCourses.mapIndexedNotNull { otherIndex: Int, otherCourse: ParsedScheduleCourse ->
                otherIndex.takeIf { otherIndex != index && hasParsedCourseConflict(parsedCourse, otherCourse) }
            }
            if (conflictIndexes.isEmpty()) return@forEachIndexed
            val groupIndexes: List<Int> = (listOf(index) + conflictIndexes).distinct().sorted()
            usedIndexes.addAll(groupIndexes)
            conflictGroups.add(
                ScheduleImportConflictGroup(
                    groupIndex = conflictGroups.size,
                    description = buildImportConflictDescription(groupIndexes.map { courseIndex: Int -> parsedCourses[courseIndex] }),
                    options = groupIndexes.map { courseIndex: Int -> parsedCourses[courseIndex].toConflictOption(courseIndex) }
                )
            )
        }
        return conflictGroups
    }

    private fun buildImportConflictDescription(courses: List<ParsedScheduleCourse>): String {
        val firstCourse: ParsedScheduleCourse = courses.first()
        return "${firstCourse.dayOfWeek.toWeekdayName()} 第${firstCourse.startSection}-${firstCourse.endSection}节的周次重叠"
    }

    private fun ParsedScheduleCourse.toConflictOption(courseIndex: Int): ScheduleImportConflictOption {
        return ScheduleImportConflictOption(
            courseIndex = courseIndex,
            title = courseName,
            detail = "${dayOfWeek.toWeekdayName()} 第${startSection}-${endSection}节｜第${startWeek}-${endWeek}周｜${classroom.ifBlank { "未识别教室" }}"
        )
    }

    private fun hasParsedCourseConflict(firstCourse: ParsedScheduleCourse, secondCourse: ParsedScheduleCourse): Boolean {
        if (firstCourse.dayOfWeek != secondCourse.dayOfWeek) return false
        if (!hasRangeOverlap(firstCourse.startSection, firstCourse.endSection, secondCourse.startSection, secondCourse.endSection)) return false
        // 周次维度用单一真相源判定,考虑单双周后才算冲突(单周课与双周课永不同周上课)
        return buildWeekConflictRule(firstCourse.startWeek, firstCourse.endWeek, firstCourse.weekMode)
            .hasWeekConflictWith(buildWeekConflictRule(secondCourse.startWeek, secondCourse.endWeek, secondCourse.weekMode))
    }

    private fun hasCourseTimeConflict(startSection: Int, endSection: Int, startWeek: Int, endWeek: Int, weekMode: String, rule: ScheduleCourseRuleEntity): Boolean {
        if (!hasRangeOverlap(startSection, endSection, rule.startSection, rule.endSection)) return false
        // 周次维度用单一真相源判定,考虑单双周后才算冲突
        return buildWeekConflictRule(startWeek, endWeek, weekMode).hasWeekConflictWith(rule)
    }

    /** 构造仅用于周次冲突判定的临时规则,其余字段无意义,只承载周次区间与单双周模式。 */
    private fun buildWeekConflictRule(startWeek: Int, endWeek: Int, weekMode: String): ScheduleCourseRuleEntity {
        return ScheduleCourseRuleEntity(
            ruleId = "",
            courseId = "",
            dayOfWeek = 0,
            startSection = 0,
            endSection = 0,
            startWeek = startWeek,
            endWeek = endWeek,
            weekMode = weekMode
        )
    }

    private fun hasRangeOverlap(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Boolean {
        return firstStart <= secondEnd && secondStart <= firstEnd
    }

    private suspend fun getCurrentMaxSection(): Int {
        val activeSemester: ScheduleSemesterEntity? = findActiveSemester(scheduleRepository.getAllSemesters())
        return scheduleRepository.getAllSectionTemplates()
            .filter { template: ScheduleSectionTemplateEntity -> activeSemester == null || template.semesterId == activeSemester.semesterId }
            .maxOfOrNull { template: ScheduleSectionTemplateEntity -> template.sectionIndex }
            ?: DEFAULT_SECTION_TIMES.size
    }

    private fun parseHtmlScheduleText(rawContent: String, maxSection: Int): List<ParsedScheduleCourse> {
        if (!HTML_TABLE_REGEX.containsMatchIn(rawContent)) return emptyList()
        return HTML_ROW_REGEX.findAll(rawContent)
            .flatMap { rowMatch: MatchResult ->
                val cells: List<String> = parseHtmlTableCells(rowMatch.value)
                if (cells.isEmpty() || cells.none { cell: String -> SECTION_TIME_REGEX.containsMatchIn(cell.replace('：', ':')) }) {
                    emptyList()
                } else {
                    parseHtmlTableRow(cells, parseHtmlSectionIndex(rowMatch.value), maxSection)
                }
            }
            .toList()
    }

    private fun parseHtmlTableCells(rowHtml: String): List<String> {
        return HTML_CELL_REGEX.findAll(rowHtml)
            .map { cellMatch: MatchResult -> decodeHtmlCellText(cellMatch.groupValues.getOrNull(HTML_CELL_CONTENT_GROUP_INDEX).orEmpty()) }
            .toList()
    }

    private fun parseHtmlSectionIndex(rowHtml: String): Int {
        return HTML_CELL_ID_REGEX.find(rowHtml)?.groupValues?.getOrNull(HTML_SECTION_ID_GROUP_INDEX)?.toIntOrNull()
            ?: HTML_SECTION_INDEX_OFFSET
    }

    private fun parseHtmlTableRow(cells: List<String>, fallbackSectionIndex: Int, maxSection: Int): List<ParsedScheduleCourse> {
        val sectionInfo: ParsedSectionInfo = parseHtmlSectionInfo(cells.firstOrNull().orEmpty(), fallbackSectionIndex, maxSection) ?: return emptyList()
        return cells.drop(HTML_COURSE_COLUMN_START_INDEX).take(MAX_DAY_OF_WEEK).flatMapIndexed { index: Int, cellText: String ->
            parseTableCellCourses(
                cellText = cellText,
                dayOfWeek = index + 1,
                sectionInfo = sectionInfo,
                maxSection = maxSection
            )
        }
    }

    private fun parseHtmlSectionInfo(sectionText: String, fallbackSectionIndex: Int, maxSection: Int): ParsedSectionInfo? {
        val parsedSectionIndex: Int = SECTION_CELL_REGEX.find(sectionText)?.groupValues?.getOrNull(SECTION_START_GROUP_INDEX)?.toIntOrNull()
            ?: fallbackSectionIndex
        val safeSectionIndex: Int = parsedSectionIndex.coerceIn(MIN_SECTION, maxSection)
        val timeRange: Pair<String, String>? = parseSectionTimeRange(sectionText)
        val sectionTimes: List<ParsedSectionTime> = timeRange?.let { range: Pair<String, String> ->
            listOf(ParsedSectionTime(safeSectionIndex, range.first, range.second))
        } ?: emptyList()
        return ParsedSectionInfo(safeSectionIndex, safeSectionIndex, sectionTimes)
    }

    private fun decodeHtmlCellText(cellHtml: String): String {
        return cellHtml
            .replace(HTML_BREAK_REGEX, "\n")
            .replace(HTML_TAG_REGEX, "")
            .replace("&" + "nbsp;", " ")
            .replace("&" + "lt;", "<")
            .replace("&" + "gt;", ">")
            .replace("&" + "amp;", "&")
            .lines()
            .joinToString(separator = "\n") { line: String -> line.trim() }
            .trim()
    }

    private fun parseTableCellCourses(
        cellText: String,
        dayOfWeek: Int,
        sectionInfo: ParsedSectionInfo,
        maxSection: Int
    ): List<ParsedScheduleCourse> {
        val normalizedCellText: String = normalizeImportedCellText(cellText)
        if (normalizedCellText.isBlank()) return emptyList()
        val courseMatches: List<MatchResult> = TABLE_COURSE_NAME_REGEX.findAll(normalizedCellText).toList()
        if (courseMatches.isEmpty()) return emptyList()
        return courseMatches.mapIndexedNotNull { index: Int, matchResult: MatchResult ->
            val nextStartIndex: Int = courseMatches.getOrNull(index + 1)?.range?.first ?: normalizedCellText.length
            val detailText: String = normalizedCellText.substring(matchResult.range.last + 1, nextStartIndex).trim()
            val detailTokens: List<String> = splitImportDetailTokens(detailText)
            val weekRange: Pair<Int, Int> = parseWeekRange(detailText)
            val weekMode: String = parseWeekMode(detailText)
            val courseName: String = matchResult.groupValues.getOrNull(COURSE_NAME_GROUP_INDEX).orEmpty().trim().ifBlank { "导入课程" }
            val teacherName: String = parseTeacherName(detailTokens)
            val classroom: String = parseClassroom(detailTokens)
            ParsedScheduleCourse(
                dayOfWeek = dayOfWeek,
                startSection = sectionInfo.startSection.coerceIn(MIN_SECTION, maxSection),
                endSection = sectionInfo.endSection.coerceIn(sectionInfo.startSection.coerceIn(MIN_SECTION, maxSection), maxSection),
                courseName = courseName,
                teacherName = teacherName,
                classroom = classroom,
                startWeek = weekRange.first,
                endWeek = weekRange.second,
                weekMode = weekMode,
                sectionTimes = sectionInfo.sectionTimes
            )
        }
    }

    private fun parseSectionTimeRange(sectionText: String): Pair<String, String>? {
        val normalizedSectionText: String = sectionText.replace('：', ':')
        val timeValues: List<String> = SECTION_TIME_REGEX.findAll(normalizedSectionText).map { matchResult: MatchResult -> matchResult.value }.toList()
        if (timeValues.size < SECTION_TIME_VALUE_COUNT) return null
        return timeValues.first() to timeValues[SECTION_END_TIME_INDEX]
    }

    private fun normalizeImportedCellText(cellText: String): String {
        return cellText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace('：', ':')
            .lines()
            .joinToString(separator = " ") { line: String -> line.trim() }
            .trim()
    }

    private fun splitImportDetailTokens(detailText: String): List<String> {
        return normalizeImportedCellText(detailText)
            .split(Regex("\\s+"))
            .map { token: String -> token.trim() }
            .filter { token: String -> token.isNotBlank() && !token.startsWith(";") }
    }

    private fun parseTeacherName(tokens: List<String>): String {
        val detailText: String = tokens.joinToString(separator = " ")
        val courseTypeIndex: Int = COURSE_TYPE_TOKEN_REGEX.find(detailText)?.range?.first ?: detailText.length
        return CHINESE_NAME_REGEX.findAll(detailText.take(courseTypeIndex))
            .map { matchResult: MatchResult -> matchResult.value }
            .firstOrNull { value: String -> canUseTeacherName(value) }
            .orEmpty()
    }

    private fun canUseTeacherName(value: String): Boolean {
        return !WEEK_TEXT_REGEX.containsMatchIn(value) &&
            !COURSE_TYPE_TOKEN_REGEX.containsMatchIn(value) &&
            !value.contains("第") &&
            !value.contains("周")
    }

    private fun parseClassroom(tokens: List<String>): String {
        val detailText: String = tokens.joinToString(separator = " ")
        return CLASSROOM_TOKEN_REGEX.find(detailText)?.value.orEmpty()
    }

    private fun parseWeekRange(value: String): Pair<Int, Int> {
        // 离散周次列表（如「1,3,5,7周」）优先识别，避免被区间/单周正则误截只剩末尾一周
        val weekList: List<Int> = parseWeekList(value)
        if (weekList.isNotEmpty()) {
            val safeStartWeek: Int = weekList.first().coerceIn(MIN_TOTAL_WEEKS, MAX_TOTAL_WEEKS)
            val safeEndWeek: Int = weekList.last().coerceIn(safeStartWeek, MAX_TOTAL_WEEKS)
            return safeStartWeek to safeEndWeek
        }
        val rangeMatch: MatchResult? = WEEK_RANGE_REGEX.find(value)
        if (rangeMatch != null) {
            val startWeek: Int = rangeMatch.groupValues.getOrNull(WEEK_START_GROUP_INDEX)?.toIntOrNull() ?: DEFAULT_START_WEEK
            val endWeek: Int = rangeMatch.groupValues.getOrNull(WEEK_END_GROUP_INDEX)?.toIntOrNull() ?: startWeek
            val safeStartWeek: Int = startWeek.coerceIn(MIN_TOTAL_WEEKS, MAX_TOTAL_WEEKS)
            val safeEndWeek: Int = endWeek.coerceIn(safeStartWeek, MAX_TOTAL_WEEKS)
            return safeStartWeek to safeEndWeek
        }
        val singleMatch: MatchResult? = SINGLE_WEEK_REGEX.find(value)
        if (singleMatch != null) {
            val singleWeek: Int = singleMatch.groupValues.getOrNull(WEEK_START_GROUP_INDEX)?.toIntOrNull() ?: DEFAULT_START_WEEK
            val safeSingleWeek: Int = singleWeek.coerceIn(MIN_TOTAL_WEEKS, MAX_TOTAL_WEEKS)
            return safeSingleWeek to safeSingleWeek
        }
        return DEFAULT_START_WEEK to DEFAULT_TOTAL_WEEKS
    }

    private fun parseWeekMode(value: String): String {
        // 显式「单周/双周」文字优先
        when {
            value.contains("单周") -> return WEEK_MODE_ODD_WEEK
            value.contains("双周") -> return WEEK_MODE_EVEN_WEEK
        }
        // 离散列表若全为奇数则按单周、全为偶数则按双周，无规律则退化为每周
        val weekList: List<Int> = parseWeekList(value)
        if (weekList.size >= MIN_WEEK_LIST_SIZE_FOR_MODE) {
            if (weekList.all { week: Int -> week % 2 == 1 }) return WEEK_MODE_ODD_WEEK
            if (weekList.all { week: Int -> week % 2 == 0 }) return WEEK_MODE_EVEN_WEEK
        }
        return WEEK_MODE_EVERY_WEEK
    }

    /**
     * 从文本中提取离散周次列表（如「1,3,5,7周」「2、4、6周」），按升序去重返回。
     * 仅识别逗号/顿号分隔的列表；无匹配时返回空列表，交由区间/单周逻辑处理。
     * 注意：数据模型只有 startWeek/endWeek/weekMode，无法存任意离散集合；
     * 纯单/双周序列可由 weekMode 精确还原，无规律列表只能退化为范围＋每周（最大努力）。
     */
    private fun parseWeekList(value: String): List<Int> {
        val listMatch: MatchResult = WEEK_LIST_REGEX.find(value) ?: return emptyList()
        val listText: String = listMatch.groupValues.getOrNull(WEEK_LIST_GROUP_INDEX).orEmpty()
        return WEEK_LIST_NUMBER_REGEX.findAll(listText)
            .mapNotNull { matchResult: MatchResult -> matchResult.value.toIntOrNull() }
            .filter { week: Int -> week in MIN_TOTAL_WEEKS..MAX_TOTAL_WEEKS }
            .distinct()
            .sorted()
            .toList()
    }

    private fun buildImportPreviewSectionTimeText(parsedCourse: ParsedScheduleCourse): String {
        val startTimeText: String = parsedCourse.sectionTimes.firstOrNull { sectionTime: ParsedSectionTime -> sectionTime.sectionIndex == parsedCourse.startSection }?.startTimeText.orEmpty()
        val endTimeText: String = parsedCourse.sectionTimes.firstOrNull { sectionTime: ParsedSectionTime -> sectionTime.sectionIndex == parsedCourse.endSection }?.endTimeText.orEmpty()
        if (startTimeText.isBlank() || endTimeText.isBlank()) return ""
        return "（$startTimeText-$endTimeText）"
    }

    private fun buildImportPreviewWeekText(parsedCourse: ParsedScheduleCourse): String {
        val modeText: String = when (parsedCourse.weekMode) {
            WEEK_MODE_ODD_WEEK -> " 单周"
            WEEK_MODE_EVEN_WEEK -> " 双周"
            else -> ""
        }
        return "第${parsedCourse.startWeek}-${parsedCourse.endWeek}周$modeText"
    }

    private fun parseDayOfWeek(value: String): Int? {
        return when {
            value.contains("一") || value == "1" -> 1
            value.contains("二") || value == "2" -> 2
            value.contains("三") || value == "3" -> 3
            value.contains("四") || value == "4" -> 4
            value.contains("五") || value == "5" -> 5
            value.contains("六") || value == "6" -> 6
            value.contains("日") || value.contains("天") || value == "7" -> 7
            else -> null
        }
    }

    private fun Int.toWeekdayName(): String {
        return when (this) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }

    private fun Int.toDayPart(): String {
        return when (this) {
            in 1..4 -> "MORNING"
            in 5..8 -> "AFTERNOON"
            else -> "EVENING"
        }
    }

    private fun parseSectionTimeLines(sectionTimesText: String, semesterId: String, nowMillis: Long): List<ScheduleSectionTemplateEntity> {
        val lines: List<String> = sectionTimesText.lines().filter { line: String -> line.isNotBlank() }
        if (lines.isEmpty()) {
            return DEFAULT_SECTION_TIMES.mapIndexed { index: Int, pair: Pair<String, String> ->
                val sectionIndex: Int = index + 1
                createSectionTemplate(semesterId, sectionIndex, pair.first, pair.second, nowMillis)
            }
        }
        return lines.mapIndexedNotNull { index: Int, line: String ->
            val tokens: List<String> = line.trim().split(Regex("\\s+")).filter { token: String -> token.isNotBlank() }
            val startTimeText: String = tokens.getOrNull(tokens.size - 2) ?: return@mapIndexedNotNull null
            val endTimeText: String = tokens.getOrNull(tokens.size - 1) ?: return@mapIndexedNotNull null
            createSectionTemplate(semesterId, index + 1, startTimeText, endTimeText, nowMillis)
        }
    }

    private fun createSectionTemplate(
        semesterId: String,
        sectionIndex: Int,
        startTimeText: String,
        endTimeText: String,
        nowMillis: Long
    ): ScheduleSectionTemplateEntity {
        return ScheduleSectionTemplateEntity(
            sectionTemplateId = "$semesterId-section-$sectionIndex",
            semesterId = semesterId,
            sectionIndex = sectionIndex,
            displayName = "第 $sectionIndex 节",
            startTimeText = startTimeText,
            endTimeText = endTimeText,
            dayPart = sectionIndex.toDayPart(),
            updatedAt = nowMillis
        )
    }

    private data class ParsedScheduleCourse(
        val dayOfWeek: Int,
        val startSection: Int,
        val endSection: Int,
        val courseName: String,
        val teacherName: String,
        val classroom: String,
        val startWeek: Int,
        val endWeek: Int,
        val weekMode: String,
        val sectionTimes: List<ParsedSectionTime> = emptyList()
    )

    private data class ParsedSectionInfo(
        val startSection: Int,
        val endSection: Int,
        val sectionTimes: List<ParsedSectionTime>
    )

    private data class ParsedSectionTime(
        val sectionIndex: Int,
        val startTimeText: String,
        val endTimeText: String
    )

    private companion object {
        private const val STOP_TIMEOUT_MILLIS: Long = 5_000L
        private const val COURSE_PREVIEW_LIMIT: Int = 4
        private const val ASSIGNMENT_PREVIEW_LIMIT: Int = 4
        private const val EXAM_PREVIEW_LIMIT: Int = 3
        private const val EVENT_PREVIEW_LIMIT: Int = 3
        private const val DONE_STATUS: String = "DONE"
        private const val TODO_STATUS: String = "TODO"
        private const val PENDING_STATUS: String = "PENDING"
        private const val WEEK_SCHEDULE_LIMIT: Int = 20
        private const val TIMELINE_COURSE_LIMIT: Int = 4
        private const val TIMELINE_ITEM_LIMIT: Int = 4
        private const val TIMELINE_TOTAL_LIMIT: Int = 12
        private const val DEFAULT_START_SECTION: Int = 1
        private const val DEFAULT_END_SECTION: Int = 2
        private const val DEFAULT_START_WEEK: Int = 1
        private const val DEFAULT_DAY_OF_WEEK: Int = 1
        private const val DEFAULT_PRIORITY: Int = 2
        private const val MIN_SECTION: Int = 1
        private const val SECTION_INDEX_STEP: Int = 1
        private const val UNIQUE_NAME_START_SUFFIX: Int = 2
        private const val UNIQUE_NAME_STEP: Int = 1
        private const val MIN_DAY_OF_WEEK: Int = 1
        private const val MAX_DAY_OF_WEEK: Int = 7
        private const val MIN_PRIORITY: Int = 1
        private const val MAX_PRIORITY: Int = 5
        private const val DEFAULT_TOTAL_WEEKS: Int = 20
        private const val MIN_TOTAL_WEEKS: Int = 1
        private const val MAX_TOTAL_WEEKS: Int = 60
        // 离散周次列表至少包含两个周次才足以推断单/双周规律
        private const val MIN_WEEK_LIST_SIZE_FOR_MODE: Int = 2
        private const val DEFAULT_SEMESTER_ID: String = "default-semester"
        private const val DEFAULT_ACCENT_COLOR: String = "#7C4DFF"
        private const val WEEK_END_DAY: Int = 7
        private const val SHORT_ID_LENGTH: Int = 4
        private const val DEFAULT_ASSIGNMENT_OFFSET_MILLIS: Long = 86_400_000L
        private const val DEFAULT_EXAM_OFFSET_MILLIS: Long = 604_800_000L
        private const val CARE_INTENSITY_LOW: String = "LOW"
        private const val CARE_INTENSITY_NORMAL: String = "NORMAL"
        private const val CARE_INTENSITY_HIGH: String = "HIGH"
        private const val WEEK_MODE_EVERY_WEEK: String = "EVERY_WEEK"
        private const val WEEK_MODE_ODD_WEEK: String = "ODD_WEEK"
        private const val WEEK_MODE_EVEN_WEEK: String = "EVEN_WEEK"
        private const val COURSE_NAME_GROUP_INDEX: Int = 1
        private const val SECTION_START_GROUP_INDEX: Int = 1
        private const val WEEK_START_GROUP_INDEX: Int = 1
        private const val WEEK_END_GROUP_INDEX: Int = 2
        // 离散周次列表（如"1,3,5,7周"）的捕获组索引：第 1 组是整段数字列表文本
        private const val WEEK_LIST_GROUP_INDEX: Int = 1
        // 判定为离散列表所需的最少周次数量，单个数字不算列表
        private const val WEEK_LIST_MIN_COUNT: Int = 2
        private const val PREVIEW_INDEX_OFFSET: Int = 1
        private const val SECTION_TIME_VALUE_COUNT: Int = 2
        private const val SECTION_END_TIME_INDEX: Int = 1
        private const val DATE_PATTERN: String = "yyyy-MM-dd"
        private val SECTION_CELL_REGEX: Regex = Regex("第\\s*(\\d+)\\s*(?:[-—~至]\\s*(\\d+))?\\s*节")
        private val TABLE_COURSE_NAME_REGEX: Regex = Regex("<<\\s*([^>]+?)\\s*>>")
        private val WEEK_RANGE_REGEX: Regex = Regex("(?:第\\s*)?(\\d+)\\s*[-—~至]\\s*(\\d+)\\s*周")
        // 离散周次列表，如"第1,3,5,7周""2、4、6周"：捕获以逗号/顿号分隔、以"周"结尾的整段数字列表
        private val WEEK_LIST_REGEX: Regex = Regex("(?:第\\s*)?((?:\\d+\\s*[,，、]\\s*)+\\d+)\\s*周")
        // 从离散列表文本中逐个抽取周次数字
        private val WEEK_LIST_NUMBER_REGEX: Regex = Regex("\\d+")
        private val SINGLE_WEEK_REGEX: Regex = Regex("第?\\s*(\\d+)\\s*周")
        private val CHINESE_NAME_REGEX: Regex = Regex("[\\u4e00-\\u9fa5]{2,4}")
        private val CLASSROOM_TOKEN_REGEX: Regex = Regex("[A-Za-z0-9\\-]*\\d{3,}[A-Za-z]")
        private val COURSE_TYPE_TOKEN_REGEX: Regex = Regex("讲课|实验|上机|学时")
        private val WEEK_TEXT_REGEX: Regex = Regex("第?\\d+周|\\d+[-—~至]\\d+周")
        private val SECTION_TIME_REGEX: Regex = Regex("\\d{1,2}:\\d{2}")
        private const val HTML_CELL_CONTENT_GROUP_INDEX: Int = 1
        private const val HTML_SECTION_ID_GROUP_INDEX: Int = 1
        private const val HTML_SECTION_INDEX_OFFSET: Int = 1
        private const val HTML_COURSE_COLUMN_START_INDEX: Int = 1
        private val HTML_TABLE_REGEX: Regex = Regex("<\\s*table", RegexOption.IGNORE_CASE)
        private val HTML_ROW_REGEX: Regex = Regex("<\\s*tr[^>]*>.*?<\\s*/\\s*tr\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val HTML_CELL_REGEX: Regex = Regex("<\\s*t[dh][^>]*>(.*?)<\\s*/\\s*t[dh]\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val HTML_BREAK_REGEX: Regex = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
        private val HTML_TAG_REGEX: Regex = Regex("<[^>]+>")
        private val HTML_CELL_ID_REGEX: Regex = Regex("id\\s*=\\s*[\"']\\d+-(\\d+)[\"']", RegexOption.IGNORE_CASE)
        private val DEFAULT_SECTION_TIMES: List<Pair<String, String>> = listOf(
            "08:00" to "08:45",
            "08:55" to "09:40",
            "10:00" to "10:45",
            "10:55" to "11:40",
            "14:00" to "14:45",
            "14:55" to "15:40",
            "16:00" to "16:45",
            "16:55" to "17:40",
            "19:00" to "19:45",
            "19:55" to "20:40",
            "20:50" to "21:35",
            "21:45" to "22:30"
        )
    }
}

data class ScheduleImportConflictGroup(
    val groupIndex: Int,
    val description: String,
    val options: List<ScheduleImportConflictOption>
)

data class ScheduleImportConflictOption(
    val courseIndex: Int,
    val title: String,
    val detail: String
)

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val todayCourses: List<ScheduleCourseEntity> = emptyList(),
    val pendingAssignments: List<ScheduleAssignmentEntity> = emptyList(),
    val upcomingExams: List<ScheduleExamEntity> = emptyList(),
    val campusEvents: List<CampusEventEntity> = emptyList(),
    val allCourses: List<ScheduleCourseEntity> = emptyList(),
    val allCourseRules: List<ScheduleCourseRuleEntity> = emptyList(),
    val allAssignments: List<ScheduleAssignmentEntity> = emptyList(),
    val allExams: List<ScheduleExamEntity> = emptyList(),
    val allCampusEvents: List<CampusEventEntity> = emptyList(),
    val aiPolicy: ScheduleAiPolicyEntity = ScheduleAiPolicyEntity(),
    val semesters: List<ScheduleSemesterEntity> = emptyList(),
    val sectionTemplates: List<ScheduleSectionTemplateEntity> = emptyList(),
    val importDrafts: List<ScheduleImportDraftEntity> = emptyList(),
    val importPreviewText: String = "",
    val weekScheduleLines: List<String> = emptyList(),
    val timelineLines: List<String> = emptyList(),
    val reminderRuleCount: Int = 0,
    val careCandidateCount: Int = 0,
    val widgetState: ScheduleWidgetStateEntity = ScheduleWidgetStateEntity()
) {
    fun canRenderAcademicContent(): Boolean {
        return !isLoading && semesters.isNotEmpty() && sectionTemplates.isNotEmpty()
    }
}
