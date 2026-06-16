package com.susking.ephone_s.settings.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.model.ApiPreset
import com.susking.ephone_s.aidata.domain.model.ApiSettingsState
import com.susking.ephone_s.aidata.api.AiRequestService
import com.susking.ephone_s.aidata.api.TtsSynthesisRequest
import com.susking.ephone_s.aidata.api.TtsSynthesisResult
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * API设置 ViewModel
 * 使用 Hilt 注入 SettingsRepository
 */
@HiltViewModel
class ApiSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val aiRequestService: AiRequestService
) : ViewModel() {

    private val _settingsState = MutableLiveData<ApiSettingsState>()
    val settingsState: LiveData<ApiSettingsState> = _settingsState

    private val _models = MutableLiveData<List<String>>()
    val models: LiveData<List<String>> = _models

    private val _secondaryModels = MutableLiveData<List<String>>()
    val secondaryModels: LiveData<List<String>> = _secondaryModels

    private val _embeddingModels = MutableLiveData<List<String>>()
    val embeddingModels: LiveData<List<String>> = _embeddingModels

    private val _asrModels = MutableLiveData<List<String>>()
    val asrModels: LiveData<List<String>> = _asrModels

    private val _ttsModels = MutableLiveData<List<String>>()
    val ttsModels: LiveData<List<String>> = _ttsModels

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isTtsPreviewLoading = MutableLiveData<Boolean>(false)
    val isTtsPreviewLoading: LiveData<Boolean> = _isTtsPreviewLoading

    private val _ttsPreviewResult = MutableLiveData<TtsPreviewResult?>()
    val ttsPreviewResult: LiveData<TtsPreviewResult?> = _ttsPreviewResult

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _mainPresets = MutableLiveData<Map<String, ApiPreset>>()
    val mainPresets: LiveData<Map<String, ApiPreset>> = _mainPresets

    private val _secondaryPresets = MutableLiveData<Map<String, ApiPreset>>()
    val secondaryPresets: LiveData<Map<String, ApiPreset>> = _secondaryPresets

    private val _embeddingPresets = MutableLiveData<Map<String, ApiPreset>>()
    val embeddingPresets: LiveData<Map<String, ApiPreset>> = _embeddingPresets

    private val _asrPresets = MutableLiveData<Map<String, ApiPreset>>()
    val asrPresets: LiveData<Map<String, ApiPreset>> = _asrPresets

    init {
        loadSettings()
        loadPresets()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _settingsState.value = repository.loadSettings()
        }
    }

    private fun loadPresets() {
        viewModelScope.launch {
            _mainPresets.value = repository.loadPresets(API_TYPE_MAIN)
            _secondaryPresets.value = repository.loadPresets(API_TYPE_SECONDARY)
            _embeddingPresets.value = repository.loadPresets(API_TYPE_EMBEDDING)
            _asrPresets.value = repository.loadPresets(API_TYPE_ASR)
        }
    }

    fun saveMainPreset(preset: ApiPreset) {
        savePreset(API_TYPE_MAIN, preset)
    }

    fun saveSecondaryPreset(preset: ApiPreset) {
        savePreset(API_TYPE_SECONDARY, preset)
    }

    fun saveEmbeddingPreset(preset: ApiPreset) {
        savePreset(API_TYPE_EMBEDDING, preset)
    }

    fun saveAsrPreset(preset: ApiPreset) {
        savePreset(API_TYPE_ASR, preset)
    }

    fun deleteMainPreset(presetName: String) {
        deletePreset(API_TYPE_MAIN, presetName)
    }

    fun deleteSecondaryPreset(presetName: String) {
        deletePreset(API_TYPE_SECONDARY, presetName)
    }

    fun deleteEmbeddingPreset(presetName: String) {
        deletePreset(API_TYPE_EMBEDDING, presetName)
    }

    fun deleteAsrPreset(presetName: String) {
        deletePreset(API_TYPE_ASR, presetName)
    }

    fun selectMainPreset(presetName: String) {
        _mainPresets.value?.get(presetName)?.let { preset ->
            val currentState = _settingsState.value ?: ApiSettingsState()
            _settingsState.value = currentState.copy(
                mainApiUrl = preset.mainApiUrl,
                mainApiKey = preset.mainApiKey,
                mainModel = preset.mainApiModel,
                apiTemperature = preset.apiTemperature,
                apiPreset = presetName
            )
            saveSettings()
        }
    }

    fun selectSecondaryPreset(presetName: String) {
        _secondaryPresets.value?.get(presetName)?.let { preset ->
            val currentState = _settingsState.value ?: ApiSettingsState()
            _settingsState.value = currentState.copy(
                secondaryApiUrl = preset.mainApiUrl,
                secondaryApiKey = preset.mainApiKey,
                secondaryApiModel = preset.mainApiModel
            )
            saveSettings()
        }
    }

    fun selectEmbeddingPreset(presetName: String) {
        _embeddingPresets.value?.get(presetName)?.let { preset ->
            val currentState = _settingsState.value ?: ApiSettingsState()
            _settingsState.value = currentState.copy(
                embeddingApiUrl = preset.mainApiUrl,
                embeddingApiKey = preset.mainApiKey,
                embeddingModel = preset.mainApiModel
            )
            saveSettings()
        }
    }

    fun selectAsrPreset(presetName: String) {
        _asrPresets.value?.get(presetName)?.let { preset ->
            val currentState = _settingsState.value ?: ApiSettingsState()
            _settingsState.value = currentState.copy(
                asrApiUrl = preset.mainApiUrl,
                asrApiKey = preset.mainApiKey,
                asrModel = preset.mainApiModel
            )
            saveSettings()
        }
    }

    private fun savePreset(apiType: String, preset: ApiPreset) {
        viewModelScope.launch {
            repository.savePreset(apiType, preset)
            loadPresets()
        }
    }

    private fun deletePreset(apiType: String, presetName: String) {
        viewModelScope.launch {
            repository.deletePreset(apiType, presetName)
            loadPresets()
        }
    }

    fun saveSettings(newState: ApiSettingsState? = null) {
        val stateToSave = newState ?: _settingsState.value
        stateToSave?.let {
            _settingsState.value = it // 修复：在保存前，先更新ViewModel的内部状态
            viewModelScope.launch {
                repository.saveSettings(it)
            }
        }
    }

    fun fetchMainModels(baseUrl: String, apiKey: String) {
        // 确保 baseUrl 以 "/" 结尾，这是 Retrofit 的要求
        val correctedBaseUrl = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl

        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.fetchModels(correctedBaseUrl, apiKey)
            result.onSuccess { modelList ->
                _models.value = modelList
                if (modelList.isEmpty()) {
                    _error.value = "成功拉取，但模型列表为空。"
                }
            }.onFailure { exception ->
                _error.value = exception.message
            }
            _isLoading.value = false
        }
    }

    fun fetchSecondaryModels(baseUrl: String, apiKey: String) {
        // 确保 baseUrl 以 "/" 结尾，这是 Retrofit 的要求
        val correctedBaseUrl = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl

        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.fetchModels(correctedBaseUrl, apiKey)
            result.onSuccess { modelList ->
                _secondaryModels.value = modelList
                if (modelList.isEmpty()) {
                    _error.value = "成功拉取，但副模型列表为空。"
                }
            }.onFailure { exception ->
                _error.value = exception.message
            }
            _isLoading.value = false
        }
    }

    fun fetchEmbeddingModels(baseUrl: String, apiKey: String) {
        // 确保 baseUrl 以 "/" 结尾，这是 Retrofit 的要求
        val correctedBaseUrl = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl

        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.fetchModels(correctedBaseUrl, apiKey)
            result.onSuccess { modelList ->
                _embeddingModels.value = modelList
                if (modelList.isEmpty()) {
                    _error.value = "成功拉取，但 Embedding 模型列表为空。"
                }
            }.onFailure { exception ->
                _error.value = exception.message
            }
            _isLoading.value = false
        }
    }

    fun fetchAsrModels(baseUrl: String, apiKey: String) {
        // 确保 baseUrl 以 "/" 结尾，这是 Retrofit 的要求
        val correctedBaseUrl = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl

        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.fetchModels(correctedBaseUrl, apiKey)
            result.onSuccess { modelList ->
                _asrModels.value = modelList
                if (modelList.isEmpty()) {
                    _error.value = "成功拉取，但 ASR 模型列表为空。"
                }
            }.onFailure { exception ->
                _error.value = exception.message
            }
            _isLoading.value = false
        }
    }

    fun fetchTtsModels(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = aiRequestService.fetchTtsModelsWithLogging(baseUrl, apiKey)
            result.onSuccess { modelList ->
                _ttsModels.value = modelList
                if (modelList.isEmpty()) {
                    _error.value = "成功拉取，但 TTS 模型列表为空。"
                }
            }.onFailure { exception ->
                _error.value = exception.message
            }
            _isLoading.value = false
        }
    }

    fun updateEmbeddingApiSettings(url: String, apiKey: String, model: String) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(
            embeddingApiUrl = url,
            embeddingApiKey = apiKey,
            embeddingModel = model
        )
    }

    fun updateAsrApiSettings(url: String, apiKey: String, model: String) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(
            asrApiUrl = url,
            asrApiKey = apiKey,
            asrModel = model
        )
        saveSettings()
    }

    fun updateTtsApiSettings(url: String, apiKey: String, model: String) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(
            ttsApiUrl = url,
            ttsApiKey = apiKey,
            ttsModel = model
        )
        saveSettings()
    }

    fun updateTtsVoiceId(voiceId: String) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(ttsVoiceId = voiceId)
        saveSettings()
    }

    fun updateTtsStreamingEnabled(isEnabled: Boolean) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(isTtsStreamingEnabled = isEnabled)
        saveSettings()
    }

    fun updateAiReplyAutoTtsEnabled(isEnabled: Boolean) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(isAiReplyAutoTtsEnabled = isEnabled)
        saveSettings()
    }

    fun getTtsPreviewAudioPath(voiceId: String): String {
        return repository.getTtsPreviewAudioPath(voiceId)
    }

    fun saveTtsPreviewAudioPath(voiceId: String, audioPath: String): Unit {
        repository.saveTtsPreviewAudioPath(voiceId, audioPath)
    }

    fun previewTtsVoice(
        model: String,
        voiceId: String,
        isStreaming: Boolean,
        previewText: String
    ) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        if (currentState.ttsApiKey.isBlank()) {
            _error.value = "请先填写小米 MiMo TTS API Key。"
            return
        }
        if (model.isBlank()) {
            _error.value = "请先拉取并选择小米 MiMo TTS 模型。"
            return
        }
        if (voiceId.isBlank()) {
            _error.value = "请先选择要试听的 TTS 音色。"
            return
        }
        val finalPreviewText: String = previewText.ifBlank { TTS_PREVIEW_TEXT }
        viewModelScope.launch {
            _isTtsPreviewLoading.value = true
            val request = TtsSynthesisRequest(
                text = finalPreviewText,
                model = model,
                voiceId = voiceId,
                isStreaming = isStreaming,
                description = "设置页默认音色试听"
            )
            val result: TtsSynthesisResult = aiRequestService.synthesizeSpeechWithLogging(request)
            _ttsPreviewResult.value = TtsPreviewResult(
                audioPath = result.audioFile?.absolutePath,
                errorMessage = result.errorMessage,
                voiceId = result.voiceId
            )
            _isTtsPreviewLoading.value = false
        }
    }

    fun consumeTtsPreviewResult() {
        _ttsPreviewResult.value = null
    }

    fun onErorrShown() {
        _error.value = null
    }

    fun updateNovelAiEnabled(isEnabled: Boolean) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(isNovelAiEnabled = isEnabled)
        saveSettings() // 保存所有设置
    }

    fun updateBrainFloatingWindowEnabled(isEnabled: Boolean) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(isBrainFloatingWindowEnabled = isEnabled)
        // 立即同步保存悬浮窗设置,确保监听器能立即读取到新值
        repository.saveBrainFloatingWindowEnabled(isEnabled)
    }

    fun updateNovelAiApiKey(apiKey: String) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(novelAiApiKey = apiKey)
        saveSettings() // 立即保存 NovelAI API Key
    }

    fun updateNovelAiModel(model: String) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(novelAiModel = model)
    }

    fun updateBackgroundActivityEnabled(isEnabled: Boolean) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(isBackgroundActivityEnabled = isEnabled)
        saveSettings()
    }

    fun updateChatAutoReplyEnabled(isEnabled: Boolean) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(isChatAutoReplyEnabled = isEnabled)
        saveSettings()
    }

    fun updateChatRequestTimeoutSeconds(timeoutSeconds: Int) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(chatRequestTimeoutSeconds = timeoutSeconds.coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS))
        saveSettings()
    }

    fun updateChatAutoReplyIntervalSeconds(intervalSeconds: Int) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(chatAutoReplyIntervalSeconds = intervalSeconds.coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS))
        saveSettings()
    }

    fun updateChatFollowUpDelaySeconds(delaySeconds: Int) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(chatFollowUpDelaySeconds = delaySeconds.coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS))
        saveSettings()
    }

    // 打字速度：每字符延迟毫秒数。下限钳制由数据层 saveChatTypingDelayPerCharMillis 负责，
    // 此处仅保证不为 0 或负数，避免 UI 临时态出现非法值。
    fun updateChatTypingDelayPerCharMillis(perCharMillis: Int) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(chatTypingDelayPerCharMillis = perCharMillis.coerceAtLeast(MINIMUM_CHAT_TYPING_DELAY_PER_CHAR_MILLIS))
        saveSettings()
    }

    fun updateBackgroundActivityInterval(interval: Int) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(backgroundActivityInterval = interval)
        saveSettings()
    }

    fun updateAiCooldownPeriod(cooldown: Float) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(aiCooldownPeriod = cooldown)
        saveSettings()
    }

    fun updateChatListLoadCount(count: Int) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(chatListLoadCount = count)
    }

    fun updateChatInitialLoadCount(count: Int) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(chatInitialLoadCount = count)
    }

    fun updateBackgroundServiceEnabled(isEnabled: Boolean) {
        val currentState = _settingsState.value ?: ApiSettingsState()
        _settingsState.value = currentState.copy(isBackgroundServiceEnabled = isEnabled)
        saveSettings()
    }

    data class TtsPreviewResult(
        val audioPath: String?,
        val errorMessage: String?,
        val voiceId: String
    )

    companion object {
        private const val API_TYPE_MAIN: String = "main"
        private const val API_TYPE_SECONDARY: String = "secondary"
        private const val API_TYPE_EMBEDDING: String = "embedding"
        private const val API_TYPE_ASR: String = "asr"
        private const val MINIMUM_CHAT_TIMING_SECONDS: Int = 1
        // 打字速度（毫秒/字）安全钳制范围，与 SettingsRepositoryImpl 中的钳制保持一致
        private const val MINIMUM_CHAT_TYPING_DELAY_PER_CHAR_MILLIS: Int = 1
        private const val MAXIMUM_CHAT_TYPING_DELAY_PER_CHAR_MILLIS: Int = 500
        private const val TTS_PREVIEW_TEXT: String = "（开心）你好呀，这是小米 MiMo TTS 联系人音色试听。[笑]"
    }
}