package com.susking.ephone_s.desktop.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.core.data.desktopLayoutDataStore
import com.susking.ephone_s.core.widget.DesktopWidgetConfig
import com.susking.ephone_s.core.widget.DesktopWidgetType
import com.susking.ephone_s.core.widget.WidgetLayoutStore
import com.susking.ephone_s.desktop.model.AppIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 桌面布局持久化存储Repository
 * 用于保存和读取桌面图标的位置信息
 */
@Singleton
class DesktopRepository @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    // 桌面卡片布局配置统一委托给 core 的 WidgetLayoutStore，与 schedule 设置页共用同一数据源
    private val widgetLayoutStore: WidgetLayoutStore = WidgetLayoutStore(context, gson)

    companion object {
        private val PAGES_KEY = stringPreferencesKey("desktop_pages")
        private val DOCK_KEY = stringPreferencesKey("dock_items")
    }

    /**
     * 保存桌面页面数据
     * @param pages 所有页面的图标列表
     */
    suspend fun savePages(pages: List<List<AppIcon>>) {
        context.desktopLayoutDataStore.edit { preferences ->
            preferences[PAGES_KEY] = gson.toJson(pages)
        }
    }

    /**
     * 保存Dock栏数据
     * @param dockItems Dock栏的图标列表
     */
    suspend fun saveDockItems(dockItems: List<AppIcon>) {
        context.desktopLayoutDataStore.edit { preferences ->
            preferences[DOCK_KEY] = gson.toJson(dockItems)
        }
    }

    /**
     * 保存指定类型卡片的位置与尺寸配置（委托 core 统一存储）。
     * @param type 卡片类型（课程表/时钟/天气）
     * @param config 待保存的配置
     */
    suspend fun saveWidgetConfig(type: DesktopWidgetType, config: DesktopWidgetConfig): Unit {
        widgetLayoutStore.saveWidgetConfig(type, config)
    }

    /**
     * 读取指定类型卡片的配置（委托 core 统一存储，含旧 key 迁移）。
     * @param type 卡片类型
     * @return Flow<DesktopWidgetConfig> 无数据时回退到该类型的默认配置
     */
    fun getWidgetConfig(type: DesktopWidgetType): Flow<DesktopWidgetConfig> {
        return widgetLayoutStore.getWidgetConfig(type)
    }

    /**
     * 读取桌面页面数据
     * @return Flow<List<List<AppIcon>>?> 返回保存的页面数据，如果没有保存则返回null
     */
    fun getPages(): Flow<List<List<AppIcon>>?> {
        return context.desktopLayoutDataStore.data.map { preferences ->
            val json = preferences[PAGES_KEY]
            if (json != null) {
                val type = object : TypeToken<List<List<AppIcon>>>() {}.type
                gson.fromJson(json, type)
            } else {
                null
            }
        }
    }

    /**
     * 读取Dock栏数据
     * @return Flow<List<AppIcon>?> 返回保存的Dock数据，如果没有保存则返回null
     */
    fun getDockItems(): Flow<List<AppIcon>?> {
        return context.desktopLayoutDataStore.data.map { preferences ->
            val json = preferences[DOCK_KEY]
            if (json != null) {
                val type = object : TypeToken<List<AppIcon>>() {}.type
                gson.fromJson(json, type)
            } else {
                null
            }
        }
    }

    /**
     * 同步获取桌面页面数据（用于初始化）
     * @return List<List<AppIcon>>? 返回保存的页面数据，如果没有保存则返回null
     */
    suspend fun getPagesSync(): List<List<AppIcon>>? {
        return getPages().first()
    }

    /**
     * 同步获取Dock栏数据（用于初始化）
     * @return List<AppIcon>? 返回保存的Dock数据，如果没有保存则返回null
     */
    suspend fun getDockItemsSync(): List<AppIcon>? {
        return getDockItems().first()
    }

    /**
     * 检查并修复桌面布局中重复的应用入口。
     *
     * 安全边界：
     * 1. 只处理桌面页面和 Dock 中的 AppIcon 列表，不读取或修改任何数据库、图片、聊天记录等其他数据。
     * 2. 以应用名称作为唯一入口标识，保留第一次出现的入口，移除后续重复入口。
     * 3. 仅在确实发现重复入口时才写回 DataStore，避免无意义覆盖用户现有布局。
     * 4. 页面与 Dock 按当前显示顺序依次扫描，因此优先保留用户当前布局中最靠前、最稳定的入口。
     *
     * @return DesktopDuplicateRepairResult 返回检查与修复统计，供设置页展示给用户。
     */
    suspend fun repairDuplicateAppEntries(): DesktopDuplicateRepairResult {
        val pages: List<List<AppIcon>> = getPagesSync() ?: emptyList()
        val dockItems: List<AppIcon> = getDockItemsSync() ?: emptyList()
        val seenAppNames: MutableSet<String> = linkedSetOf()
        val removedAppNames: MutableList<String> = mutableListOf()
        var hasChanges: Boolean = false

        val repairedPages: List<List<AppIcon>> = pages.map { page: List<AppIcon> ->
            page.filter { icon: AppIcon ->
                if (seenAppNames.add(icon.name)) {
                    true
                } else {
                    removedAppNames.add(icon.name)
                    hasChanges = true
                    false
                }
            }
        }

        val repairedDockItems: List<AppIcon> = dockItems.filter { icon: AppIcon ->
            if (seenAppNames.add(icon.name)) {
                true
            } else {
                removedAppNames.add(icon.name)
                hasChanges = true
                false
            }
        }

        if (hasChanges) {
            context.desktopLayoutDataStore.edit { preferences: MutablePreferences ->
                preferences[PAGES_KEY] = gson.toJson(repairedPages)
                preferences[DOCK_KEY] = gson.toJson(repairedDockItems)
            }
        }

        return DesktopDuplicateRepairResult(
            checkedEntryCount = pages.sumOf { page: List<AppIcon> -> page.size } + dockItems.size,
            removedEntryCount = removedAppNames.size,
            removedAppNames = removedAppNames.distinct(),
            hasChanges = hasChanges
        )
    }

    /**
     * 清除所有保存的数据（用于重置桌面布局）
     */
    suspend fun clearAll() {
        context.desktopLayoutDataStore.edit { it.clear() }
    }
}

/**
 * 桌面重复入口修复结果。
 */
data class DesktopDuplicateRepairResult(
    val checkedEntryCount: Int,
    val removedEntryCount: Int,
    val removedAppNames: List<String>,
    val hasChanges: Boolean
)