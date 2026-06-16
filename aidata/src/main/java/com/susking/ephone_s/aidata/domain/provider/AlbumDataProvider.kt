package com.susking.ephone_s.aidata.domain.provider

import java.io.File

/**
 * 相册数据提供者接口
 *
 * 由于album模块在aidata模块中无法直接访问，
 * 需要在app模块中实现此接口来提供相册数据
 */
interface AlbumDataProvider {
    
    /**
     * 导出相册数据到指定目录
     *
     * @param albumDir 相册导出目录
     * @param photosDir 照片文件导出目录
     * @return 相册数据的JSON字符串
     */
    suspend fun exportAlbumData(albumDir: File, photosDir: File): String
    
    /**
     * 获取相册相关的SharedPreferences数据
     *
     * @return Map<文件名, 键值对>
     */
    fun getAlbumSettings(): Map<String, Map<String, Any?>>
    
    /**
     * 导入相册数据
     *
     * @param tempDir 临时解压目录（包含album子目录）
     */
    suspend fun importAlbumData(tempDir: File)
}