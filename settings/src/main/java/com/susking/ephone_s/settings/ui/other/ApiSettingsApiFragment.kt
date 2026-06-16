package com.susking.ephone_s.settings.ui.other

import android.R
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.susking.ephone_s.aidata.domain.model.ApiPreset
import com.susking.ephone_s.aidata.domain.model.ApiSettingsState
import com.susking.ephone_s.settings.databinding.FragmentApiSettingsApiBinding
import com.susking.ephone_s.settings.ui.main.ApiSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.Locale

@AndroidEntryPoint
class ApiSettingsApiFragment : Fragment() {

    private var _binding: FragmentApiSettingsApiBinding? = null
    private val binding: FragmentApiSettingsApiBinding get() = _binding!!
    private val viewModel: ApiSettingsViewModel by activityViewModels()
    private var ttsPreviewPlayer: MediaPlayer? = null
    private var latestTtsPreviewAudioPath: String? = null
    private var latestTtsPreviewVoiceId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiSettingsApiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun observeViewModel(): Unit {
        viewModel.settingsState.observe(viewLifecycleOwner) { state: ApiSettingsState ->
            bindSettingsState(state)
        }

        viewModel.models.observe(viewLifecycleOwner) { modelIds: List<String> ->
            bindModelAdapter(binding.mainApiModelSpinner, modelIds)
        }

        viewModel.secondaryModels.observe(viewLifecycleOwner) { modelIds: List<String> ->
            bindModelAdapter(binding.secondaryApiModelSpinner, modelIds)
        }

        viewModel.embeddingModels.observe(viewLifecycleOwner) { modelIds: List<String> ->
            bindModelAdapter(binding.embeddingApiModelSpinner, modelIds)
        }

        viewModel.asrModels.observe(viewLifecycleOwner) { modelIds: List<String> ->
            bindModelAdapter(binding.asrModelSpinner, modelIds)
        }

        viewModel.ttsModels.observe(viewLifecycleOwner) { modelIds: List<String> ->
            bindModelAdapter(binding.ttsModelSpinner, modelIds)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading: Boolean ->
            bindLoadingState(isLoading)
        }

        viewModel.isTtsPreviewLoading.observe(viewLifecycleOwner) { isLoading: Boolean ->
            bindTtsPreviewLoadingState(isLoading)
        }

        viewModel.ttsPreviewResult.observe(viewLifecycleOwner) { result: ApiSettingsViewModel.TtsPreviewResult? ->
            handleTtsPreviewResult(result)
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage: String? ->
            showError(errorMessage)
        }

        viewModel.mainPresets.observe(viewLifecycleOwner) { presets: Map<String, ApiPreset> ->
            bindPresetAdapter(binding.mainApiPresetSpinner, presets)
        }

        viewModel.secondaryPresets.observe(viewLifecycleOwner) { presets: Map<String, ApiPreset> ->
            bindPresetAdapter(binding.secondaryApiPresetSpinner, presets)
        }

        viewModel.embeddingPresets.observe(viewLifecycleOwner) { presets: Map<String, ApiPreset> ->
            bindPresetAdapter(binding.embeddingApiPresetSpinner, presets)
        }

        viewModel.asrPresets.observe(viewLifecycleOwner) { presets: Map<String, ApiPreset> ->
            bindPresetAdapter(binding.asrApiPresetSpinner, presets)
        }
    }

    private fun setupListeners(): Unit {
        setupFetchListeners()
        setupCollapseListeners()
        setupPresetListeners()
        binding.apiTemperatureSlider.addOnChangeListener { _, value: Float, _ ->
            updateTemperatureLabel(value)
        }
        setupTtsVoiceSpinner()
        setupTtsListeners()
    }

    private fun setupFetchListeners(): Unit {
        binding.buttonFetchMainModel.setOnClickListener {
            viewModel.fetchMainModels(
                baseUrl = binding.mainApiUrlInput.text.toString().trim(),
                apiKey = binding.mainApiKeyInput.text.toString().trim()
            )
        }

        binding.buttonFetchSecondaryModel.setOnClickListener {
            viewModel.fetchSecondaryModels(
                baseUrl = binding.secondaryApiUrlInput.text.toString().trim(),
                apiKey = binding.secondaryApiKeyInput.text.toString().trim()
            )
        }

        binding.buttonFetchEmbeddingModel.setOnClickListener {
            viewModel.fetchEmbeddingModels(
                baseUrl = binding.embeddingApiUrlInput.text.toString().trim(),
                apiKey = binding.embeddingApiKeyInput.text.toString().trim()
            )
        }

        binding.buttonFetchAsrModel.setOnClickListener {
            viewModel.fetchAsrModels(
                baseUrl = binding.asrApiUrlInput.text.toString().trim(),
                apiKey = binding.asrApiKeyInput.text.toString().trim()
            )
        }

        binding.buttonFetchTtsModel.setOnClickListener {
            viewModel.fetchTtsModels(
                baseUrl = binding.ttsApiUrlInput.text.toString().trim(),
                apiKey = binding.ttsApiKeyInput.text.toString().trim()
            )
        }
    }

    private fun setupCollapseListeners(): Unit {
        binding.buttonToggleMainApiFields.setOnClickListener {
            toggleContainer(binding.mainApiFieldsContainer, binding.buttonToggleMainApiFields)
        }
        binding.buttonToggleSecondaryApiFields.setOnClickListener {
            toggleContainer(binding.secondaryApiFieldsContainer, binding.buttonToggleSecondaryApiFields)
        }
        binding.buttonToggleEmbeddingApiFields.setOnClickListener {
            toggleContainer(binding.embeddingApiFieldsContainer, binding.buttonToggleEmbeddingApiFields)
        }
        binding.buttonToggleAsrApiFields.setOnClickListener {
            toggleContainer(binding.asrApiFieldsContainer, binding.buttonToggleAsrApiFields)
        }
        binding.buttonToggleTtsApiFields.setOnClickListener {
            toggleContainer(binding.ttsApiFieldsContainer, binding.buttonToggleTtsApiFields)
        }
    }

    private fun setupPresetListeners(): Unit {
        binding.mainApiPresetSpinner.setOnItemClickListener { _, _, position: Int, _ ->
            val presetName: String = binding.mainApiPresetSpinner.adapter.getItem(position) as String
            viewModel.selectMainPreset(presetName)
        }
        binding.secondaryApiPresetSpinner.setOnItemClickListener { _, _, position: Int, _ ->
            val presetName: String = binding.secondaryApiPresetSpinner.adapter.getItem(position) as String
            viewModel.selectSecondaryPreset(presetName)
        }
        binding.embeddingApiPresetSpinner.setOnItemClickListener { _, _, position: Int, _ ->
            val presetName: String = binding.embeddingApiPresetSpinner.adapter.getItem(position) as String
            viewModel.selectEmbeddingPreset(presetName)
        }
        binding.asrApiPresetSpinner.setOnItemClickListener { _, _, position: Int, _ ->
            val presetName: String = binding.asrApiPresetSpinner.adapter.getItem(position) as String
            viewModel.selectAsrPreset(presetName)
        }

        binding.buttonSaveMainPreset.setOnClickListener { saveMainPreset() }
        binding.buttonSaveSecondaryPreset.setOnClickListener { saveSecondaryPreset() }
        binding.buttonSaveEmbeddingPreset.setOnClickListener { saveEmbeddingPreset() }
        binding.buttonSaveAsrPreset.setOnClickListener { saveAsrPreset() }

        binding.buttonDeleteMainPreset.setOnClickListener {
            deletePreset(binding.mainApiPresetSpinner, viewModel::deleteMainPreset, "主API")
        }
        binding.buttonDeleteSecondaryPreset.setOnClickListener {
            deletePreset(binding.secondaryApiPresetSpinner, viewModel::deleteSecondaryPreset, "副API")
        }
        binding.buttonDeleteEmbeddingPreset.setOnClickListener {
            deletePreset(binding.embeddingApiPresetSpinner, viewModel::deleteEmbeddingPreset, "Embedding API")
        }
        binding.buttonDeleteAsrPreset.setOnClickListener {
            deletePreset(binding.asrApiPresetSpinner, viewModel::deleteAsrPreset, "ASR API")
        }
    }

    private fun setupTtsVoiceSpinner(): Unit {
        val voiceIds: List<String> = listOf("冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean")
        val adapter: ArrayAdapter<String> = ArrayAdapter(requireContext(), R.layout.simple_dropdown_item_1line, voiceIds)
        binding.ttsVoiceSpinner.setAdapter(adapter)
    }

    private fun setupTtsListeners(): Unit {
        // 监听 TTS 模型选择，若为 voicedesign 则隐藏相关控件
        binding.ttsModelSpinner.setOnItemClickListener { _, _, _, _ ->
            val selectedModel: String = binding.ttsModelSpinner.text.toString().trim()
            updateTtsUiForModel(selectedModel)
        }
        binding.ttsStreamingSwitch.setOnCheckedChangeListener { _, isChecked: Boolean ->
            if (viewModel.settingsState.value?.isTtsStreamingEnabled != isChecked) {
                viewModel.updateTtsStreamingEnabled(isChecked)
            }
        }
        binding.aiReplyAutoTtsSwitch.setOnCheckedChangeListener { _, isChecked: Boolean ->
            if (viewModel.settingsState.value?.isAiReplyAutoTtsEnabled != isChecked) {
                viewModel.updateAiReplyAutoTtsEnabled(isChecked)
            }
        }
        binding.ttsVoiceSpinner.setOnItemClickListener { _, _, _, _ ->
            val voiceId: String = binding.ttsVoiceSpinner.text.toString()
            viewModel.updateTtsVoiceId(voiceId)
            bindTtsPreviewAudioForVoiceId(voiceId)
        }
        binding.buttonPreviewTtsVoice.setOnClickListener {
            val model: String = binding.ttsModelSpinner.text.toString().trim()
            val voiceId: String = binding.ttsVoiceSpinner.text.toString().trim()
            val isStreaming: Boolean = binding.ttsStreamingSwitch.isChecked
            val previewText: String = binding.ttsPreviewTextInput.text?.toString()?.trim().orEmpty()
            viewModel.updateTtsApiSettings(
                url = binding.ttsApiUrlInput.text.toString().trim(),
                apiKey = binding.ttsApiKeyInput.text.toString().trim(),
                model = model
            )
            viewModel.updateTtsVoiceId(voiceId)
            binding.buttonPlayTtsPreviewAudio.isEnabled = false
            viewModel.previewTtsVoice(
                model = model,
                voiceId = voiceId,
                isStreaming = isStreaming,
                previewText = previewText
            )
        }
        binding.buttonPlayTtsPreviewAudio.setOnClickListener {
            val previewAudioPath: String = latestTtsPreviewAudioPath.orEmpty()
            if (previewAudioPath.isBlank() || !File(previewAudioPath).exists()) {
                Toast.makeText(requireContext(), "请先重新生成试听音频", Toast.LENGTH_SHORT).show()
                binding.buttonPlayTtsPreviewAudio.isEnabled = false
                return@setOnClickListener
            }
            playTtsPreview(previewAudioPath, latestTtsPreviewVoiceId.orEmpty())
        }
    }

    private fun bindSettingsState(state: ApiSettingsState): Unit {
        setTextIfChanged(binding.mainApiUrlInput, state.mainApiUrl)
        setTextIfChanged(binding.mainApiKeyInput, state.mainApiKey)
        setTextIfChanged(binding.mainApiModelSpinner, state.mainModel)
        setTextIfChanged(binding.mainApiPresetSpinner, state.apiPreset)
        setTextIfChanged(binding.secondaryApiUrlInput, state.secondaryApiUrl)
        setTextIfChanged(binding.secondaryApiKeyInput, state.secondaryApiKey)
        setTextIfChanged(binding.secondaryApiModelSpinner, state.secondaryApiModel)
        setTextIfChanged(binding.embeddingApiUrlInput, state.embeddingApiUrl)
        setTextIfChanged(binding.embeddingApiKeyInput, state.embeddingApiKey)
        setTextIfChanged(binding.embeddingApiModelSpinner, state.embeddingModel)
        setTextIfChanged(binding.asrApiUrlInput, state.asrApiUrl)
        setTextIfChanged(binding.asrApiKeyInput, state.asrApiKey)
        setTextIfChanged(binding.asrModelSpinner, state.asrModel)
        setTextIfChanged(binding.ttsApiUrlInput, state.ttsApiUrl)
        setTextIfChanged(binding.ttsApiKeyInput, state.ttsApiKey)
        setTextIfChanged(binding.ttsModelSpinner, state.ttsModel)
        setTextIfChanged(binding.ttsVoiceSpinner, state.ttsVoiceId)
        bindTtsPreviewAudioForVoiceId(state.ttsVoiceId)
        if (binding.ttsStreamingSwitch.isChecked != state.isTtsStreamingEnabled) {
            binding.ttsStreamingSwitch.isChecked = state.isTtsStreamingEnabled
        }
        if (binding.aiReplyAutoTtsSwitch.isChecked != state.isAiReplyAutoTtsEnabled) {
            binding.aiReplyAutoTtsSwitch.isChecked = state.isAiReplyAutoTtsEnabled
        }
        if (binding.apiTemperatureSlider.value != state.apiTemperature) {
            binding.apiTemperatureSlider.value = state.apiTemperature
        }
        updateTemperatureLabel(state.apiTemperature)
        // 根据当前 TTS 模型更新 UI 可见性
        updateTtsUiForModel(state.ttsModel)
    }

    private fun bindModelAdapter(spinner: AutoCompleteTextView, modelIds: List<String>): Unit {
        val adapter: ArrayAdapter<String> = ArrayAdapter(requireContext(), R.layout.simple_dropdown_item_1line, modelIds)
        spinner.setAdapter(adapter)
        if (modelIds.isNotEmpty() && spinner.text.isBlank()) {
            spinner.setText(modelIds.first(), false)
        }
    }

    private fun bindPresetAdapter(spinner: AutoCompleteTextView, presets: Map<String, ApiPreset>): Unit {
        val presetNames: List<String> = presets.keys.toList()
        val adapter: ArrayAdapter<String> = ArrayAdapter(requireContext(), R.layout.simple_dropdown_item_1line, presetNames)
        spinner.setAdapter(adapter)
    }

    private fun bindLoadingState(isLoading: Boolean): Unit {
        binding.buttonFetchMainModel.isEnabled = !isLoading
        binding.buttonFetchSecondaryModel.isEnabled = !isLoading
        binding.buttonFetchEmbeddingModel.isEnabled = !isLoading
        binding.buttonFetchAsrModel.isEnabled = !isLoading
        binding.buttonFetchTtsModel.isEnabled = !isLoading
        binding.buttonFetchMainModel.text = if (isLoading) "正在拉取..." else "拉取主模型"
        binding.buttonFetchSecondaryModel.text = if (isLoading) "正在拉取..." else "拉取副模型"
        binding.buttonFetchEmbeddingModel.text = if (isLoading) "正在拉取..." else "拉取 Embedding 模型"
        binding.buttonFetchAsrModel.text = if (isLoading) "正在拉取..." else "拉取 ASR 模型"
        binding.buttonFetchTtsModel.text = if (isLoading) "正在拉取..." else "拉取 TTS 模型"
    }

    private fun bindTtsPreviewLoadingState(isLoading: Boolean): Unit {
        binding.buttonPreviewTtsVoice.isEnabled = !isLoading
        binding.buttonPlayTtsPreviewAudio.isEnabled = !isLoading && !latestTtsPreviewAudioPath.isNullOrBlank()
        binding.buttonPreviewTtsVoice.text = if (isLoading) "正在生成..." else "重新生成试听音频"
    }

    private fun handleTtsPreviewResult(result: ApiSettingsViewModel.TtsPreviewResult?): Unit {
        if (result == null) return
        if (!result.errorMessage.isNullOrBlank()) {
            Toast.makeText(requireContext(), "试听失败：${result.errorMessage}", Toast.LENGTH_LONG).show()
            viewModel.consumeTtsPreviewResult()
            return
        }
        val audioPath: String = result.audioPath.orEmpty()
        if (audioPath.isBlank() || !File(audioPath).exists()) {
            Toast.makeText(requireContext(), "试听失败：没有可播放的语音文件", Toast.LENGTH_LONG).show()
            viewModel.consumeTtsPreviewResult()
            return
        }
        latestTtsPreviewAudioPath = audioPath
        latestTtsPreviewVoiceId = result.voiceId
        viewModel.saveTtsPreviewAudioPath(result.voiceId, audioPath)
        binding.buttonPlayTtsPreviewAudio.isEnabled = true
        Toast.makeText(requireContext(), "试听音频已生成，可点击播放", Toast.LENGTH_SHORT).show()
        viewModel.consumeTtsPreviewResult()
    }

    private fun bindTtsPreviewAudioForVoiceId(voiceId: String): Unit {
        val audioPath: String = viewModel.getTtsPreviewAudioPath(voiceId)
        val hasPlayableAudio: Boolean = audioPath.isNotBlank() && File(audioPath).exists()
        latestTtsPreviewAudioPath = audioPath.takeIf { hasPlayableAudio }
        latestTtsPreviewVoiceId = voiceId
        binding.buttonPlayTtsPreviewAudio.isEnabled = hasPlayableAudio && viewModel.isTtsPreviewLoading.value != true
    }

    private fun playTtsPreview(audioPath: String, voiceId: String): Unit {
        releaseTtsPreviewPlayer()
        ttsPreviewPlayer = MediaPlayer().apply {
            setDataSource(audioPath)
            setOnPreparedListener { player: MediaPlayer ->
                player.start()
                Toast.makeText(requireContext(), "正在试听 $voiceId", Toast.LENGTH_SHORT).show()
            }
            setOnCompletionListener {
                releaseTtsPreviewPlayer()
            }
            setOnErrorListener { _, _, _ ->
                releaseTtsPreviewPlayer()
                Toast.makeText(requireContext(), "试听播放失败", Toast.LENGTH_SHORT).show()
                true
            }
            prepareAsync()
        }
    }

    private fun releaseTtsPreviewPlayer(): Unit {
        ttsPreviewPlayer?.release()
        ttsPreviewPlayer = null
    }

    /**
     * 根据所选 TTS 模型更新 UI 可见性。
     * 若模型包含 voicedesign，则隐藏流式开关、默认音色、试听文本和试听按钮。
     */
    private fun updateTtsUiForModel(model: String): Unit {
        val isVoiceDesign: Boolean = model.contains("voicedesign", ignoreCase = true)
        val visibility: Int = if (isVoiceDesign) View.GONE else View.VISIBLE

        binding.ttsStreamingSwitch.visibility = visibility
        binding.ttsVoiceLayout.visibility = visibility
        binding.ttsPreviewTextLayout.visibility = visibility
        binding.buttonPreviewTtsVoice.visibility = visibility
        binding.buttonPlayTtsPreviewAudio.visibility = visibility
    }

    private fun showError(errorMessage: String?): Unit {
        if (errorMessage == null) return
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        viewModel.onErorrShown()
    }

    private fun toggleContainer(container: View, toggleButton: com.google.android.material.button.MaterialButton): Unit {
        val isVisible: Boolean = container.visibility == View.VISIBLE
        container.visibility = if (isVisible) View.GONE else View.VISIBLE
        toggleButton.text = if (isVisible) "展开" else "收起"
    }

    private fun saveMainPreset(): Unit {
        val presetName: String = binding.mainApiPresetSpinner.text.toString().trim()
        if (presetName.isBlank()) {
            showPresetNameRequiredToast()
            return
        }
        viewModel.saveMainPreset(
            ApiPreset(
                name = presetName,
                mainApiUrl = binding.mainApiUrlInput.text.toString(),
                mainApiKey = binding.mainApiKeyInput.text.toString(),
                mainApiModel = binding.mainApiModelSpinner.text.toString(),
                apiTemperature = binding.apiTemperatureSlider.value
            )
        )
        Toast.makeText(requireContext(), "主API预设 '$presetName' 已保存", Toast.LENGTH_SHORT).show()
    }

    private fun saveSecondaryPreset(): Unit {
        val presetName: String = binding.secondaryApiPresetSpinner.text.toString().trim()
        if (presetName.isBlank()) {
            showPresetNameRequiredToast()
            return
        }
        viewModel.saveSecondaryPreset(
            ApiPreset(
                name = presetName,
                mainApiUrl = binding.secondaryApiUrlInput.text.toString(),
                mainApiKey = binding.secondaryApiKeyInput.text.toString(),
                mainApiModel = binding.secondaryApiModelSpinner.text.toString()
            )
        )
        Toast.makeText(requireContext(), "副API预设 '$presetName' 已保存", Toast.LENGTH_SHORT).show()
    }

    private fun saveEmbeddingPreset(): Unit {
        val presetName: String = binding.embeddingApiPresetSpinner.text.toString().trim()
        if (presetName.isBlank()) {
            showPresetNameRequiredToast()
            return
        }
        viewModel.saveEmbeddingPreset(
            ApiPreset(
                name = presetName,
                mainApiUrl = binding.embeddingApiUrlInput.text.toString(),
                mainApiKey = binding.embeddingApiKeyInput.text.toString(),
                mainApiModel = binding.embeddingApiModelSpinner.text.toString()
            )
        )
        Toast.makeText(requireContext(), "Embedding API 预设 '$presetName' 已保存", Toast.LENGTH_SHORT).show()
    }

    private fun saveAsrPreset(): Unit {
        val presetName: String = binding.asrApiPresetSpinner.text.toString().trim()
        if (presetName.isBlank()) {
            showPresetNameRequiredToast()
            return
        }
        viewModel.saveAsrPreset(
            ApiPreset(
                name = presetName,
                mainApiUrl = binding.asrApiUrlInput.text.toString(),
                mainApiKey = binding.asrApiKeyInput.text.toString(),
                mainApiModel = binding.asrModelSpinner.text.toString()
            )
        )
        Toast.makeText(requireContext(), "ASR API 预设 '$presetName' 已保存", Toast.LENGTH_SHORT).show()
    }

    private fun deletePreset(
        spinner: AutoCompleteTextView,
        deleteAction: (String) -> Unit,
        label: String
    ): Unit {
        val presetName: String = spinner.text.toString().trim()
        if (presetName.isBlank()) {
            Toast.makeText(requireContext(), "请选择要删除的预设", Toast.LENGTH_SHORT).show()
            return
        }
        deleteAction(presetName)
        spinner.setText("", false)
        Toast.makeText(requireContext(), "$label 预设 '$presetName' 已删除", Toast.LENGTH_SHORT).show()
    }

    private fun showPresetNameRequiredToast(): Unit {
        Toast.makeText(requireContext(), "请输入预设名称", Toast.LENGTH_SHORT).show()
    }

    private fun setTextIfChanged(textView: AutoCompleteTextView, value: String): Unit {
        if (textView.text.toString() != value) {
            textView.setText(value, false)
        }
    }

    private fun setTextIfChanged(
        textView: com.google.android.material.textfield.TextInputEditText,
        value: String
    ): Unit {
        if (textView.text.toString() != value) {
            textView.setText(value)
        }
    }

    private fun updateTemperatureLabel(value: Float): Unit {
        binding.apiTemperatureLabel.text = String.format(Locale.US, "API 温度 (Temperature) %.1f", value)
    }

    fun getApiSettings(): Triple<String, String, String> {
        return Triple(
            binding.mainApiUrlInput.text.toString(),
            binding.mainApiKeyInput.text.toString(),
            binding.mainApiModelSpinner.text.toString()
        )
    }

    fun getPresetAndTemp(): Pair<String, Float> {
        return Pair(
            binding.mainApiPresetSpinner.text.toString(),
            binding.apiTemperatureSlider.value
        )
    }

    override fun onPause(): Unit {
        super.onPause()
        val currentState: ApiSettingsState = viewModel.settingsState.value ?: ApiSettingsState()
        val newState: ApiSettingsState = currentState.copy(
            mainApiUrl = binding.mainApiUrlInput.text.toString(),
            mainApiKey = binding.mainApiKeyInput.text.toString(),
            mainModel = binding.mainApiModelSpinner.text.toString(),
            secondaryApiUrl = binding.secondaryApiUrlInput.text.toString(),
            secondaryApiKey = binding.secondaryApiKeyInput.text.toString(),
            secondaryApiModel = binding.secondaryApiModelSpinner.text.toString(),
            embeddingApiUrl = binding.embeddingApiUrlInput.text.toString(),
            embeddingApiKey = binding.embeddingApiKeyInput.text.toString(),
            embeddingModel = binding.embeddingApiModelSpinner.text.toString(),
            asrApiUrl = binding.asrApiUrlInput.text.toString(),
            asrApiKey = binding.asrApiKeyInput.text.toString(),
            asrModel = binding.asrModelSpinner.text.toString(),
            ttsApiUrl = binding.ttsApiUrlInput.text.toString(),
            ttsApiKey = binding.ttsApiKeyInput.text.toString(),
            ttsModel = binding.ttsModelSpinner.text.toString(),
            ttsVoiceId = binding.ttsVoiceSpinner.text.toString(),
            isTtsStreamingEnabled = binding.ttsStreamingSwitch.isChecked,
            isAiReplyAutoTtsEnabled = binding.aiReplyAutoTtsSwitch.isChecked,
            apiPreset = binding.mainApiPresetSpinner.text.toString(),
            apiTemperature = binding.apiTemperatureSlider.value
        )
        viewModel.saveSettings(newState)
    }

    override fun onDestroyView(): Unit {
        releaseTtsPreviewPlayer()
        super.onDestroyView()
        _binding = null
    }
}