package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

/**
 * 照片数据访问对象
 */
@Dao
interface PhotoDao {
    /**
     * 插入一张新照片。如果已存在,则替换。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    /**
     * 更新照片
     */
    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    /**
     * 删除照片
     */
    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)

    /**
     * 批量删除照片
     */
    @Delete
    suspend fun deletePhotos(photos: List<PhotoEntity>)

    /**
     * 根据相册ID获取所有照片,按添加日期降序排列
     */
    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY dateAdded DESC")
    fun getPhotosByAlbum(albumId: Long): Flow<List<PhotoEntity>>

    /**
     * 获取所有照片,按添加日期降序排列
     */
    @Query("SELECT * FROM photos ORDER BY dateAdded DESC")
    fun getAllPhotosSortedByDate(): Flow<List<PhotoEntity>>
    
    /**
     * 获取所有照片的列表（用于导出）
     */
    @Query("SELECT * FROM photos ORDER BY dateAdded DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    /**
     * 获取所有被收藏的照片,按添加日期降序排列
     */
    @Query("SELECT * FROM photos WHERE isFavorited = 1 ORDER BY dateAdded DESC")
    fun getFavoritedPhotos(): Flow<List<PhotoEntity>>

    /**
     * 获取所有被收藏的照片,返回一个挂起函数列表
     */
    @Query("SELECT * FROM photos WHERE isFavorited = 1 ORDER BY dateAdded DESC")
    suspend fun getFavoritedPhotosList(): List<PhotoEntity>

    /**
     * 根据相册ID获取所有照片,返回一个挂起函数列表
     */
    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY dateAdded DESC")
    suspend fun getPhotosByAlbumList(albumId: Long): List<PhotoEntity>
}