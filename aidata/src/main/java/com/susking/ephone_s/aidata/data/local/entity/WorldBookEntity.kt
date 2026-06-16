package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "world_books")
data class WorldBookEntity(
    @PrimaryKey(autoGenerate = true)
    val worldBookId: Long = 0,    // 世界书的唯一标识符
    val title: String,            // 世界书的标题
    val category: String,         // 世界书的分类
    val isSystem: Boolean = false, // 是否是系统世界书，系统世界书不能被删除，且始终置顶
    val order: Int = 0,           // 世界书的排序，用于手动拖拽排序
    val createdAt: Long,          // 世界书的创建时间戳 (Unix毫秒)
    val updatedAt: Long           // 世界书的最后更新时间戳 (Unix毫秒)
)