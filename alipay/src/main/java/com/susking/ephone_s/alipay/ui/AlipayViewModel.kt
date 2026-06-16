package com.susking.ephone_s.alipay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.repository.AlipayRepositoryImpl
import com.susking.ephone_s.aidata.domain.alipay.BillRecord
import com.susking.ephone_s.aidata.domain.alipay.WalletInfo
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * 支付宝主界面ViewModel
 */
class AlipayViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: AlipayRepository = AlipayRepositoryImpl(application)
    
    private val _walletInfo = MutableStateFlow<WalletInfo?>(null)
    val walletInfo: StateFlow<WalletInfo?> = _walletInfo.asStateFlow()
    
    private val _billList = MutableStateFlow<List<BillRecord>>(emptyList())
    val billList: StateFlow<List<BillRecord>> = _billList.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    companion object {
        private const val DEFAULT_USER_ID = "user_main"
        private const val DEFAULT_INITIAL_BALANCE = 10000.0 // 初始余额10000元
    }
    
    init {
        loadData(showLoading = false)
    }
    
    /**
     * 加载数据
     * @param showLoading 是否显示加载动画
     */
    private fun loadData(showLoading: Boolean = false) {
        if (showLoading) {
            _isLoading.value = true
        }
        
        // 加载钱包信息(使用单独的协程持续监听)
        viewModelScope.launch {
            try {
                repository.getWalletInfo(DEFAULT_USER_ID).collect { wallet ->
                    if (wallet == null) {
                        // 如果钱包不存在,初始化
                        repository.initializeWallet(
                            DEFAULT_USER_ID,
                            BigDecimal(DEFAULT_INITIAL_BALANCE)
                        )
                    } else {
                        _walletInfo.value = wallet
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载钱包信息失败: ${e.message}"
            }
        }
        
        // 加载账单列表(使用单独的协程持续监听)
        viewModelScope.launch {
            try {
                repository.getBillList(limit = 20).collect { bills ->
                    _billList.value = bills
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载账单失败: ${e.message}"
            }
        }
        
        // 如果显示加载动画,短暂延迟后关闭
        if (showLoading) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 刷新数据(手动刷新时显示加载动画)
     */
    fun refresh() {
        loadData(showLoading = true)
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * 添加测试账单(用于测试)
     */
    fun addTestBill() {
        viewModelScope.launch {
            try {
                repository.addBillRecord(
                    amount = BigDecimal("-50.00"),
                    type = "shopping",
                    description = "测试购物消费"
                )
            } catch (e: Exception) {
                _errorMessage.value = "添加账单失败: ${e.message}"
            }
        }
    }
}