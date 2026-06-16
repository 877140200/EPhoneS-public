
package com.susking.ephone_s.aidata.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.model.ApiPreset
import com.susking.ephone_s.aidata.domain.model.ApiSettingsState
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * SettingsRepository 接口的实现类。
 * 封装了所有对 SharedPreferences 的读写操作，统一管理应用的各项设置。
 * @param context 应用上下文，用于获取 SharedPreferences 实例。
 */
class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private val gson = Gson()

    private val mainPrefs: SharedPreferences = context.getSharedPreferences(GLOBAL_PREFS_NAME, Context.MODE_PRIVATE)
    private val novelAiGenPrefs: SharedPreferences = context.getSharedPreferences(GEN_PREFS_NAME, Context.MODE_PRIVATE)
    private val _newFeedsCount = MutableStateFlow(0)
    private val _brainFloatingWindowEnabled = MutableStateFlow(false)
    private val _backgroundServiceEnabled = MutableStateFlow(true)
    private val _chatAutoReplyEnabled = MutableStateFlow(false)
    init {
        _newFeedsCount.value = getNewFeedsCount()
        _brainFloatingWindowEnabled.value = isBrainFloatingWindowEnabled()
        _backgroundServiceEnabled.value = isBackgroundServiceEnabled()
        _chatAutoReplyEnabled.value = isChatAutoReplyEnabled()
        clearLegacyGlobalApiPresets()
    }
    
    companion object {
        private const val SILICON_FLOW_LEGACY_HOST: String = "api.siliconflow.com"
        private const val SILICON_FLOW_OFFICIAL_HOST: String = "api.siliconflow.cn"
        // --- SharedPreferences 文件名和键名常量 ---

        // 全局设置 (之前在 ApiSettingsRepository 和 NovelAiSettingsRepository 的一部分)
        private const val GLOBAL_PREFS_NAME = "ephone_api_settings"
        private const val KEY_MAIN_API_URL = "main_api_url"
        private const val KEY_MAIN_API_KEY = "main_api_key"
        private const val KEY_MAIN_MODEL = "main_api_model"
        private const val KEY_API_TEMPERATURE = "api_temperature"
        private const val KEY_SECONDARY_API_URL = "secondary_api_url"
        private const val KEY_SECONDARY_API_KEY = "secondary_api_key"
        private const val KEY_SECONDARY_API_MODEL = "secondary_api_model"
        private const val KEY_EMBEDDING_API_URL = "embedding_api_url"
        private const val KEY_EMBEDDING_API_KEY = "embedding_api_key"
        private const val KEY_EMBEDDING_MODEL = "embedding_model"
        private const val KEY_ASR_API_URL = "asr_api_url"
        private const val KEY_ASR_API_KEY = "asr_api_key"
        private const val KEY_ASR_MODEL = "asr_model"
        private const val DEFAULT_ASR_API_URL = "https://api.siliconflow.cn"
        private const val DEFAULT_ASR_MODEL = "FunAudioLLM/SenseVoiceSmall"
        private const val KEY_TTS_API_URL = "tts_api_url"
        private const val KEY_TTS_API_KEY = "tts_api_key"
        private const val KEY_TTS_MODEL = "tts_model"
        private const val KEY_TTS_VOICE_ID = "tts_voice_id"
        private const val KEY_TTS_PREVIEW_AUDIO_PATH_PREFIX = "tts_preview_audio_path_"
        private const val KEY_TTS_STREAMING_ENABLED = "tts_streaming_enabled"
        private const val KEY_AI_REPLY_AUTO_TTS_ENABLED = "ai_reply_auto_tts_enabled"
        private const val DEFAULT_TTS_API_URL = "https://api.xiaomimimo.com/v1"
        private const val DEFAULT_TTS_VOICE_ID = "冰糖"
        private const val KEY_ENABLE_NOVEL_AI = "enable_novel_ai"
        private const val KEY_NOVEL_AI_MODEL = "novel_ai_model"
        private const val KEY_NOVEL_AI_API_KEY = "novel_ai_api_key"
        private const val KEY_ENABLE_BRAIN_FLOATING_WINDOW = "brain_floating_window_enabled"
        private const val KEY_BACKGROUND_ACTIVITY_ENABLED = "background_activity_enabled"
        private const val KEY_BACKGROUND_ACTIVITY_INTERVAL = "background_activity_interval"
        private const val KEY_AI_COOLDOWN_PERIOD = "ai_cooldown_period"
        private const val KEY_NEW_FEEDS_COUNT = "new_feeds_count"
        private const val KEY_FAVORITES_DISPLAY_MODE = "favorites_display_mode"
        private const val KEY_CHAT_LIST_LOAD_COUNT = "chat_list_load_count"
        private const val KEY_CHAT_INITIAL_LOAD_COUNT = "chat_initial_load_count"
        private const val KEY_FEEDS_BACKGROUND = "feeds_background"
        private const val KEY_BACKGROUND_SERVICE_ENABLED = "background_service_enabled"
        private const val KEY_CHAT_AUTO_REPLY_ENABLED = "chat_auto_reply_enabled"
        private const val KEY_CHAT_REQUEST_TIMEOUT_SECONDS = "chat_request_timeout_seconds"
        private const val KEY_CHAT_AUTO_REPLY_INTERVAL_SECONDS = "chat_auto_reply_interval_seconds"
        private const val KEY_CHAT_FOLLOW_UP_DELAY_SECONDS = "chat_follow_up_delay_seconds"
        // 打字速度：AI 逐条发消息时，每个字符增加的延迟毫秒数，数值越大越像真人慢打字
        private const val KEY_CHAT_TYPING_DELAY_PER_CHAR_MILLIS = "chat_typing_delay_per_char_millis"
        private const val KEY_LEGACY_API_PRESETS = "api_presets"
        private const val DEFAULT_CHAT_REQUEST_TIMEOUT_SECONDS: Int = 180
        private const val DEFAULT_CHAT_AUTO_REPLY_INTERVAL_SECONDS: Int = 5
        private const val DEFAULT_CHAT_FOLLOW_UP_DELAY_SECONDS: Int = 1200
        private const val DEFAULT_CHAT_TYPING_DELAY_PER_CHAR_MILLIS: Int = 60
        // 打字速度安全钳制范围：下限 1ms（不能为 0 或负），上限 500ms（防止单字过慢卡到天荒地老）
        private const val MINIMUM_CHAT_TYPING_DELAY_PER_CHAR_MILLIS: Int = 1
        private const val MAXIMUM_CHAT_TYPING_DELAY_PER_CHAR_MILLIS: Int = 500

        // NovelAI 生成设置
        private const val GEN_PREFS_NAME = "novelai_generation_settings"
        private const val KEY_IMAGE_SIZE = "image_size"
        private const val KEY_STEPS = "steps"
        private const val KEY_CFG_SCALE = "cfg_scale"
        private const val KEY_SAMPLER = "sampler"
        private const val KEY_SEED = "seed"
        private const val KEY_UC_PRESET = "uc_preset"
        private const val KEY_QUALITY_TAGS = "quality_tags"
        private const val KEY_SMEA = "smea"
        private const val KEY_SMEA_DYN = "smea_dyn"
        private const val KEY_POS_PROMPT = "positive_prompt"
        private const val KEY_NEG_PROMPT = "negative_prompt"
        private const val KEY_CORS_PROXY = "cors_proxy"
        private const val KEY_CUSTOM_CORS_PROXY_URL = "custom_cors_proxy_url"
    }

    // --- Main API ---

    override fun getMainApiUrl(): String = mainPrefs.getString(KEY_MAIN_API_URL, "") ?: ""
    override fun getMainApiKey(): String = mainPrefs.getString(KEY_MAIN_API_KEY, "") ?: ""
    override fun getMainModel(): String = mainPrefs.getString(KEY_MAIN_MODEL, "") ?: ""
    override fun getApiTemperature(): Float = mainPrefs.getFloat(KEY_API_TEMPERATURE, 0.8f)

    override fun saveMainApiSettings(url: String, apiKey: String, model: String, temperature: Float) {
        mainPrefs.edit()
            .putString(KEY_MAIN_API_URL, url)
            .putString(KEY_MAIN_API_KEY, apiKey)
            .putString(KEY_MAIN_MODEL, model)
            .putFloat(KEY_API_TEMPERATURE, temperature)
            .apply()
    }

    // --- Secondary API ---

    override fun getSecondaryApiUrl(): String = mainPrefs.getString(KEY_SECONDARY_API_URL, "") ?: ""
    override fun getSecondaryApiKey(): String = mainPrefs.getString(KEY_SECONDARY_API_KEY, "") ?: ""
    override fun getSecondaryApiModel(): String = mainPrefs.getString(KEY_SECONDARY_API_MODEL, "") ?: ""

    override fun saveSecondaryApiSettings(url: String, apiKey: String, model: String) {
        mainPrefs.edit()
            .putString(KEY_SECONDARY_API_URL, url)
            .putString(KEY_SECONDARY_API_KEY, apiKey)
            .putString(KEY_SECONDARY_API_MODEL, model)
            .apply()
    }

    // --- Embedding API ---

    override fun getEmbeddingApiUrl(): String = mainPrefs.getString(KEY_EMBEDDING_API_URL, "") ?: ""
    override fun getEmbeddingApiKey(): String = mainPrefs.getString(KEY_EMBEDDING_API_KEY, "") ?: ""
    override fun getEmbeddingModel(): String = mainPrefs.getString(KEY_EMBEDDING_MODEL, "") ?: ""

    override fun saveEmbeddingApiSettings(url: String, apiKey: String, model: String) {
        mainPrefs.edit()
            .putString(KEY_EMBEDDING_API_URL, url)
            .putString(KEY_EMBEDDING_API_KEY, apiKey)
            .putString(KEY_EMBEDDING_MODEL, model)
            .apply()
    }

    // --- ASR API ---

    override fun getAsrApiUrl(): String = mainPrefs.getString(KEY_ASR_API_URL, DEFAULT_ASR_API_URL) ?: DEFAULT_ASR_API_URL
    override fun getAsrApiKey(): String = mainPrefs.getString(KEY_ASR_API_KEY, "") ?: ""
    override fun getAsrModel(): String = mainPrefs.getString(KEY_ASR_MODEL, DEFAULT_ASR_MODEL) ?: DEFAULT_ASR_MODEL

    override fun saveAsrApiSettings(url: String, apiKey: String, model: String) {
        mainPrefs.edit()
            .putString(KEY_ASR_API_URL, url)
            .putString(KEY_ASR_API_KEY, apiKey)
            .putString(KEY_ASR_MODEL, model)
            .apply()
    }

    // --- TTS API ---

    override fun getTtsApiUrl(): String = mainPrefs.getString(KEY_TTS_API_URL, DEFAULT_TTS_API_URL) ?: DEFAULT_TTS_API_URL
    override fun getTtsApiKey(): String = mainPrefs.getString(KEY_TTS_API_KEY, "") ?: ""
    override fun getTtsModel(): String = mainPrefs.getString(KEY_TTS_MODEL, "") ?: ""
    override fun getTtsVoiceId(): String = mainPrefs.getString(KEY_TTS_VOICE_ID, DEFAULT_TTS_VOICE_ID) ?: DEFAULT_TTS_VOICE_ID
    override fun isTtsStreamingEnabled(): Boolean = mainPrefs.getBoolean(KEY_TTS_STREAMING_ENABLED, true)
    override fun isAiReplyAutoTtsEnabled(): Boolean = mainPrefs.getBoolean(KEY_AI_REPLY_AUTO_TTS_ENABLED, false)

    override fun getTtsPreviewAudioPath(voiceId: String): String {
        val safeVoiceId: String = voiceId.ifBlank { DEFAULT_TTS_VOICE_ID }
        return mainPrefs.getString(KEY_TTS_PREVIEW_AUDIO_PATH_PREFIX + safeVoiceId, "") ?: ""
    }

    override fun saveTtsPreviewAudioPath(voiceId: String, audioPath: String) {
        val safeVoiceId: String = voiceId.ifBlank { DEFAULT_TTS_VOICE_ID }
        mainPrefs.edit()
            .putString(KEY_TTS_PREVIEW_AUDIO_PATH_PREFIX + safeVoiceId, audioPath)
            .apply()
    }

    override fun saveTtsApiSettings(url: String, apiKey: String, model: String) {
        mainPrefs.edit()
            .putString(KEY_TTS_API_URL, url)
            .putString(KEY_TTS_API_KEY, apiKey)
            .putString(KEY_TTS_MODEL, model)
            .apply()
    }

    override fun saveTtsVoiceSettings(voiceId: String, isStreamingEnabled: Boolean, isAutoGenerateEnabled: Boolean) {
        mainPrefs.edit()
            .putString(KEY_TTS_VOICE_ID, voiceId)
            .putBoolean(KEY_TTS_STREAMING_ENABLED, isStreamingEnabled)
            .putBoolean(KEY_AI_REPLY_AUTO_TTS_ENABLED, isAutoGenerateEnabled)
            .apply()
    }

    // --- NovelAI General Settings ---

    override fun isNovelAiEnabled(): Boolean = mainPrefs.getBoolean(KEY_ENABLE_NOVEL_AI, true)
    override fun getNovelAiApiKey(): String = mainPrefs.getString(KEY_NOVEL_AI_API_KEY, "") ?: ""
    override fun getNovelAiModel(): String = mainPrefs.getString(KEY_NOVEL_AI_MODEL, "NAI Diffusion V4.5 Full（完整版含nsfw）") ?: "NAI Diffusion V4.5 Full（完整版含nsfw）"

    override fun saveNovelAiGeneralSettings(isEnabled: Boolean, apiKey: String, model: String) {
        mainPrefs.edit()
            .putBoolean(KEY_ENABLE_NOVEL_AI, isEnabled)
            .putString(KEY_NOVEL_AI_API_KEY, apiKey)
            .putString(KEY_NOVEL_AI_MODEL, model)
            .apply()
    }

    // --- NovelAI Generation Settings ---

    override fun getNovelAiResolution(): String = novelAiGenPrefs.getString(KEY_IMAGE_SIZE, "方图 (1024x1024)") ?: "方图 (1024x1024)"
    override fun getNovelAiSteps(): Int = novelAiGenPrefs.getInt(KEY_STEPS, 28)
    override fun getNovelAiCfgScale(): Float = novelAiGenPrefs.getFloat(KEY_CFG_SCALE, 5.0f)
    override fun getNovelAiSampler(): String = novelAiGenPrefs.getString(KEY_SAMPLER, "Euler Ancestral") ?: "Euler Ancestral"
    override fun getNovelAiSeed(): Int = novelAiGenPrefs.getInt(KEY_SEED, -1)
    override fun getNovelAiUcPreset(): String = novelAiGenPrefs.getString(KEY_UC_PRESET, "Preset 1 - Light") ?: "Preset 1 - Light"
    override fun hasNovelAiQualityTags(): Boolean = novelAiGenPrefs.getBoolean(KEY_QUALITY_TAGS, true)
    override fun useNovelAiSmea(): Boolean = novelAiGenPrefs.getBoolean(KEY_SMEA, true)
    override fun useNovelAiSmeaDyn(): Boolean = novelAiGenPrefs.getBoolean(KEY_SMEA_DYN, false)
    override fun getNovelAiPositivePrompt(): String = novelAiGenPrefs.getString(KEY_POS_PROMPT, "masterpiece, best quality") ?: "masterpiece, best quality"
    override fun getNovelAiNegativePrompt(): String = novelAiGenPrefs.getString(KEY_NEG_PROMPT, "lowres, bad anatomy, bad hands, text, error") ?: "lowres, bad anatomy, bad hands, text, error"
    override fun getNovelAiCorsProxy(): String = novelAiGenPrefs.getString(KEY_CORS_PROXY, "corsproxy.io (推荐)") ?: "corsproxy.io (推荐)"
    override fun getNovelAiCustomCorsProxyUrl(): String = novelAiGenPrefs.getString(KEY_CUSTOM_CORS_PROXY_URL, "") ?: ""

    override fun saveNovelAiGenerationSettings(
        resolution: String, steps: Int, cfgScale: Float, sampler: String, seed: Int,
        ucPreset: String, qualityTags: Boolean, smea: Boolean, smeaDyn: Boolean,
        positivePrompt: String, negativePrompt: String, corsProxy: String, customCorsProxyUrl: String
    ) {
        novelAiGenPrefs.edit()
            .putString(KEY_IMAGE_SIZE, resolution)
            .putInt(KEY_STEPS, steps)
            .putFloat(KEY_CFG_SCALE, cfgScale)
            .putString(KEY_SAMPLER, sampler)
            .putInt(KEY_SEED, seed)
            .putString(KEY_UC_PRESET, ucPreset)
            .putBoolean(KEY_QUALITY_TAGS, qualityTags)
            .putBoolean(KEY_SMEA, smea)
            .putBoolean(KEY_SMEA_DYN, smeaDyn)
            .putString(KEY_POS_PROMPT, positivePrompt)
            .putString(KEY_NEG_PROMPT, negativePrompt)
            .putString(KEY_CORS_PROXY, corsProxy)
            .putString(KEY_CUSTOM_CORS_PROXY_URL, customCorsProxyUrl)
            .apply()
    }

    override fun restoreNovelAiDefaults() {
        novelAiGenPrefs.edit().clear().apply()
    }

    override fun isBrainFloatingWindowEnabled(): Boolean = mainPrefs.getBoolean(KEY_ENABLE_BRAIN_FLOATING_WINDOW, false)

    override fun getBrainFloatingWindowEnabledFlow(): Flow<Boolean> = _brainFloatingWindowEnabled

    override fun saveBrainFloatingWindowEnabled(enabled: Boolean) {
        mainPrefs.edit()
            .putBoolean(KEY_ENABLE_BRAIN_FLOATING_WINDOW, enabled)
            .commit() // 使用 commit() 确保立即同步保存,避免监听器读取到旧值
        _brainFloatingWindowEnabled.value = enabled
    }

    // --- Background Activity Settings ---

    override fun isBackgroundActivityEnabled(): Boolean = mainPrefs.getBoolean(KEY_BACKGROUND_ACTIVITY_ENABLED, false)
    override fun getBackgroundActivityInterval(): Int = mainPrefs.getInt(KEY_BACKGROUND_ACTIVITY_INTERVAL, 3600)
    override fun getAiCooldownPeriod(): Float = mainPrefs.getFloat(KEY_AI_COOLDOWN_PERIOD, 24f)

    override fun saveBackgroundActivitySettings(isEnabled: Boolean, interval: Int, cooldownPeriod: Float) {
        mainPrefs.edit()
            .putBoolean(KEY_BACKGROUND_ACTIVITY_ENABLED, isEnabled)
            .putInt(KEY_BACKGROUND_ACTIVITY_INTERVAL, interval)
            .putFloat(KEY_AI_COOLDOWN_PERIOD, cooldownPeriod)
            .apply()
    }

    // --- Chat Auto Reply Settings ---

    override fun isChatAutoReplyEnabled(): Boolean = mainPrefs.getBoolean(KEY_CHAT_AUTO_REPLY_ENABLED, false)

    override fun getChatAutoReplyEnabledFlow(): Flow<Boolean> = _chatAutoReplyEnabled

    override fun getChatRequestTimeoutSeconds(): Int = mainPrefs.getInt(KEY_CHAT_REQUEST_TIMEOUT_SECONDS, DEFAULT_CHAT_REQUEST_TIMEOUT_SECONDS)

    override fun getChatAutoReplyIntervalSeconds(): Int = mainPrefs.getInt(KEY_CHAT_AUTO_REPLY_INTERVAL_SECONDS, DEFAULT_CHAT_AUTO_REPLY_INTERVAL_SECONDS)

    override fun getChatFollowUpDelaySeconds(): Int = mainPrefs.getInt(KEY_CHAT_FOLLOW_UP_DELAY_SECONDS, DEFAULT_CHAT_FOLLOW_UP_DELAY_SECONDS)

    override fun getChatTypingDelayPerCharMillis(): Int =
        mainPrefs.getInt(KEY_CHAT_TYPING_DELAY_PER_CHAR_MILLIS, DEFAULT_CHAT_TYPING_DELAY_PER_CHAR_MILLIS)

    override fun saveChatTypingDelayPerCharMillis(millis: Int) {
        mainPrefs.edit()
            .putInt(
                KEY_CHAT_TYPING_DELAY_PER_CHAR_MILLIS,
                millis.coerceIn(MINIMUM_CHAT_TYPING_DELAY_PER_CHAR_MILLIS, MAXIMUM_CHAT_TYPING_DELAY_PER_CHAR_MILLIS)
            )
            .apply()
    }

    override fun saveChatAutoReplyEnabled(enabled: Boolean) {
        mainPrefs.edit()
            .putBoolean(KEY_CHAT_AUTO_REPLY_ENABLED, enabled)
            .apply()
        _chatAutoReplyEnabled.value = enabled
    }

    override fun saveChatAutoReplyTimingSettings(requestTimeoutSeconds: Int, autoReplyIntervalSeconds: Int, followUpDelaySeconds: Int) {
        mainPrefs.edit()
            .putInt(KEY_CHAT_REQUEST_TIMEOUT_SECONDS, requestTimeoutSeconds.coerceAtLeast(1))
            .putInt(KEY_CHAT_AUTO_REPLY_INTERVAL_SECONDS, autoReplyIntervalSeconds.coerceAtLeast(1))
            .putInt(KEY_CHAT_FOLLOW_UP_DELAY_SECONDS, followUpDelaySeconds.coerceAtLeast(1))
            .apply()
    }

    // --- Chat List Settings ---

    override fun getChatListLoadCount(): Int = mainPrefs.getInt(KEY_CHAT_LIST_LOAD_COUNT, 20)

    override fun getChatInitialLoadCount(): Int = mainPrefs.getInt(KEY_CHAT_INITIAL_LOAD_COUNT, 30)

    override fun saveChatListLoadCount(count: Int) {
        mainPrefs.edit()
            .putInt(KEY_CHAT_LIST_LOAD_COUNT, count)
            .apply()
    }

    override fun saveChatInitialLoadCount(count: Int) {
        mainPrefs.edit()
            .putInt(KEY_CHAT_INITIAL_LOAD_COUNT, count)
            .apply()
    }

    // --- Background Service Settings ---

    override fun isBackgroundServiceEnabled(): Boolean = mainPrefs.getBoolean(KEY_BACKGROUND_SERVICE_ENABLED, true)

    override fun getBackgroundServiceEnabledFlow(): Flow<Boolean> = _backgroundServiceEnabled

    override fun saveBackgroundServiceEnabled(enabled: Boolean) {
        mainPrefs.edit()
            .putBoolean(KEY_BACKGROUND_SERVICE_ENABLED, enabled)
            .apply()
        _backgroundServiceEnabled.value = enabled
    }


    // --- New Feeds Count ---

    override fun getNewFeedsCount(): Int = mainPrefs.getInt(KEY_NEW_FEEDS_COUNT, 0)

    override fun setNewFeedsCount(count: Int) {
        mainPrefs.edit().putInt(KEY_NEW_FEEDS_COUNT, count).apply()
        _newFeedsCount.value = count
    }

    override fun clearNewFeedsCount() {
        mainPrefs.edit().remove(KEY_NEW_FEEDS_COUNT).apply()
        _newFeedsCount.value = 0
    }

    override fun getNewFeedsCountFlow(): Flow<Int> = _newFeedsCount

    // --- Favorites Display Mode ---

    override fun getFavoritesDisplayMode(): String {
        return mainPrefs.getString(KEY_FAVORITES_DISPLAY_MODE, "collapsed") ?: "collapsed"
    }

    override fun saveFavoritesDisplayMode(mode: String) {
        mainPrefs.edit().putString(KEY_FAVORITES_DISPLAY_MODE, mode).apply()
    }

    // --- Feeds Background (QQ空间动态背景) ---

    override suspend fun getFeedsBackground(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        mainPrefs.getString(KEY_FEEDS_BACKGROUND, null)
    }

    override suspend fun saveFeedsBackground(uriString: String?) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mainPrefs.edit().putString(KEY_FEEDS_BACKGROUND, uriString).apply()
        }
    }

    // ========================================
    // API设置状态管理（用于UI层）
    // ========================================
    
    override suspend fun loadSettings(): ApiSettingsState {
        return ApiSettingsState(
            mainApiUrl = getMainApiUrl(),
            mainApiKey = getMainApiKey(),
            mainModel = getMainModel(),
            secondaryApiUrl = getSecondaryApiUrl(),
            secondaryApiKey = getSecondaryApiKey(),
            secondaryApiModel = getSecondaryApiModel(),
            embeddingApiUrl = getEmbeddingApiUrl(),
            embeddingApiKey = getEmbeddingApiKey(),
            embeddingModel = getEmbeddingModel(),
            asrApiUrl = getAsrApiUrl(),
            asrApiKey = getAsrApiKey(),
            asrModel = getAsrModel(),
            apiPreset = "", // 暂时留空
            apiTemperature = getApiTemperature(),
            isNovelAiEnabled = isNovelAiEnabled(),
            novelAiApiKey = getNovelAiApiKey(),
            novelAiModel = getNovelAiModel(),
            isBrainFloatingWindowEnabled = isBrainFloatingWindowEnabled(),
            isBackgroundActivityEnabled = isBackgroundActivityEnabled(),
            backgroundActivityInterval = getBackgroundActivityInterval(),
            aiCooldownPeriod = getAiCooldownPeriod(),
            isChatAutoReplyEnabled = isChatAutoReplyEnabled(),
            chatRequestTimeoutSeconds = getChatRequestTimeoutSeconds(),
            chatAutoReplyIntervalSeconds = getChatAutoReplyIntervalSeconds(),
            chatFollowUpDelaySeconds = getChatFollowUpDelaySeconds(),
            chatTypingDelayPerCharMillis = getChatTypingDelayPerCharMillis(),
            language = "简体中文", // 暂时硬编码
            chatListLoadCount = getChatListLoadCount(),
            chatInitialLoadCount = getChatInitialLoadCount(),
            isBackgroundServiceEnabled = isBackgroundServiceEnabled(),
            ttsApiUrl = getTtsApiUrl(),
            ttsApiKey = getTtsApiKey(),
            ttsModel = getTtsModel(),
            ttsVoiceId = getTtsVoiceId(),
            isTtsStreamingEnabled = isTtsStreamingEnabled(),
            isAiReplyAutoTtsEnabled = isAiReplyAutoTtsEnabled()
        )
    }
    
    override suspend fun saveSettings(state: ApiSettingsState) {
        // 保存主API设置
        saveMainApiSettings(
            state.mainApiUrl,
            state.mainApiKey,
            state.mainModel,
            state.apiTemperature
        )
        
        // 保存副API设置
        saveSecondaryApiSettings(
            state.secondaryApiUrl,
            state.secondaryApiKey,
            state.secondaryApiModel
        )
        
        // 保存Embedding API设置
        saveEmbeddingApiSettings(
            state.embeddingApiUrl,
            state.embeddingApiKey,
            state.embeddingModel
        )
        
        // 保存ASR API设置
        saveAsrApiSettings(
            state.asrApiUrl,
            state.asrApiKey,
            state.asrModel
        )
        
        // 保存NovelAI设置
        saveNovelAiGeneralSettings(
            state.isNovelAiEnabled,
            state.novelAiApiKey,
            state.novelAiModel
        )
        
        // 保存大脑悬浮窗设置
        saveBrainFloatingWindowEnabled(state.isBrainFloatingWindowEnabled)
        
        // 保存后台活动设置
        saveBackgroundActivitySettings(
            state.isBackgroundActivityEnabled,
            state.backgroundActivityInterval,
            state.aiCooldownPeriod
        )

        // 保存聊天自动回复设置
        saveChatAutoReplyEnabled(state.isChatAutoReplyEnabled)
        saveChatAutoReplyTimingSettings(
            state.chatRequestTimeoutSeconds,
            state.chatAutoReplyIntervalSeconds,
            state.chatFollowUpDelaySeconds
        )
        // 保存打字速度设置（每字符延迟毫秒），导入时一并恢复
        saveChatTypingDelayPerCharMillis(state.chatTypingDelayPerCharMillis)

        // 保存聊天列表设置
        saveChatListLoadCount(state.chatListLoadCount)
        saveChatInitialLoadCount(state.chatInitialLoadCount)

        // 保存后台服务设置
        saveBackgroundServiceEnabled(state.isBackgroundServiceEnabled)

        // 保存 TTS 设置
        saveTtsApiSettings(
            state.ttsApiUrl,
            state.ttsApiKey,
            state.ttsModel
        )
        saveTtsVoiceSettings(
            state.ttsVoiceId,
            state.isTtsStreamingEnabled,
            state.isAiReplyAutoTtsEnabled
        )
    }
    
    // ========================================
    // 预设管理
    // ========================================
    
    override suspend fun loadPresets(apiType: String): Map<String, ApiPreset> {
        val presetsJson = mainPrefs.getString(buildPresetKey(apiType), null) ?: return emptyMap()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, ApiPreset>>() {}.type
            gson.fromJson(presetsJson, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    override suspend fun savePreset(apiType: String, preset: ApiPreset) {
        val currentPresets = loadPresets(apiType).toMutableMap()
        currentPresets[preset.name] = preset
        val json = gson.toJson(currentPresets)
        mainPrefs.edit().putString(buildPresetKey(apiType), json).apply()
    }
    
    override suspend fun deletePreset(apiType: String, presetName: String) {
        val currentPresets = loadPresets(apiType).toMutableMap()
        currentPresets.remove(presetName)
        val json = gson.toJson(currentPresets)
        mainPrefs.edit().putString(buildPresetKey(apiType), json).apply()
    }

    private fun buildPresetKey(apiType: String): String {
        return "api_presets_$apiType"
    }

    private fun clearLegacyGlobalApiPresets() {
        if (!mainPrefs.contains(KEY_LEGACY_API_PRESETS)) return
        mainPrefs.edit().remove(KEY_LEGACY_API_PRESETS).apply()
    }
    
    // ========================================
    // 模型列表获取
    // ========================================
    
    override suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> {
        return try {
            val normalizedBaseUrl: String = normalizeApiBaseUrl(baseUrl)
            val authorizationHeader: String = buildAuthorizationHeader(apiKey)
            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
            
            val service = retrofit.create(OpenAiApiService::class.java)
            val response = service.getModels(authorizationHeader)
            
            if (response.isSuccessful) {
                val models = response.body()?.data?.map { it.id } ?: emptyList()
                Result.success(models)
            } else {
                val errorMessage: String = buildModelFetchErrorMessage(response)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeApiBaseUrl(baseUrl: String): String {
        val trimmedBaseUrl: String = baseUrl.trim()
        if (trimmedBaseUrl.contains(SILICON_FLOW_LEGACY_HOST, ignoreCase = true)) {
            return trimmedBaseUrl.replace(SILICON_FLOW_LEGACY_HOST, SILICON_FLOW_OFFICIAL_HOST, ignoreCase = true)
        }
        return trimmedBaseUrl
    }

    private fun buildAuthorizationHeader(apiKey: String): String {
        val normalizedApiKey: String = normalizeApiKey(apiKey)
        if (normalizedApiKey.startsWith("Bearer ", ignoreCase = true)) {
            return normalizedApiKey
        }
        return "Bearer $normalizedApiKey"
    }

    private fun normalizeApiKey(apiKey: String): String {
        return apiKey
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .trim()
    }

    private fun buildModelFetchErrorMessage(response: retrofit2.Response<ModelApiResponse>): String {
        val errorBody: String = response.errorBody()?.string().orEmpty().trim()
        if (errorBody.isNotBlank()) {
            return "HTTP ${response.code()}: $errorBody"
        }
        return "HTTP ${response.code()}: ${response.message()}"
    }
 

    // ========================================
    // SharedPreferences导出（用于数据导出功能）
    // ========================================
    
    override fun getSharedPreferences(prefsName: String): Map<String, Any?> {
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsFile = File(sharedPrefsDir, "$prefsName.xml")
        
        return if (prefsFile.exists()) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.all
        } else {
            emptyMap()
        }
    }
    
    override fun getMultipleSharedPreferences(prefsNames: List<String>): Map<String, Map<String, Any?>> {
        val result = mutableMapOf<String, Map<String, Any?>>()
        
        for (prefsName in prefsNames) {
            val data = getSharedPreferences(prefsName)
            if (data.isNotEmpty()) {
                result[prefsName] = data
            }
        }
        
        return result
    }
    
    override fun getAllSharedPreferences(): Map<String, Map<String, Any?>> {
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val result = mutableMapOf<String, Map<String, Any?>>()
        
        if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
            sharedPrefsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".xml")) {
                    val prefsName = file.name.removeSuffix(".xml")
                    val data = getSharedPreferences(prefsName)
                    if (data.isNotEmpty()) {
                        result[prefsName] = data
                    }
                }
            }
        }
        
        return result
    }

    // ========================================
    // SharedPreferences导入（用于数据导入功能）
    // ========================================
    
    override fun importSharedPreferences(prefsName: String, data: Map<String, Any?>) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        data.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as? Set<String> ?: emptySet())
                }
                else -> {
                    // 跳过不支持的类型
                }
            }
        }
        
        editor.apply()
    }
    
    override fun importMultipleSharedPreferences(prefsData: Map<String, Map<String, Any?>>) {
        prefsData.forEach { (prefsName, data) ->
            importSharedPreferences(prefsName, data)
        }
    }
    
    /**
     * OpenAI API服务接口
     * 用于获取可用模型列表
     */
    private interface OpenAiApiService {
        @retrofit2.http.GET("v1/models")
        @retrofit2.http.Headers("Content-Type: application/json")
        suspend fun getModels(
            @retrofit2.http.Header("Authorization") authorization: String
        ): retrofit2.Response<ModelApiResponse>
    }

    /**
     * 模型API响应数据类
     */
    private data class ModelApiResponse(
        @com.google.gson.annotations.SerializedName("data")
        val data: List<ApiModel>
    )

    /**
     * API模型数据类
     */
    private data class ApiModel(
        @com.google.gson.annotations.SerializedName("id")
        val id: String
    )
}