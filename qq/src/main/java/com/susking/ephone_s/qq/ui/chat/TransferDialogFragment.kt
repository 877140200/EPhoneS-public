package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.ShowPaymentDialogEvent
import com.susking.ephone_s.qq.databinding.DialogTransferBinding
import com.susking.ephone_s.qq.domain.manager.QqChatManager
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TransferDialogFragment : DialogFragment() {

    private var _binding: DialogTransferBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: QqViewModel by activityViewModels()
    @Inject lateinit var chatManager: QqChatManager
    
    private lateinit var contactId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = requireArguments().getString(ARG_CONTACT_ID)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.confirmButton.setOnClickListener {
            val amountString = binding.transferAmountEditText.text.toString()
            val notes = binding.transferNotesEditText.text.toString()

            if (amountString.isNotEmpty()) {
                val amount = amountString.toDoubleOrNull() ?: 0.0
                
                // 使用EventBus发送支付确认事件
                EventBus.post(
                    ShowPaymentDialogEvent(
                        orderAmount = amount,
                        onConfirm = {
                            // 支付成功后直接发送转账消息
                            chatManager.sendMessage(
                                contactId = contactId,
                                text = null,
                                imageUrl = null,
                                type = "transfer",
                                amount = amount,
                                notes = notes
                            )
                        }
                    )
                )
                dismiss()
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TransferDialogFragment"
        private const val ARG_CONTACT_ID = "contact_id"
        
        fun newInstance(contactId: String): TransferDialogFragment {
            val fragment = TransferDialogFragment()
            val args = Bundle()
            args.putString(ARG_CONTACT_ID, contactId)
            fragment.arguments = args
            return fragment
        }
    }
}