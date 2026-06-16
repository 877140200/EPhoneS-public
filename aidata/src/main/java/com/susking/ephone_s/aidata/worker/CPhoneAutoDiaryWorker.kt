package com.susking.ephone_s.aidata.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.service.MemorySummarizationService
import com.susking.ephone_s.aidata.domain.use_case.GenerateCPhoneDataUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * CPhone 联系人级自动日记后台任务。
 *
 * 重要规则：
 * - 自动日记开关按联系人保存，新联系人默认关闭。
 * - 每天 05:00 触发时，只生成刚结束的上一段 05:00 到 05:00 窗口。
 * - 每日日记和每日分层摘要共用一次模型请求。
 * - 每周、每月、每年自动任务生成分层摘要，不生成周期日记。
 * - 失败后只记录日志，不向 WorkManager 请求重试。
 */
@HiltWorker
class CPhoneAutoDiaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val generateCPhoneDataUseCase: GenerateCPhoneDataUseCase,
    private val memorySummarizationService: MemorySummarizationService
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val contactId: String = inputData.getString(KEY_CONTACT_ID).orEmpty()
        if (contactId.isBlank()) {
            Log.w(TAG, "自动日记任务缺少联系人ID，已跳过")
            return@withContext Result.success()
        }

        try {
            if (!isAutoDiaryEnabled(applicationContext, contactId)) {
                cancel(applicationContext, contactId)
                Log.i(TAG, "联系人自动日记未启用，任务已取消: contactId=$contactId")
                return@withContext Result.success()
            }

            val preferences: SharedPreferences = getPreferences(applicationContext)
            val nowTimestamp: Long = System.currentTimeMillis()
            executeContactAutomaticTasks(contactId, nowTimestamp, preferences)
            schedule(applicationContext, contactId)
            Log.i(TAG, "联系人自动日记任务完成: contactId=$contactId")
            Result.success()
        } catch (exception: Exception) {
            Log.e(TAG, "联系人自动日记任务失败且不重试: contactId=$contactId", exception)
            if (isAutoDiaryEnabled(applicationContext, contactId)) {
                schedule(applicationContext, contactId)
            }
            Result.success()
        }
    }

    private suspend fun executeContactAutomaticTasks(
        contactId: String,
        nowTimestamp: Long,
        preferences: SharedPreferences
    ): Unit {
        val dailyWindow: DiaryTimeWindow = buildPreviousDailyWindow(nowTimestamp)
        executeDailyDiaryGeneration(contactId, dailyWindow, preferences)

        val triggerTypes: List<DiaryScheduleType> = getDueScheduleTypes(nowTimestamp)
        triggerTypes.forEach { scheduleType: DiaryScheduleType ->
            executePeriodSummaryGeneration(contactId, scheduleType, nowTimestamp, preferences)
        }
    }

    private suspend fun executeDailyDiaryGeneration(
        contactId: String,
        dailyWindow: DiaryTimeWindow,
        preferences: SharedPreferences
    ): Unit {
        val key: String = buildLastRunKey(contactId, DiaryScheduleType.DAILY)
        if (preferences.getString(key, null) == dailyWindow.windowKey) return

        val result: kotlin.Result<MemorySummary?> = generateCPhoneDataUseCase.executeAutomaticDailyDiary(
            contactId = contactId,
            windowStart = dailyWindow.startTimestamp,
            windowEnd = dailyWindow.endTimestamp,
            windowLabel = dailyWindow.displayLabel
        )
        result.onSuccess { savedSummary: MemorySummary? ->
            preferences.edit().putString(key, dailyWindow.windowKey).apply()
            // 自动每日摘要落库后必须补建向量索引，否则无法被语义召回。
            if (savedSummary != null) {
                runCatching { memorySummarizationService.vectorizeStoredSummary(savedSummary) }
                    .onFailure { error: Throwable ->
                        Log.e(TAG, "每日自动摘要向量化失败: contactId=$contactId, summaryId=${savedSummary.id}", error)
                    }
            }
            Log.i(TAG, "每日自动日记和摘要生成成功: contactId=$contactId, window=${dailyWindow.displayLabel}")
        }.onFailure { error: Throwable ->
            Log.e(TAG, "每日自动日记和摘要生成失败: contactId=$contactId, window=${dailyWindow.displayLabel}", error)
        }
    }

    private suspend fun executePeriodSummaryGeneration(
        contactId: String,
        scheduleType: DiaryScheduleType,
        nowTimestamp: Long,
        preferences: SharedPreferences
    ): Unit {
        val summaryLevel: SummaryLevel = scheduleType.toSummaryLevel() ?: return
        val summaryWindow: DiaryTimeWindow = buildPreviousPeriodWindow(scheduleType, nowTimestamp)
        val key: String = buildLastRunKey(contactId, scheduleType)
        if (preferences.getString(key, null) == summaryWindow.windowKey) return

        runCatching {
            memorySummarizationService.generateSummaryWindow(
                contactId = contactId,
                level = summaryLevel,
                windowStart = summaryWindow.startTimestamp,
                windowEnd = summaryWindow.endTimestamp
            )
        }.onSuccess { summary: MemorySummary? ->
            if (summary != null) {
                preferences.edit().putString(key, summaryWindow.windowKey).apply()
                Log.i(TAG, "周期分层摘要生成成功: contactId=$contactId, type=${scheduleType.displayName}, window=${summaryWindow.displayLabel}")
            } else {
                Log.i(TAG, "周期分层摘要源数据不足，已跳过: contactId=$contactId, type=${scheduleType.displayName}, window=${summaryWindow.displayLabel}")
            }
        }.onFailure { error: Throwable ->
            Log.e(TAG, "周期分层摘要生成失败: contactId=$contactId, type=${scheduleType.displayName}, window=${summaryWindow.displayLabel}", error)
        }
    }

    private fun getDueScheduleTypes(timestamp: Long): List<DiaryScheduleType> {
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val result: MutableList<DiaryScheduleType> = mutableListOf()
        if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) result.add(DiaryScheduleType.WEEKLY)
        if (calendar.get(Calendar.DAY_OF_MONTH) == FIRST_DAY_OF_PERIOD) result.add(DiaryScheduleType.MONTHLY)
        if (calendar.get(Calendar.MONTH) == Calendar.JANUARY && calendar.get(Calendar.DAY_OF_MONTH) == FIRST_DAY_OF_PERIOD) {
            result.add(DiaryScheduleType.YEARLY)
        }
        return result
    }

    private fun buildLastRunKey(contactId: String, scheduleType: DiaryScheduleType): String {
        return "${KEY_PREFIX_LAST_RUN}${contactId}_${scheduleType.name}"
    }

    private fun buildPreviousPeriodWindow(scheduleType: DiaryScheduleType, timestamp: Long): DiaryTimeWindow {
        return when (scheduleType) {
            DiaryScheduleType.DAILY -> buildPreviousDailyWindow(timestamp)
            DiaryScheduleType.WEEKLY -> buildPreviousWeeklyWindow(timestamp)
            DiaryScheduleType.MONTHLY -> buildPreviousMonthlyWindow(timestamp)
            DiaryScheduleType.YEARLY -> buildPreviousYearlyWindow(timestamp)
        }
    }

    private enum class DiaryScheduleType(val displayName: String) {
        DAILY("每日"),
        WEEKLY("每周"),
        MONTHLY("每月"),
        YEARLY("每年");

        fun toSummaryLevel(): SummaryLevel? {
            return when (this) {
                DAILY -> null
                WEEKLY -> SummaryLevel.WEEKLY
                MONTHLY -> SummaryLevel.MONTHLY
                YEARLY -> SummaryLevel.YEARLY
            }
        }
    }

    private data class DiaryTimeWindow(
        val startTimestamp: Long,
        val endTimestamp: Long,
        val windowKey: String,
        val displayLabel: String
    )

    companion object {
        private const val TAG: String = "CPhoneAutoDiaryWorker"
        private const val WORK_NAME_PREFIX: String = "CPhoneAutoDiaryWork_"
        private const val PREFERENCES_NAME: String = "cphone_auto_diary_schedule"
        private const val KEY_CONTACT_ID: String = "contact_id"
        private const val KEY_PREFIX_ENABLED: String = "enabled_contact_"
        private const val KEY_PREFIX_LAST_RUN: String = "last_run_"
        private const val FIRST_DAY_OF_PERIOD: Int = 1
        private const val TARGET_HOUR: Int = 5
        private const val TARGET_MINUTE: Int = 0
        private const val TARGET_SECOND: Int = 0

        private val mutableAutoDiarySettingVersion: MutableStateFlow<Int> = MutableStateFlow(0)
        val autoDiarySettingVersion: StateFlow<Int> = mutableAutoDiarySettingVersion.asStateFlow()

        fun isAutoDiaryEnabled(context: Context, contactId: String): Boolean {
            return getPreferences(context).getBoolean(buildEnabledKey(contactId), false)
        }

        fun updateAutoDiaryEnabled(context: Context, contactId: String, isEnabled: Boolean): Unit {
            getPreferences(context).edit()
                .putBoolean(buildEnabledKey(contactId), isEnabled)
                .apply()
            notifyAutoDiarySettingChanged()
            if (isEnabled) {
                schedule(context, contactId)
                runNow(context, contactId)
            } else {
                cancel(context, contactId)
            }
        }

        fun schedule(context: Context, contactId: String): Unit {
            if (!isAutoDiaryEnabled(context, contactId)) {
                cancel(context, contactId)
                Log.d(TAG, "联系人自动日记未启用，不启动后台任务: contactId=$contactId")
                return
            }
            val constraints: Constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<CPhoneAutoDiaryWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_CONTACT_ID to contactId))
                .setInitialDelay(calculateDelayToNextFiveOClock(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(buildWorkName(contactId), ExistingWorkPolicy.REPLACE, request)
        }

        fun scheduleEnabledContactsAndCatchUp(context: Context, profiles: List<PersonProfile>): Unit {
            profiles.forEach { profile: PersonProfile ->
                if (isAutoDiaryEnabled(context, profile.id)) {
                    schedule(context, profile.id)
                    runNow(context, profile.id)
                }
            }
        }

        suspend fun scheduleEnabledContactsAndCatchUp(context: Context, repository: PersonProfileRepository): Unit {
            scheduleEnabledContactsAndCatchUp(context, repository.getPersonProfiles())
        }

        fun runNow(context: Context, contactId: String): Unit {
            val constraints: Constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<CPhoneAutoDiaryWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_CONTACT_ID to contactId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("${buildWorkName(contactId)}_catch_up", ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, contactId: String): Unit {
            WorkManager.getInstance(context).cancelUniqueWork(buildWorkName(contactId))
            WorkManager.getInstance(context).cancelUniqueWork("${buildWorkName(contactId)}_catch_up")
        }

        private fun notifyAutoDiarySettingChanged(): Unit {
            mutableAutoDiarySettingVersion.value = mutableAutoDiarySettingVersion.value + 1
        }

        private fun getPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }

        private fun buildEnabledKey(contactId: String): String {
            return "$KEY_PREFIX_ENABLED$contactId"
        }

        private fun buildWorkName(contactId: String): String {
            return "$WORK_NAME_PREFIX$contactId"
        }

        private fun buildPreviousDailyWindow(timestamp: Long): DiaryTimeWindow {
            val endCalendar: Calendar = Calendar.getInstance()
            endCalendar.timeInMillis = timestamp
            endCalendar.set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            endCalendar.set(Calendar.MINUTE, TARGET_MINUTE)
            endCalendar.set(Calendar.SECOND, TARGET_SECOND)
            endCalendar.set(Calendar.MILLISECOND, 0)
            if (timestamp < endCalendar.timeInMillis) {
                endCalendar.add(Calendar.DAY_OF_YEAR, -1)
            }
            val startCalendar: Calendar = endCalendar.clone() as Calendar
            startCalendar.add(Calendar.DAY_OF_YEAR, -1)
            val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            return DiaryTimeWindow(
                startTimestamp = startCalendar.timeInMillis,
                endTimestamp = endCalendar.timeInMillis,
                windowKey = "${startCalendar.timeInMillis}_${endCalendar.timeInMillis}",
                displayLabel = "${formatter.format(startCalendar.time)} 至 ${formatter.format(endCalendar.time)}"
            )
        }

        private fun buildPreviousWeeklyWindow(timestamp: Long): DiaryTimeWindow {
            val endCalendar: Calendar = buildPeriodEndCalendar(timestamp)
            endCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            if (timestamp < endCalendar.timeInMillis) {
                endCalendar.add(Calendar.WEEK_OF_YEAR, -1)
            }
            val startCalendar: Calendar = endCalendar.clone() as Calendar
            startCalendar.add(Calendar.WEEK_OF_YEAR, -1)
            return buildWindow(startCalendar, endCalendar)
        }

        private fun buildPreviousMonthlyWindow(timestamp: Long): DiaryTimeWindow {
            val endCalendar: Calendar = buildPeriodEndCalendar(timestamp)
            endCalendar.set(Calendar.DAY_OF_MONTH, FIRST_DAY_OF_PERIOD)
            if (timestamp < endCalendar.timeInMillis) {
                endCalendar.add(Calendar.MONTH, -1)
            }
            val startCalendar: Calendar = endCalendar.clone() as Calendar
            startCalendar.add(Calendar.MONTH, -1)
            return buildWindow(startCalendar, endCalendar)
        }

        private fun buildPreviousYearlyWindow(timestamp: Long): DiaryTimeWindow {
            val endCalendar: Calendar = buildPeriodEndCalendar(timestamp)
            endCalendar.set(Calendar.MONTH, Calendar.JANUARY)
            endCalendar.set(Calendar.DAY_OF_MONTH, FIRST_DAY_OF_PERIOD)
            if (timestamp < endCalendar.timeInMillis) {
                endCalendar.add(Calendar.YEAR, -1)
            }
            val startCalendar: Calendar = endCalendar.clone() as Calendar
            startCalendar.add(Calendar.YEAR, -1)
            return buildWindow(startCalendar, endCalendar)
        }

        private fun buildPeriodEndCalendar(timestamp: Long): Calendar {
            val calendar: Calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            calendar.set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            calendar.set(Calendar.MINUTE, TARGET_MINUTE)
            calendar.set(Calendar.SECOND, TARGET_SECOND)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar
        }

        private fun buildWindow(startCalendar: Calendar, endCalendar: Calendar): DiaryTimeWindow {
            val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            return DiaryTimeWindow(
                startTimestamp = startCalendar.timeInMillis,
                endTimestamp = endCalendar.timeInMillis,
                windowKey = "${startCalendar.timeInMillis}_${endCalendar.timeInMillis}",
                displayLabel = "${formatter.format(startCalendar.time)} 至 ${formatter.format(endCalendar.time)}"
            )
        }

        private fun calculateDelayToNextFiveOClock(): Long {
            val now: Calendar = Calendar.getInstance()
            val nextRun: Calendar = Calendar.getInstance()
            nextRun.set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            nextRun.set(Calendar.MINUTE, TARGET_MINUTE)
            nextRun.set(Calendar.SECOND, TARGET_SECOND)
            nextRun.set(Calendar.MILLISECOND, 0)
            if (!nextRun.after(now)) nextRun.add(Calendar.DAY_OF_YEAR, 1)
            return nextRun.timeInMillis - now.timeInMillis
        }
    }
}
