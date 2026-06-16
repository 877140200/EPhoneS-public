package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 工作状态实体
 * 用于记录用户的上班打卡状态
 */
@Entity(tableName = "work_status")
data class WorkStatusEntity(
    @PrimaryKey val userId: String = "user_main",
    val isWorking: Boolean = false,
    val workStartTime: Long = 0,
    val workEndTime: Long = 0,
    val lastWorkDate: String = "" // 格式: yyyy-MM-dd
)