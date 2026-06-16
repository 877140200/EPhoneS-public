package com.susking.ephone_s.album.data.repository

import com.susking.ephone_s.album.data.local.mapper.AlbumMapper
import com.susking.ephone_s.album.data.local.mapper.PhotoMapper
import com.susking.ephone_s.album.domain.model.Album
import com.susking.ephone_s.album.domain.model.Photo
import com.susking.ephone_s.album.domain.repository.AlbumRepository
import com.susking.ephone_s.aidata.data.local.dao.AlbumDao
import com.susking.ephone_s.aidata.data.local.dao.PhotoDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 相册仓库实现
 */
class AlbumRepositoryImpl(
    private val albumDao: AlbumDao,
    private val photoDao: PhotoDao
) : AlbumRepository {

    override fun getAllAlbums(): Flow<List<Album>> {
        return albumDao.getAllAlbums().map { entities ->
            AlbumMapper.toDomainList(entities)
        }
    }

    override fun getAllPhotosSortedByDate(): Flow<List<Photo>> {
        return photoDao.getAllPhotosSortedByDate().map { entities ->
            PhotoMapper.toDomainList(entities)
        }
    }

    override fun getPhotosByAlbum(albumId: Long): Flow<List<Photo>> {
        return photoDao.getPhotosByAlbum(albumId).map { entities ->
            PhotoMapper.toDomainList(entities)
        }
    }

    override suspend fun createAlbum(album: Album) {
        albumDao.insertAlbum(AlbumMapper.toEntity(album))
    }

    override suspend fun addPhoto(photo: Photo) {
        photoDao.insertPhoto(PhotoMapper.toEntity(photo))
    }

    override suspend fun addPhotoToAlbum(albumName: String, photoPath: String) {
        // 查找相册,如果不存在则创建
        val existingAlbum = albumDao.getAlbumByName(albumName)
        val albumId = if (existingAlbum == null) {
            albumDao.insertAlbum(AlbumMapper.toEntity(Album(name = albumName)))
        } else {
            existingAlbum.id
        }

        // 创建并插入照片记录
        val newPhoto = Photo(uri = photoPath, albumId = albumId)
        photoDao.insertPhoto(PhotoMapper.toEntity(newPhoto))

        // 更新相册封面和照片计数
        val albumToUpdate = albumDao.getAlbumById(albumId)
        if (albumToUpdate != null) {
            val updatedAlbum = albumToUpdate.copy(
                coverImagePath = newPhoto.uri,
                photoCount = albumToUpdate.photoCount + 1
            )
            albumDao.updateAlbum(updatedAlbum)
        }
    }

    override suspend fun updatePhoto(photo: Photo) {
        photoDao.updatePhoto(PhotoMapper.toEntity(photo))

        // 收藏状态变更后,需要同步更新收藏夹相册
        val favoritesAlbumName = "收藏夹"
        var favoritesAlbum = albumDao.getAlbumByName(favoritesAlbumName)

        val favoritedPhotos = photoDao.getFavoritedPhotosList()

        if (favoritedPhotos.isNotEmpty()) {
            if (favoritesAlbum == null) {
                // 如果收藏夹不存在且有收藏照片,则创建收藏夹
                val newAlbumId = albumDao.insertAlbum(AlbumMapper.toEntity(Album(name = favoritesAlbumName)))
                favoritesAlbum = AlbumMapper.toEntity(Album(id = newAlbumId, name = favoritesAlbumName))
            }

            // 更新收藏夹的封面为最新一张收藏的照片,并更新照片数量
            val latestFavoritedPhoto = favoritedPhotos.first()
            val updatedFavoritesAlbum = favoritesAlbum!!.copy(
                coverImagePath = latestFavoritedPhoto.uri,
                photoCount = favoritedPhotos.size
            )
            albumDao.updateAlbum(updatedFavoritesAlbum)

        } else {
            // 如果没有收藏的照片了,但收藏夹还存在,可以选择删除或清空它
            // 这里我们选择更新封面为空,数量为0
            if (favoritesAlbum != null) {
                val updatedFavoritesAlbum = favoritesAlbum.copy(
                    coverImagePath = null,
                    photoCount = 0
                )
                albumDao.updateAlbum(updatedFavoritesAlbum)
            }
        }
    }

    override suspend fun deletePhoto(photo: Photo) {
        val albumId = photo.albumId
        val wasFavorited = photo.isFavorited

        // 1. 从数据库删除照片
        photoDao.deletePhoto(PhotoMapper.toEntity(photo))

        // 2. 更新原相册
        val originalAlbum = albumDao.getAlbumById(albumId)
        if (originalAlbum != null) {
            val remainingPhotos = photoDao.getPhotosByAlbumList(albumId)
            val updatedAlbum = originalAlbum.copy(
                photoCount = remainingPhotos.size,
                coverImagePath = remainingPhotos.firstOrNull()?.uri
            )
            albumDao.updateAlbum(updatedAlbum)
        }

        // 3. 如果删除的照片是收藏的,还要更新收藏夹
        if (wasFavorited) {
            val favoritesAlbum = albumDao.getAlbumByName("收藏夹")
            if (favoritesAlbum != null) {
                val favoritedPhotos = photoDao.getFavoritedPhotosList()
                val updatedFavoritesAlbum = favoritesAlbum.copy(
                    photoCount = favoritedPhotos.size,
                    coverImagePath = favoritedPhotos.firstOrNull()?.uri
                )
                albumDao.updateAlbum(updatedFavoritesAlbum)
            }
        }
    }

    override suspend fun deletePhotos(photos: List<Photo>) {
        if (photos.isEmpty()) return

        val photosToDelete = photos.toList()
        photoDao.deletePhotos(photosToDelete.map { PhotoMapper.toEntity(it) })

        val albumIds = photosToDelete.map { it.albumId }.distinct()
        for (albumId in albumIds) {
            val album = albumDao.getAlbumById(albumId)
            if (album != null) {
                val remainingPhotos = photoDao.getPhotosByAlbumList(albumId)
                val updatedAlbum = album.copy(
                    photoCount = remainingPhotos.size,
                    coverImagePath = remainingPhotos.firstOrNull()?.uri
                )
                albumDao.updateAlbum(updatedAlbum)
            }
        }

        val wasAnyFavorited = photosToDelete.any { it.isFavorited }
        if (wasAnyFavorited) {
            val favoritesAlbum = albumDao.getAlbumByName("收藏夹")
            if (favoritesAlbum != null) {
                val favoritedPhotos = photoDao.getFavoritedPhotosList()
                val updatedFavoritesAlbum = favoritesAlbum.copy(
                    photoCount = favoritedPhotos.size,
                    coverImagePath = favoritedPhotos.firstOrNull()?.uri
                )
                albumDao.updateAlbum(updatedFavoritesAlbum)
            }
        }
    }

    override suspend fun getAlbumByName(name: String): Album? {
        return albumDao.getAlbumByName(name)?.let { AlbumMapper.toDomain(it) }
    }

    override fun getFavoritedPhotos(): Flow<List<Photo>> {
        return photoDao.getFavoritedPhotos().map { entities ->
            PhotoMapper.toDomainList(entities)
        }
    }

    override suspend fun insertPhoto(photo: Photo) {
        photoDao.insertPhoto(PhotoMapper.toEntity(photo))
        // 更新相册封面
        val album = albumDao.getAlbumById(photo.albumId)
        if (album != null) {
            val updatedAlbum = album.copy(
                coverImagePath = photo.uri,
                photoCount = album.photoCount + 1
            )
            albumDao.updateAlbum(updatedAlbum)
        }
    }

    override suspend fun updateAlbum(album: Album) {
        albumDao.updateAlbum(AlbumMapper.toEntity(album))
    }
}