package com.susking.ephone_s.album.domain.repository

import com.susking.ephone_s.album.domain.model.Album
import com.susking.ephone_s.album.domain.model.Photo
import kotlinx.coroutines.flow.Flow

/**
 * 相册仓库接口
 */
interface AlbumRepository {
    fun getAllAlbums(): Flow<List<Album>>
    fun getAllPhotosSortedByDate(): Flow<List<Photo>>
    fun getPhotosByAlbum(albumId: Long): Flow<List<Photo>>
    suspend fun createAlbum(album: Album)
    suspend fun addPhoto(photo: Photo)
    suspend fun addPhotoToAlbum(albumName: String, photoPath: String)
    suspend fun updatePhoto(photo: Photo)
    suspend fun deletePhoto(photo: Photo)
    suspend fun deletePhotos(photos: List<Photo>)
    suspend fun getAlbumByName(name: String): Album?
    fun getFavoritedPhotos(): Flow<List<Photo>>
    suspend fun insertPhoto(photo: Photo)
    suspend fun updateAlbum(album: Album)
}