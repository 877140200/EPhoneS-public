package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 作息时间表
 * 用于模拟角色的睡眠规律,支持"不需要睡觉"的特殊人设
 * 
 * @property bedtime 就寝时间(小时,0-23)
 * @property wakeTime 起床时间(小时,0-23)
 * @property isNightOwl 是否是夜猫子
 */
@Parcelize
data class SleepSchedule(
    val bedtime: Int = 23,              // 就寝时间(小时,0-23)
    val wakeTime: Int = 7,              // 起床时间(小时,0-23)
    val isNightOwl: Boolean = false     // 是否是夜猫子
) : Parcelable