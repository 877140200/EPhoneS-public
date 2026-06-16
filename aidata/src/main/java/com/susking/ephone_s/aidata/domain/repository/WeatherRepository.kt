package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.WeatherInfo
import kotlinx.coroutines.flow.Flow

/**
 * 天气信息仓库。
 *
 * 负责当前天气的本地缓存读写。数据为瞬时缓存，使用 DataStore 实现，
 * 不纳入导入导出与数据迁移（瞬时数据无迁移价值）。
 *
 * 注意：天气与定位的"外部 API 请求"不在此仓库发起，而是经 brain 悬浮窗链路
 * （AiRequestService）完成后，再把结果通过 [saveWeather] 写入缓存。
 */
interface WeatherRepository {

    /**
     * 观察当前天气缓存，无缓存时发射 null。
     * @return Flow<WeatherInfo?> 天气信息流
     */
    fun observeWeather(): Flow<WeatherInfo?>

    /**
     * 读取当前天气缓存的快照（一次性，非流）。
     * @return WeatherInfo? 当前缓存，无则返回 null
     */
    suspend fun getWeatherSync(): WeatherInfo?

    /**
     * 写入/更新天气缓存。
     * @param weatherInfo 最新天气信息
     */
    suspend fun saveWeather(weatherInfo: WeatherInfo): Unit
}
