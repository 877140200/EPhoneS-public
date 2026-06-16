package com.susking.ephone_s.qq.ui.backpack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.repository.BackpackRepository
import com.susking.ephone_s.qq.data.model.BackpackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 背包历史ViewModel
 * 
 * 负责背包历史数据的管理
 */
@HiltViewModel
class BackpackHistoryViewModel @Inject constructor(
    private val backpackRepository: BackpackRepository
) : ViewModel() {
    
    // 历史记录列表
    private val _historyItems = MutableStateFlow<List<BackpackItem>>(emptyList())
    val historyItems: StateFlow<List<BackpackItem>> = _historyItems.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    init {
        loadHistoryItems()
    }
    
    /**
     * 加载历史记录
     */
    private fun loadHistoryItems() {
        viewModelScope.launch {
            try {
                backpackRepository.getAllHistoryItems().collect { entities ->
                    _historyItems.value = entities.map { entity ->
                        BackpackItem(
                            id = entity.id,
                            productName = entity.productName,
                            imageUrl = entity.imageUrl,
                            price = entity.price,
                            source = entity.source,
                            obtainedTime = entity.obtainedTime,
                            orderId = entity.orderId,
                            isDiscarded = entity.isDiscarded,
                            operationType = entity.operationType,
                            operationTime = entity.operationTime,
                            giftRecipient = entity.giftRecipient
                        )
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载历史记录失败: ${e.message}"
            }
        }
    }
    
    /**
     * 删除单条历史记录
     */
    fun deleteHistoryItem(itemId: Long) {
        viewModelScope.launch {
            try {
                backpackRepository.deleteHistoryItem(itemId)
            } catch (e: Exception) {
                _errorMessage.value = "删除失败: ${e.message}"
            }
        }
    }
    
    /**
     * 清空所有历史记录
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                backpackRepository.clearAllHistory()
            } catch (e: Exception) {
                _errorMessage.value = "清空失败: ${e.message}"
            }
        }
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}