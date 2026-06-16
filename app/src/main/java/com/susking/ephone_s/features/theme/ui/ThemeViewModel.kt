package com.susking.ephone_s.features.theme.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.features.theme.domain.model.Theme
import com.susking.ephone_s.features.theme.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {

    val themes = themeRepository.getThemes().asLiveData()
    val currentTheme = themeRepository.getCurrentTheme().asLiveData()

    fun setCurrentTheme(theme: Theme): Unit {
        viewModelScope.launch {
            themeRepository.setCurrentTheme(theme)
        }
    }

    fun resetToDefaultTheme(): Unit {
        viewModelScope.launch {
            themeRepository.resetToDefaultTheme()
        }
    }

    fun createCustomThemeWithWallpaperCustomization(
        name: String,
        description: String,
        wallpaperUri: Uri,
        iconOverrides: Map<String, Uri>,
        dockedBrainImageUri: Uri?,
        draggingBrainImageUri: Uri?
    ): Unit {
        viewModelScope.launch {
            themeRepository.createCustomThemeWithWallpaperCustomization(
                name = name,
                description = description,
                wallpaperUri = wallpaperUri.toString(),
                iconOverrides = iconOverrides.mapValues { entry -> entry.value.toString() },
                dockedBrainImageUri = dockedBrainImageUri?.toString(),
                draggingBrainImageUri = draggingBrainImageUri?.toString()
            )
        }
    }

    fun pinTheme(theme: Theme): Unit {
        viewModelScope.launch {
            themeRepository.pinTheme(theme)
        }
    }

    fun updateCustomThemeAppearance(
        theme: Theme,
        name: String,
        description: String,
        wallpaperUri: Uri,
        iconOverrides: Map<String, Uri>,
        dockedBrainImageUri: Uri?,
        draggingBrainImageUri: Uri?
    ): Unit {
        viewModelScope.launch {
            themeRepository.updateCustomThemeAppearance(
                theme = theme,
                name = name,
                description = description,
                wallpaperUri = wallpaperUri.toString(),
                iconOverrides = iconOverrides.mapValues { entry -> entry.value.toString() },
                dockedBrainImageUri = dockedBrainImageUri?.toString(),
                draggingBrainImageUri = draggingBrainImageUri?.toString()
            )
        }
    }

    fun deleteCustomTheme(theme: Theme): Unit {
        viewModelScope.launch {
            themeRepository.deleteCustomTheme(theme)
        }
    }
}