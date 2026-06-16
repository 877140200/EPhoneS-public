package com.susking.ephone_s.aidata.data.local.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.local.entity.VideoCallMessageEntity

/**
 * 视频通话消息列表类型转换器
 * 用于在Room数据库中存储和读取消息列表
 */
class VideoCallMessageListConverter {
    private val gson = Gson()
    
    @TypeConverter
    fun fromMessageList(messages: List<VideoCallMessageEntity>?): String {
        return gson.toJson(messages ?: emptyList<VideoCallMessageEntity>())
    }
    
    @TypeConverter
    fun toMessageList(messagesJson: String): List<VideoCallMessageEntity> {
        val type = object : TypeToken<List<VideoCallMessageEntity>>() {}.type
        return try {
            gson.fromJson(messagesJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}