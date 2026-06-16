package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.qq.databinding.DialogEditImageDescriptionBinding

/**
 * 编辑图片描述的对话框
 * 用于编辑用户发送的图片消息的imageDescription字段
 */
class EditImageDescriptionDialogFragment : DialogFragment() {

    private var _binding: DialogEditImageDescriptionBinding? = null
    private val binding get() = _binding!!

    private lateinit var originalMessage: ChatMessage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            originalMessage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_MESSAGE, ChatMessage::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_MESSAGE)
            } ?: throw IllegalArgumentException("EditImageDescriptionDialogFragment requires a ChatMessage argument")
        } ?: throw IllegalArgumentException("EditImageDescriptionDialogFragment requires a ChatMessage argument")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditImageDescriptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // 设置对话框的宽度为 MATCH_PARENT，高度为 WRAP_CONTENT
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 填充原始图片描述到编辑框
        binding.imageDescriptionEditText.setText(originalMessage.imageDescription ?: "")
        binding.imageDescriptionEditText.requestFocus()
        
        // 如果有描述，将光标移到末尾
        if (!originalMessage.imageDescription.isNullOrBlank()) {
            binding.imageDescriptionEditText.setSelection(originalMessage.imageDescription!!.length)
        }

        binding.saveButton.setOnClickListener {
            val newDescription = binding.imageDescriptionEditText.text.toString().trim()
            // 允许保存空描述（清空描述）
            val resultBundle = Bundle().apply {
                putString(RESULT_MESSAGE_ID, originalMessage.id)
                putString(RESULT_NEW_DESCRIPTION, newDescription)
            }
            setFragmentResult(REQUEST_KEY, resultBundle)
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
        const val TAG = "EditImageDescriptionDialogFragment"
        const val REQUEST_KEY = "edit_image_description_request_key"
        const val RESULT_MESSAGE_ID = "result_message_id"
        const val RESULT_NEW_DESCRIPTION = "result_new_description"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(message: ChatMessage): EditImageDescriptionDialogFragment {
            return EditImageDescriptionDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MESSAGE, message)
                }
            }
        }
    }
}