package com.susking.ephone_s.cphone.domain.model

/**
 * 日记领域模型
 * 
 * @property id 日记唯一标识
 * @property title 日记标题
 * @property content 日记内容（支持Markdown格式）
 * @property timestamp 创建时间戳
 * @property isFavorite 是否收藏
 */
data class DiaryEntry(
    val id: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val isFavorite: Boolean = false
)