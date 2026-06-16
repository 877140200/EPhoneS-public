package com.susking.ephone_s.aidata.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.susking.ephone_s.aidata.data.local.dao.ScheduleDao
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAssignmentEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseRuleEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleExamEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleReminderRecordEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleReminderRuleEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSectionTemplateEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSemesterEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 课程表本地提醒 Worker。
 *
 * 职责：周期性扫描所有启用的提醒规则，按目标的真实发生时间计算提醒触发点，
 * 当「提醒窗口已开启且目标尚未发生」且该次发生还未提醒过时，写入提醒记录并发系统通知。
 *
 * 重要设计：
 * - 真正请求外部模型仍必须交给 brain 展示并转发，这里只做本地通知，不请求 AI。
 * - 提醒记录主键含目标发生时间(targetAt)，所以周期性课程每周的不同发生点会生成不同记录，
 *   从而实现每周重新提醒，而不是一生只提醒一次。
 * - 触发判定用 `triggerAt <= now < targetAt`，即使 Worker 曾长时间未运行，只要提醒窗口
 *   仍然有效且未发送过，补一次提醒也安全；记录的幂等性由主键去重保证。
 */
@HiltWorker
class ScheduleReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduleDao: ScheduleDao
) : CoroutineWorker(context, params) {

    /** 一个待评估的目标发生点：targetAt 为真实发生时间，triggerAt 为应当提醒的时间。 */
    private data class ReminderOccurrence(
        val rule: ScheduleReminderRuleEntity,
        val targetAt: Long,
        val triggerAt: Long
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val nowMillis: Long = System.currentTimeMillis()
            val enabledRules: List<ScheduleReminderRuleEntity> = scheduleDao.getAllReminderRules()
                .filter { rule: ScheduleReminderRuleEntity -> rule.isEnabled }
            if (enabledRules.isEmpty()) {
                Log.i(TAG, "课程表提醒：没有启用的提醒规则，跳过本次执行")
                return@withContext Result.success()
            }

            // 预加载评估所需数据，避免在循环中反复查库。
            val assignmentMap: Map<String, ScheduleAssignmentEntity> = scheduleDao.getAllAssignments()
                .associateBy { assignment: ScheduleAssignmentEntity -> assignment.assignmentId }
            val examMap: Map<String, ScheduleExamEntity> = scheduleDao.getAllExams()
                .associateBy { exam: ScheduleExamEntity -> exam.examId }
            val courseRulesByCourseId: Map<String, List<ScheduleCourseRuleEntity>> = scheduleDao.getAllCourseRules()
                .filter { rule: ScheduleCourseRuleEntity -> rule.isEnabled }
                .groupBy { rule: ScheduleCourseRuleEntity -> rule.courseId }
            val semesters: List<ScheduleSemesterEntity> = scheduleDao.getAllSemesters()
            val activeSemester: ScheduleSemesterEntity? = semesters
                .filter { semester: ScheduleSemesterEntity -> semester.isActive }
                .maxByOrNull { semester: ScheduleSemesterEntity -> semester.startDateMillis }
            val sectionStartTimeMap: Map<Int, String> = activeSemester?.let { semester: ScheduleSemesterEntity ->
                scheduleDao.getAllSectionTemplates()
                    .filter { template: ScheduleSectionTemplateEntity -> template.semesterId == semester.semesterId }
                    .associate { template: ScheduleSectionTemplateEntity -> template.sectionIndex to template.startTimeText }
            } ?: emptyMap()
            val currentWeek: Int = calculateCurrentWeek(nowMillis, activeSemester)

            // 已存在记录主键集合，用于幂等去重。
            val existingRecordIds: Set<String> = scheduleDao.getAllReminderRecords()
                .map { record: ScheduleReminderRecordEntity -> record.reminderRecordId }
                .toSet()

            val occurrences: List<ReminderOccurrence> = enabledRules.flatMap { rule: ScheduleReminderRuleEntity ->
                buildOccurrencesForRule(
                    rule = rule,
                    nowMillis = nowMillis,
                    assignmentMap = assignmentMap,
                    examMap = examMap,
                    courseRulesByCourseId = courseRulesByCourseId,
                    sectionStartTimeMap = sectionStartTimeMap,
                    activeSemester = activeSemester,
                    currentWeek = currentWeek
                )
            }

            var deliveredCount: Int = 0
            var latestDeliveredRule: ScheduleReminderRuleEntity? = null
            occurrences.forEach { occurrence: ReminderOccurrence ->
                // 提醒窗口已开启(已到提醒时间)且目标尚未发生时才提醒。
                val isWindowOpen: Boolean = occurrence.triggerAt <= nowMillis && nowMillis < occurrence.targetAt
                if (!isWindowOpen) return@forEach
                val recordId: String = buildRecordId(occurrence.rule, occurrence.targetAt)
                if (existingRecordIds.contains(recordId)) return@forEach
                scheduleDao.upsertReminderRecord(
                    ScheduleReminderRecordEntity(
                        reminderRecordId = recordId,
                        reminderRuleId = occurrence.rule.reminderRuleId,
                        targetType = occurrence.rule.targetType,
                        targetId = occurrence.rule.targetId,
                        triggerAt = occurrence.triggerAt,
                        deliveredAt = nowMillis,
                        status = "DELIVERED"
                    )
                )
                deliveredCount += 1
                latestDeliveredRule = occurrence.rule
            }

            if (deliveredCount > 0) {
                showSummaryNotification(deliveredCount, latestDeliveredRule)
            }
            Log.i(TAG, "课程表提醒检查完成：启用规则 ${enabledRules.size}，候选发生点 ${occurrences.size}，新增提醒 $deliveredCount")
            Result.success()
        } catch (exception: Exception) {
            Log.e(TAG, "课程表提醒 Worker 执行失败", exception)
            Result.retry()
        }
    }

    /**
     * 根据规则目标类型计算所有待评估的发生点。
     * - 作业 / 考试：单一发生时间。
     * - 课程：该课程的每条启用且本周生效的时间规则各产生一个本周发生点。
     */
    private fun buildOccurrencesForRule(
        rule: ScheduleReminderRuleEntity,
        nowMillis: Long,
        assignmentMap: Map<String, ScheduleAssignmentEntity>,
        examMap: Map<String, ScheduleExamEntity>,
        courseRulesByCourseId: Map<String, List<ScheduleCourseRuleEntity>>,
        sectionStartTimeMap: Map<Int, String>,
        activeSemester: ScheduleSemesterEntity?,
        currentWeek: Int
    ): List<ReminderOccurrence> {
        val offsetMillis: Long = rule.minutesBefore.toLong() * MILLIS_PER_MINUTE
        return when (rule.targetType) {
            TARGET_TYPE_ASSIGNMENT -> {
                val dueAt: Long = assignmentMap[rule.targetId]?.dueAt ?: 0L
                if (dueAt <= 0L) emptyList()
                else listOf(ReminderOccurrence(rule, dueAt, dueAt - offsetMillis))
            }
            TARGET_TYPE_EXAM -> {
                val examAt: Long = examMap[rule.targetId]?.examAt ?: 0L
                if (examAt <= 0L) emptyList()
                else listOf(ReminderOccurrence(rule, examAt, examAt - offsetMillis))
            }
            TARGET_TYPE_COURSE -> {
                if (activeSemester == null) return emptyList()
                val courseRules: List<ScheduleCourseRuleEntity> = courseRulesByCourseId[rule.targetId].orEmpty()
                courseRules.mapNotNull { courseRule: ScheduleCourseRuleEntity ->
                    if (!isRuleActiveInWeek(courseRule, currentWeek)) return@mapNotNull null
                    val classStartAt: Long = calculateCourseStartMillis(
                        activeSemester = activeSemester,
                        currentWeek = currentWeek,
                        dayOfWeek = courseRule.dayOfWeek,
                        startSection = courseRule.startSection,
                        ruleStartTimeText = courseRule.startTimeText,
                        sectionStartTimeMap = sectionStartTimeMap
                    ) ?: return@mapNotNull null
                    ReminderOccurrence(rule, classStartAt, classStartAt - offsetMillis)
                }
            }
            else -> emptyList()
        }
    }

    /** 计算课程在本周指定星期、指定起始节的上课时间(毫秒)。无法解析上课钟点时返回 null。 */
    private fun calculateCourseStartMillis(
        activeSemester: ScheduleSemesterEntity,
        currentWeek: Int,
        dayOfWeek: Int,
        startSection: Int,
        ruleStartTimeText: String,
        sectionStartTimeMap: Map<Int, String>
    ): Long? {
        val startClockText: String = ruleStartTimeText.ifBlank { sectionStartTimeMap[startSection].orEmpty() }
        val clockMinutes: Int = parseClockToMinutes(startClockText) ?: return null
        val calendar: Calendar = Calendar.getInstance(CHINA_TIME_ZONE).apply {
            timeInMillis = activeSemester.startDateMillis
            // 学期起始日视为第 1 周周一 0 点，叠加周偏移与星期偏移。
            add(Calendar.DAY_OF_YEAR, (currentWeek - FIRST_WEEK) * DAYS_PER_WEEK + (dayOfWeek - MIN_DAY_OF_WEEK))
            set(Calendar.HOUR_OF_DAY, clockMinutes / MINUTES_PER_HOUR)
            set(Calendar.MINUTE, clockMinutes % MINUTES_PER_HOUR)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /** 解析 "HH:mm" 为当天分钟数。格式非法返回 null。 */
    private fun parseClockToMinutes(clockText: String): Int? {
        val normalizedText: String = clockText.trim().replace('：', ':')
        val parts: List<String> = normalizedText.split(':')
        if (parts.size < CLOCK_PART_COUNT) return null
        val hour: Int = parts[0].trim().toIntOrNull() ?: return null
        val minute: Int = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * MINUTES_PER_HOUR + minute
    }

    /** 与提示词摘要保持一致的单双周判定。 */
    private fun isRuleActiveInWeek(rule: ScheduleCourseRuleEntity, currentWeek: Int): Boolean {
        if (currentWeek < rule.startWeek || currentWeek > rule.endWeek) return false
        return when (rule.weekMode) {
            WEEK_MODE_ODD -> currentWeek % 2 == 1
            WEEK_MODE_EVEN -> currentWeek % 2 == 0
            else -> true
        }
    }

    /** 与提示词摘要保持一致的当前周次计算。 */
    private fun calculateCurrentWeek(nowMillis: Long, activeSemester: ScheduleSemesterEntity?): Int {
        if (activeSemester == null) return FIRST_WEEK
        val diffMillis: Long = (nowMillis - activeSemester.startDateMillis).coerceAtLeast(0L)
        val calculatedWeek: Int = (diffMillis / WEEK_MILLIS).toInt() + FIRST_WEEK
        return calculatedWeek.coerceIn(FIRST_WEEK, activeSemester.totalWeeks.coerceAtLeast(FIRST_WEEK))
    }

    private fun buildRecordId(rule: ScheduleReminderRuleEntity, targetAt: Long): String {
        return "record-${rule.reminderRuleId}-${rule.targetId}-$targetAt"
    }

    private fun showSummaryNotification(deliveredCount: Int, latestDeliveredRule: ScheduleReminderRuleEntity?): Unit {
        val manager: NotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("课程表提醒")
            .setContentText("有 $deliveredCount 条课程表提醒需要注意")
            .setContentIntent(createSchedulePendingIntent(latestDeliveredRule))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createSchedulePendingIntent(rule: ScheduleReminderRuleEntity?): PendingIntent {
        val intent: Intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            ?: Intent()
        intent.action = ACTION_OPEN_SCHEDULE_TARGET
        intent.putExtra(EXTRA_TARGET_TYPE, rule?.targetType.orEmpty())
        intent.putExtra(EXTRA_TARGET_ID, rule?.targetId.orEmpty())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            applicationContext,
            rule?.targetId?.hashCode() ?: NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG: String = "ScheduleReminderWorker"
        private const val WORK_NAME: String = "ScheduleReminderWork"
        private const val REPEAT_MINUTES: Long = 15L
        private const val CHANNEL_ID: String = "schedule_reminders"
        private const val CHANNEL_NAME: String = "课程表提醒"
        private const val NOTIFICATION_ID: Int = 43015

        private const val TARGET_TYPE_COURSE: String = "COURSE"
        private const val TARGET_TYPE_ASSIGNMENT: String = "ASSIGNMENT"
        private const val TARGET_TYPE_EXAM: String = "EXAM"
        private const val WEEK_MODE_ODD: String = "ODD_WEEK"
        private const val WEEK_MODE_EVEN: String = "EVEN_WEEK"

        private const val FIRST_WEEK: Int = 1
        private const val MIN_DAY_OF_WEEK: Int = 1
        private const val DAYS_PER_WEEK: Int = 7
        private const val MINUTES_PER_HOUR: Int = 60
        private const val MILLIS_PER_MINUTE: Long = 60L * 1000L
        private const val CLOCK_PART_COUNT: Int = 2
        private const val WEEK_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
        private val CHINA_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

        const val ACTION_OPEN_SCHEDULE_TARGET: String = "com.susking.ephone_s.action.OPEN_SCHEDULE_TARGET"
        const val EXTRA_TARGET_TYPE: String = "schedule_target_type"
        const val EXTRA_TARGET_ID: String = "schedule_target_id"

        fun schedule(context: Context): Unit {
            val request = PeriodicWorkRequestBuilder<ScheduleReminderWorker>(REPEAT_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runNow(context: Context): Unit {
            val request = OneTimeWorkRequestBuilder<ScheduleReminderWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context): Unit {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
