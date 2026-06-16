package com.susking.ephone_s.aidata.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Open-Meteo 天气接口响应 DTO。
 *
 * 仅声明本应用关心的字段（当前天气）。Open-Meteo 免费、免 Key，
 * 端点示例：
 * https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current=temperature_2m,weather_code
 *
 * @property latitude 实际返回的纬度
 * @property longitude 实际返回的经度
 * @property current 当前天气块，请求失败或无数据时为 null
 */
data class WeatherDto(
    @SerializedName("latitude")
    val latitude: Double = 0.0,
    @SerializedName("longitude")
    val longitude: Double = 0.0,
    @SerializedName("current")
    val current: CurrentWeatherDto? = null
)

/**
 * Open-Meteo 当前天气块。
 *
 * @property temperature 当前气温（摄氏度），对应请求参数 temperature_2m
 * @property weatherCode WMO 天气代码，对应请求参数 weather_code，用于映射天气文案与图标
 */
data class CurrentWeatherDto(
    @SerializedName("temperature_2m")
    val temperature: Double = 0.0,
    @SerializedName("weather_code")
    val weatherCode: Int = 0
)
