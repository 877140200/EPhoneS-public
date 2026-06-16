package com.susking.ephone_s.qq.ui.sticker

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import com.susking.ephone_s.qq.databinding.DialogUploadStickerBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.regex.Pattern

/**
 * 用于上传URL格式表情的对话框。
 * 用户可以输入多行"名称：URL"格式的文本，批量上传表情。
 */
@AndroidEntryPoint
class UploadStickerDialogFragment : DialogFragment() {

    private var _binding: DialogUploadStickerBinding? = null
    private val binding get() = _binding!!

    private val stickerViewModel: StickerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUploadStickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupListeners() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        binding.buttonUpload.setOnClickListener {
            handleUploadStickers()
        }
    }

    /**
     * 处理用户上传表情的逻辑。
     * 解析输入文本，创建StickerEntity列表，并批量保存。
     * 重名（与已有表情或本批次内其它行同名）的行不会被导入，会保留在文本框中并提示用户。
     * 成功导入与格式错误的行会从文本框中清除。
     */
    private fun handleUploadStickers() {
        val inputText: String = binding.inputStickerUrls.text.toString()
        val lines: List<String> = inputText.trim().split('\n')
        val newStickers: MutableList<StickerEntity> = mutableListOf()
        // 重名校验基准：已存在的表情名称 + 本批次已接受的名称
        val existingNames: MutableSet<String> = stickerViewModel.allStickers.value
            .map { it.name }
            .toMutableSet()
        // 保留在文本框中的重复行（原样回填，供用户修改后重试）
        val duplicateLines: MutableList<String> = mutableListOf()
        var errorCount: Int = 0

        val urlPattern: Pattern = Pattern.compile("^(.+?)[:：]\\s*(https?://[^\\s]+)$")

        for (line: String in lines) {
            val trimmedLine: String = line.trim()
            if (trimmedLine.isBlank()) {
                continue
            }

            val matcher = urlPattern.matcher(trimmedLine)
            if (matcher.matches()) {
                val name: String = matcher.group(1)?.trim() ?: ""
                val url: String = matcher.group(2)?.trim() ?: ""

                if (name.isNotBlank() && url.isNotBlank()) {
                    if (existingNames.contains(name)) {
                        // 重名：不导入，保留原始行
                        duplicateLines.add(trimmedLine)
                        Log.w(TAG, "批量导入存在重名，已保留此行: $trimmedLine")
                    } else {
                        newStickers.add(StickerEntity(id = 0, name = name, url = url, categoryId = null))
                        existingNames.add(name)
                    }
                } else {
                    errorCount++
                    Log.w(TAG, "批量导入格式错误，名称或URL为空: $trimmedLine")
                }
            } else {
                errorCount++
                Log.w(TAG, "批量导入格式错误，已跳过此行: $trimmedLine")
            }
        }

        if (errorCount > 0) {
            Toast.makeText(
                requireContext(),
                "有 $errorCount 行的格式不正确，已被系统跳过。",
                Toast.LENGTH_LONG
            ).show()
        }

        if (newStickers.isNotEmpty()) {
            stickerViewModel.insertStickers(newStickers)
            Toast.makeText(
                requireContext(),
                "已成功批量导入 ${newStickers.size} 个新表情！",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (duplicateLines.isNotEmpty()) {
            // 重名行回填文本框，成功与格式错误的行已被清除，提示用户处理重名
            binding.inputStickerUrls.setText(duplicateLines.joinToString("\n"))
            binding.inputStickerUrls.setSelection(binding.inputStickerUrls.text?.length ?: 0)
            Toast.makeText(
                requireContext(),
                "有 ${duplicateLines.size} 个表情名称重复，不能重名，已为你保留这些行",
                Toast.LENGTH_LONG
            ).show()
            // 存在重名时不关闭对话框，便于用户继续修改
            return
        }

        if (newStickers.isNotEmpty()) {
            dismiss()
        } else if (errorCount == 0) {
            Toast.makeText(
                requireContext(),
                "没有找到可导入的内容。请检查您粘贴的格式是否正确。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG: String = "UploadStickerDialog"
    }
}