package com.susking.ephone_s.qq.ui.chat

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.ShowPaymentDialogEvent
import com.susking.ephone_s.qq.databinding.DialogWaimaiDetailBinding
import com.susking.ephone_s.qq.domain.manager.QqTransactionManager
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WaimaiDetailDialogFragment : DialogFragment() {

    private var _binding: DialogWaimaiDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: QqViewModel by activityViewModels()
    
    @Inject lateinit var waimaiManager: QqTransactionManager
    @Inject lateinit var qqChatManager: com.susking.ephone_s.qq.domain.manager.QqChatManager

    private var message: ChatMessage? = null
    private var contactId: String = ""
    private var listener: ChatMessageInteractionListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.susking.ephone_s.core.R.style.Theme_EPhoneS_CustomDialog)
        arguments?.let {
            message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_MESSAGE, ChatMessage::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_MESSAGE)
            }
            contactId = message?.contactId ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogWaimaiDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindMessageDetails()
        setupClickListeners()
        observeMessageUpdates()
    }

    private fun bindMessageDetails() {
        message?.let { msg ->
            when (msg.type) {
                "waimai_order" -> {
                    binding.waimaiOrderDetailsLayout.isVisible = true
                    binding.waimaiRequestDetailsLayout.isVisible = false
                    binding.waimaiRequestActionsLayout.isVisible = false

                    val senderName = msg.senderName ?: (if (msg.role == "user") "我" else "对方")
                    val recipientName = msg.recipientName ?: (if (msg.role == "user") {
                        viewModel.contactManager.contacts.value?.find { it.id == msg.contactId }?.let { it.remarkName ?: it.realName } ?: "对方"
                    } else "我")


                    binding.waimaiOrderSender.text = "赠送方：$senderName"
                    binding.waimaiOrderRecipient.text = "接收方：$recipientName"
                    binding.waimaiOrderProduct.text = "商品：${msg.productInfo}"
                    binding.waimaiOrderAmount.text = "金额：${formatCurrency(msg.amount)}"
                    binding.waimaiOrderGreeting.isVisible = !msg.greeting.isNullOrBlank()
                    binding.waimaiOrderGreeting.text = "问候语：${msg.greeting}"
                }
                "waimai_request" -> {
                    binding.waimaiOrderDetailsLayout.isVisible = false
                    binding.waimaiRequestDetailsLayout.isVisible = true
                    
                    binding.waimaiRequestProduct.text = "商品：${msg.productInfo}"
                    binding.waimaiRequestAmount.text = "金额：${formatCurrency(msg.amount)}"
                    
                    if (msg.role == "user") {
                         binding.waimaiRequestStatus.text = when (msg.status) {
                            "pending" -> "状态：等待对方处理"
                            "paid" -> "状态：代付请求已被同意"
                            "rejected" -> "状态：代付请求已被拒绝"
                            else -> ""
                        }
                        binding.waimaiRequestActionsLayout.isVisible = false

                    } else {
                        binding.waimaiRequestStatus.text = when (msg.status) {
                            "pending" -> "状态：等待你处理"
                            "paid" -> "状态：你已同意代付"
                            "rejected" -> "状态：你已拒绝代付"
                            else -> ""
                        }
                        // Only show accept/reject buttons for pending requests received by the user
                        binding.waimaiRequestActionsLayout.isVisible = msg.status == "pending"
                    }
                }
            }
        }
    }

    private fun observeMessageUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                qqChatManager.getMessagesForContact(contactId).collect { messages ->
                    val updatedMessage = messages.find { it.id == message?.id }
                    if (updatedMessage != null) {
                        message = updatedMessage
                        bindMessageDetails()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.waimaiAcceptButton.setOnClickListener {
            // 接受代付请求需要支付确认
            message?.let { msg ->
                EventBus.post(
                    ShowPaymentDialogEvent(
                        orderAmount = msg.amount ?: 0.0,
                        onConfirm = {
                            // 支付成功后更新状态
                            updateRequestStatus("paid")
                        }
                    )
                )
            }
        }
        binding.waimaiRejectButton.setOnClickListener {
            updateRequestStatus("rejected")
        }
    }

    private fun updateRequestStatus(newStatus: String) {
        message?.let {
            waimaiManager.updateWaimaiRequestStatus(it.id, it.contactId, newStatus)
        }
        dismiss()
    }

    private fun formatCurrency(amount: Double?): String {
        return NumberFormat.getCurrencyInstance(Locale.CHINA).format(amount ?: 0.0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_MESSAGE = "message"
        const val TAG = "WaimaiDetailDialogFragment"

        fun newInstance(message: ChatMessage, listener: ChatMessageInteractionListener): WaimaiDetailDialogFragment {
            val fragment = WaimaiDetailDialogFragment()
            fragment.listener = listener
            val args = Bundle()
            args.putParcelable(ARG_MESSAGE, message)
            fragment.arguments = args
            return fragment
        }
    }
}