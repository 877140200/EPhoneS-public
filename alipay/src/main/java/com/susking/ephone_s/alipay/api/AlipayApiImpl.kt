package com.susking.ephone_s.alipay.api

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.susking.ephone_s.alipay.ui.AlipayFragment
import com.susking.ephone_s.alipay.ui.PaymentConfirmationDialogFragment

/**
 * 支付宝模块API实现类
 */
class AlipayApiImpl : AlipayApi {
    override fun createAlipayFragment(): Fragment {
        return AlipayFragment()
    }
    
    override fun showPaymentConfirmationDialog(
        fragmentManager: FragmentManager,
        orderAmount: Double,
        onConfirm: () -> Unit
    ) {
        val dialog = PaymentConfirmationDialogFragment.newInstance(
            orderAmount = orderAmount,
            onConfirm = onConfirm
        )
        dialog.show(fragmentManager, "payment_confirmation")
    }
}