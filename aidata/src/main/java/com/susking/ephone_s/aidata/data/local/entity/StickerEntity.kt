package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 代表一个表情的实体。
 * @property id 表情的唯一标识符，自动生成。
 * @property name 表情的名称或含义。
 * @property url 表情的图片链接。
 * @property categoryId 表情所属分类的ID，可以为空（未分类）。
 * @property displayOrder 表情的显示顺序。
 */
@Entity(
    tableName = "stickers",
    foreignKeys = [
        ForeignKey(
            entity = StickerCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )    ],
    indices = [Index("categoryId")]
)
data class StickerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val url: String,
    val categoryId: Int?,
    val displayOrder: Int = 0
)