package com.susking.ephone_s.album

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.susking.ephone_s.aidata.data.local.AlbumDatabase
import com.susking.ephone_s.aidata.domain.provider.AlbumDataProvider
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * AlbumDataProvider的实现
 * 
 * 负责导出和导入相册数据
 */
class AlbumDataProviderImpl @Inject constructor(
    private val context: Context,
    private val albumDatabase: AlbumDatabase,
    private val gson: Gson
) : AlbumDataProvider {
    
    companion object {
        private const val TAG = "AlbumDataProviderImpl"
    }
    
    /**
     * 导出相册数据到指定目录
     */
    override suspend fun exportAlbumData(albumDir: File, photosDir: File): String {
        Log.d(TAG, "开始导出相册数据")
        
        // 获取所有相册
        val albums = albumDatabase.albumDao().getAllAlbums().first()
        
        // 获取所有照片
        val photos = albumDatabase.photoDao().getAllPhotos().first()
        
        // 复制照片文件到导出目录的images子文件夹
        val imagesDir = File(photosDir, "images").apply { mkdirs() }
        val processedPhotos = photos.map { photo ->
            val sourceFile = File(photo.uri)
            if (sourceFile.exists()) {
                try {
                    val destFile = File(imagesDir, sourceFile.name)
                    sourceFile.copyTo(destFile, overwrite = true)
                    // 更新照片路径为相对路径
                    photo.copy(uri = "images/${destFile.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "复制照片失败: ${photo.uri}", e)
                    photo
                }
            } else {
                photo
            }
        }
        
        // 构建导出数据
        val data = mapOf(
            "albums" to albums,
            "photos" to processedPhotos
        )
        
        Log.d(TAG, "相册数据导出完成: ${albums.size}个相册, ${photos.size}张照片")
        return gson.toJson(data)
    }
    
    /**
     * 获取相册相关的SharedPreferences数据
     */
    override fun getAlbumSettings(): Map<String, Map<String, Any?>> {
        // 相册模块目前没有使用SharedPreferences
        // 如果将来有设置，在这里读取
        return emptyMap()
    }
    
    /**
     * 导入相册数据
     */
    override suspend fun importAlbumData(tempDir: File) {
        Log.d(TAG, "开始导入相册数据")
        
        val albumDataFile = File(tempDir, "album/data.json")
        if (!albumDataFile.exists()) {
            Log.w(TAG, "相册数据文件不存在")
            return
        }
        
        try {
            // 读取JSON数据
            val dataJson = albumDataFile.readText()
            val data = gson.fromJson(dataJson, Map::class.java) as Map<String, Any>
            
            // 解析相册数据
            val albumsJson = gson.toJson(data["albums"])
            val albums = gson.fromJson(
                albumsJson,
                Array<com.susking.ephone_s.aidata.data.local.entity.AlbumEntity>::class.java
            ).toList()
            
            // 解析照片数据
            val photosJson = gson.toJson(data["photos"])
            val photos = gson.fromJson(
                photosJson,
                Array<com.susking.ephone_s.aidata.data.local.entity.PhotoEntity>::class.java
            ).toList()
            
            // 处理照片路径：将相对路径转换为绝对路径
            val processedPhotos = photos.map { photo ->
                if (!photo.uri.startsWith("http") && !photo.uri.startsWith("/")) {
                    // 相对路径，需要复制文件
                    // 照片在album/photos/images/目录下
                    val sourceFile = File(tempDir, "album/photos/${photo.uri}")
                    if (sourceFile.exists()) {
                        try {
                            // 复制到应用存储目录
                            val destDir = File(context.filesDir, "album_photos").apply { mkdirs() }
                            val destFile = File(destDir, sourceFile.name)
                            sourceFile.copyTo(destFile, overwrite = true)
                            photo.copy(uri = destFile.absolutePath)
                        } catch (e: Exception) {
                            Log.e(TAG, "复制照片失败: ${photo.uri}", e)
                            photo
                        }
                    } else {
                        Log.w(TAG, "照片文件不存在: ${sourceFile.absolutePath}")
                        photo
                    }
                } else {
                    photo
                }
            }
            
            // 插入到数据库
            albums.forEach { album ->
                albumDatabase.albumDao().insertAlbum(album)
            }
            
            processedPhotos.forEach { photo ->
                albumDatabase.photoDao().insertPhoto(photo)
            }
            
            Log.d(TAG, "相册数据导入完成: ${albums.size}个相册, ${processedPhotos.size}张照片")
            
        } catch (e: Exception) {
            Log.e(TAG, "导入相册数据失败", e)
            throw e
        }
    }
}