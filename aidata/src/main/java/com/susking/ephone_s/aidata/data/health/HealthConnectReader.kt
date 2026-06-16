package com.susking.ephone_s.aidata.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.susking.ephone_s.aidata.data.local.entity.HealthDailyRecordEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Health Connect 读取器：从系统健康仓库读取原始记录并按「天」聚合成 [HealthDailyRecordEntity]。
 *
 * 注意：Health Connect 是本机系统 IPC（等同读本地数据库），不属于对外 API 网络请求，
 * 因此不经 brain 悬浮窗转发。读取逻辑无状态，所有结果由调用方（Repository）落库。
 */
class HealthConnectReader(
    private val context: Context,
) {

    /** 本读取器需要的 6 类读权限（步数/距离/活动消耗/睡眠/心率/静息心率）。 */
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    /** Health Connect 是否在本设备可用。 */
    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** 获取已授予的权限集合；SDK 不可用时返回空集。 */
    suspend fun getGrantedPermissions(): Set<String> {
        if (!isAvailable()) return emptySet()
        return HealthConnectClient.getOrCreate(context)
            .permissionController.getGrantedPermissions()
    }

    /** 是否已拿到全部所需权限。 */
    suspend fun hasAllPermissions(): Boolean =
        getGrantedPermissions().containsAll(requiredPermissions)

    /**
     * 读取最近 [days] 天的健康数据并按天聚合。
     * 返回按日期升序的每日汇总列表；无数据的天不产出条目。
     */
    suspend fun readRecentDays(days: Int): List<HealthDailyRecordEntity> {
        if (!isAvailable()) return emptyList()
        val client: HealthConnectClient = HealthConnectClient.getOrCreate(context)
        val zone: ZoneId = ZoneId.systemDefault()
        val now: Instant = Instant.now()
        val startInstant: Instant = LocalDate.now(zone)
            .minusDays((days - 1).toLong())
            .atStartOfDay(zone)
            .toInstant()
        val range: TimeRangeFilter = TimeRangeFilter.between(startInstant, now)
        val syncTime: Long = now.toEpochMilli()

        // 以日期字符串为键的可变聚合容器。
        val builders: MutableMap<String, DailyBuilder> = linkedMapOf()
        fun builderFor(instant: Instant): DailyBuilder {
            val date: String = LocalDate.ofInstant(instant, zone).toString()
            return builders.getOrPut(date) { DailyBuilder(date) }
        }

        // 步数
        client.readRecords(ReadRecordsRequest(StepsRecord::class, timeRangeFilter = range))
            .records.forEach { builderFor(it.startTime).steps += it.count }

        // 距离（米）
        client.readRecords(ReadRecordsRequest(DistanceRecord::class, timeRangeFilter = range))
            .records.forEach { builderFor(it.startTime).distanceMeters += it.distance.inMeters }

        // 活动消耗（千卡）
        client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRangeFilter = range))
            .records.forEach { builderFor(it.startTime).activeCaloriesKcal += it.energy.inKilocalories }

        // 睡眠（按入睡日归属，累加各段时长与分期）
        client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = range))
            .records.forEach { session ->
                val builder: DailyBuilder = builderFor(session.startTime)
                builder.sleepSessionCount += 1
                builder.sleepTotalMinutes += ChronoUnit.MINUTES.between(session.startTime, session.endTime)
                builder.addSleepSession(session.startTime, session.endTime)
                session.stages.forEach { stage ->
                    val stageMinutes: Long = ChronoUnit.MINUTES.between(stage.startTime, stage.endTime)
                    when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP ->
                            builder.sleepDeepMinutes += stageMinutes
                        SleepSessionRecord.STAGE_TYPE_LIGHT ->
                            builder.sleepLightMinutes += stageMinutes
                        SleepSessionRecord.STAGE_TYPE_REM ->
                            builder.sleepRemMinutes += stageMinutes
                        else -> Unit // AWAKE / UNKNOWN 等不计入分期统计
                    }
                }
            }

        // 心率（聚合每日均值/峰值/最低值）
        client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = range))
            .records.forEach { record ->
                record.samples.forEach { sample ->
                    builderFor(sample.time).addHeartRateSample(sample.beatsPerMinute.toInt())
                }
            }

        // 静息心率（取当日最后一条）
        client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, timeRangeFilter = range))
            .records.forEach { builderFor(it.time).restingHeartRate = it.beatsPerMinute.toInt() }

        return builders.values.map { it.build(syncTime) }.sortedBy { it.date }
    }

    /**
     * 读取指定日期的所有睡眠会话及其分期段详情（用于睡眠详情页）。
     *
     * 返回该日所有睡眠会话，每个会话包含起止时间与分期段列表（清醒/浅睡/深睡/REM，含时间段）。
     * 按入睡时间（startTime）归属到日期，返回按会话开始时间升序排列的列表。
     */
    suspend fun readSleepSessionsForDay(date: String): List<SleepSessionDetail> {
        if (!isAvailable()) return emptyList()
        val client: HealthConnectClient = HealthConnectClient.getOrCreate(context)
        val zone: ZoneId = ZoneId.systemDefault()

        // 解析日期并构建该天的时间范围（00:00 到次日 00:00）
        val targetDate: LocalDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return emptyList()
        val startOfDay: Instant = targetDate.atStartOfDay(zone).toInstant()
        val endOfDay: Instant = targetDate.plusDays(1).atStartOfDay(zone).toInstant()
        val range: TimeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)

        // 读取该日所有睡眠会话
        val sessions = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = range))
            .records
            .sortedBy { it.startTime }

        return sessions.map { session ->
            val stages = session.stages.map { stage ->
                SleepStageDetail(
                    type = formatStageType(stage.stage),
                    startTime = stage.startTime.toEpochMilli(),
                    endTime = stage.endTime.toEpochMilli(),
                    durationMinutes = ChronoUnit.MINUTES.between(stage.startTime, stage.endTime)
                )
            }
            SleepSessionDetail(
                startTime = session.startTime.toEpochMilli(),
                endTime = session.endTime.toEpochMilli(),
                totalMinutes = ChronoUnit.MINUTES.between(session.startTime, session.endTime),
                stages = stages
            )
        }
    }

    /** 将 Health Connect 的睡眠分期类型转为中文。 */
    private fun formatStageType(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> "清醒"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "浅度睡眠"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "深度睡眠"
        SleepSessionRecord.STAGE_TYPE_REM -> "快速动眼睡眠"
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "睡眠（未分期）"
        else -> "未知"
    }


    /**
     * 单日聚合中间态。心率以累加和 + 计数算均值，避免一次性持有全部采样点。
     * 睡眠记录最早开始与最晚结束时间，用于卡片显示时间段。
     */
    private class DailyBuilder(val date: String) {
        var steps: Long = 0L
        var distanceMeters: Double = 0.0
        var activeCaloriesKcal: Double = 0.0
        var sleepTotalMinutes: Long = 0L
        var sleepDeepMinutes: Long = 0L
        var sleepLightMinutes: Long = 0L
        var sleepRemMinutes: Long = 0L
        var sleepSessionCount: Int = 0
        var sleepStartTime: Instant? = null
        var sleepEndTime: Instant? = null
        var restingHeartRate: Int? = null

        private var heartRateSum: Long = 0L
        private var heartRateCount: Int = 0
        private var heartRateMax: Int? = null
        private var heartRateMin: Int? = null

        fun addHeartRateSample(bpm: Int) {
            heartRateSum += bpm
            heartRateCount += 1
            heartRateMax = maxOf(heartRateMax ?: bpm, bpm)
            heartRateMin = minOf(heartRateMin ?: bpm, bpm)
        }

        fun addSleepSession(start: Instant, end: Instant) {
            sleepStartTime = if (sleepStartTime == null) start else minOf(sleepStartTime!!, start)
            sleepEndTime = if (sleepEndTime == null) end else maxOf(sleepEndTime!!, end)
        }

        fun build(syncTime: Long): HealthDailyRecordEntity {
            // 根据实际有数据的项生成同步数据类型文本（用于主页显示同步内容）。
            val syncedTypes: List<String> = buildList {
                if (steps > 0) add("步数")
                if (distanceMeters > 0) add("距离")
                if (activeCaloriesKcal > 0) add("卡路里")
                if (sleepTotalMinutes > 0) add("睡眠")
                if (heartRateCount > 0) add("心率")
                if (restingHeartRate != null) add("静息心率")
            }

            return HealthDailyRecordEntity(
                date = date,
                steps = steps,
                distanceMeters = distanceMeters,
                activeCaloriesKcal = activeCaloriesKcal,
                sleepTotalMinutes = sleepTotalMinutes,
                sleepDeepMinutes = sleepDeepMinutes,
                sleepLightMinutes = sleepLightMinutes,
                sleepRemMinutes = sleepRemMinutes,
                sleepSessionCount = sleepSessionCount,
                sleepStartTime = sleepStartTime?.toEpochMilli(),
                sleepEndTime = sleepEndTime?.toEpochMilli(),
                heartRateAvg = if (heartRateCount > 0) (heartRateSum / heartRateCount).toInt() else null,
                heartRateMax = heartRateMax,
                heartRateMin = heartRateMin,
                restingHeartRate = restingHeartRate,
                lastSyncTime = syncTime,
                syncedDataTypes = syncedTypes.joinToString("、")
            )
        }
    }
}

/**
 * 单个睡眠会话详情（包含起止时间与分期段列表）。
 */
data class SleepSessionDetail(
    val startTime: Long,       // 会话开始时间（毫秒时间戳）
    val endTime: Long,         // 会话结束时间
    val totalMinutes: Long,    // 总时长（分钟）
    val stages: List<SleepStageDetail>  // 分期段列表
)

/**
 * 单个睡眠分期段详情。
 */
data class SleepStageDetail(
    val type: String,          // 分期类型中文名（"清醒"/"浅度睡眠"/"深度睡眠"/"快速动眼睡眠"）
    val startTime: Long,       // 分期开始时间（毫秒时间戳）
    val endTime: Long,         // 分期结束时间
    val durationMinutes: Long  // 分期时长（分钟）
)

