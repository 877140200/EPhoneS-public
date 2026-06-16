package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * Room TypeConverter,用于在BigDecimal和String之间进行转换。
 * Room本身不支持BigDecimal,因此需要这个转换器来将其存储为数据库兼容的类型。
 */
class BigDecimalConverter {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toPlainString()
    }

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return value?.let { BigDecimal(it) }
    }
}