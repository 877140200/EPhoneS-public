package com.susking.ephone_s.album.domain.service

import com.susking.ephone_s.album.domain.model.Photo
import kotlinx.coroutines.flow.Flow

/**
 * 相册服务接口 - 用于跨模块通信
 * 提供给其他模块使用的简化接口
 */
interface AlbumService {
    /**
     * 添加照片到指定相册
     * @param albumName 相册名称,如果不存在会自动创建
     * @param photoPath 照片路径
     */
    suspend fun addPhotoToAlbum(albumName: String, photoPath: String)
    
    /**
     * 获取所有照片
     */
    fun observeAllPhotos(): Flow<List<Photo>>
    
    /**
     * 获取所有照片 (挂起函数)
     */
    suspend fun getAllPhotos(): List<Photo>
}