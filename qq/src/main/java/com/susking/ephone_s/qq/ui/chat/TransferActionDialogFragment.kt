package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.susking.ephone_s.qq.databinding.DialogTransferActionBinding

class TransferActionDialogFragment : DialogFragment() {

    private var _binding: DialogTransferActionBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private var onActionSelected: ((String) -> Unit)? = null
    private var listener: ChatMessageInteractionListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTransferActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.acceptButton.setOnClickListener {
            onActionSelected?.invoke(ACTION_ACCEPT)
            dismiss()
        }

        binding.declineButton.setOnClickListener {
            onActionSelected?.invoke(ACTION_DECLINE)
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TransferActionDialog"
        const val ACTION_ACCEPT = "accept"
        const val ACTION_DECLINE = "decline"

        fun newInstance(listener: ChatMessageInteractionListener, onActionSelected: (String) -> Unit): TransferActionDialogFragment {
            return TransferActionDialogFragment().apply {
                this.listener = listener
                this.onActionSelected = onActionSelected
            }
        }
    }
}