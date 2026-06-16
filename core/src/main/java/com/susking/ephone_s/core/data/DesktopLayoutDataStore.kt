package com.susking.ephone_s.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 桌面布局 DataStore 单例入口。
 *
 * DataStore 要求同一个文件在同一进程内只能存在一个活跃实例。
 * 因此所有模块读写 desktop_layout.preferences_pb 时都必须复用这个顶层委托，
 * 不能在 desktop、schedule 等模块里各自重新声明同名 preferencesDataStore。
 */
val Context.desktopLayoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "desktop_layout")
