package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 提示词储存器 - 句子条目。
 *
 * 「锦囊」提示词储存器把用户收藏的提示词分为「句子」与「词语」两类，本表存句子类。
 * 用户在酒馆里随机一句完整提示词时从本表抽取。数据随完整备份导入导出（见 ExportData.promptSentences）。
 */
@Entity(tableName = "prompt_sentences")
data class PromptSentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // 句子内容
    val content: String,
    // 创建时间戳，用于列表按时间倒序展示
    val createdAt: Long = System.currentTimeMillis()
)
