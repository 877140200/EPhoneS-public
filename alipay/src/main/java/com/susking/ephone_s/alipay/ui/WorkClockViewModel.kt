package com.susking.ephone_s.alipay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.repository.AlipayRepositoryImpl
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.model.WorkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar

/**
 * 上班打卡ViewModel
 */
class WorkClockViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: AlipayRepository = AlipayRepositoryImpl(application)
    
    private val _workStatus = MutableStateFlow(WorkStatus(
        isWorking = false,
        canFinishWork = false,
        hasWorkedToday = false,
        workStartTime = 0,
        workEndTime = 0
    ))
    val workStatus: StateFlow<WorkStatus> = _workStatus.asStateFlow()
    
    private val _todayIncome = MutableStateFlow(0.0)
    val todayIncome: StateFlow<Double> = _todayIncome.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    init {
        checkAndAutoFinishWork()
        loadWorkStatus()
        loadTodayIncome()
    }
    
    /**
     * 检查并自动下班
     * 在ViewModel初始化时（即用户进入支付宝时）检查是否需要自动下班
     */
    private fun checkAndAutoFinishWork() {
        viewModelScope.launch {
            try {
                val earnedMoney = repository.checkAndAutoFinishWork()
                if (earnedMoney != null && earnedMoney > BigDecimal.ZERO) {
                    _errorMessage.value = "自动下班成功！今天赚了¥${earnedMoney}元"
                }
            } catch (e: Exception) {
                // 静默处理错误
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 加载工作状态
     */
    private fun loadWorkStatus() {
        viewModelScope.launch {
            try {
                repository.getWorkStatus().collect { status ->
                    _workStatus.value = status
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载工作状态失败: ${e.message}"
            }
        }
    }
    
    /**
     * 加载今日收入
     */
    private fun loadTodayIncome() {
        viewModelScope.launch {
            try {
                val today = getTodayStartTime()
                val tomorrow = getTomorrowStartTime()
                
                repository.getBillsByTimeRange(today, tomorrow).collect { bills ->
                    val income = bills
                        .filter { it.type == "salary" && it.amount > BigDecimal.ZERO }
                        .sumOf { it.amount.toDouble() }
                    _todayIncome.value = income
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载今日收入失败: ${e.message}"
            }
        }
    }
    
    /**
     * 切换工作状态(上班/下班)
     */
    fun toggleWorkStatus() {
        viewModelScope.launch {
            try {
                // 检查是否在工作时间内
                if (!isWithinWorkHours()) {
                    _errorMessage.value = "工作时间为早上8:00到晚上18:00，当前不在工作时间内"
                    return@launch
                }
                
                val currentStatus = _workStatus.value
                
                when {
                    !currentStatus.hasWorkedToday -> {
                        // 开始上班
                        val success = repository.startWork()
                        if (success) {
                            _errorMessage.value = "上班打卡成功！"
                        } else {
                            _errorMessage.value = "上班打卡失败，可能已过工作时间或今天已上过班"
                        }
                    }
                    currentStatus.isWorking && currentStatus.canFinishWork -> {
                        // 结束上班
                        val success = repository.finishWork()
                        if (success) {
                            _errorMessage.value = "下班打卡成功！工资已发放"
                        } else {
                            _errorMessage.value = "下班失败，请稍后再试"
                        }
                    }
                    currentStatus.isWorking && !currentStatus.canFinishWork -> {
                        _errorMessage.value = "需要工作满1小时才能下班哦"
                    }
                    else -> {
                        _errorMessage.value = "今天已经下班了，明天再来吧"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "操作失败: ${e.message}"
            }
        }
    }
    
    /**
     * 检查当前是否在工作时间内（早上8点到晚上6点）
     */
    private fun isWithinWorkHours(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour in 8..17 // 8:00 到 17:59 可以上班
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * 获取今天0点的时间戳
     */
    private fun getTodayStartTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    /**
     * 获取明天0点的时间戳
     */
    private fun getTomorrowStartTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}