package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.susking.ephone_s.aidata.data.local.entity.HealthDailyRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 每日健康数据访问对象。
 *
 * 提供按天 upsert（主键为日期，冲突即覆盖）、响应式观察近 N 天、一次性全量导出三类操作。
 */
@Dao
interface HealthDailyRecordDao {

    /**
     * 插入或覆盖一天的健康汇总（主键 date 冲突时替换）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: HealthDailyRecordEntity)

    /**
     * 批量插入或覆盖（一次同步多天时用）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<HealthDailyRecordEntity>)

    /**
     * 响应式观察最近若干天的记录（按日期倒序），供健康页 UI 渲染卡片。
     */
    @Query("SELECT * FROM health_daily_records ORDER BY date DESC LIMIT :limitDays")
    fun observeRecentDays(limitDays: Int): Flow<List<HealthDailyRecordEntity>>

    /**
     * 取指定日期的记录（一次性），无则返回 null。
     */
    @Query("SELECT * FROM health_daily_records WHERE date = :date")
    suspend fun getByDate(date: String): HealthDailyRecordEntity?

    /**
     * 一次性取全部记录（用于导出，非响应式）。命名沿用项目导出查询惯例。
     */
    @Query("SELECT * FROM health_daily_records ORDER BY date DESC")
    suspend fun getAllHealthDailyRecordsList(): List<HealthDailyRecordEntity>

    /**
     * 清空全部记录（导入「覆盖」模式时用）。
     */
    @Query("DELETE FROM health_daily_records")
    suspend fun deleteAll()
}
