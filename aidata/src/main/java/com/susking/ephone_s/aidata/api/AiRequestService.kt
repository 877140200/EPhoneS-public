package com.susking.ephone_s.aidata.api

import android.content.Context
import java.io.File
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.service.OnlineEmbeddingService
import com.susking.ephone_s.aidata.prompt.AiPromptRequest

/**
 * TTS 语音合成请求。
 * text 是 AI 原文，允许直接包含 MiMo 音频标签。
 */
data class TtsSynthesisRequest(
    val text: String,
    val model: String,
    val voiceId: String,
    val isStreaming: Boolean,
    val description: String
)

/**
 * TTS 语音合成结果。
 * audioFile 是最终可播放音频文件，失败时为空。
 */
data class TtsSynthesisResult(
    val audioFile: File?,
    val durationMillis: Long?,
    val model: String,
    val voiceId: String,
    val isStreaming: Boolean,
    val errorMessage: String? = null
)

/**
 * TTS 流式 PCM 回调。
 *
 * onPcmChunk 会在每个 MiMo 流式片段解析成功后立即回调，调用方可以边收边播。
 * onCompleted 会在最终 WAV 缓存文件写入后回调，调用方仍可复用原有缓存逻辑。
 */
interface TtsStreamingCallback {
    suspend fun onPcmChunk(pcmBytes: ByteArray): Unit
    suspend fun onCompleted(result: TtsSynthesisResult): Unit
    suspend fun onFailed(result: TtsSynthesisResult): Unit
}

/**
 * 图片生成预览信息
 * 用于在实际生成前展示请求参数
 */
data class GenerationPreview(
    val requestBody: Any,
    val finalUrl: String,
    val model: String
)

/**
 * IP 定位结果。
 * 作为原生 GPS 定位失败时的兜底，由 ip-api.com 返回经纬度与城市名。
 *
 * @property latitude 纬度
 * @property longitude 经度
 * @property locationName 展示用位置名（城市或省/州）
 */
data class IpLocationResult(
    val latitude: Double,
    val longitude: Double,
    val locationName: String
)

/**
 * 天气查询结果。
 * 由 Open-Meteo 免费接口返回当前温度与天气代码。
 *
 * @property temperatureCelsius 当前气温（摄氏度）
 * @property weatherCode WMO 天气代码，用于映射图标与文案
 */
data class WeatherFetchResult(
    val temperatureCelsius: Double,
    val weatherCode: Int
)

/**
 * AI请求服务接口
 *
 * 用于解耦aidata模块和brain模块的依赖关系
 * - aidata模块定义接口
 * - brain模块实现接口
 * - 通过依赖注入在运行时连接
 */
interface AiRequestService {
    
    /**
     * 执行AI请求并获取结果
     *
     * @param context Android Context
     * @param promptRequest 提示词请求对象
     * @return AI响应的content字符串，失败返回null
     */
    suspend fun getChatCompletion(context: Context, promptRequest: AiPromptRequest): String?
    
    /**
     * 使用NovelAI生成图片
     *
     * @param prompt 图片生成提示词
     * @param profile 角色档案，用于获取角色专属提示词和配置
     * @return 返回图片的Base64编码字符串（格式：data:image/png;base64,...），失败返回null
     */
    suspend fun generateImage(prompt: String, profile: PersonProfile): String?
    
    /**
     * 使用NovelAI生成图片并记录日志
     *
     * @param prompt 图片生成提示词
     * @param profile 角色档案，用于获取角色专属提示词和配置
     * @param description 任务描述，例如："相册图片生成 [1/5]"
     * @return 返回图片的Base64编码字符串（格式：data:image/png;base64,...），失败返回null
     */
    suspend fun generateImageWithLogging(
        prompt: String,
        profile: PersonProfile,
        description: String
    ): String?
    
    /**
     * 预注册后台任务（状态为WAITING）
     * 用于在任务调度时立即显示所有待执行的任务
     *
     * @param activityChainId 活动链ID，用于后续更新状态
     * @param description 任务描述
     * @param prompt 提示词
     */
    suspend fun registerWaitingTask(
        activityChainId: String,
        description: String,
        prompt: String
    )
    
    /**
     * 使用NovelAI生成图片并记录日志（带延迟和预注册的activityChainId）
     * 用于已经预注册的任务，会先将状态从WAITING更新为PROCESSING
     *
     * @param activityChainId 预注册时使用的活动链ID
     * @param prompt 图片生成提示词
     * @param profile 角色档案
     * @param description 任务描述
     * @param delaySeconds 执行前延迟的秒数（0表示不延迟）
     * @return 返回图片的Base64编码字符串，失败返回null
     */
    suspend fun generateImageWithChainId(
        activityChainId: String,
        prompt: String,
        profile: PersonProfile,
        description: String,
        delaySeconds: Long
    ): String?
    
    /**
     * 通过 brain 悬浮窗链路执行向量化请求。
     * 所有向外界 API 发送的请求都必须通过 brain 转发并显示在悬浮窗中。
     *
     * @param text 需要向量化的文本
     * @param description 悬浮窗中展示的任务描述
     * @return 向量化结果，失败返回 null
     */
    suspend fun generateEmbeddingWithLogging(
        text: String,
        description: String
    ): OnlineEmbeddingService.EmbeddingResult?

    /**
     * 通过 brain 悬浮窗链路执行语音转写请求。
     * 所有向外界 ASR API 发送的请求都必须通过 brain 转发并显示在悬浮窗中。
     *
     * @param audioFile 需要转写的本地音频文件
     * @param mimeType 音频文件 MIME 类型
     * @param description 悬浮窗中展示的任务描述
     * @return ASR 转写文本，失败返回 null
     */
    suspend fun transcribeAudioWithLogging(
        audioFile: File,
        mimeType: String,
        description: String
    ): String?

    /**
     * 通过 brain 悬浮窗链路获取小米 MiMo TTS 模型列表。
     * 设置页不直接访问外部 API，避免绕过 Brain 请求展示规则。
     *
     * @param baseUrl 小米 MiMo API 基础地址
     * @param apiKey 小米 MiMo API Key
     * @return 可用 TTS 模型列表，失败返回 Result.failure
     */
    suspend fun fetchTtsModelsWithLogging(
        baseUrl: String,
        apiKey: String
    ): Result<List<String>>

    /**
     * 通过 brain 悬浮窗链路执行小米 MiMo TTS 语音合成。
     * text 必须使用 AI 原文，不清洗音频标签。
     *
     * @param request TTS 合成请求
     * @return 合成结果，失败时包含错误信息
     */
    suspend fun synthesizeSpeechWithLogging(
        request: TtsSynthesisRequest
    ): TtsSynthesisResult

    /**
     * 通过 brain 悬浮窗链路执行小米 MiMo TTS 流式语音合成。
     *
     * 该接口只在 request.isStreaming 为 true 时边解析边回调 PCM 片段；非流式请求会降级为完整合成。
     * 最终仍会写入 WAV 缓存文件，便于聊天气泡复用缓存和导入导出。
     */
    suspend fun synthesizeSpeechStreamingWithLogging(
        request: TtsSynthesisRequest,
        callback: TtsStreamingCallback
    ): TtsSynthesisResult

    /**
     * 通过 brain 悬浮窗链路执行 IP 定位请求。
     * 原生 GPS 定位失败时的兜底，所有向外界 API 发送的请求都必须通过 brain 转发并显示在悬浮窗中。
     *
     * @return IP 定位结果，失败返回 null
     */
    suspend fun fetchIpLocationWithLogging(): IpLocationResult?

    /**
     * 通过 brain 悬浮窗链路执行天气查询请求。
     * 使用 Open-Meteo 免费接口，所有向外界 API 发送的请求都必须通过 brain 转发并显示在悬浮窗中。
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @return 天气查询结果，失败返回 null
     */
    suspend fun fetchWeatherWithLogging(
        latitude: Double,
        longitude: Double
    ): WeatherFetchResult?

    /**
     * 获取图片生成预览信息
     * 用于在实际生成前展示请求参数供用户确认
     *
     * @param prompt 图片生成提示词
     * @param profile 角色档案
     * @return GenerationPreview 预览信息，失败返回null
     */
    fun getGenerationPreview(prompt: String, profile: PersonProfile): GenerationPreview?
}