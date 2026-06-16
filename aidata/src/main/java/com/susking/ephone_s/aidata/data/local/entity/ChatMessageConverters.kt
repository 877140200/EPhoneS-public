package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.domain.model.QuotedMessage

/**
 * Room 类型转换器，用于在数据库中存储复杂类型。
 * 这个转换器专门处理 ChatMessageEntity 中的 List<String>。
 */
class ChatMessageConverters {
    private val gson = Gson()

    /**
     * 将 String 列表转换为 JSON 字符串以便存入数据库。
     * @param value 要转换的字符串列表。
     * @return 转换后的 JSON 字符串。
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        // 如果列表为空，则直接返回一个空列表的JSON表示，而不是null，以保证数据一致性
        return gson.toJson(value ?: emptyList<String>())
    }

    /**
     * 将数据库中的 JSON 字符串转换回 String 列表。
     * @param value 数据库中的 JSON 字符串。
     * @return 转换后的 String 列表。
     */
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        // 如果数据库中的值为 null 或为空，返回一个空的列表
        if (value.isNullOrBlank()) {
            return emptyList()
        }
        val listType = object : TypeToken<List<String>>() {}.type
        // 使用 try-catch 增加健壮性，防止无效的 JSON 导致崩溃
        return try {
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromQuotedMessage(quotedMessage: QuotedMessage?): String? {
        return gson.toJson(quotedMessage)
    }

    @TypeConverter
    fun toQuotedMessage(json: String?): QuotedMessage? {
        return gson.fromJson(json, QuotedMessage::class.java)
    }
}