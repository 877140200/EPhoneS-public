package com.susking.ephone_s.aidata.domain.model

/**
 * 当前天气信息。
 *
 * 作为桌面天气卡片渲染与 AI 提示词注入的统一数据载体。
 * 该数据为瞬时缓存（位置 + 天气 + 温度 + 刷新时间），无迁移价值，
 * 仅存于 DataStore，不纳入导入导出。
 *
 * @property locationName 位置名称（城市/地区），用于卡片与提示词展示
 * @property temperatureCelsius 当前气温（摄氏度）
 * @property weatherText 天气文案（如"晴""多云""小雨"）
 * @property weatherCode Open-Meteo 天气代码，用于映射卡片图标
 * @property updatedAt 数据刷新时间戳（毫秒），用于节流判断
 * @property latitude 纬度，用于复用上次坐标
 * @property longitude 经度，用于复用上次坐标
 */
data class WeatherInfo(
    val locationName: String,
    val temperatureCelsius: Double,
    val weatherText: String,
    val weatherCode: Int,
    val updatedAt: Long,
    val latitude: Double,
    val longitude: Double
)
