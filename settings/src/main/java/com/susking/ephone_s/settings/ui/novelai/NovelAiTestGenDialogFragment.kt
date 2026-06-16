package com.susking.ephone_s.settings.ui.novelai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.gson.GsonBuilder
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.settings.databinding.DialogNovelaiTestGenerationBinding
import com.susking.ephone_s.core.ui.dialog.ConfirmAiPromptDialogFragment
import com.susking.ephone_s.settings.R
import kotlinx.coroutines.launch

class NovelAiTestGenDialogFragment : DialogFragment() {

    private var _binding: DialogNovelaiTestGenerationBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.susking.ephone_s.core.R.style.Theme_EPhoneS_CustomDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogNovelaiTestGenerationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置 Fragment Result 监听器来处理确认对话框的结果
        // 【修复】从 `setFragmentResultListener` 改为 `childFragmentManager.setFragmentResultListener`
        // 因为确认对话框是由 childFragmentManager 显示的，所以需要在这里监听结果
        childFragmentManager.setFragmentResultListener(ConfirmAiPromptDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val confirmed = bundle.getBoolean(ConfirmAiPromptDialogFragment.RESULT_CONFIRMED)
            if (confirmed) {
                // 用户确认，开始生成
                val positivePrompt = binding.testPromptInput.text.toString()
                startImageGeneration(positivePrompt)
            } else {
                // 用户取消，恢复UI
                showLoading(false)
            }
        }

        binding.toolbar.setNavigationOnClickListener { dismiss() }
        binding.generateButton.setOnClickListener {
            showConfirmationDialog()
        }
    }

    private fun showConfirmationDialog() {
        val positivePrompt = binding.testPromptInput.text.toString()
        if (positivePrompt.isBlank()) {
            Toast.makeText(context, "请输入正面提示词", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查 API Key
        val apiKey = AiDataApi.getSettingsRepository().getNovelAiApiKey()
        if (apiKey.isBlank()) {
            Toast.makeText(context, "请先在设置中配置 NovelAI API Key", Toast.LENGTH_LONG).show()
            return
        }

        // 显示加载状态，因为准备预览也需要一些时间
        showLoading(true)

        lifecycleScope.launch {
            // 注意：这里仅用于预览，实际生成时会重新构建
            val dummyProfile = PersonProfile(
                id = "test",
                remarkName = "测试角色",
                realName = "测试角色",
                persona = "",
                naiPromptSource = "system",
                naiPositivePrompt = null,
                naiNegativePrompt = null
            )

            // 通过 AiDataApi 获取预览数据
            val aiRequestService = AiDataApi.getAiRequestService()
            val previewData = aiRequestService.getGenerationPreview(positivePrompt, dummyProfile)

            if (previewData == null) {
                showLoading(false)
                binding.errorText.text = "无法构建预览，请检查设置和后台日志。"
                binding.errorText.isVisible = true
                return@launch
            }

            // 使用格式化后的JSON显示
            val gson = GsonBuilder().setPrettyPrinting().create()
            val prettyJson = gson.toJson(previewData!!.requestBody)

            // 显示对话框
            ConfirmAiPromptDialogFragment.newInstance(
                promptJson = prettyJson,
                url = previewData.finalUrl,
                model = previewData.model,
                timestamp = System.currentTimeMillis()
            ).show(childFragmentManager, ConfirmAiPromptDialogFragment.TAG)
        }
    }

    private fun startImageGeneration(positivePrompt: String) {
        // 加载状态已经在 showConfirmationDialog 中设置
        // showLoading(true)

        lifecycleScope.launch {
            // 注意：测试生成我们用一个临时的、空的PersonProfile，因为它会回退到使用系统默认的P/N词
            val dummyProfile = PersonProfile(
                id = "test",
                remarkName = "测试角色",
                realName = "测试角色",
                persona = "",
                naiPromptSource = "system",
                naiPositivePrompt = null,
                naiNegativePrompt = null
            )

            try {
                val aiRequestService = AiDataApi.getAiRequestService()
                val imageBase64 = aiRequestService.generateImage(positivePrompt, dummyProfile)

                // 如果用户在生成时关闭对话框，视图可能为空
                if (view == null) return@launch
                showLoading(false)

                if (imageBase64 != null) {
                    binding.errorText.isVisible = false
                    binding.resultImage.isVisible = true
                    // Glide可以直接加载Base64
                    Glide.with(this@NovelAiTestGenDialogFragment)
                        .load(imageBase64)
                        .into(binding.resultImage)
                } else {
                    // 理论上，由于异常被抛出，这个分支可能不会被走到，但在API Key为空时会
                    binding.resultImage.isVisible = false
                    binding.errorText.isVisible = true
                    binding.errorText.text = "生成失败，请检查API Key或后台日志。"
                }
            } catch (e: Exception) {
                if (view == null) return@launch
                showLoading(false)
                binding.resultImage.isVisible = false
                binding.errorText.isVisible = true
                binding.errorText.text = "生成异常: ${e.message}"
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.statusText.isVisible = isLoading
        binding.generateButton.isEnabled = !isLoading
        if(isLoading) {
            binding.errorText.isVisible = false
            binding.resultImage.isVisible = false
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "NovelAiTestGenDialog"
        fun newInstance(): NovelAiTestGenDialogFragment = NovelAiTestGenDialogFragment()
    }
}
