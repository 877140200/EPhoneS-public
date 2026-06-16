package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.susking.ephone_s.qq.databinding.DialogEditImagePromptBinding

class EditImagePromptDialogFragment : DialogFragment() {

    private var _binding: DialogEditImagePromptBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditImagePromptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // 设置对话框的宽度为 MATCH_PARENT，高度为 WRAP_CONTENT
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentPrompt = arguments?.getString(ARG_CURRENT_PROMPT)
        val messageId = arguments?.getString(ARG_MESSAGE_ID)

        binding.promptEditText.setText(currentPrompt)

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.confirmButton.setOnClickListener {
            val newPrompt = binding.promptEditText.text.toString()
            if (messageId != null) {
                setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(
                        RESULT_MESSAGE_ID to messageId,
                        RESULT_NEW_PROMPT to newPrompt
                    )
                )
            }
            dismiss()
        }

        binding.aiRewriteButton.setOnClickListener {
            val specialRequirements = binding.specialRequirementsEditText.text.toString()
            val includeOriginalPrompt = binding.includeOriginalPromptCheckbox.isChecked
            if (messageId != null) {
                setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(
                        RESULT_MESSAGE_ID to messageId,
                        RESULT_ACTION to ACTION_AI_REWRITE,
                        RESULT_SPECIAL_REQUIREMENTS to specialRequirements,
                        RESULT_INCLUDE_ORIGINAL_PROMPT to includeOriginalPrompt
                    )
                )
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "edit_image_prompt_request"
        const val RESULT_MESSAGE_ID = "message_id"
        const val RESULT_NEW_PROMPT = "new_prompt"
        const val RESULT_ACTION = "action"
        const val ACTION_AI_REWRITE = "ai_rewrite"
        const val RESULT_SPECIAL_REQUIREMENTS = "special_requirements"
        const val RESULT_INCLUDE_ORIGINAL_PROMPT = "include_original_prompt"
        private const val ARG_CURRENT_PROMPT = "current_prompt"
        private const val ARG_MESSAGE_ID = "message_id"
        const val TAG = "EditImagePromptDialogFragment"

        fun newInstance(messageId: String, currentPrompt: String): EditImagePromptDialogFragment {
            return EditImagePromptDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MESSAGE_ID, messageId)
                    putString(ARG_CURRENT_PROMPT, currentPrompt)
                }
            }
        }
    }
}