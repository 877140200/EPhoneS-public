package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日健康数据汇总实体。
 *
 * 数据来源：系统 Health Connect（由 Health Sync 从华为运动健康灌入）。
 * 设计取舍：Health Connect 自身保留 30 天+原始历史，本表只按「天」存汇总值，
 * 用于趋势展示、跨设备备份与（后续）AI 健康关怀提示词注入，不重复存原始采样点。
 *
 * 主键为日期字符串 [date]（格式 yyyy-MM-dd，本地时区），保证同一天 upsert 覆盖、天然去重。
 * 所有数值列允许为 0/缺省，因为某些类目当天可能无数据（如未佩戴手表）。
 */
@Entity(tableName = "health_daily_records")
data class HealthDailyRecordEntity(
    @PrimaryKey
    val date: String,

    // === 活动类 ===
    // 当日总步数。
    @ColumnInfo(defaultValue = "0")
    val steps: Long = 0L,
    // 当日总距离（米）。
    @ColumnInfo(defaultValue = "0")
    val distanceMeters: Double = 0.0,
    // 当日活动消耗（千卡）。
    @ColumnInfo(defaultValue = "0")
    val activeCaloriesKcal: Double = 0.0,

    // === 睡眠类 ===
    // 当日睡眠总时长（分钟，多段会话累加）。
    @ColumnInfo(defaultValue = "0")
    val sleepTotalMinutes: Long = 0L,
    // 深睡时长（分钟），无分期数据时为 0。
    @ColumnInfo(defaultValue = "0")
    val sleepDeepMinutes: Long = 0L,
    // 浅睡时长（分钟），无分期数据时为 0。
    @ColumnInfo(defaultValue = "0")
    val sleepLightMinutes: Long = 0L,
    // REM 睡眠时长（分钟），无分期数据时为 0。
    @ColumnInfo(defaultValue = "0")
    val sleepRemMinutes: Long = 0L,
    // 当日睡眠会话段数（午睡、夜间等分别计）。
    @ColumnInfo(defaultValue = "0")
    val sleepSessionCount: Int = 0,
    // 当日最早睡眠开始时间（毫秒时间戳，UTC），用于卡片显示起止时间段，无睡眠为 null。
    val sleepStartTime: Long? = null,
    // 当日最晚睡眠结束时间（毫秒时间戳，UTC），无睡眠为 null。
    val sleepEndTime: Long? = null,

    // === 心率类 ===
    // 当日心率平均值（bpm），无数据为 null。
    val heartRateAvg: Int? = null,
    // 当日心率峰值（bpm），无数据为 null。
    val heartRateMax: Int? = null,
    // 当日心率最低值（bpm），无数据为 null。
    val heartRateMin: Int? = null,
    // 当日静息心率（bpm），无数据为 null。
    val restingHeartRate: Int? = null,

    // === 元数据 ===
    // 本条记录最后一次从 Health Connect 同步的时间戳（毫秒）。
    @ColumnInfo(defaultValue = "0")
    val lastSyncTime: Long = 0L,
    // 本次同步获取到的数据类型列表（中文逗号分隔，如"步数、睡眠、心率"），用于主页显示同步内容。
    @ColumnInfo(defaultValue = "''")
    val syncedDataTypes: String = "",
)
