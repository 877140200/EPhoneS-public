package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.ShowPaymentDialogEvent
import com.susking.ephone_s.qq.databinding.DialogWaimaiOrderBinding
import com.susking.ephone_s.qq.domain.manager.QqTransactionManager
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WaimaiOrderDialogFragment : DialogFragment() {

    private var _binding: DialogWaimaiOrderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()
    
    @Inject lateinit var waimaiManager: QqTransactionManager
    
    private lateinit var contactId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.susking.ephone_s.core.R.style.Theme_EPhoneS_CustomDialog)
        contactId = requireArguments().getString(ARG_CONTACT_ID)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogWaimaiOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.orderForAiButton.setOnClickListener {
            val productInfo = binding.productInfoEditText.text.toString()
            val amount = binding.amountEditText.text.toString().toDoubleOrNull()
            if (productInfo.isNotBlank() && amount != null) {
                // 使用EventBus发送支付确认事件
                EventBus.post(
                    ShowPaymentDialogEvent(
                        orderAmount = amount,
                        onConfirm = {
                            // 支付成功后发送外卖订单
                            waimaiManager.sendWaimaiOrder(contactId, mapOf("productInfo" to productInfo, "amount" to amount))
                        }
                    )
                )
                dismiss()
            }
        }

        binding.requestPaymentButton.setOnClickListener {
            val productInfo = binding.productInfoEditText.text.toString()
            val amount = binding.amountEditText.text.toString().toDoubleOrNull()
            if (productInfo.isNotBlank() && amount != null) {
                waimaiManager.sendWaimaiRequest(contactId, mapOf("productInfo" to productInfo, "amount" to amount))
                dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        //  dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "WaimaiOrderDialogFragment"
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String): WaimaiOrderDialogFragment {
            val fragment = WaimaiOrderDialogFragment()
            val args = Bundle()
            args.putString(ARG_CONTACT_ID, contactId)
            fragment.arguments = args
            return fragment
        }
    }
}