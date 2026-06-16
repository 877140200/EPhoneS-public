package com.susking.ephone_s.aidata.domain.model

/**
 * API预设数据类
 * 用于保存和加载不同的API配置预设
 */
data class ApiPreset(
    val name: String,
    val mainApiUrl: String,
    val mainApiKey: String,
    val mainApiModel: String,
    val secondaryApiUrl: String = "",
    val secondaryApiKey: String = "",
    val secondaryApiModel: String = "",
    val apiTemperature: Float = 0.8f
)