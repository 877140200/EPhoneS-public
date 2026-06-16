package com.susking.ephone_s.aidata.data.local.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room TypeConverter,用于在List<Long>和String(JSON)之间进行转换
 * 用于存储世界书ID列表等Long类型列表数据
 */
class LongListConverter {
    private val gson = Gson()
    
    /**
     * 将List<Long>转换为JSON字符串存储到数据库
     */
    @TypeConverter
    fun fromLongList(value: List<Long>?): String {
        if (value == null || value.isEmpty()) {
            return "[]"
        }
        return gson.toJson(value)
    }
    
    /**
     * 将JSON字符串从数据库读取并转换为List<Long>
     */
    @TypeConverter
    fun toLongList(value: String?): List<Long> {
        if (value.isNullOrBlank() || value == "[]") {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<Long>>() {}.type
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}