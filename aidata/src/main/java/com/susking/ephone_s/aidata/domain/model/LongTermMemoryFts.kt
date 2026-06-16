package com.susking.ephone_s.aidata.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * 用于全文搜索的长期记忆虚拟表。
 *
 * @property memoryText 记忆正文，将被索引以进行全文搜索。
 */
@Entity(tableName = "long_term_memories_fts")
@Fts4(contentEntity = LongTermMemory::class)
data class LongTermMemoryFts(
    @ColumnInfo(name = "memoryText")
    val memoryText: String
)
