package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // e.g., "new_comment", "new_feed"
    val feedId: Int?,
    val commentId: Long?,
    val actorId: String,
    val actorName: String,
    val timestamp: Long,
    val isRead: Boolean = false
)