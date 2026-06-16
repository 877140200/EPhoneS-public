package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.susking.ephone_s.qq.databinding.DialogEditRawMessageBinding

class EditRawMessageDialogFragment : DialogFragment() {

    private var _binding: DialogEditRawMessageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditRawMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rawJson = arguments?.getString(ARG_RAW_JSON) ?: ""
        val cleanedJson = cleanJsonString(rawJson)
        
        // 美化JSON格式以便阅读和编辑
        try {
            val jsonElement = JsonParser.parseString(cleanedJson)
            val prettyJson = GsonBuilder().setPrettyPrinting().create().toJson(jsonElement)
            binding.rawMessageEditText.setText(prettyJson)
        } catch (e: Exception) {
            // 如果JSON格式不正确，则显示原始字符串
            binding.rawMessageEditText.setText(cleanedJson)
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.saveButton.setOnClickListener {
            val modifiedJson = binding.rawMessageEditText.text.toString()
            val cleanedModifiedJson = cleanJsonString(modifiedJson)
            // 校验修改后的内容是否是合法的JSON
            try {
                JsonParser.parseString(cleanedModifiedJson)
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_RAW_JSON to cleanedModifiedJson))
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "JSON格式无效，请检查后保存", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun cleanJsonString(rawJson: String): String {
        val trimmed = rawJson.trim()
        if (trimmed.startsWith("```json")) {
            return trimmed.removePrefix("```json").removeSuffix("```").trim()
        }
        if (trimmed.startsWith("```")) {
            return trimmed.removePrefix("```").removeSuffix("```").trim()
        }
        return trimmed
    }

    companion object {
        const val TAG = "EditRawMessageDialogFragment"
        const val REQUEST_KEY = "edit_raw_message_request"
        const val RESULT_RAW_JSON = "result_raw_json"
        private const val ARG_RAW_JSON = "arg_raw_json"

        fun newInstance(rawJson: String): EditRawMessageDialogFragment {
            return EditRawMessageDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RAW_JSON, rawJson)
                }
            }
        }
    }
}