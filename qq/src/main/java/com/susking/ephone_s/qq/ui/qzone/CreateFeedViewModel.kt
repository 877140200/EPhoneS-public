package com.susking.ephone_s.qq.ui.qzone

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateFeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val personProfileRepository: PersonProfileRepository
) : ViewModel() {

    private val _feedCreationResult = MutableLiveData<Result<Unit>>()
    val feedCreationResult: LiveData<Result<Unit>> = _feedCreationResult

    fun createFeed(content: String) {
        viewModelScope.launch {
            try {
                // 获取当前用户信息
                val userProfile = personProfileRepository.getUserProfile()
                // 创建动态
                feedRepository.createFeed(userProfile.id, userProfile.nickname, content)
                _feedCreationResult.postValue(Result.success(Unit))
            } catch (e: Exception) {
                _feedCreationResult.postValue(Result.failure(e))
            }
        }
    }
}