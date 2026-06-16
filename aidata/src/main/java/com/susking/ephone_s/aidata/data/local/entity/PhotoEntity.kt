package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 照片数据库实体 (包含 Room 注解)
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["albumId"])]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val albumId: Long,
    val dateAdded: Long,
    val isFavorited: Boolean
)