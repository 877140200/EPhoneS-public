package com.susking.ephone_s.aidata.data.local.converters

import androidx.room.TypeConverter
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus

/**
 * Room类型转换器，用于AiActivityStatus枚举与String之间的转换。
 */
class StatusConverter {

    @TypeConverter
    fun fromStatus(status: AiActivityStatus): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(value: String): AiActivityStatus {
        return AiActivityStatus.valueOf(value)
    }
}