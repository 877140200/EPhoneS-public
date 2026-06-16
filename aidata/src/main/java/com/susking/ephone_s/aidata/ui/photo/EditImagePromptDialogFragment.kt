package com.susking.ephone_s.aidata.ui.photo

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.databinding.DialogEditImagePromptBinding

/**
 * CPhone编辑图片提示词对话框
 */
class EditImagePromptDialogFragment : DialogFragment() {

    private var _binding: DialogEditImagePromptBinding? = null
    private val binding get() = _binding!!

    private var photoId: String = ""
    private var originalPrompt: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            photoId = it.getString(ARG_PHOTO_ID, "")
            originalPrompt = it.getString(ARG_ORIGINAL_PROMPT, "")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEditImagePromptBinding.inflate(LayoutInflater.from(context))

        binding.promptEditText.setText(originalPrompt)

        setupClickListeners()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return binding.root
    }

    private fun setupClickListeners() {
        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.confirmButton.setOnClickListener {
            val newPrompt = binding.promptEditText.text.toString().trim()
            if (newPrompt.isNotEmpty()) {
                setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(
                        RESULT_PHOTO_ID to photoId,
                        RESULT_NEW_PROMPT to newPrompt,
                        RESULT_ACTION to ACTION_REGENERATE
                    )
                )
                dismiss()
            }
        }

        binding.aiRewriteButton.setOnClickListener {
            val specialRequirements = binding.specialRequirementsEditText.text.toString().trim()
            val includeOriginalPrompt = binding.includeOriginalPromptCheckbox.isChecked

            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_PHOTO_ID to photoId,
                    RESULT_ACTION to ACTION_AI_REWRITE,
                    RESULT_SPECIAL_REQUIREMENTS to specialRequirements,
                    RESULT_INCLUDE_ORIGINAL_PROMPT to includeOriginalPrompt
                )
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CPhoneEditImagePromptDialog"
        const val REQUEST_KEY = "cphone_edit_image_prompt_request"

        const val RESULT_PHOTO_ID = "photo_id"
        const val RESULT_NEW_PROMPT = "new_prompt"
        const val RESULT_ACTION = "action"
        const val RESULT_SPECIAL_REQUIREMENTS = "special_requirements"
        const val RESULT_INCLUDE_ORIGINAL_PROMPT = "include_original_prompt"

        const val ACTION_REGENERATE = "regenerate"
        const val ACTION_AI_REWRITE = "ai_rewrite"

        private const val ARG_PHOTO_ID = "photo_id"
        private const val ARG_ORIGINAL_PROMPT = "original_prompt"

        fun newInstance(photoId: String, originalPrompt: String) =
            EditImagePromptDialogFragment().apply {
                arguments = bundleOf(
                    ARG_PHOTO_ID to photoId,
                    ARG_ORIGINAL_PROMPT to originalPrompt
                )
            }
    }
}