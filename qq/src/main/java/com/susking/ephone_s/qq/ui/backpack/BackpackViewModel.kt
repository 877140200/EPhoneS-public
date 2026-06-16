package com.susking.ephone_s.qq.ui.backpack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.entity.BackpackItemEntity
import com.susking.ephone_s.aidata.domain.repository.BackpackRepository
import com.susking.ephone_s.qq.data.model.BackpackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 背包ViewModel
 * 
 * 负责背包数据的管理和EventBus事件监听
 */
@HiltViewModel
class BackpackViewModel @Inject constructor(
    private val backpackRepository: BackpackRepository
) : ViewModel() {
    
    // 背包物品列表
    private val _items = MutableStateFlow<List<BackpackItem>>(emptyList())
    val items: StateFlow<List<BackpackItem>> = _items.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    init {
        loadItems()
    }
    
    /**
     * 加载背包物品
     */
    private fun loadItems() {
        viewModelScope.launch {
            try {
                backpackRepository.getAllItems().collect { entities ->
                    _items.value = entities.map { it.toBackpackItem() }
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载失败: ${e.message}"
            }
        }
    }
    
    /**
     * 丢弃物品
     * 使用新的recordItemOperation方法记录操作类型和时间
     */
    fun discardItem(itemId: Long) {
        viewModelScope.launch {
            try {
                backpackRepository.recordItemOperation(itemId, "discarded")
            } catch (e: Exception) {
                _errorMessage.value = "丢弃失败: ${e.message}"
            }
        }
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Entity转UI模型
     */
    private fun BackpackItemEntity.toBackpackItem(): BackpackItem {
        return BackpackItem(
            id = id,
            productName = productName,
            imageUrl = imageUrl,
            price = price,
            source = source,
            obtainedTime = obtainedTime,
            orderId = orderId,
            isDiscarded = isDiscarded,
            operationType = operationType,
            operationTime = operationTime,
            giftRecipient = giftRecipient
        )
    }
}