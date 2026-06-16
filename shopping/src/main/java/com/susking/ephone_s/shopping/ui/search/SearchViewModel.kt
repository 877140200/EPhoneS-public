package com.susking.ephone_s.shopping.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.model.ShoppingProduct
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 搜索ViewModel
 * 
 * 负责商品搜索逻辑
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val productRepository: ShoppingProductRepository
) : ViewModel() {
    
    // 搜索关键词
    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery
    
    // 搜索结果
    private val _searchResults = MutableStateFlow<List<ShoppingProduct>>(emptyList())
    val searchResults: StateFlow<List<ShoppingProduct>> = _searchResults.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // 导航事件
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()
    
    /**
     * 更新搜索关键词
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            performSearch(query)
        } else {
            _searchResults.value = emptyList()
        }
    }
    
    /**
     * 执行搜索
     */
    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                productRepository.searchProducts(query).collect { products ->
                    _searchResults.value = products
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 打开商品详情
     */
    fun openProductDetail(productId: Long) {
        _navigationEvent.value = NavigationEvent.ToProductDetail(productId)
    }
    
    /**
     * 导航事件
     */
    sealed class NavigationEvent {
        data class ToProductDetail(val productId: Long) : NavigationEvent()
    }
}