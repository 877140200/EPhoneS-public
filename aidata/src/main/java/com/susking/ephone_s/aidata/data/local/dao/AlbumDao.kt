package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

/**
 * 相册数据访问对象
 */
@Dao
interface AlbumDao {
    /**
     * 插入一个新的相册。如果已存在,则替换。
     * @return 返回新插入行的 rowId
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    /**
     * 更新相册
     */
    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    /**
     * 获取所有相册,按创建时间降序排列
     */
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    /**
     * 根据名称查找相册
     */
    @Query("SELECT * FROM albums WHERE name = :name LIMIT 1")
    suspend fun getAlbumByName(name: String): AlbumEntity?

    /**
     * 根据 ID 查找相册
     */
    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getAlbumById(albumId: Long): AlbumEntity?
}