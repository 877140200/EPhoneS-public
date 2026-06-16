package com.susking.ephone_s.health.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.health.SleepSessionDetail
import com.susking.ephone_s.aidata.domain.repository.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 睡眠详情页 ViewModel：加载指定日期的睡眠会话及分期段详情。
 */
@HiltViewModel
class SleepDetailViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SleepDetailUiState>(SleepDetailUiState.Loading)
    val uiState: StateFlow<SleepDetailUiState> = _uiState.asStateFlow()

    /** 加载指定日期的睡眠会话列表。 */
    fun loadSleepSessions(date: String) {
        viewModelScope.launch {
            _uiState.value = SleepDetailUiState.Loading
            val sessions = healthRepository.readSleepSessionsForDay(date)
            _uiState.value = if (sessions.isEmpty()) {
                SleepDetailUiState.Empty
            } else {
                SleepDetailUiState.Success(sessions)
            }
        }
    }
}

/**
 * 睡眠详情页 UI 状态。
 */
sealed interface SleepDetailUiState {
    /** 加载中。 */
    data object Loading : SleepDetailUiState

    /** 该日期无睡眠会话。 */
    data object Empty : SleepDetailUiState

    /** 加载成功，携带睡眠会话列表。 */
    data class Success(val sessions: List<SleepSessionDetail>) : SleepDetailUiState
}
