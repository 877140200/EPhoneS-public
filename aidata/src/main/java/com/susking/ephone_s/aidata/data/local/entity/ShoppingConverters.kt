package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Shopping模块的Room TypeConverter
 * 
 * 用于转换购物系统中的复杂数据类型(款式列表、订单商品列表、收礼人列表)
 */
class ShoppingConverters {
    
    private val gson = Gson()
    
    /**
     * 将款式列表JSON字符串转换为List<ProductVariationData>
     */
    @TypeConverter
    fun fromVariationsJson(value: String?): List<ProductVariationData>? {
        if (value == null) return null
        val type = object : TypeToken<List<ProductVariationData>>() {}.type
        return gson.fromJson(value, type)
    }
    
    /**
     * 将List<ProductVariationData>转换为JSON字符串
     */
    @TypeConverter
    fun toVariationsJson(list: List<ProductVariationData>?): String? {
        if (list == null) return null
        return gson.toJson(list)
    }
    
    /**
     * 将订单商品列表JSON字符串转换为List<OrderItemData>
     */
    @TypeConverter
    fun fromOrderItemsJson(value: String?): List<OrderItemData>? {
        if (value == null) return null
        val type = object : TypeToken<List<OrderItemData>>() {}.type
        return gson.fromJson(value, type)
    }
    
    /**
     * 将List<OrderItemData>转换为JSON字符串
     */
    @TypeConverter
    fun toOrderItemsJson(list: List<OrderItemData>?): String? {
        if (list == null) return null
        return gson.toJson(list)
    }
    
    /**
     * 将收礼人列表JSON字符串转换为List<String>
     */
    @TypeConverter
    fun fromRecipientsJson(value: String?): List<String>? {
        if (value == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
    
    /**
     * 将List<String>转换为JSON字符串
     */
    @TypeConverter
    fun toRecipientsJson(list: List<String>?): String? {
        if (list == null) return null
        return gson.toJson(list)
    }
}

/**
 * 商品款式数据类(用于JSON序列化)
 */
data class ProductVariationData(
    val name: String,
    val price: Double,
    val imageUrl: String? = null
)

/**
 * 订单商品数据类(用于JSON序列化)
 */
data class OrderItemData(
    val name: String,
    val price: Double,
    val imageUrl: String,
    val quantity: Int
)