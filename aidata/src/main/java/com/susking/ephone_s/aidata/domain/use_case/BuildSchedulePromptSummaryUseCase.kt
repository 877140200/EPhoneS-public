package com.susking.ephone_s.aidata.domain.use_case

import com.susking.ephone_s.aidata.data.local.entity.CampusEventEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAdjustmentEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAssignmentEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseRuleEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleExamEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSemesterEntity
import com.susking.ephone_s.aidata.domain.model.schedule.SchedulePromptSummary
import com.susking.ephone_s.aidata.domain.repository.ScheduleRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * 构建 AI 可见的校园状态摘要。
 * 这里刻意只输出摘要，不输出完整原始课表，避免提示词过长或让 ai 机械报表。
 */
class BuildSchedulePromptSummaryUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {

    suspend fun execute(nowMillis: Long = System.currentTimeMillis()): SchedulePromptSummary {
        val policy = scheduleRepository.getAiPolicy()
        if (!policy.isAiVisible) {
            return SchedulePromptSummary(content = "")
        }

        val courses: List<ScheduleCourseEntity> = scheduleRepository.getAllCourses().filter { course: ScheduleCourseEntity -> course.isEnabled }
        val rules: List<ScheduleCourseRuleEntity> = scheduleRepository.getAllCourseRules().filter { rule: ScheduleCourseRuleEntity -> rule.isEnabled }
        val adjustments: List<ScheduleAdjustmentEntity> = scheduleRepository.getAllAdjustments()
        val assignments: List<ScheduleAssignmentEntity> = scheduleRepository.getAllAssignments()
        val exams: List<ScheduleExamEntity> = scheduleRepository.getAllExams()
        val campusEvents: List<CampusEventEntity> = scheduleRepository.getAllCampusEvents()

        val courseMap: Map<String, ScheduleCourseEntity> = courses.associateBy { course: ScheduleCourseEntity -> course.courseId }
        val todayDayOfWeek: Int = getChineseDayOfWeek(nowMillis)
        val tomorrowDayOfWeek: Int = if (todayDayOfWeek == MAX_DAY_OF_WEEK) MIN_DAY_OF_WEEK else todayDayOfWeek + 1
        val currentWeek: Int = calculateCurrentWeek(nowMillis, scheduleRepository.getAllSemesters())
        val todayCourses: List<String> = buildCourseLines(
            rules = rules,
            courseMap = courseMap,
            dayOfWeek = todayDayOfWeek,
            currentWeek = currentWeek,
            titlePrefix = "今天"
        )
        val tomorrowCourses: List<String> = if (policy.includeTomorrowCourses) {
            buildCourseLines(
                rules = rules,
                courseMap = courseMap,
                dayOfWeek = tomorrowDayOfWeek,
                currentWeek = currentWeek,
                titlePrefix = "明天"
            ).take(TOMORROW_COURSE_LIMIT)
        } else {
            emptyList()
        }
        val adjustmentLines: List<String> = adjustments
            .sortedByDescending { adjustment: ScheduleAdjustmentEntity -> adjustment.updatedAt }
            .take(ADJUSTMENT_LIMIT)
            .map { adjustment: ScheduleAdjustmentEntity -> buildAdjustmentLine(adjustment, courseMap) }
        val assignmentLines: List<String> = assignments
            .filter { assignment: ScheduleAssignmentEntity -> policy.includeCompletedAssignments || assignment.status != ASSIGNMENT_DONE_STATUS }
            .sortedWith(compareBy<ScheduleAssignmentEntity> { assignment: ScheduleAssignmentEntity -> normalizeEmptyTime(assignment.dueAt) }.thenByDescending { assignment: ScheduleAssignmentEntity -> assignment.priority })
            .take(ASSIGNMENT_LIMIT)
            .map { assignment: ScheduleAssignmentEntity -> buildAssignmentLine(assignment, courseMap, nowMillis) }
        val examLines: List<String> = exams
            .filter { exam: ScheduleExamEntity -> exam.examAt >= nowMillis - PAST_VISIBLE_TOLERANCE_MILLIS }
            .sortedBy { exam: ScheduleExamEntity -> exam.examAt }
            .take(EXAM_LIMIT)
            .map { exam: ScheduleExamEntity -> buildExamLine(exam, courseMap, nowMillis) }
        val campusEventLines: List<String> = campusEvents
            .filter { event: CampusEventEntity -> !event.isCompleted }
            .sortedWith(compareBy<CampusEventEntity> { event: CampusEventEntity -> normalizeEmptyTime(event.startAt) }.thenByDescending { event: CampusEventEntity -> event.importance })
            .take(CAMPUS_EVENT_LIMIT)
            .map { event: CampusEventEntity -> buildCampusEventLine(event) }

        val sections: List<String> = listOf(
            buildSection("今天课程", todayCourses.ifEmpty { listOf("今天没有记录到固定课程。") }),
            buildSection("明天关键课程", tomorrowCourses),
            buildSection("调课与停课", adjustmentLines),
            buildSection("待完成作业", assignmentLines),
            buildSection("近期考试", examLines),
            buildSection("校园动态", campusEventLines),
            buildCarePolicyLine(policy.canAiProactivelyCare, policy.careIntensity)
        ).filter { section: String -> section.isNotBlank() }

        return SchedulePromptSummary(
            content = sections.joinToString(separator = "\n")
        )
    }

    private fun buildCourseLines(
        rules: List<ScheduleCourseRuleEntity>,
        courseMap: Map<String, ScheduleCourseEntity>,
        dayOfWeek: Int,
        currentWeek: Int,
        titlePrefix: String
    ): List<String> {
        return rules
            .filter { rule: ScheduleCourseRuleEntity -> rule.dayOfWeek == dayOfWeek && isRuleActiveInWeek(rule, currentWeek) }
            .sortedBy { rule: ScheduleCourseRuleEntity -> rule.startSection }
            .take(TODAY_COURSE_LIMIT)
            .mapNotNull { rule: ScheduleCourseRuleEntity ->
                val course: ScheduleCourseEntity = courseMap[rule.courseId] ?: return@mapNotNull null
                val classroom: String = rule.classroomOverride.ifBlank { course.classroom }
                val timeText: String = buildSectionTimeText(rule.startSection, rule.endSection, rule.startTimeText, rule.endTimeText)
                "$titlePrefix $timeText ${course.courseName}${classroom.takeIf { value: String -> value.isNotBlank() }?.let { value: String -> "，地点 $value" }.orEmpty()}"
            }
    }

    private fun isRuleActiveInWeek(rule: ScheduleCourseRuleEntity, currentWeek: Int): Boolean {
        if (currentWeek < rule.startWeek || currentWeek > rule.endWeek) return false
        return when (rule.weekMode) {
            "ODD_WEEK" -> currentWeek % 2 == 1
            "EVEN_WEEK" -> currentWeek % 2 == 0
            else -> true
        }
    }

    private fun buildAdjustmentLine(adjustment: ScheduleAdjustmentEntity, courseMap: Map<String, ScheduleCourseEntity>): String {
        val courseName: String = adjustment.courseId?.let { courseId: String -> courseMap[courseId]?.courseName } ?: "临时课程"
        val typeText: String = when (adjustment.adjustmentType) {
            "CANCEL" -> "停课"
            "MAKE_UP" -> "补课"
            else -> "调课"
        }
        val adjustedText: String = adjustment.adjustedDate?.let { date: Long -> "调整到 ${formatDateTime(date)}" }.orEmpty()
        return "$courseName $typeText $adjustedText ${adjustment.reason}".trim()
    }

    private fun buildAssignmentLine(
        assignment: ScheduleAssignmentEntity,
        courseMap: Map<String, ScheduleCourseEntity>,
        nowMillis: Long
    ): String {
        val courseName: String = assignment.courseId?.let { courseId: String -> courseMap[courseId]?.courseName } ?: "未关联课程"
        val dueText: String = if (assignment.dueAt > 0L) "截止 ${formatDateTime(assignment.dueAt)}，剩余 ${buildDayDistanceText(nowMillis, assignment.dueAt)}" else "未设置截止时间"
        return "$courseName：${assignment.title}，$dueText，状态 ${assignment.status}"
    }

    private fun buildExamLine(
        exam: ScheduleExamEntity,
        courseMap: Map<String, ScheduleCourseEntity>,
        nowMillis: Long
    ): String {
        val courseName: String = exam.courseId?.let { courseId: String -> courseMap[courseId]?.courseName } ?: exam.examName
        val distanceText: String = buildDayDistanceText(nowMillis, exam.examAt)
        return "$courseName：${exam.examName}，${formatDateTime(exam.examAt)}，剩余 $distanceText，复习状态 ${exam.reviewStatus}"
    }

    private fun buildCampusEventLine(event: CampusEventEntity): String {
        val timeText: String = if (event.startAt > 0L) formatDateTime(event.startAt) else "未设置时间"
        val locationText: String = event.location.takeIf { value: String -> value.isNotBlank() }?.let { value: String -> "，地点 $value" }.orEmpty()
        return "${event.title}，$timeText$locationText"
    }

    private fun buildSection(title: String, lines: List<String>): String {
        if (lines.isEmpty()) return ""
        return "# $title\n${lines.joinToString(separator = "\n") { line: String -> "- $line" }}"
    }

    private fun buildCarePolicyLine(canAiProactivelyCare: Boolean, careIntensity: String): String {
        if (!canAiProactivelyCare) {
            return "# AI 关心规则\n- 只把校园状态作为理解用户处境的背景，不主动提醒。"
        }
        val intensityText: String = when (careIntensity) {
            "QUIET" -> "非常克制，只在用户主动提到学习、疲惫或安排时轻轻回应。"
            "STICKY" -> "可以更黏人地关心，但仍需自然，不能机械报课表。"
            else -> "可以在语境自然时温柔提醒课程、作业或考试，不要频繁打断对话。"
        }
        return "# AI 关心规则\n- $intensityText"
    }

    private fun buildSectionTimeText(startSection: Int, endSection: Int, startTimeText: String, endTimeText: String): String {
        val sectionText: String = "第${startSection}-${endSection}节"
        val clockText: String = if (startTimeText.isNotBlank() && endTimeText.isNotBlank()) {
            "($startTimeText-$endTimeText)"
        } else {
            ""
        }
        return "$sectionText$clockText"
    }

    private fun buildDayDistanceText(fromMillis: Long, toMillis: Long): String {
        // 按自然日(零点)做差，跨过几个午夜就算几天，避免用 24 小时整段导致的口径偏差。
        val dayCount: Long = countDayDifference(fromMillis, toMillis)
        return when {
            dayCount < 0L -> "已过期"
            dayCount == 0L -> "今天"
            dayCount == 1L -> "明天"
            else -> "${dayCount}天"
        }
    }

    /**
     * 计算从 fromMillis 到 toMillis 跨越的自然日数。
     * 例如今天 20:00 到明天 09:00 返回 1（明天），同一天内返回 0（今天）。
     */
    private fun countDayDifference(fromMillis: Long, toMillis: Long): Long {
        val fromStartOfDay: Long = atStartOfDay(fromMillis)
        val toStartOfDay: Long = atStartOfDay(toMillis)
        return (toStartOfDay - fromStartOfDay) / DAY_MILLIS
    }

    /**
     * 把时间戳归一化到当天零点(Asia/Shanghai)，用于自然日比较。
     */
    private fun atStartOfDay(timestamp: Long): Long {
        val calendar: Calendar = Calendar.getInstance(CHINA_TIME_ZONE).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(timestamp)
    }

    private fun normalizeEmptyTime(timestamp: Long): Long {
        return if (timestamp <= 0L) Long.MAX_VALUE else timestamp
    }

    private fun calculateCurrentWeek(nowMillis: Long, semesters: List<ScheduleSemesterEntity>): Int {
        val activeSemester: ScheduleSemesterEntity = semesters
            .filter { semester: ScheduleSemesterEntity -> semester.isActive }
            .maxByOrNull { semester: ScheduleSemesterEntity -> semester.startDateMillis }
            ?: return FIRST_WEEK
        val diffMillis: Long = (nowMillis - activeSemester.startDateMillis).coerceAtLeast(0L)
        val calculatedWeek: Int = (diffMillis / WEEK_MILLIS).toInt() + FIRST_WEEK
        return calculatedWeek.coerceIn(FIRST_WEEK, activeSemester.totalWeeks.coerceAtLeast(FIRST_WEEK))
    }

    private fun getChineseDayOfWeek(timestamp: Long): Int {
        val calendar: Calendar = Calendar.getInstance(CHINA_TIME_ZONE).apply {
            timeInMillis = timestamp
        }
        val calendarDay: Int = calendar.get(Calendar.DAY_OF_WEEK)
        return if (calendarDay == Calendar.SUNDAY) MAX_DAY_OF_WEEK else calendarDay - 1
    }

    private companion object {
        private const val TODAY_COURSE_LIMIT: Int = 8
        private const val TOMORROW_COURSE_LIMIT: Int = 4
        private const val ASSIGNMENT_LIMIT: Int = 5
        private const val EXAM_LIMIT: Int = 4
        private const val ADJUSTMENT_LIMIT: Int = 4
        private const val CAMPUS_EVENT_LIMIT: Int = 4
        private const val FIRST_WEEK: Int = 1
        private const val MIN_DAY_OF_WEEK: Int = 1
        private const val MAX_DAY_OF_WEEK: Int = 7
        private const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
        private const val WEEK_MILLIS: Long = 7L * DAY_MILLIS
        private const val PAST_VISIBLE_TOLERANCE_MILLIS: Long = 6L * 60L * 60L * 1000L
        private const val ASSIGNMENT_DONE_STATUS: String = "DONE"
        private val CHINA_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
        private val dateTimeFormat: SimpleDateFormat = SimpleDateFormat("M月d日 HH:mm", Locale.CHINESE).apply {
            timeZone = CHINA_TIME_ZONE
        }
    }
}
