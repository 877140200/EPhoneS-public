package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heartbeats")
data class HeartbeatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: String,
    val content: String,
    val timestamp: Long,
    val isFavorited: Boolean = false,
    val aiTurnId: String? = null, // 新增：用于版本切换
    val sourceMessageId: String? = null // 新增：用于关联原始消息的ID
)