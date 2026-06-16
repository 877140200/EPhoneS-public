package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 通用回忆实体
 * 用于存储AI创建的回忆记录
 */
@Entity(tableName = "general_memories")
data class GeneralMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: String, // 关联的联系人ID
    val description: String, // 回忆描述
    val createdDate: Long = System.currentTimeMillis() // 创建时间戳
)