package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 代表数据库中 "ai_response_versions" 表的实体类。
 * 用于存储 ChatMessageEntity 的 AI 响应版本，以避免单行数据过大。
 */
@Entity(
    tableName = "ai_response_versions",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatMessageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatMessageId"])]
)
data class AiResponseVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatMessageId: String,
    val versionContent: String
)