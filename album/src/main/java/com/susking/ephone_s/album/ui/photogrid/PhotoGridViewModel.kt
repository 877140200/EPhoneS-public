package com.susking.ephone_s.album.ui.photogrid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.album.domain.model.Photo
import com.susking.ephone_s.album.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhotoGridViewModel(
    private val repository: AlbumRepository,
    private val albumId: Long?,
    private val isFavorites: Boolean
) : ViewModel() {

    val photos: StateFlow<List<Photo>> = if (isFavorites) {
        repository.getFavoritedPhotos()
    } else {
        repository.getPhotosByAlbum(albumId!!)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addPhoto(photoUri: String) {
        if (!isFavorites) {
            viewModelScope.launch {
                repository.addPhoto(Photo(uri = photoUri, albumId = albumId!!))
            }
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
}
