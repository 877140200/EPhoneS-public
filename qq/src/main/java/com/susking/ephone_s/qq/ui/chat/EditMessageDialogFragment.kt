package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.qq.databinding.DialogEditMessageBinding

class EditMessageDialogFragment : DialogFragment() {

    private var _binding: DialogEditMessageBinding? = null
    private val binding get() = _binding!!

    private lateinit var originalMessage: ChatMessage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // 将 getSerializable 替换为 getParcelable
            // 对于 Android 13 (API 33) 及更高版本，推荐使用带 Class 参数的方法
            // 对于更低版本，可以使用 getParcelable<ChatMessage>(ARG_MESSAGE)
            originalMessage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_MESSAGE, ChatMessage::class.java)
            } else {
                @Suppress("DEPRECATION") // 兼容旧版本，抑制警告
                it.getParcelable(ARG_MESSAGE)
            } ?: throw IllegalArgumentException("EditMessageDialogFragment requires a ChatMessage argument")
        } ?: throw IllegalArgumentException("EditMessageDialogFragment requires a ChatMessage argument")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // 设置对话框的宽度为 MATCH_PARENT，高度为 WRAP_CONTENT
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 填充原始消息内容到编辑框
        binding.messageEditText.setText(originalMessage.content)
        binding.messageEditText.requestFocus()
        binding.messageEditText.setSelection(originalMessage.content!!.length)

        binding.saveButton.setOnClickListener {
            val newText = binding.messageEditText.text.toString()
            if (newText.isNotBlank()) {
                // 将更新后的消息ID和新文本通过 FragmentResultListener 返回给父Fragment
                val resultBundle = Bundle().apply {
                    putString(RESULT_MESSAGE_ID, originalMessage.id)
                    putString(RESULT_NEW_TEXT, newText)
                }
                setFragmentResult(REQUEST_KEY, resultBundle)
                dismiss() // 关闭对话框
            } else {
                Toast.makeText(requireContext(), "消息内容不能为空", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss() // 关闭对话框
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditMessageDialogFragment"
        const val REQUEST_KEY = "edit_message_request_key"
        const val RESULT_MESSAGE_ID = "result_message_id"
        const val RESULT_NEW_TEXT = "result_new_text"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(message: ChatMessage): EditMessageDialogFragment {
            return EditMessageDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MESSAGE, message) // 将 putSerializable 替换为 putParcelable
                }
            }
        }
    }
}