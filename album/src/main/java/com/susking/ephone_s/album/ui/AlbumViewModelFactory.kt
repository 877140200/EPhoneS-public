package com.susking.ephone_s.album.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.susking.ephone_s.album.domain.repository.AlbumRepository

class AlbumViewModelFactory(
    private val repository: AlbumRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlbumViewModel::class.java)) {
            return AlbumViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
