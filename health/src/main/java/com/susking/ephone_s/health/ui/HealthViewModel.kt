package com.susking.ephone_s.health.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.entity.HealthDailyRecordEntity
import com.susking.ephone_s.aidata.domain.repository.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 健康页 ViewModel。
 *
 * 状态机：加载中 → (SDK 不可用 | 缺权限 | 就绪)。就绪时从 Room 的 Flow 持续观察近 N 天记录。
 * 同步动作（[syncNow]）由 Fragment 在打开/前台/亮屏时触发，结果回流到 Room 自动刷新 UI。
 */
@HiltViewModel
class HealthViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    /** 需要向用户申请的权限集合，供 Fragment 发起授权请求。 */
    val requiredPermissions: Set<String>
        get() = healthRepository.requiredPermissions

    init {
        observeRecords()
        refreshPermissionState()
    }

    /** 持续观察本地记录，一旦同步写入即刷新就绪态。 */
    private fun observeRecords() {
        healthRepository.observeRecentDays()
            .onEach { records ->
                // 仅在已就绪/加载态下用数据更新，避免覆盖"缺权限/不可用"提示。
                val current = _uiState.value
                if (current is HealthUiState.Ready || current is HealthUiState.Loading) {
                    // 计算最近同步信息（找最新的一条记录）
                    val syncInfo = computeSyncInfo(records)
                    _uiState.value = HealthUiState.Ready(records, syncInfo)
                }
            }
            .launchIn(viewModelScope)
    }

    /** 从记录列表中计算最近同步信息。 */
    private fun computeSyncInfo(records: List<HealthDailyRecordEntity>): String {
        val latest = records.maxByOrNull { it.lastSyncTime } ?: return "暂无同步记录"
        if (latest.lastSyncTime == 0L) return "暂无同步记录"

        val time = runCatching {
            val zone = java.time.ZoneId.systemDefault()
            val instant = java.time.Instant.ofEpochMilli(latest.lastSyncTime).atZone(zone)
            val month = instant.monthValue.toString().padStart(2, '0')
            val day = instant.dayOfMonth.toString().padStart(2, '0')
            val hour = instant.hour.toString().padStart(2, '0')
            val minute = instant.minute.toString().padStart(2, '0')
            "$month-$day $hour:$minute"
        }.getOrDefault("--")

        val types = latest.syncedDataTypes
        // 文字必须是本次同步到的新内容；无变化类目时只显示时间，避免出现"更新 无"。
        return if (types.isBlank()) "最近同步：$time" else "最近同步：$time · 更新 $types"
    }

    /** 重新评估 SDK 可用性与权限，决定展示哪种状态。 */
    fun refreshPermissionState() {
        viewModelScope.launch {
            when {
                !healthRepository.isHealthConnectAvailable() ->
                    _uiState.value = HealthUiState.Unavailable
                !healthRepository.hasAllPermissions() ->
                    _uiState.value = HealthUiState.NeedPermission
                else -> {
                    // 有权限：先标记就绪（数据由 observeRecords 回填），并触发一次同步。
                    if (_uiState.value !is HealthUiState.Ready) {
                        _uiState.value = HealthUiState.Ready(emptyList(), "暂无同步记录")
                    }
                    syncNow()
                }
            }
        }
    }

    /** 从 Health Connect 同步一次到本地库（无权限/不可用时静默跳过）。 */
    fun syncNow() {
        viewModelScope.launch {
            healthRepository.syncRecentDays()
        }
    }
}

/**
 * 健康页 UI 状态。
 */
sealed interface HealthUiState {
    /** 初始加载。 */
    data object Loading : HealthUiState

    /** Health Connect 在本设备不可用。 */
    data object Unavailable : HealthUiState

    /** 可用但尚未授予全部权限，需引导授权。 */
    data object NeedPermission : HealthUiState

    /** 就绪，携带近 N 天记录（可能为空列表，表示暂无数据）与最近同步信息。 */
    data class Ready(
        val records: List<HealthDailyRecordEntity>,
        val syncInfo: String = "暂无同步记录"
    ) : HealthUiState
}
