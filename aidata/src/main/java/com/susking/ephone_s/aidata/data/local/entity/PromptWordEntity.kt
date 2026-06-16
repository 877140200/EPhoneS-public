package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 提示词储存器 - 词语条目。
 *
 * 与 [PromptSentenceEntity] 配套，存「词语」类提示词。
 * 用户在酒馆里点词语随机时，从本表按自定义数量范围随机抽取若干个。
 * 数据随完整备份导入导出（见 ExportData.promptWords）。
 */
@Entity(tableName = "prompt_words")
data class PromptWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // 词语内容
    val content: String,
    // 创建时间戳，用于列表按时间倒序展示
    val createdAt: Long = System.currentTimeMillis()
)
