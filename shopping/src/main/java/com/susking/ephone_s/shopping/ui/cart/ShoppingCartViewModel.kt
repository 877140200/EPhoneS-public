package com.susking.ephone_s.shopping.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.model.CartItem
import com.susking.ephone_s.aidata.domain.model.OrderProduct
import com.susking.ephone_s.aidata.domain.model.Recipient
import com.susking.ephone_s.aidata.domain.repository.ShoppingCartRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingOrderRepository
import com.susking.ephone_s.core.util.AddToBackpackEvent
import com.susking.ephone_s.core.util.EventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * 购物车ViewModel
 *
 * 管理购物车的状态和业务逻辑
 */
@HiltViewModel
class ShoppingCartViewModel @Inject constructor(
    private val cartRepository: ShoppingCartRepository,
    private val alipayRepository: AlipayRepository,
    private val orderRepository: ShoppingOrderRepository
) : ViewModel() {
    
    // 购物车商品列表
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()
    
    // 总金额
    private val _totalAmount = MutableStateFlow(0.0)
    val totalAmount: StateFlow<Double> = _totalAmount.asStateFlow()
    
    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // 支付成功消息
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    // 显示支付确认对话框事件
    private val _showPaymentDialog = MutableStateFlow(false)
    val showPaymentDialog: StateFlow<Boolean> = _showPaymentDialog.asStateFlow()
    
    init {
        loadCartItems()
    }
    
    /**
     * 加载购物车商品
     */
    private fun loadCartItems() {
        viewModelScope.launch {
            cartRepository.getAllCartItems().collect { items ->
                _cartItems.value = items
                calculateTotalAmount()
            }
        }
    }
    
    /**
     * 计算总金额
     */
    private fun calculateTotalAmount() {
        viewModelScope.launch {
            val total = cartRepository.calculateTotalAmount()
            _totalAmount.value = total
        }
    }
    
    /**
     * 更新商品数量
     */
    fun updateQuantity(itemId: Long, newQuantity: Int) {
        viewModelScope.launch {
            try {
                cartRepository.updateQuantity(itemId, newQuantity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 删除商品
     */
    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            try {
                cartRepository.removeFromCart(itemId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 清空购物车
     */
    fun clearCart() {
        viewModelScope.launch {
            try {
                cartRepository.clearCart()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 结算 - 显示支付确认对话框
     */
    fun checkout() {
        // 检查购物车是否为空
        if (_cartItems.value.isEmpty()) {
            _errorMessage.value = "购物车为空"
            return
        }
        
        // 检查金额
        if (_totalAmount.value <= 0) {
            _errorMessage.value = "订单金额错误"
            return
        }
        
        // 显示支付确认对话框
        _showPaymentDialog.value = true
    }
    
    /**
     * 确认支付 - 执行实际支付逻辑
     */
    fun confirmPayment() {
        viewModelScope.launch {
            try {
                val total = _totalAmount.value
                val items = _cartItems.value
                
                // 获取钱包余额
                val wallet = alipayRepository.getWalletInfo("user_main").first()
                val currentBalance = wallet?.balance ?: BigDecimal.ZERO
                val paymentAmount = BigDecimal(total)
                
                // 再次检查余额是否足够
                if (currentBalance < paymentAmount) {
                    _errorMessage.value = "余额不足,当前余额: ¥${currentBalance},需要支付: ¥${String.format("%.2f", total)}"
                    return@launch
                }
                
                // 执行支付 - 扣款并创建账单
                alipayRepository.performTransaction(
                    amount = paymentAmount.negate(), // 负数表示支出
                    type = "购物",
                    description = "商城购物支付"
                )
                
                // 创建订单
                val orderProducts = items.map { item ->
                    OrderProduct(
                        name = item.product.name,
                        price = item.getActualPrice(),
                        quantity = item.quantity,
                        imageUrl = item.getActualImageUrl(),
                        variationName = item.getVariationName()
                    )
                }
                
                // 使用默认收货信息
                val recipient = Recipient(
                    name = "默认收货人",
                    phone = "138****8888",
                    address = "默认收货地址"
                )
                
                val orderId = orderRepository.createOrder(
                    chatId = "shopping_mall", // 商城订单的chatId
                    products = orderProducts,
                    totalAmount = total,
                    recipient = recipient,
                    note = null
                )
                
                // 支付成功后,将每个商品添加到背包
                orderProducts.forEach { product ->
                    EventBus.post(
                        AddToBackpackEvent(
                            productName = product.name,
                            imageUrl = product.imageUrl,
                            price = product.price,
                            orderId = orderId,
                            source = "购物商城"
                        )
                    )
                }
                
                // 支付成功,清空购物车
                cartRepository.clearCart()
                
                // 显示成功消息
                _successMessage.value = "支付成功! ¥${String.format("%.2f", total)}"
                
            } catch (e: Exception) {
                _errorMessage.value = "支付失败: ${e.message}"
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 重置支付对话框状态
     */
    fun resetPaymentDialogState() {
        _showPaymentDialog.value = false
    }
    
    /**
     * 清除错误消息
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * 清除成功消息
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }
}