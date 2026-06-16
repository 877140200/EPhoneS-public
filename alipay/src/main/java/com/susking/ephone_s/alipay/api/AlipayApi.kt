package com.susking.ephone_s.alipay.api

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * 支付宝模块对外API接口
 * 提供给其他模块调用的接口
 */
interface AlipayApi {
    /**
     * 创建支付宝主界面Fragment
     * @return AlipayFragment实例
     */
    fun createAlipayFragment(): Fragment
    
    /**
     * 显示支付确认对话框
     * @param fragmentManager FragmentManager
     * @param orderAmount 订单金额
     * @param onConfirm 确认支付回调
     */
    fun showPaymentConfirmationDialog(
        fragmentManager: FragmentManager,
        orderAmount: Double,
        onConfirm: () -> Unit
    )
}