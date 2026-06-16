package com.susking.ephone_s.settings.ui.novelai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.settings.R
import com.susking.ephone_s.settings.databinding.DialogNovelaiSettingsBinding

class NovelAiSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogNovelaiSettingsBinding? = null
    private val binding get() = _binding!!

    // 移除 SharedPreferences 引用

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 应用新的自定义对话框样式
        setStyle(STYLE_NORMAL, com.susking.ephone_s.core.R.style.Theme_EPhoneS_CustomDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNovelaiSettingsBinding.inflate(inflater, container, false)
        // 移除 SharedPreferences 初始化
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        loadSettings()
        setupListeners()
    }

    // [新增] 在对话框显示时调整其布局参数
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, // 宽度填充父容器
            ViewGroup.LayoutParams.WRAP_CONTENT // 高度根据内容自适应
        )
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { dismiss() }
        binding.buttonSave.setOnClickListener {
            saveSettings()
            Toast.makeText(context, "NovelAI 生成设置已保存", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        binding.buttonRestoreDefaults.setOnClickListener { restoreDefaults() }

        // 新增：监听 CORS 代理下拉菜单的变化，以控制自定义URL输入框的可见性
        binding.corsProxySpinner.doOnTextChanged { text, _, _, _ ->
            binding.customProxyLayout.isVisible = text.toString() == "自定义代理"
        }
    }

    private fun setupSpinners() {
        // 为下拉菜单填充根据截图更新后的选项
        val imageSizes = arrayOf(
            "纵向 (512x768)",
            "横向 (768x512)",
            "正方形 (640x640)",
            "竖图 (832x1216)",
            "横图 (1216x832)",
            "方图 (1024x1024)",
            "纵向 (1088x1920)",
            "风景 (1920x1088)"
        )
        val samplers = arrayOf(
            "Euler",
            "Euler Ancestral",
            "DPM++ 2S Ancestral",
            "DPM++ 2M",
            "DPM++ SDE",
            "DDIM"
        )
        val ucPresets = arrayOf(
            "Preset 0 - Heavy",
            "Preset 1 - Light",
            "Preset 2 - Human Focus",
            "Preset 3 - None"
        )
        val corsProxies = arrayOf(
            "直连 (无代理)",
            "corsproxy.io (推荐)",
            "allorigins.win",
            "cors-anywhere (需激活)",
            "自定义代理"
        )

        binding.imageSizeSpinner.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, imageSizes))
        binding.samplerSpinner.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, samplers))
        binding.ucPresetSpinner.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ucPresets))
        binding.corsProxySpinner.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, corsProxies))
    }

    private fun loadSettings() {
        // 使用 NovelAiSettingsRepository 加载设置
        binding.imageSizeSpinner.setText(AiDataApi.getSettingsRepository().getNovelAiResolution(), false)
        binding.stepsInput.setText(AiDataApi.getSettingsRepository().getNovelAiSteps().toString())
        binding.cfgScaleInput.setText(AiDataApi.getSettingsRepository().getNovelAiCfgScale().toString())
        binding.samplerSpinner.setText(AiDataApi.getSettingsRepository().getNovelAiSampler(), false)
        binding.seedInput.setText(AiDataApi.getSettingsRepository().getNovelAiSeed().toString())
        binding.ucPresetSpinner.setText(AiDataApi.getSettingsRepository().getNovelAiUcPreset(), false)
        binding.qualityTagsSwitch.isChecked = AiDataApi.getSettingsRepository().hasNovelAiQualityTags()
        binding.smeaSwitch.isChecked = AiDataApi.getSettingsRepository().useNovelAiSmea()
        binding.smeaDynSwitch.isChecked = AiDataApi.getSettingsRepository().useNovelAiSmeaDyn()
        binding.positivePromptInput.setText(AiDataApi.getSettingsRepository().getNovelAiPositivePrompt())
        binding.negativePromptInput.setText(AiDataApi.getSettingsRepository().getNovelAiNegativePrompt())
        binding.corsProxySpinner.setText(AiDataApi.getSettingsRepository().getNovelAiCorsProxy(), false)
        binding.customProxyInput.setText(AiDataApi.getSettingsRepository().getNovelAiCustomCorsProxyUrl())

        // 根据加载的值，更新自定义代理输入框的可见性
        binding.customProxyLayout.isVisible = AiDataApi.getSettingsRepository().getNovelAiCorsProxy() == "自定义代理"
    }

    private fun saveSettings() {
        // 使用 SettingsRepository 保存设置
        val repository = AiDataApi.getSettingsRepository()
        repository.saveNovelAiGenerationSettings(
            resolution = binding.imageSizeSpinner.text.toString(),
            steps = binding.stepsInput.text.toString().toIntOrNull() ?: 28,
            cfgScale = binding.cfgScaleInput.text.toString().toFloatOrNull() ?: 5.0f,
            sampler = binding.samplerSpinner.text.toString(),
            seed = binding.seedInput.text.toString().toIntOrNull() ?: 0,
            ucPreset = binding.ucPresetSpinner.text.toString(),
            qualityTags = binding.qualityTagsSwitch.isChecked,
            smea = binding.smeaSwitch.isChecked,
            smeaDyn = binding.smeaDynSwitch.isChecked,
            positivePrompt = binding.positivePromptInput.text.toString(),
            negativePrompt = binding.negativePromptInput.text.toString(),
            corsProxy = binding.corsProxySpinner.text.toString(),
            customCorsProxyUrl = binding.customProxyInput.text.toString()
        )
    }

    private fun restoreDefaults() {
        // 使用 NovelAiSettingsRepository 恢复默认值
        AiDataApi.getSettingsRepository().restoreNovelAiDefaults()
        loadSettings() // 重新加载默认值到UI
        Toast.makeText(context, "已恢复默认设置", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "NovelAiSettingsDialog"
        fun newInstance(): NovelAiSettingsDialogFragment = NovelAiSettingsDialogFragment()

        // 移除所有 SharedPreferences 常量
    }
}