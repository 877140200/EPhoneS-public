package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// FeedComment 将作为 FeedEntity 的一部分，所以放在同一个文件下更清晰
data class FeedComment(
    val id: Long = System.currentTimeMillis(), // 使用时间戳作为唯一ID
    val commenterId: String, // 评论者（AI）的 contactId
    val commenterName: String, // 评论者（AI）的 realName
    val commentText: String? = null, // 文本评论，设为可选
    val stickerUrl: String? = null, // 表情图片的URL，设为可选
    val stickerMeaning: String? = null, // 表情的含义，设为可选
    val timestamp: Long
)

class FeedTypeConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromCommentList(comments: List<FeedComment>?): String? {
        return gson.toJson(comments)
    }

    @TypeConverter
    fun toCommentList(commentsString: String?): List<FeedComment>? {
        if (commentsString == null) return null
        val listType = object : TypeToken<List<FeedComment>>() {}.type
        return gson.fromJson(commentsString, listType)
    }

    @TypeConverter
    fun fromStringList(likes: List<String>?): String? {
        return gson.toJson(likes)
    }

    @TypeConverter
    fun toStringList(likesString: String?): List<String>? {
        if (likesString == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(likesString, listType)
    }
}