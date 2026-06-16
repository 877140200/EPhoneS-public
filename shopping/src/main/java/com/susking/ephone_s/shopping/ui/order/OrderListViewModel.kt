package com.susking.ephone_s.shopping.ui.order

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.model.ShoppingOrder
import com.susking.ephone_s.aidata.domain.repository.ShoppingOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 订单列表ViewModel
 * 
 * 负责订单列表的展示和管理
 */
@HiltViewModel
class OrderListViewModel @Inject constructor(
    private val orderRepository: ShoppingOrderRepository
) : ViewModel() {
    
    // 订单列表
    private val _orders = MutableStateFlow<List<ShoppingOrder>>(emptyList())
    val orders: StateFlow<List<ShoppingOrder>> = _orders.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // UI事件
    private val _uiEvent = MutableLiveData<UiEvent>()
    val uiEvent: LiveData<UiEvent> = _uiEvent
    
    // 当前联系人ID(可选,用于筛选某个联系人的订单)
    private var currentChatId: String? = null
    
    init {
        loadOrders()
    }
    
    /**
     * 加载所有订单
     */
    fun loadOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (currentChatId != null) {
                    // 加载指定联系人的订单
                    orderRepository.getOrdersByChatId(currentChatId!!).collect { orderList ->
                        _orders.value = orderList
                    }
                } else {
                    // 加载所有订单
                    orderRepository.getAllOrders().collect { orderList ->
                        _orders.value = orderList
                    }
                }
            } catch (e: Exception) {
                _uiEvent.value = UiEvent.ShowError("加载订单失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 设置筛选的联系人
     */
    fun filterByChatId(chatId: String?) {
        currentChatId = chatId
        loadOrders()
    }
    
    /**
     * 删除订单
     */
    fun deleteOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                orderRepository.deleteOrder(orderId)
                _uiEvent.value = UiEvent.ShowSuccess("订单已删除")
            } catch (e: Exception) {
                _uiEvent.value = UiEvent.ShowError("删除失败: ${e.message}")
            }
        }
    }
    
    /**
     * 查看订单详情
     */
    fun viewOrderDetail(order: ShoppingOrder) {
        _uiEvent.value = UiEvent.NavigateToDetail(order)
    }
    
    /**
     * UI事件
     */
    sealed class UiEvent {
        data class ShowError(val message: String) : UiEvent()
        data class ShowSuccess(val message: String) : UiEvent()
        data class NavigateToDetail(val order: ShoppingOrder) : UiEvent()
    }
}