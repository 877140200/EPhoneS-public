package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.ApiPreset
import com.susking.ephone_s.aidata.domain.model.ApiSettingsState
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    // Main API
    fun getMainApiUrl(): String
    fun getMainApiKey(): String
    fun getMainModel(): String
    fun getApiTemperature(): Float
    fun saveMainApiSettings(url: String, apiKey: String, model: String, temperature: Float)

    // Secondary API
    fun getSecondaryApiUrl(): String
    fun getSecondaryApiKey(): String
    fun getSecondaryApiModel(): String
    fun saveSecondaryApiSettings(url: String, apiKey: String, model: String)
 
    // Embedding API
    fun getEmbeddingApiUrl(): String
    fun getEmbeddingApiKey(): String
    fun getEmbeddingModel(): String
    fun saveEmbeddingApiSettings(url: String, apiKey: String, model: String)

    // ASR API
    fun getAsrApiUrl(): String
    fun getAsrApiKey(): String
    fun getAsrModel(): String
    fun saveAsrApiSettings(url: String, apiKey: String, model: String)

    // NovelAI API
    fun isNovelAiEnabled(): Boolean
    fun getNovelAiApiKey(): String
    fun getNovelAiModel(): String
    fun getNovelAiResolution(): String
    fun getNovelAiSteps(): Int
    fun getNovelAiCfgScale(): Float
    fun getNovelAiSampler(): String
    fun getNovelAiSeed(): Int
    fun getNovelAiUcPreset(): String
    fun hasNovelAiQualityTags(): Boolean
    fun useNovelAiSmea(): Boolean
    fun useNovelAiSmeaDyn(): Boolean
    fun getNovelAiPositivePrompt(): String
    fun getNovelAiNegativePrompt(): String
    fun getNovelAiCorsProxy(): String
    fun getNovelAiCustomCorsProxyUrl(): String
    fun saveNovelAiGeneralSettings(isEnabled: Boolean, apiKey: String, model: String)
    fun saveNovelAiGenerationSettings(
        resolution: String, steps: Int, cfgScale: Float, sampler: String, seed: Int,
        ucPreset: String, qualityTags: Boolean, smea: Boolean, smeaDyn: Boolean,
        positivePrompt: String, negativePrompt: String, corsProxy: String, customCorsProxyUrl: String
    )
    fun restoreNovelAiDefaults()

    // Brain Floating Window
    fun isBrainFloatingWindowEnabled(): Boolean
    fun getBrainFloatingWindowEnabledFlow(): Flow<Boolean>
    fun saveBrainFloatingWindowEnabled(enabled: Boolean)

    // Background Activity
    fun isBackgroundActivityEnabled(): Boolean
    fun getBackgroundActivityInterval(): Int
    fun getAiCooldownPeriod(): Float
    fun saveBackgroundActivitySettings(isEnabled: Boolean, interval: Int, cooldownPeriod: Float)

    // Chat Auto Reply
    fun isChatAutoReplyEnabled(): Boolean
    fun getChatAutoReplyEnabledFlow(): Flow<Boolean>
    fun getChatRequestTimeoutSeconds(): Int
    fun getChatAutoReplyIntervalSeconds(): Int
    fun getChatFollowUpDelaySeconds(): Int
    // 打字速度：每字符延迟毫秒数。数值越大，AI 逐条发消息的间隔越长，越像真人慢打字。
    fun getChatTypingDelayPerCharMillis(): Int
    fun saveChatAutoReplyEnabled(enabled: Boolean)
    fun saveChatAutoReplyTimingSettings(requestTimeoutSeconds: Int, autoReplyIntervalSeconds: Int, followUpDelaySeconds: Int)
    fun saveChatTypingDelayPerCharMillis(millis: Int)

    // Chat List Settings
    fun getChatListLoadCount(): Int
    fun getChatInitialLoadCount(): Int
    fun saveChatListLoadCount(count: Int)
    fun saveChatInitialLoadCount(count: Int)
    
    // TTS API
    fun getTtsApiUrl(): String
    fun getTtsApiKey(): String
    fun getTtsModel(): String
    fun getTtsVoiceId(): String
    fun isTtsStreamingEnabled(): Boolean
    fun isAiReplyAutoTtsEnabled(): Boolean
    fun getTtsPreviewAudioPath(voiceId: String): String
    fun saveTtsPreviewAudioPath(voiceId: String, audioPath: String)
    fun saveTtsApiSettings(url: String, apiKey: String, model: String)
    fun saveTtsVoiceSettings(voiceId: String, isStreamingEnabled: Boolean, isAutoGenerateEnabled: Boolean)

    // Background Service (后台消息接收服务)
    fun isBackgroundServiceEnabled(): Boolean
    fun getBackgroundServiceEnabledFlow(): Flow<Boolean>
    fun saveBackgroundServiceEnabled(enabled: Boolean)

    // New Feeds Count
    fun getNewFeedsCount(): Int
    fun setNewFeedsCount(count: Int)
    fun clearNewFeedsCount()
    fun getNewFeedsCountFlow(): Flow<Int>

    // Favorites Display Mode
    fun getFavoritesDisplayMode(): String
    fun saveFavoritesDisplayMode(mode: String)

    // Feeds Background (QQ空间动态背景)
    suspend fun getFeedsBackground(): String?
    suspend fun saveFeedsBackground(uriString: String?)

    // ========================================
    // API设置状态管理（用于UI层）
    // ========================================
    
    /**
     * 加载完整的API设置状态
     * @return ApiSettingsState 包含所有设置的状态对象
     */
    suspend fun loadSettings(): ApiSettingsState
    
    /**
     * 保存完整的API设置状态
     * @param state 要保存的设置状态
     */
    suspend fun saveSettings(state: ApiSettingsState)
    
    // ========================================
    // 预设管理
    // ========================================
    
    /**
     * 加载所有预设
     * @return Map<预设名称, 预设对象>
     */
    suspend fun loadPresets(apiType: String): Map<String, ApiPreset>
    
    /**
     * 保存一个预设
     * @param preset 要保存的预设
     */
    suspend fun savePreset(apiType: String, preset: ApiPreset)
    
    /**
     * 删除一个预设
     * @param presetName 要删除的预设名称
     */
    suspend fun deletePreset(apiType: String, presetName: String)
    
    // ========================================
    // 模型列表获取
    // ========================================
    
    /**
     * 从API获取可用模型列表
     * @param baseUrl API基础URL
     * @param apiKey API密钥
     * @return Result<List<String>> 成功返回模型列表,失败返回异常
     */
    suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>>
    
    // ========================================
    // SharedPreferences导出（用于数据导出功能）
    // ========================================
    
    /**
     * 获取指定名称的SharedPreferences的所有数据
     * 
     * @param prefsName SharedPreferences文件名（不包含.xml扩展名）
     * @return SharedPreferences的所有键值对，如果文件不存在则返回空Map
     */
    fun getSharedPreferences(prefsName: String): Map<String, Any?>
    
    /**
     * 获取多个SharedPreferences的数据
     * 
     * @param prefsNames SharedPreferences文件名列表
     * @return Map<文件名, 键值对>
     */
    fun getMultipleSharedPreferences(prefsNames: List<String>): Map<String, Map<String, Any?>>
    
    /**
     * 获取所有SharedPreferences文件的数据
     *
     * @return Map<文件名, 键值对>
     */
    fun getAllSharedPreferences(): Map<String, Map<String, Any?>>
    
    /**
     * 导入SharedPreferences数据
     *
     * @param prefsName SharedPreferences文件名
     * @param data 要导入的键值对数据
     */
    fun importSharedPreferences(prefsName: String, data: Map<String, Any?>)
    
    /**
     * 批量导入多个SharedPreferences
     *
     * @param prefsData Map<文件名, 键值对>
     */
    fun importMultipleSharedPreferences(prefsData: Map<String, Map<String, Any?>>)
}