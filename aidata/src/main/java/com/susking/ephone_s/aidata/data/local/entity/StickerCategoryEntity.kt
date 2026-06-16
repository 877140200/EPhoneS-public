package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 代表一个表情包分类的实体。
 * @property id 分类的唯一标识符，自动生成。
 * @property name 分类的名称。
 */
@Entity(tableName = "sticker_categories")
data class StickerCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)