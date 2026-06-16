package com.susking.ephone_s.desktop

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.provider.DesktopDataProvider
import com.susking.ephone_s.desktop.data.DesktopRepository
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * DesktopDataProvider的实现
 * 
 * 负责导出和导入桌面布局数据
 */
class DesktopDataProviderImpl @Inject constructor(
    private val context: Context,
    private val desktopRepository: DesktopRepository,
    private val gson: Gson
) : DesktopDataProvider {
    
    companion object {
        private const val TAG = "DesktopDataProviderImpl"
    }
    
    /**
     * 导出桌面布局数据到指定目录
     */
    override suspend fun exportDesktopLayout(desktopDir: File): String {
        Log.d(TAG, "开始导出桌面布局数据")
        
        // 获取桌面页面数据
        val pages = desktopRepository.getPages().first()
        
        // 获取Dock栏数据
        val dockItems = desktopRepository.getDockItems().first()
        
        // 构建导出数据
        val data = mapOf(
            "pages" to (pages ?: emptyList()),
            "dock" to (dockItems ?: emptyList())
        )
        
        Log.d(TAG, "桌面布局数据导出完成: ${pages?.size ?: 0}个页面, ${dockItems?.size ?: 0}个Dock图标")
        return gson.toJson(data)
    }
    
    /**
     * 获取桌面相关的SharedPreferences数据
     */
    override fun getDesktopSettings(): Map<String, Map<String, Any?>> {
        // Desktop模块使用DataStore而不是SharedPreferences
        // 如果将来有其他设置需要导出，在这里添加
        return emptyMap()
    }
    
    /**
     * 导入桌面布局数据
     */
    override suspend fun importDesktopLayout(tempDir: File) {
        Log.d(TAG, "开始导入桌面布局数据")
        
        val desktopLayoutFile = File(tempDir, "desktop/layout.json")
        if (!desktopLayoutFile.exists()) {
            Log.w(TAG, "桌面布局数据文件不存在")
            return
        }
        
        try {
            // 读取JSON数据
            val dataJson = desktopLayoutFile.readText()
            val data = gson.fromJson(dataJson, Map::class.java) as Map<String, Any>
            
            // 解析页面数据
            val pagesJson = gson.toJson(data["pages"])
            val pages = gson.fromJson(
                pagesJson,
                Array<Array<com.susking.ephone_s.desktop.model.AppIcon>>::class.java
            ).map { it.toList() }
            
            // 解析Dock数据
            val dockJson = gson.toJson(data["dock"])
            val dockItems = gson.fromJson(
                dockJson,
                Array<com.susking.ephone_s.desktop.model.AppIcon>::class.java
            ).toList()
            
            // 保存到DesktopRepository
            desktopRepository.savePages(pages)
            desktopRepository.saveDockItems(dockItems)
            
            Log.d(TAG, "桌面布局数据导入完成: ${pages.size}个页面, ${dockItems.size}个Dock图标")
            
        } catch (e: Exception) {
            Log.e(TAG, "导入桌面布局数据失败", e)
            throw e
        }
    }
}