package com.susking.ephone_s.core.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.susking.ephone_s.core.data.desktopLayoutDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 桌面卡片布局配置的统一存储入口。
 *
 * 作为 desktop（拖拽桌面卡片）与 schedule（设置页改尺寸）两模块共用的单一数据源，
 * 避免各模块各自重复声明 key 与配置结构导致写入不同步。
 *
 * 存储位置复用 core 的 [desktopLayoutDataStore]，按卡片类型分键：
 * widget_config_SCHEDULE / widget_config_CLOCK / widget_config_WEATHER。
 *
 * 迁移策略：对课程表卡片，若新 key 尚无数据但旧 key（schedule_widget_config）存在，
 * 则回退读取旧数据，保证老用户的课程表卡片位置与尺寸不丢失。
 */
class WidgetLayoutStore(
    private val context: Context,
    private val gson: Gson
) {

    /**
     * 保存指定类型卡片的位置与尺寸配置。
     * @param type 卡片类型（课程表/时钟/天气）
     * @param config 待保存的配置
     */
    suspend fun saveWidgetConfig(type: DesktopWidgetType, config: DesktopWidgetConfig): Unit {
        context.desktopLayoutDataStore.edit { preferences: MutablePreferences ->
            preferences[buildWidgetConfigKey(type)] = gson.toJson(config)
        }
    }

    /**
     * 读取指定类型卡片的配置。
     * @param type 卡片类型
     * @return Flow<DesktopWidgetConfig> 无数据时回退到该类型的默认配置
     */
    fun getWidgetConfig(type: DesktopWidgetType): Flow<DesktopWidgetConfig> {
        return context.desktopLayoutDataStore.data.map { preferences: Preferences ->
            val json: String? = preferences[buildWidgetConfigKey(type)]
                ?: if (type == DesktopWidgetType.SCHEDULE) preferences[LEGACY_SCHEDULE_WIDGET_KEY] else null
            parseWidgetConfig(json, type)
        }
    }

    /**
     * 将 JSON 反序列化为配置，失败或为空时回退到类型默认值。
     */
    private fun parseWidgetConfig(json: String?, type: DesktopWidgetType): DesktopWidgetConfig {
        val defaultConfig: DesktopWidgetConfig = DesktopWidgetConfig.fromType(type)
        if (json.isNullOrBlank()) return defaultConfig
        return runCatching { gson.fromJson(json, DesktopWidgetConfig::class.java) }.getOrDefault(defaultConfig)
    }

    companion object {
        // 旧版课程表卡片的存储 key，仅用于一次性迁移到新版按类型分键的存储
        private const val LEGACY_SCHEDULE_WIDGET_KEY_NAME: String = "schedule_widget_config"
        private val LEGACY_SCHEDULE_WIDGET_KEY = stringPreferencesKey(LEGACY_SCHEDULE_WIDGET_KEY_NAME)

        // 新版统一存储 key 前缀，按类型拼接：widget_config_SCHEDULE/CLOCK/WEATHER
        private const val WIDGET_CONFIG_KEY_PREFIX: String = "widget_config_"

        /**
         * 按卡片类型生成 DataStore 存储 key。
         */
        private fun buildWidgetConfigKey(type: DesktopWidgetType): Preferences.Key<String> {
            return stringPreferencesKey("$WIDGET_CONFIG_KEY_PREFIX${type.storageKey}")
        }
    }
}
