package com.susking.ephone_s.alipay.ui

import android.app.Dialog
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.data.local.AlipayDatabase
import com.susking.ephone_s.alipay.databinding.DialogPaymentConfirmationBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * 支付确认对话框
 * 显示订单金额、支付方式选择和确认支付按钮
 */
class PaymentConfirmationDialogFragment : DialogFragment() {

    private var _binding: DialogPaymentConfirmationBinding? = null
    private val binding get() = _binding!!

    private var orderAmount: Double = 0.0
    
    companion object {
        private const val ARG_ORDER_AMOUNT = "order_amount"
        private var onConfirmCallback: (() -> Unit)? = null

        /**
         * 创建对话框实例
         * @param orderAmount 订单金额
         * @param onConfirm 确认支付回调
         */
        fun newInstance(
            orderAmount: Double,
            onConfirm: () -> Unit
        ): PaymentConfirmationDialogFragment {
            onConfirmCallback = onConfirm
            return PaymentConfirmationDialogFragment().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_ORDER_AMOUNT, orderAmount)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogPaymentConfirmationBinding.inflate(layoutInflater)

        // 获取传入的订单金额
        orderAmount = arguments?.getDouble(ARG_ORDER_AMOUNT) ?: 0.0

        setupUI()
        loadBalance()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }


    /**
     * 设置UI
     */
    private fun setupUI() {
        // 显示订单金额
        binding.textOrderAmount.text = String.format("¥%.2f", orderAmount)

        // 取消按钮
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        // 确认支付按钮
        binding.buttonConfirm.setOnClickListener {
            onConfirmCallback?.invoke()
            onConfirmCallback = null
            dismiss()
        }

        // 支付方式卡片点击(当前只有支付宝余额,所以不需要处理)
        binding.cardAlipayBalance.setOnClickListener {
            // 当前已选中,无需处理
        }
    }

    /**
     * 加载支付宝余额
     */
    private fun loadBalance() {
        lifecycleScope.launch {
            try {
                val database = AlipayDatabase.Companion.getDatabase(requireContext())
                val wallet = database.alipayDao().getWallet("user_main").first()

                val balance = wallet?.balance ?: BigDecimal.ZERO
                binding.textBalance.text = String.format("余额: ¥%,.2f", balance)

                // 检查余额是否足够
                if (balance < BigDecimal(orderAmount)) {
                    binding.buttonConfirm.isEnabled = false
                    binding.textBalance.text = String.format("余额: ¥%,.2f (余额不足)", balance)
                    binding.textBalance.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.textBalance.text = "余额: 加载失败"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        onConfirmCallback = null
    }
}