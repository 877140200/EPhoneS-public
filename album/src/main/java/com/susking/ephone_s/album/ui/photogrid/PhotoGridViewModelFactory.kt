package com.susking.ephone_s.album.ui.photogrid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.susking.ephone_s.album.domain.repository.AlbumRepository

class PhotoGridViewModelFactory(
    private val repository: AlbumRepository,
    private val albumId: Long?,
    private val isFavorites: Boolean
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PhotoGridViewModel::class.java)) {
            return PhotoGridViewModel(repository, albumId, isFavorites) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
