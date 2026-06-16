package com.susking.ephone_s.aidata.domain.provider

import java.io.File

/**
 * 桌面数据提供者接口
 *
 * 由于desktop模块在aidata模块中无法直接访问，
 * 需要在app模块中实现此接口来提供桌面布局数据
 */
interface DesktopDataProvider {
    
    /**
     * 导出桌面布局数据到指定目录
     *
     * @param desktopDir 桌面导出目录
     * @return 桌面布局数据的JSON字符串
     */
    suspend fun exportDesktopLayout(desktopDir: File): String
    
    /**
     * 获取桌面相关的SharedPreferences数据
     *
     * @return Map<文件名, 键值对>
     */
    fun getDesktopSettings(): Map<String, Map<String, Any?>>
    
    /**
     * 导入桌面布局数据
     *
     * @param tempDir 临时解压目录（包含desktop子目录）
     */
    suspend fun importDesktopLayout(tempDir: File)
}