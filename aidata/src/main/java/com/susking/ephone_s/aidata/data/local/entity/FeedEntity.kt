package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "feeds")
@TypeConverters(FeedTypeConverters::class) // 应用类型转换器
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: String, // 作者的ID
    val authorName: String, // 作者的名字，用于显示
    val content: String,
    val timestamp: Long,
    val comments: List<FeedComment> = emptyList(),
    val likes: List<String> = emptyList(), // 存储点赞者的ID列表
    val originalFeedId: Int? = null, // 用于表示转发的原始动态ID
    val imageUrls: List<String> = emptyList(),
    val imagePrompts: List<String> = emptyList(), // NovelAI提示词列表,用于后续重新生成或编辑
    val imageDescriptions: List<String> = emptyList() // 图片描述列表(hiddenContent),用于备注或后续扩展
)