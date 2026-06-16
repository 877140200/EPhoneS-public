package com.susking.ephone_s.shopping.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.model.ShoppingProduct
import com.susking.ephone_s.aidata.domain.repository.ShoppingCartRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 商品详情ViewModel
 *
 * 管理商品详情页的状态和业务逻辑
 */
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val productRepository: ShoppingProductRepository,
    private val cartRepository: ShoppingCartRepository
) : ViewModel() {
    
    // 商品信息
    private val _product = MutableStateFlow<ShoppingProduct?>(null)
    val product: StateFlow<ShoppingProduct?> = _product.asStateFlow()
    
    // 选中的款式索引
    private val _selectedVariationIndex = MutableStateFlow<Int?>(null)
    val selectedVariationIndex: StateFlow<Int?> = _selectedVariationIndex.asStateFlow()
    
    // 数量
    private val _quantity = MutableStateFlow(1)
    val quantity: StateFlow<Int> = _quantity.asStateFlow()
    
    // 加入购物车结果
    private val _addToCartResult = MutableStateFlow<Boolean?>(null)
    val addToCartResult: StateFlow<Boolean?> = _addToCartResult.asStateFlow()
    
    /**
     * 加载商品信息
     */
    fun loadProduct(productId: Long) {
        viewModelScope.launch {
            try {
                val product = productRepository.getProductById(productId)
                _product.value = product
                
                // 如果只有一个款式,自动选中
                if (product?.variations?.size == 1) {
                    _selectedVariationIndex.value = 0
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 选择款式
     * @param index 款式索引，传入null表示取消选择
     */
    fun selectVariation(index: Int?) {
        _selectedVariationIndex.value = index
    }
    
    /**
     * 增加数量
     */
    fun increaseQuantity() {
        _quantity.value = _quantity.value + 1
    }
    
    /**
     * 减少数量
     */
    fun decreaseQuantity() {
        if (_quantity.value > 1) {
            _quantity.value = _quantity.value - 1
        }
    }
    
    /**
     * 加入购物车
     */
    fun addToCart() {
        val currentProduct = _product.value ?: return
        
        // 如果有款式,必须选择款式
        if (currentProduct.variations.isNotEmpty() && _selectedVariationIndex.value == null) {
            _addToCartResult.value = false
            return
        }
        
        viewModelScope.launch {
            try {
                cartRepository.addToCart(
                    productId = currentProduct.id,
                    quantity = _quantity.value,
                    selectedVariationIndex = _selectedVariationIndex.value
                )
                _addToCartResult.value = true
                
                // 重置数量
                _quantity.value = 1
            } catch (e: Exception) {
                e.printStackTrace()
                _addToCartResult.value = false
            }
        }
    }
    
    /**
     * 获取当前价格
     * 如果选择了款式,返回款式价格,否则返回商品价格
     */
    fun getCurrentPrice(): Double {
        val currentProduct = _product.value ?: return 0.0
        val variationIndex = _selectedVariationIndex.value
        
        return if (variationIndex != null && variationIndex < currentProduct.variations.size) {
            currentProduct.variations[variationIndex].price
        } else {
            currentProduct.price
        }
    }
    
    /**
     * 获取总价
     */
    fun getTotalPrice(): Double {
        return getCurrentPrice() * _quantity.value
    }
}