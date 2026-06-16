package com.susking.ephone_s.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 天气缓存 DataStore 单例入口。
 *
 * 天气是瞬时缓存数据（位置 + 天气 + 温度 + 刷新时间），无迁移价值，
 * 因此使用轻量的 DataStore 而非 Room，且不纳入导入导出。
 *
 * DataStore 要求同一个文件在同一进程内只能存在一个活跃实例，
 * 因此所有模块读写 weather_cache.preferences_pb 时都必须复用这个顶层委托。
 */
val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_cache")
