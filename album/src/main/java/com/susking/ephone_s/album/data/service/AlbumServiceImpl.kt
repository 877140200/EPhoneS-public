package com.susking.ephone_s.album.data.service

import com.susking.ephone_s.album.domain.model.Photo
import com.susking.ephone_s.album.domain.repository.AlbumRepository
import com.susking.ephone_s.album.domain.service.AlbumService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * AlbumService 实现类
 */
class AlbumServiceImpl(
    private val repository: AlbumRepository
) : AlbumService {
    
    override suspend fun addPhotoToAlbum(albumName: String, photoPath: String) {
        repository.addPhotoToAlbum(albumName, photoPath)
    }
    
    override fun observeAllPhotos(): Flow<List<Photo>> {
        return repository.getAllPhotosSortedByDate()
    }
    
    override suspend fun getAllPhotos(): List<Photo> {
        return repository.getAllPhotosSortedByDate().first()
    }
}