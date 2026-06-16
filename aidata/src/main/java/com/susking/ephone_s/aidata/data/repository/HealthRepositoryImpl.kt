package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.health.HealthConnectReader
import com.susking.ephone_s.aidata.data.health.SleepSessionDetail
import com.susking.ephone_s.aidata.data.local.dao.HealthDailyRecordDao
import com.susking.ephone_s.aidata.data.local.entity.HealthDailyRecordEntity
import com.susking.ephone_s.aidata.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlin.math.abs

/**
 * [HealthRepository] 的实现：组合 Health Connect 读取器与本地 DAO。
 *
 * 同步流程：检查权限 → 读 Health Connect 近 N 天 → 按天 upsert 进 Room。
 * UI 始终从 Room 的 Flow 读，保证离线/无权限时仍能展示上次同步的缓存。
 */
class HealthRepositoryImpl(
    private val reader: HealthConnectReader,
    private val dao: HealthDailyRecordDao,
) : HealthRepository {

    override val requiredPermissions: Set<String>
        get() = reader.requiredPermissions

    override fun isHealthConnectAvailable(): Boolean = reader.isAvailable()

    override suspend fun hasAllPermissions(): Boolean = reader.hasAllPermissions()

    override suspend fun getGrantedPermissions(): Set<String> = reader.getGrantedPermissions()

    override suspend fun syncRecentDays(days: Int): Boolean {
        if (!reader.isAvailable() || !reader.hasAllPermissions()) return false
        val freshRecords: List<HealthDailyRecordEntity> = reader.readRecentDays(days)
        // 仅比对最近 N 天：历史健康数据通常已定型不再变化，无需逐天比对，省查询也避免误刷同步时间。
        val diffFloorDate: String = LocalDate.now()
            .minusDays((DIFF_WINDOW_DAYS - 1).toLong())
            .toString()
        // 逐天决定是否写库：库中缺失的天照常写入；窗口内有变化的天刷新同步时间并记录新内容；其余跳过。
        val changedRecords: List<HealthDailyRecordEntity> = freshRecords.mapNotNull { fresh ->
            val old: HealthDailyRecordEntity? = dao.getByDate(fresh.date)
            when {
                // 库中尚无该天（含历史天首次同步）：直接写入，保证近 N 天卡片完整。
                old == null -> fresh
                // 超出比对窗口的历史天：信任其不再变化，跳过，不重写也不刷新同步时间。
                fresh.date < diffFloorDate -> null
                // 比对窗口内：仅当数据确有变化时才写库，文案记录本次同步到的新内容。
                else -> {
                    val changedTypes: List<String> = computeChangedTypes(old, fresh)
                    if (changedTypes.isEmpty()) null
                    else fresh.copy(syncedDataTypes = changedTypes.joinToString("、"))
                }
            }
        }
        if (changedRecords.isNotEmpty()) {
            dao.upsertAll(changedRecords)
        }
        return true
    }

    override fun observeRecentDays(limitDays: Int): Flow<List<HealthDailyRecordEntity>> =
        dao.observeRecentDays(limitDays)

    override suspend fun readSleepSessionsForDay(date: String): List<SleepSessionDetail> =
        reader.readSleepSessionsForDay(date)

    /**
     * 比较同一天的旧记录与新读取数据，返回本次发生变化的数据类目（中文）。
     * 仅比对实际数据列，忽略 lastSyncTime / syncedDataTypes 两个元数据列。
     * 距离与卡路里为浮点累加值，用容差比较以吸收极小抖动，避免误判为变化。
     */
    private fun computeChangedTypes(
        old: HealthDailyRecordEntity,
        fresh: HealthDailyRecordEntity,
    ): List<String> = buildList {
        if (old.steps != fresh.steps) add("步数")
        if (!old.distanceMeters.roughlyEquals(fresh.distanceMeters)) add("距离")
        if (!old.activeCaloriesKcal.roughlyEquals(fresh.activeCaloriesKcal)) add("卡路里")
        val isSleepChanged: Boolean = old.sleepTotalMinutes != fresh.sleepTotalMinutes ||
            old.sleepDeepMinutes != fresh.sleepDeepMinutes ||
            old.sleepLightMinutes != fresh.sleepLightMinutes ||
            old.sleepRemMinutes != fresh.sleepRemMinutes ||
            old.sleepSessionCount != fresh.sleepSessionCount ||
            old.sleepStartTime != fresh.sleepStartTime ||
            old.sleepEndTime != fresh.sleepEndTime
        if (isSleepChanged) add("睡眠")
        val isHeartRateChanged: Boolean = old.heartRateAvg != fresh.heartRateAvg ||
            old.heartRateMax != fresh.heartRateMax ||
            old.heartRateMin != fresh.heartRateMin
        if (isHeartRateChanged) add("心率")
        if (old.restingHeartRate != fresh.restingHeartRate) add("静息心率")
    }

    /** 浮点值容差相等判断（用于距离/卡路里，吸收 Health Connect 重复读取的极小抖动）。 */
    private fun Double.roughlyEquals(other: Double): Boolean =
        abs(this - other) < FLOAT_EQUALITY_TOLERANCE

    private companion object {
        /** 差异比对窗口：仅比对最近 3 天，更早的历史数据视为不再变化。 */
        const val DIFF_WINDOW_DAYS: Int = 3
        /** 距离（米）/卡路里（千卡）浮点比较容差，低于此差值视为未变化。 */
        const val FLOAT_EQUALITY_TOLERANCE: Double = 0.5
    }
}
