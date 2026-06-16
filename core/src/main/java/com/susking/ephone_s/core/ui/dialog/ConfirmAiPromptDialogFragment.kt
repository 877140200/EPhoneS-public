package com.susking.ephone_s.core.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.core.databinding.DialogConfirmAiPromptBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfirmAiPromptDialogFragment : DialogFragment() {

    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogConfirmAiPromptBinding.inflate(requireActivity().layoutInflater, null, false)
        val displayPromptJson = requireArguments().getString(ARG_PROMPT_JSON, "")
        val targetUrl = requireArguments().getString(ARG_URL, "")
        val model = requireArguments().getString(ARG_MODEL, "")
        val timestamp = requireArguments().getLong(ARG_TIMESTAMP, 0)

        binding.textViewUrl.text = "发送到: $targetUrl"
        binding.textViewModel.text = "模型: $model"

        val formattedTime = if (timestamp > 0) {
            val date = Date(timestamp)
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            "时间: ${format.format(date)}"
        } else {
            "时间: 不可用"
        }
        binding.textViewTimestamp.text = formattedTime
        binding.textViewEstimatedTokenCount.text = "预计token数: ${estimateTokenCount(displayPromptJson)}"

        setupRecyclerView(binding, displayPromptJson)

        return AlertDialog.Builder(requireContext())
            .setTitle("确认发送")
            .setView(binding.root)
            .setPositiveButton("确认") { _, _ ->
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CONFIRMED to true))
            }
            .setNegativeButton("取消") { _, _ ->
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CONFIRMED to false))
            }
            .create()
    }

    private fun setupRecyclerView(binding: DialogConfirmAiPromptBinding, json: String) {
        lifecycleScope.launch(Dispatchers.Default) {
            val sections = parseAndGroupPrompt(json)
            withContext(Dispatchers.Main) {
                binding.promptRecyclerView.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = PromptSectionAdapter(sections)
                    // 启用垂直滚动条，让用户更容易看到滚动位置
                    isVerticalScrollBarEnabled = true
                    scrollBarStyle = android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
                }
            }
        }
    }

    private fun parseAndGroupPrompt(json: String): List<PromptSection> {
        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val promptMap = gson.fromJson<Map<String, Any>>(json, type)
            val messagesList = promptMap["messages"] as? List<Map<String, Any>> ?: return emptyList()

            return messagesList.mapIndexed { index, message ->
                val role = message["role"] as? String ?: "unknown"
                PromptSection(
                    title = "$role #${index + 1}",
                    content = formatJsonTextForDisplay(gson.toJson(message)),
                    isExpanded = index == messagesList.lastIndex // 默认展开最后一条消息
                )
            }

        } catch (e: JsonSyntaxException) {
            // 如果JSON解析失败，则将整个内容作为一个部分显示
            return listOf(
                PromptSection(
                    title = "原始JSON",
                    content = formatJsonTextForDisplay(json),
                    isExpanded = true
                )
            )
        }
    }

    private fun formatJsonTextForDisplay(text: String): String {
        val unicodePattern = Regex("""\\+u([0-9a-fA-F]{4})""")
        val textWithUnicodeCharacters = unicodePattern.replace(text) { matchResult ->
            matchResult.groupValues[1].toInt(16).toChar().toString()
        }
        return textWithUnicodeCharacters
            .replace(Regex("""\\+n"""), "\n")
            .replace(Regex("""\\+t"""), "\t")
            .replace(Regex("""\\+r"""), "\r")
            .replace(Regex("""\\+\""""), "\"")
            .replace(Regex("""\\+'"""), "'")
            .replace(Regex("""\\+/"""), "/")
    }

    private fun estimateTokenCount(text: String): Int {
        if (text.isBlank()) {
            return 0
        }

        val chineseCharacterCount: Int = text.count { character: Char -> isChineseCharacter(character) }
        val nonChineseText: String = text.filterNot { character: Char -> isChineseCharacter(character) }
        val nonChineseTokenCount: Int = (nonChineseText.length + TOKEN_CHARACTER_RATIO - 1) / TOKEN_CHARACTER_RATIO
        return chineseCharacterCount + nonChineseTokenCount
    }

    private fun isChineseCharacter(character: Char): Boolean {
        return character.code in CHINESE_CHARACTER_START_CODE..CHINESE_CHARACTER_END_CODE
    }

    companion object {
        const val TAG = "ConfirmAiPromptDialog"
        const val REQUEST_KEY = "ConfirmAiPromptRequest"
        const val RESULT_CONFIRMED = "confirmed"
        private const val ARG_PROMPT_JSON = "prompt_json"
        private const val ARG_URL = "url"
        private const val ARG_MODEL = "model"
        private const val ARG_TIMESTAMP = "timestamp"
        private const val TOKEN_CHARACTER_RATIO = 4
        private const val CHINESE_CHARACTER_START_CODE = 0x4E00
        private const val CHINESE_CHARACTER_END_CODE = 0x9FFF

        fun newInstance(promptJson: String, url: String, model: String, timestamp: Long): ConfirmAiPromptDialogFragment {
            return ConfirmAiPromptDialogFragment().apply {
                arguments = bundleOf(
                    ARG_PROMPT_JSON to promptJson,
                    ARG_URL to url,
                    ARG_MODEL to model,
                    ARG_TIMESTAMP to timestamp
                )
            }
        }
    }
}