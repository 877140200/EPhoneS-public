package com.susking.ephone_s.aidata.domain.model

/**
 * API设置状态数据类
 * 用于在UI层和数据层之间传递完整的设置状态
 */
data class ApiSettingsState(
    // API Tab
    val mainApiUrl: String = "",
    val mainApiKey: String = "",
    val mainModel: String = "",
    val secondaryApiUrl: String = "",
    val secondaryApiKey: String = "",
    val secondaryApiModel: String = "",
    val embeddingApiUrl: String = "",
    val embeddingApiKey: String = "",
    val embeddingModel: String = "",
    val asrApiUrl: String = "https://api.siliconflow.cn",
    val asrApiKey: String = "",
    val asrModel: String = "FunAudioLLM/SenseVoiceSmall",
    val apiPreset: String = "",
    val apiTemperature: Float = 0.8f,

    // Features Tab
    val isNovelAiEnabled: Boolean = false,
    val novelAiApiKey: String = "",
    val novelAiModel: String = "",
    val isBrainFloatingWindowEnabled: Boolean = false,
    val isBackgroundActivityEnabled: Boolean = false,
    val backgroundActivityInterval: Int = 3600, // in seconds
    val aiCooldownPeriod: Float = 24f, // in hours
    val isChatAutoReplyEnabled: Boolean = false, // 聊天自动回复开关
    val chatRequestTimeoutSeconds: Int = 180, // 聊天AI请求超时时间，单位秒
    val chatAutoReplyIntervalSeconds: Int = 5, // 面板或键盘收起后的自动回复等待时间，单位秒
    val chatFollowUpDelaySeconds: Int = 1200, // 用户沉默后自动追问等待时间，界面按分钟展示，内部按秒保存
    val chatTypingDelayPerCharMillis: Int = 60, // 打字速度：AI 逐条发消息时每字符延迟毫秒数，数值越大越像真人慢打字

    // App Tab
    val language: String = "简体中文",
    val chatListLoadCount: Int = 20,
    val chatInitialLoadCount: Int = 20,
    val isBackgroundServiceEnabled: Boolean = true, // 后台消息接收服务开关

    // TTS Tab
    val ttsApiUrl: String = "https://api.xiaomimimo.com/v1",
    val ttsApiKey: String = "",
    val ttsModel: String = "",
    val ttsVoiceId: String = "冰糖",
    val isTtsStreamingEnabled: Boolean = true,
    val isAiReplyAutoTtsEnabled: Boolean = false
)