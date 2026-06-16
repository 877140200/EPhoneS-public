package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "world_book_entries",
    foreignKeys = [ForeignKey(
        entity = WorldBookEntity::class,
        parentColumns = ["worldBookId"],
        childColumns = ["worldBookId"],
        onDelete = ForeignKey.CASCADE // 当世界书被删除时，其下的所有条目也一并删除
    )],
    indices = [Index(value = ["worldBookId"])] // 为外键创建索引以提高查询性能
)
data class WorldBookEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val entryId: Long = 0,          // 内容条目的唯一ID
    val worldBookId: Long,          // 外键，指向所属的世界书
    var name: String,               // 条目名称
    var content: String,            // 条目内容 (Prompt)
    var isEnabled: Boolean = true,  // 条目是否启用
    var isSystemEntry: Boolean = false, // 是否是系统世界书条目，系统条目不可删除，名称不可修改
    var lampColor: String = "green", // 灯色状态 (默认 "green")
    var displayOrder: Int           // 用于排序的顺序号
)