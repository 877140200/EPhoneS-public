package com.susking.ephone_s.aidata.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.model.WeatherInfo
import com.susking.ephone_s.aidata.domain.repository.WeatherRepository
import com.susking.ephone_s.core.data.weatherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 天气仓库实现。
 *
 * 天气属于易变的临时缓存数据，使用 DataStore Preferences 存储（核心模块的 weatherDataStore），
 * 不写入 Room，也不纳入导入导出——重新定位即可刷新，没有迁移价值。
 *
 * 整条天气数据以单个 JSON 字符串落盘，读写都通过 Gson 序列化 [WeatherInfo]。
 *
 * @property context 应用级上下文，用于访问 core 的 weatherDataStore 单例委托
 * @property gson JSON 序列化工具
 */
class WeatherRepositoryImpl(
    private val context: Context,
    private val gson: Gson
) : WeatherRepository {

    /**
     * 观察天气缓存的变化流，无缓存时发射 null。
     */
    override fun observeWeather(): Flow<WeatherInfo?> {
        return context.weatherDataStore.data.map { preferences ->
            parseWeather(preferences[WEATHER_JSON_KEY])
        }
    }

    /**
     * 同步读取一次当前天气缓存，无缓存返回 null。
     */
    override suspend fun getWeatherSync(): WeatherInfo? {
        val preferences = context.weatherDataStore.data.first()
        return parseWeather(preferences[WEATHER_JSON_KEY])
    }

    /**
     * 保存最新天气到缓存，覆盖旧值。
     */
    override suspend fun saveWeather(weatherInfo: WeatherInfo) {
        val json: String = gson.toJson(weatherInfo)
        context.weatherDataStore.edit { preferences ->
            preferences[WEATHER_JSON_KEY] = json
        }
    }

    /**
     * 将存储的 JSON 字符串解析为 [WeatherInfo]，解析失败或为空时返回 null。
     */
    private fun parseWeather(json: String?): WeatherInfo? {
        if (json.isNullOrBlank()) {
            return null
        }
        return runCatching {
            gson.fromJson(json, WeatherInfo::class.java)
        }.getOrNull()
    }

    companion object {
        // 天气缓存 JSON 的存储键
        private val WEATHER_JSON_KEY = stringPreferencesKey("weather_info_json")
    }
}
