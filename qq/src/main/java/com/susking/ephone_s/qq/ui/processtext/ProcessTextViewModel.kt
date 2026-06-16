package com.susking.ephone_s.qq.ui.processtext

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.qq.domain.use_case.AddTextToFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 处理文本选择的ViewModel
 */
@HiltViewModel
class ProcessTextViewModel @Inject constructor(
    private val addTextToFavoriteUseCase: AddTextToFavoriteUseCase
) : ViewModel() {

    private val _state = MutableLiveData(ProcessTextState())
    val state: LiveData<ProcessTextState> = _state

    /**
     * 保存文本到收藏
     */
    fun saveToFavorites(text: String, source: String) {
        viewModelScope.launch {
            _state.value = _state.value?.copy(isSaving = true)
            addTextToFavoriteUseCase(text, source)
                .onSuccess {
                    _state.value = ProcessTextState(saveSuccess = true)
                }
                .onFailure { e ->
                    _state.value = ProcessTextState(errorMessage = "保存失败: ${e.message}")
                }
        }
    }
}

/**
 * 处理文本界面状态
 */
data class ProcessTextState(
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)