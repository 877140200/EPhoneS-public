package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 相册数据库实体 (包含 Room 注解)
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val coverImagePath: String?,
    @ColumnInfo(defaultValue = "0")
    val photoCount: Int
)