package com.susking.ephone_s.qq.ui.chat.profile

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.qq.databinding.DialogEditInnerContentBinding

class EditInnerContentDialogFragment : DialogFragment() {

    private var _binding: DialogEditInnerContentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEditInnerContentBinding.inflate(layoutInflater)
        val title: String = requireArguments().getString(ARG_TITLE).orEmpty()
        val content: String = requireArguments().getString(ARG_CONTENT).orEmpty()
        val requestKey: String = requireArguments().getString(ARG_REQUEST_KEY) ?: REQUEST_KEY

        binding.titleTextView.text = title
        binding.contentEditText.setText(content)

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton("保存") { _, _ ->
                val newContent: String = binding.contentEditText.text.toString()
                setFragmentResult(requestKey, bundleOf(RESULT_CONTENT to newContent))
            }
            .setNegativeButton("取消", null)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditInnerContentDialogFragment"
        const val REQUEST_KEY = "edit_inner_content_request"
        const val RESULT_CONTENT = "new_content"

        private const val ARG_TITLE = "title"
        private const val ARG_CONTENT = "content"
        private const val ARG_REQUEST_KEY = "request_key"

        fun newInstance(title: String, content: String, requestKey: String = REQUEST_KEY): EditInnerContentDialogFragment {
            return EditInnerContentDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_CONTENT to content,
                    ARG_REQUEST_KEY to requestKey
                )
            }
        }
    }
}
