package com.susking.ephone_s.alipay

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.susking.ephone_s.alipay.api.AlipayApi
import com.susking.ephone_s.alipay.api.AlipayApiImpl

/**
 * 支付宝Fragment提供者实现
 * 用于在app模块中创建支付宝Fragment
 */
class AlipayFragmentProviderImpl : AlipayApi {
    private val alipayApi: AlipayApi = AlipayApiImpl()
    
    override fun createAlipayFragment(): Fragment {
        return alipayApi.createAlipayFragment()
    }
    
    override fun showPaymentConfirmationDialog(
        fragmentManager: FragmentManager,
        orderAmount: Double,
        onConfirm: () -> Unit
    ) {
        alipayApi.showPaymentConfirmationDialog(fragmentManager, orderAmount, onConfirm)
    }
}