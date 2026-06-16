package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.health.SleepSessionDetail
import com.susking.ephone_s.aidata.data.local.entity.HealthDailyRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 健康数据仓库契约。
 *
 * 负责协调 Health Connect 读取与本地 Room 落库，向上层（健康页 UI、后续 AI 关怀）
 * 暴露响应式的每日健康汇总。Health Connect 属本机 IPC，不经 brain。
 */
interface HealthRepository {

    /** Health Connect 在本设备是否可用。 */
    fun isHealthConnectAvailable(): Boolean

    /** 读取所需的健康权限集合（供 UI 发起授权请求）。 */
    val requiredPermissions: Set<String>

    /** 是否已授予全部所需权限。 */
    suspend fun hasAllPermissions(): Boolean

    /** 获取已授予的权限集合。 */
    suspend fun getGrantedPermissions(): Set<String>

    /**
     * 从 Health Connect 同步最近 [days] 天数据到本地库。
     * 返回是否成功（无权限或 SDK 不可用返回 false）。
     */
    suspend fun syncRecentDays(days: Int = DEFAULT_SYNC_DAYS): Boolean

    /** 响应式观察最近 [limitDays] 天的本地健康记录（按日期倒序）。 */
    fun observeRecentDays(limitDays: Int = DEFAULT_SYNC_DAYS): Flow<List<HealthDailyRecordEntity>>

    /**
     * 读取指定日期的睡眠会话详情（用于睡眠详情页）。
     *
     * 返回该日所有睡眠会话及其分期段列表，实时从 Health Connect 读取。
     */
    suspend fun readSleepSessionsForDay(date: String): List<SleepSessionDetail>

    companion object {
        const val DEFAULT_SYNC_DAYS: Int = 7
    }
}
