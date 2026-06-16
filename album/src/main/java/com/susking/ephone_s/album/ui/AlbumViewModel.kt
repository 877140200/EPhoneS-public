package com.susking.ephone_s.album.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.album.domain.model.Album
import com.susking.ephone_s.album.domain.model.Photo
import com.susking.ephone_s.album.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlbumViewModel(private val repository: AlbumRepository) : ViewModel() {

    val albums: StateFlow<List<Album>> = repository.getAllAlbums()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allPhotos: StateFlow<List<Photo>> = repository.getAllPhotosSortedByDate()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            val favoritesAlbum = repository.getAlbumByName("收藏夹")
            if (favoritesAlbum == null) {
                repository.createAlbum(Album(name = "收藏夹"))
            }
        }
    
    }

    fun renameAlbum(album: Album, newName: String) {
        viewModelScope.launch {
            val updatedAlbum = album.copy(name = newName)
            repository.updateAlbum(updatedAlbum)
        }
    }

    fun createAlbum(albumName: String) {
        viewModelScope.launch {
            repository.createAlbum(Album(name = albumName))
        }
    }

    fun updatePhoto(photo: Photo) {
        viewModelScope.launch {
            repository.updatePhoto(photo)
        }
    }

    fun deletePhoto(photo: Photo) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
        }
    }

    fun favoritePhotos(photos: List<Photo>) {
        viewModelScope.launch {
            photos.forEach { photo ->
                repository.updatePhoto(photo.copy(isFavorited = true))
            }
        }
    }

    fun deletePhotos(photos: List<Photo>) {
        viewModelScope.launch {
            repository.deletePhotos(photos)
        }
    }

    fun addPhotoToDefaultAlbum(imagePath: String) {
        viewModelScope.launch {
            var defaultAlbum = repository.getAlbumByName("默认相册")
            if (defaultAlbum == null) {
                val newAlbum = Album(name = "默认相册")
                repository.createAlbum(newAlbum)
                defaultAlbum = repository.getAlbumByName("默认相册")
            }
            defaultAlbum?.let {
                val photo = Photo(albumId = it.id, uri = imagePath)
                repository.insertPhoto(photo)
            }
        }
    }
}
