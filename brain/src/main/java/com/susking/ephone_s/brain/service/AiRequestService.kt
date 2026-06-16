package com.susking.ephone_s.brain.service

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.api.IpLocationResult
import com.susking.ephone_s.aidata.api.TtsStreamingCallback
import com.susking.ephone_s.aidata.api.TtsSynthesisRequest
import com.susking.ephone_s.aidata.api.TtsSynthesisResult
import com.susking.ephone_s.aidata.api.WeatherFetchResult
import com.susking.ephone_s.aidata.data.remote.dto.IpLocationDto
import com.susking.ephone_s.aidata.data.remote.dto.WeatherDto
import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.data.remote.dto.EmbeddingRequest
import com.susking.ephone_s.aidata.data.remote.dto.EmbeddingResponse
import com.susking.ephone_s.aidata.domain.service.OnlineEmbeddingService
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import com.susking.ephone_s.aidata.prompt.ChatCompletionResponse
import com.susking.ephone_s.aidata.prompt.ImageContentPart
import com.susking.ephone_s.aidata.prompt.ImageUrlPayload
import com.susking.ephone_s.brain.api.ActivityLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * AI 请求服务实现类
 * 职责: 只负责执行 HTTP 请求和响应解析
 * 提示词准备由 aidata 模块的 AiPromptService 负责
 *
 * 实现aidata模块定义的AiRequestService接口，避免循环依赖
 */
class AiRequestService(
    private val activityLogger: ActivityLogger
) : com.susking.ephone_s.aidata.api.AiRequestService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(6000, TimeUnit.SECONDS)
        .writeTimeout(6000, TimeUnit.SECONDS)
        .readTimeout(6000, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val prettyPrintGson = GsonBuilder().setPrettyPrinting().create()
    private val activeCalls: ConcurrentHashMap<String, Call> = ConcurrentHashMap()
    private val cancelledRequestIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    
    // ==================== 执行请求方法 ====================
    /**
     * 执行 AI 请求并获取结果
     *
     * @param context Android Context
     * @param promptRequest 由 aidata 模块的 AiPromptService 准备好的提示词请求
     * @return AI 响应的 content 字符串
     */
    override suspend fun getChatCompletion(context: Context, promptRequest: AiPromptRequest): String? {
        val apiKey = AiDataApi.getSettingsRepository().getMainApiKey().trim()
        val activityChainId = UUID.randomUUID().toString()
        val activityType = promptRequest.activityType
        
        if (apiKey.isBlank()) {
            Log.e("AiRequestService", "API Key未设置，请在设置中配置。")
            val errorDescription = if (promptRequest.contactName != null) {
                "${promptRequest.contactName}: $activityType (失败)"
            } else {
                "AI: $activityType (失败)"
            }
            if (activityType != "独立行动决策") {
                activityLogger.log(
                    AiActivity(
                        activityChainId = activityChainId,
                        description = errorDescription,
                        prompt = promptRequest.displayPromptJson,
                        rawResponse = "错误：API Key未设置，请在设置中配置。",
                        timestamp = System.currentTimeMillis(),
                        status = AiActivityStatus.FAILED
                    )
                )
            }
            return """[{"type": "error", "content": ${gson.toJson("错误：API Key未设置，请在设置中配置。")}}]"""
        }
        
        if (activityType != "独立行动决策") {
            val description = if (promptRequest.contactName != null) {
                "${promptRequest.contactName}: $activityType (请求中)"
            } else {
                "AI: $activityType (请求中)"
            }
            activityLogger.log(
                AiActivity(
                    activityChainId = activityChainId,
                    description = description,
                    prompt = promptRequest.displayPromptJson,
                    rawResponse = "",
                    timestamp = System.currentTimeMillis(),
                    status = AiActivityStatus.PROCESSING
                )
            )
        }
        
        val logSafeRequest = promptRequest.request.copy(
            messages = promptRequest.request.messages.map { payload ->
                if (payload.content is List<*>) {
                    val newContent = (payload.content as List<*>).map { part ->
                        if (part is ImageContentPart) {
                            part.copy(imageUrl = ImageUrlPayload(url = "data:image/jpeg;base64,<...base64 data omitted...>"))
                        } else {
                            part
                        }
                    }
                    payload.copy(content = newContent)
                } else {
                    payload
                }
            }
        )
        Log.d("AiRequestService", "发送给AI的组合提示词: \n${prettyPrintGson.toJson(logSafeRequest)}")
        
        val jsonBody = gson.toJson(promptRequest.request)
        val request = Request.Builder()
            .url(promptRequest.url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        return withContext(Dispatchers.IO) {
            try {
                val timeoutSeconds: Int = AiDataApi.getSettingsRepository().getChatRequestTimeoutSeconds().coerceAtLeast(MINIMUM_CHAT_REQUEST_TIMEOUT_SECONDS)
                val response = executeRequest(activityChainId, request, timeoutSeconds)
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    throw IOException("请求失败: ${response.code} - $errorBody")
                }
                val responseBody = response.body?.string()
                val chatResponse = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
                val aiContent = chatResponse.choices.firstOrNull()?.message?.content as? String
                
                if (activityType != "独立行动决策") {
                    val description = if (promptRequest.contactName != null) {
                        "${promptRequest.contactName}: $activityType (成功)"
                    } else {
                        "AI: $activityType (成功)"
                    }
                    activityLogger.log(
                        AiActivity(
                            activityChainId = activityChainId,
                            description = description,
                            prompt = promptRequest.displayPromptJson,
                            rawResponse = responseBody ?: "无原始回复",
                            timestamp = System.currentTimeMillis(),
                            status = AiActivityStatus.SUCCESS
                        )
                    )
                }
                aiContent
            } catch (e: Exception) {
                if (isRequestCancelled(activityChainId)) {
                    return@withContext null
                }
                val errorMessage = "错误：获取AI回复失败: ${e.message}"
                if (activityType != "独立行动决策") {
                    val description = if (promptRequest.contactName != null) {
                        "${promptRequest.contactName}: $activityType (失败)"
                    } else {
                        "AI: $activityType (失败)"
                    }
                    activityLogger.log(
                        AiActivity(
                            activityChainId = activityChainId,
                            description = description,
                            prompt = promptRequest.displayPromptJson,
                            rawResponse = errorMessage,
                            timestamp = System.currentTimeMillis(),
                            status = AiActivityStatus.FAILED
                        )
                    )
                }
                """[{"type": "error", "content": ${gson.toJson(errorMessage)}}]"""
            }
        }
    }
    
    /**
     * 使用NovelAI生成图片
     *
     * @param prompt 图片生成提示词
     * @param profile 角色档案，用于获取角色专属提示词和配置
     * @return 返回图片的Base64编码字符串（格式：data:image/png;base64,...），失败返回null
     */
    override suspend fun generateImage(prompt: String, profile: PersonProfile): String? {
        return try {
            NovelAiService.generateImage(prompt, profile, gson)
        } catch (e: Exception) {
            Log.e("AiRequestService", "NovelAI图片生成失败", e)
            null
        }
    }
    
    /**
     * 使用NovelAI生成图片并记录日志
     *
     * @param prompt 图片生成提示词
     * @param profile 角色档案，用于获取角色专属提示词和配置
     * @param description 任务描述，例如："相册图片生成 [1/5]"
     * @return 返回图片的Base64编码字符串（格式：data:image/png;base64,...），失败返回null
     */
    override suspend fun generateImageWithLogging(
        prompt: String,
        profile: PersonProfile,
        description: String
    ): String? {
        val activityChainId = UUID.randomUUID().toString()
        
        // 记录开始处理 - 标记为后台任务
        activityLogger.log(
            AiActivity(
                activityChainId = activityChainId,
                description = description,
                prompt = prompt,
                rawResponse = "",
                timestamp = System.currentTimeMillis(),
                status = AiActivityStatus.PROCESSING,
                isBackgroundTask = true
            )
        )
        
        return try {
            val imageBase64 = NovelAiService.generateImage(prompt, profile, gson)
            
            if (imageBase64 != null) {
                // 记录成功 - 标记为后台任务
                activityLogger.log(
                    AiActivity(
                        activityChainId = activityChainId,
                        description = description,
                        prompt = prompt,
                        rawResponse = "图片生成成功 (Base64长度: ${imageBase64.length})",
                        timestamp = System.currentTimeMillis(),
                        status = AiActivityStatus.SUCCESS,
                        isBackgroundTask = true
                    )
                )
            } else {
                // 记录失败 - 标记为后台任务
                activityLogger.log(
                    AiActivity(
                        activityChainId = activityChainId,
                        description = description,
                        prompt = prompt,
                        rawResponse = "图片生成返回null",
                        timestamp = System.currentTimeMillis(),
                        status = AiActivityStatus.FAILED,
                        isBackgroundTask = true
                    )
                )
            }
            
            imageBase64
        } catch (e: Exception) {
            Log.e("AiRequestService", "NovelAI图片生成失败", e)
            // 记录异常失败 - 标记为后台任务
            activityLogger.log(
                AiActivity(
                    activityChainId = activityChainId,
                    description = description,
                    prompt = prompt,
                    rawResponse = "异常信息: ${e.message}\n${e.stackTraceToString()}",
                    timestamp = System.currentTimeMillis(),
                    status = AiActivityStatus.FAILED,
                    isBackgroundTask = true
                )
            )
            null
        }
    }
    
    /**
     * 预注册后台任务（状态为WAITING）
     */
    override suspend fun registerWaitingTask(
        activityChainId: String,
        description: String,
        prompt: String
    ) {
        activityLogger.log(
            AiActivity(
                activityChainId = activityChainId,
                description = description,
                prompt = prompt,
                rawResponse = "",
                timestamp = System.currentTimeMillis(),
                status = AiActivityStatus.WAITING,
                isBackgroundTask = true
            )
        )
    }
    
    /**
     * 使用NovelAI生成图片并记录日志（带延迟和预注册的activityChainId）
     */
    override suspend fun generateImageWithChainId(
        activityChainId: String,
        prompt: String,
        profile: PersonProfile,
        description: String,
        delaySeconds: Long
    ): String? {
        // 注意:延迟已由WorkManager的setInitialDelay处理,这里不需要额外延迟
        
        // 更新状态为PROCESSING
        activityLogger.log(
            AiActivity(
                activityChainId = activityChainId,
                description = description,
                prompt = prompt,
                rawResponse = "",
                timestamp = System.currentTimeMillis(),
                status = AiActivityStatus.PROCESSING,
                isBackgroundTask = true
            )
        )
        
        return try {
            val imageBase64 = NovelAiService.generateImage(prompt, profile, gson)
            
            if (imageBase64 != null) {
                activityLogger.log(
                    AiActivity(
                        activityChainId = activityChainId,
                        description = description,
                        prompt = prompt,
                        rawResponse = "图片生成成功 (Base64长度: ${imageBase64.length})",
                        timestamp = System.currentTimeMillis(),
                        status = AiActivityStatus.SUCCESS,
                        isBackgroundTask = true
                    )
                )
            } else {
                activityLogger.log(
                    AiActivity(
                        activityChainId = activityChainId,
                        description = description,
                        prompt = prompt,
                        rawResponse = "图片生成返回null",
                        timestamp = System.currentTimeMillis(),
                        status = AiActivityStatus.FAILED,
                        isBackgroundTask = true
                    )
                )
            }
            
            imageBase64
        } catch (e: Exception) {
            Log.e("AiRequestService", "NovelAI图片生成失败", e)
            activityLogger.log(
                AiActivity(
                    activityChainId = activityChainId,
                    description = description,
                    prompt = prompt,
                    rawResponse = "异常信息: ${e.message}\n${e.stackTraceToString()}",
                    timestamp = System.currentTimeMillis(),
                    status = AiActivityStatus.FAILED,
                    isBackgroundTask = true
                )
            )
            null
        }
    }

    /**
     * 通过 brain 悬浮窗链路执行向量化请求。
     */
    override suspend fun generateEmbeddingWithLogging(
        text: String,
        description: String
    ): OnlineEmbeddingService.EmbeddingResult? {
        val apiKey: String = AiDataApi.getSettingsRepository().getEmbeddingApiKey().trim()
        val authorizationHeader: String = buildAuthorizationHeader(apiKey)
        val apiUrl: String = normalizeApiBaseUrl(AiDataApi.getSettingsRepository().getEmbeddingApiUrl())
        val model: String = AiDataApi.getSettingsRepository().getEmbeddingModel().trim()
        val activityChainId: String = UUID.randomUUID().toString()
        val displayPrompt: String = buildEmbeddingDisplayPrompt(text, model)
        if (apiKey.isBlank() || apiUrl.isBlank() || model.isBlank()) {
            logEmbeddingActivity(activityChainId, "$description (失败)", displayPrompt, "错误：Embedding API未配置", AiActivityStatus.FAILED)
            return null
        }
        logEmbeddingActivity(activityChainId, "$description (请求中)", displayPrompt, "", AiActivityStatus.PROCESSING)
        return withContext(Dispatchers.IO) {
            try {
                val requestBody: EmbeddingRequest = EmbeddingRequest(input = text, model = model)
                val jsonBody: String = gson.toJson(requestBody)
                val request: Request = Request.Builder()
                    .url("${apiUrl.trimEnd('/')}/v1/embeddings")
                    .header("Authorization", authorizationHeader)
                    .header("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = executeRequest(activityChainId, request)
                if (!response.isSuccessful) {
                    val errorBody: String? = response.body?.string()
                    throw IOException("向量化请求失败: ${response.code} - $errorBody")
                }
                val responseBody: String = response.body?.string().orEmpty()
                val embeddingResponse: EmbeddingResponse = gson.fromJson(responseBody, EmbeddingResponse::class.java)
                val embeddingData = embeddingResponse.data.firstOrNull() ?: throw IOException("API响应中没有向量数据")
                val result = OnlineEmbeddingService.EmbeddingResult(
                    embedding = embeddingData.embedding,
                    dimension = embeddingData.embedding.size,
                    modelName = embeddingResponse.model,
                    modelVersion = embeddingResponse.model,
                    totalTokens = embeddingResponse.usage.totalTokens
                )
                logEmbeddingActivity(activityChainId, "$description (成功)", displayPrompt, responseBody, AiActivityStatus.SUCCESS)
                result
            } catch (e: Exception) {
                if (isRequestCancelled(activityChainId)) {
                    return@withContext null
                }
                val errorMessage: String = "错误：获取向量失败: ${e.message}"
                Log.e("AiRequestService", "Embedding请求失败", e)
                logEmbeddingActivity(activityChainId, "$description (失败)", displayPrompt, errorMessage, AiActivityStatus.FAILED)
                null
            }
        }
    }

    /**
     * 通过 brain 悬浮窗链路执行语音识别请求。
     * content 侧只返回转写文本，真实音频文件路径由 QQ 消息本身保存。
     */
    override suspend fun transcribeAudioWithLogging(
        audioFile: File,
        mimeType: String,
        description: String
    ): String? {
        val apiKey: String = AiDataApi.getSettingsRepository().getAsrApiKey().trim()
        val authorizationHeader: String = buildAuthorizationHeader(apiKey)
        val apiUrl: String = normalizeApiBaseUrl(AiDataApi.getSettingsRepository().getAsrApiUrl())
        val model: String = AiDataApi.getSettingsRepository().getAsrModel().trim()
        val activityChainId: String = UUID.randomUUID().toString()
        val displayPrompt: String = buildAsrDisplayPrompt(audioFile, mimeType, model)
        if (apiKey.isBlank() || apiUrl.isBlank() || model.isBlank()) {
            logAsrActivity(activityChainId, "$description (失败)", displayPrompt, "错误：ASR API未配置", AiActivityStatus.FAILED)
            return null
        }
        if (!audioFile.exists() || !audioFile.isFile || audioFile.length() <= 0L) {
            logAsrActivity(activityChainId, "$description (失败)", displayPrompt, "错误：音频文件不存在或为空", AiActivityStatus.FAILED)
            return null
        }
        logAsrActivity(activityChainId, "$description (请求中)", displayPrompt, "", AiActivityStatus.PROCESSING)
        return withContext(Dispatchers.IO) {
            try {
                val transcriptionResult: Pair<String, String> = executeAsrTranscriptionRequest(
                    activityChainId = activityChainId,
                    apiUrl = apiUrl,
                    authorizationHeader = authorizationHeader,
                    model = model,
                    audioFile = audioFile,
                    mimeType = mimeType,
                    responseFormat = ASR_VERBOSE_RESPONSE_FORMAT
                ).getOrElse { verboseException: Throwable ->
                    executeAsrTranscriptionRequest(
                        activityChainId = activityChainId,
                        apiUrl = apiUrl,
                        authorizationHeader = authorizationHeader,
                        model = model,
                        audioFile = audioFile,
                        mimeType = mimeType,
                        responseFormat = null
                    ).getOrElse { basicException: Throwable ->
                        throw IOException(
                            "语音转写请求失败，详细格式失败: ${verboseException.message}；基础格式失败: ${basicException.message}",
                            basicException
                        )
                    }
                }
                val transcript: String = transcriptionResult.first
                val responseBody: String = transcriptionResult.second
                logAsrActivity(activityChainId, "$description (成功)", displayPrompt, responseBody, AiActivityStatus.SUCCESS)
                transcript
            } catch (e: Exception) {
                if (isRequestCancelled(activityChainId)) {
                    return@withContext null
                }
                val errorMessage: String = "错误：语音转写失败: ${e.message}"
                Log.e("AiRequestService", "ASR请求失败", e)
                logAsrActivity(activityChainId, "$description (失败)", displayPrompt, errorMessage, AiActivityStatus.FAILED)
                null
            }
        }
    }

    /**
     * 通过 Brain 悬浮窗链路执行 IP 定位请求。
     * 原生 GPS 定位失败时的兜底，使用免费的 ip-api.com，无需密钥。
     * 所有向外界 API 发送的请求都必须通过 brain 转发并显示在悬浮窗中。
     */
    override suspend fun fetchIpLocationWithLogging(): IpLocationResult? {
        val activityChainId: String = UUID.randomUUID().toString()
        val description: String = "IP 定位"
        val displayPrompt: String = "GET $IP_LOCATION_URL"
        logWeatherActivity(activityChainId, "$description (请求中)", displayPrompt, "", AiActivityStatus.PROCESSING)
        return withContext(Dispatchers.IO) {
            try {
                val request: Request = Request.Builder()
                    .url(IP_LOCATION_URL)
                    .get()
                    .build()
                executeRequest(activityChainId, request, IP_LOCATION_TIMEOUT_SECONDS).use { response ->
                    val responseBody: String = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        logWeatherActivity(activityChainId, "$description (失败)", displayPrompt, "错误：HTTP ${response.code} - $responseBody", AiActivityStatus.FAILED)
                        return@withContext null
                    }
                    val dto: IpLocationDto = gson.fromJson(responseBody, IpLocationDto::class.java)
                    if (!dto.isSuccessful()) {
                        logWeatherActivity(activityChainId, "$description (失败)", displayPrompt, "错误：定位失败 - $responseBody", AiActivityStatus.FAILED)
                        return@withContext null
                    }
                    logWeatherActivity(activityChainId, "$description (成功)", displayPrompt, responseBody, AiActivityStatus.SUCCESS)
                    IpLocationResult(
                        latitude = dto.latitude,
                        longitude = dto.longitude,
                        locationName = dto.resolveLocationName()
                    )
                }
            } catch (e: Exception) {
                if (isRequestCancelled(activityChainId)) {
                    return@withContext null
                }
                val errorMessage: String = "错误：IP 定位失败: ${e.message}"
                Log.e("AiRequestService", "IP定位请求失败", e)
                logWeatherActivity(activityChainId, "$description (失败)", displayPrompt, errorMessage, AiActivityStatus.FAILED)
                null
            }
        }
    }

    /**
     * 通过 Brain 悬浮窗链路执行天气查询请求。
     * 使用 Open-Meteo 免费接口，无需密钥。
     * 所有向外界 API 发送的请求都必须通过 brain 转发并显示在悬浮窗中。
     */
    override suspend fun fetchWeatherWithLogging(
        latitude: Double,
        longitude: Double
    ): WeatherFetchResult? {
        val activityChainId: String = UUID.randomUUID().toString()
        val description: String = "天气更新"
        val weatherUrl: String = buildWeatherUrl(latitude, longitude)
        val displayPrompt: String = "GET $weatherUrl"
        logWeatherActivity(activityChainId, "$description (请求中)", displayPrompt, "", AiActivityStatus.PROCESSING)
        return withContext(Dispatchers.IO) {
            try {
                val request: Request = Request.Builder()
                    .url(weatherUrl)
                    .get()
                    .build()
                executeRequest(activityChainId, request, WEATHER_TIMEOUT_SECONDS).use { response ->
                    val responseBody: String = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        logWeatherActivity(activityChainId, "$description (失败)", displayPrompt, "错误：HTTP ${response.code} - $responseBody", AiActivityStatus.FAILED)
                        return@withContext null
                    }
                    val dto: WeatherDto = gson.fromJson(responseBody, WeatherDto::class.java)
                    val current = dto.current
                    if (current == null) {
                        logWeatherActivity(activityChainId, "$description (失败)", displayPrompt, "错误：响应缺少 current 字段 - $responseBody", AiActivityStatus.FAILED)
                        return@withContext null
                    }
                    logWeatherActivity(activityChainId, "$description (成功)", displayPrompt, responseBody, AiActivityStatus.SUCCESS)
                    WeatherFetchResult(
                        temperatureCelsius = current.temperature,
                        weatherCode = current.weatherCode
                    )
                }
            } catch (e: Exception) {
                if (isRequestCancelled(activityChainId)) {
                    return@withContext null
                }
                val errorMessage: String = "错误：天气查询失败: ${e.message}"
                Log.e("AiRequestService", "天气请求失败", e)
                logWeatherActivity(activityChainId, "$description (失败)", displayPrompt, errorMessage, AiActivityStatus.FAILED)
                null
            }
        }
    }

    /**
     * 拼接 Open-Meteo 当前天气查询地址。
     * 仅请求当前温度与天气代码，控制响应体大小。
     */
    private fun buildWeatherUrl(latitude: Double, longitude: Double): String {
        return "$WEATHER_BASE_URL?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code"
    }

    /**
     * 天气与 IP 定位请求的悬浮窗日志方法。
     */
    private suspend fun logWeatherActivity(
        activityChainId: String,
        description: String,
        prompt: String,
        rawResponse: String,
        status: AiActivityStatus
    ) {
        activityLogger.log(
            AiActivity(
                activityChainId = activityChainId,
                description = description,
                prompt = prompt,
                rawResponse = rawResponse,
                timestamp = System.currentTimeMillis(),
                status = status,
                isBackgroundTask = true
            )
        )
    }

    private fun executeAsrTranscriptionRequest(
        activityChainId: String,
        apiUrl: String,
        authorizationHeader: String,
        model: String,
        audioFile: File,
        mimeType: String,
        responseFormat: String?
    ): Result<Pair<String, String>> {
        return try {
            val requestBodyBuilder: MultipartBody.Builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody(mimeType.toMediaType())
                )
            if (!responseFormat.isNullOrBlank()) {
                requestBodyBuilder.addFormDataPart(FORM_FIELD_RESPONSE_FORMAT, responseFormat)
            }
            val requestBody: MultipartBody = requestBodyBuilder.build()
            val request: Request = Request.Builder()
                .url("${apiUrl.trimEnd('/')}/v1/audio/transcriptions")
                .header("Authorization", authorizationHeader)
                .post(requestBody)
                .build()
            executeRequest(activityChainId, request).use { response ->
                val responseBody: String = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(IOException("语音转写请求失败: ${response.code} - $responseBody"))
                }
                val transcript: String = extractAsrTranscript(responseBody)
                if (transcript.isBlank()) {
                    return Result.failure(IOException("API响应中没有转写文本"))
                }
                Result.success(transcript to responseBody)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancelRequest(activityChainId: String) {
        cancelledRequestIds.add(activityChainId)
        activeCalls.remove(activityChainId)?.cancel()
    }

    private fun executeRequest(activityChainId: String, request: Request, timeoutSeconds: Int? = null): Response {
        val call: Call = client.newCall(request)
        timeoutSeconds?.let { safeTimeoutSeconds: Int ->
            call.timeout().timeout(safeTimeoutSeconds.coerceAtLeast(MINIMUM_CHAT_REQUEST_TIMEOUT_SECONDS).toLong(), TimeUnit.SECONDS)
        }
        activeCalls[activityChainId] = call
        return try {
            call.execute()
        } finally {
            activeCalls.remove(activityChainId, call)
        }
    }

    private fun isRequestCancelled(activityChainId: String): Boolean {
        return cancelledRequestIds.remove(activityChainId)
    }

    private fun normalizeApiBaseUrl(apiUrl: String): String {
        val trimmedApiUrl: String = apiUrl.trim()
        if (trimmedApiUrl.contains(SILICON_FLOW_LEGACY_HOST, ignoreCase = true)) {
            return trimmedApiUrl.replace(SILICON_FLOW_LEGACY_HOST, SILICON_FLOW_OFFICIAL_HOST, ignoreCase = true)
        }
        return trimmedApiUrl
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

    private fun extractAsrTranscript(responseBody: String): String {
        val responseObject = gson.fromJson(responseBody, com.google.gson.JsonObject::class.java)
        return buildAsrTranscriptWithMetadata(responseObject).ifBlank {
            val dataObject = responseObject.getAsJsonObject("data")
            buildAsrTranscriptWithMetadata(dataObject)
        }
    }

    private fun buildAsrTranscriptWithMetadata(responseObject: com.google.gson.JsonObject?): String {
        if (responseObject == null) {
            return ""
        }
        val textValue: String = getJsonString(responseObject, "text")
        val metadataText: String = buildAsrMetadataText(responseObject)
        val segmentText: String = buildAsrSegmentsText(responseObject)
        return listOf(metadataText, textValue, segmentText)
            .filter { value: String -> value.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()
    }

    private fun buildAsrMetadataText(responseObject: com.google.gson.JsonObject): String {
        val language: String = getJsonString(responseObject, "language")
        val emotion: String = getJsonString(responseObject, "emotion")
            .ifBlank { getJsonString(responseObject, "emotion_type") }
        val event: String = getJsonString(responseObject, "event")
            .ifBlank { getJsonString(responseObject, "audio_event") }
        val metadataItems: List<String> = listOf(
            formatAsrMetadataItem("语言", language),
            formatAsrMetadataItem("情绪", emotion),
            formatAsrMetadataItem("事件", event)
        ).filter { item: String -> item.isNotBlank() }
        if (metadataItems.isEmpty()) {
            return ""
        }
        return metadataItems.joinToString(separator = " ")
    }

    private fun buildAsrSegmentsText(responseObject: com.google.gson.JsonObject): String {
        val segments = responseObject.getAsJsonArray("segments") ?: return ""
        val segmentLines: List<String> = segments.mapNotNull { element: com.google.gson.JsonElement ->
            if (!element.isJsonObject) {
                return@mapNotNull null
            }
            val segmentObject: com.google.gson.JsonObject = element.asJsonObject
            val text: String = getJsonString(segmentObject, "text")
            if (text.isBlank()) {
                return@mapNotNull null
            }
            val emotion: String = getJsonString(segmentObject, "emotion")
                .ifBlank { getJsonString(segmentObject, "emotion_type") }
            val event: String = getJsonString(segmentObject, "event")
                .ifBlank { getJsonString(segmentObject, "audio_event") }
            val prefix: String = listOf(
                formatAsrMetadataItem("情绪", emotion),
                formatAsrMetadataItem("事件", event)
            ).filter { item: String -> item.isNotBlank() }
                .joinToString(separator = " ")
            if (prefix.isBlank()) text else "$prefix $text"
        }
        return segmentLines.joinToString(separator = "\n")
    }

    private fun formatAsrMetadataItem(label: String, value: String): String {
        if (value.isBlank()) {
            return ""
        }
        return "[$label: $value]"
    }

    private fun getJsonString(responseObject: com.google.gson.JsonObject, key: String): String {
        val value = responseObject.get(key) ?: return ""
        if (value.isJsonNull) {
            return ""
        }
        return value.asString.trim()
    }

    private fun buildAsrDisplayPrompt(audioFile: File, mimeType: String, model: String): String {
        return prettyPrintGson.toJson(
            mapOf(
                "type" to "audio_transcription",
                "model" to model,
                "fileName" to audioFile.name,
                "fileSizeBytes" to audioFile.length(),
                "mimeType" to mimeType,
                "preferredResponseFormat" to ASR_VERBOSE_RESPONSE_FORMAT,
                "fallbackResponseFormat" to "json"
            )
        )
    }

    private fun buildEmbeddingDisplayPrompt(text: String, model: String): String {
        val safeText: String = if (text.length > MAX_EMBEDDING_LOG_TEXT_LENGTH) {
            text.take(MAX_EMBEDDING_LOG_TEXT_LENGTH) + "..."
        } else {
            text
        }
        return prettyPrintGson.toJson(
            mapOf(
                "type" to "embedding",
                "model" to model,
                "input" to safeText
            )
        )
    }

    private suspend fun logEmbeddingActivity(
        activityChainId: String,
        description: String,
        prompt: String,
        rawResponse: String,
        status: AiActivityStatus
    ) {
        activityLogger.log(
            AiActivity(
                activityChainId = activityChainId,
                description = description,
                prompt = prompt,
                rawResponse = rawResponse,
                timestamp = System.currentTimeMillis(),
                status = status,
                isBackgroundTask = true
            )
        )
    }

    private suspend fun logAsrActivity(
        activityChainId: String,
        description: String,
        prompt: String,
        rawResponse: String,
        status: AiActivityStatus
    ) {
        activityLogger.log(
            AiActivity(
                activityChainId = activityChainId,
                description = description,
                prompt = prompt,
                rawResponse = rawResponse,
                timestamp = System.currentTimeMillis(),
                status = status,
                isBackgroundTask = true
            )
        )
    }

    private suspend fun logTtsActivity(
        activityChainId: String,
        description: String,
        prompt: String,
        rawResponse: String,
        status: AiActivityStatus
    ) {
        activityLogger.log(
            AiActivity(
                activityChainId = activityChainId,
                description = description,
                prompt = prompt,
                rawResponse = rawResponse,
                timestamp = System.currentTimeMillis(),
                status = status,
                isBackgroundTask = true
            )
        )
    }
    
    /**
     * 通过 Brain 悬浮窗链路获取小米 MiMo TTS 模型列表。
     */
    override suspend fun fetchTtsModelsWithLogging(
        baseUrl: String,
        apiKey: String
    ): Result<List<String>> {
        val activityChainId: String = UUID.randomUUID().toString()
        val apiUrl: String = baseUrl.trim().ifBlank { MIMO_DEFAULT_BASE_URL }.trimEnd('/')
        val displayPrompt: String = prettyPrintGson.toJson(
            mapOf(
                "type" to "mimo_tts_models",
                "baseUrl" to apiUrl
            )
        )
        logTtsActivity(activityChainId, "设置：TTS 模型获取 (请求中)", displayPrompt, "", AiActivityStatus.PROCESSING)
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    throw IOException("小米 MiMo API Key 未配置")
                }
                val request: Request = Request.Builder()
                    .url("$apiUrl/models")
                    .header("api-key", normalizeApiKey(apiKey))
                    .header("Content-Type", "application/json")
                    .get()
                    .build()
                executeRequest(activityChainId, request).use { response: Response ->
                    val responseBody: String = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("TTS 模型列表获取失败: ${response.code} - $responseBody")
                    }
                    val models: List<String> = extractTtsModelIds(responseBody)
                    logTtsActivity(activityChainId, "设置：TTS 模型获取 (成功)", displayPrompt, responseBody, AiActivityStatus.SUCCESS)
                    Result.success(models)
                }
            } catch (e: Exception) {
                if (isRequestCancelled(activityChainId)) {
                    return@withContext Result.failure(IOException("请求已取消"))
                }
                val errorMessage: String = "错误：TTS 模型列表获取失败: ${e.message}"
                Log.e("AiRequestService", "TTS模型获取失败", e)
                logTtsActivity(activityChainId, "设置：TTS 模型获取 (失败)", displayPrompt, errorMessage, AiActivityStatus.FAILED)
                Result.failure(e)
            }
        }
    }

    /**
     * 通过 Brain 悬浮窗链路执行小米 MiMo TTS 语音合成。
     */
    override suspend fun synthesizeSpeechWithLogging(
        request: TtsSynthesisRequest
    ): TtsSynthesisResult {
        val settingsRepository = AiDataApi.getSettingsRepository()
        val apiKey: String = settingsRepository.getTtsApiKey().trim()
        val apiUrl: String = settingsRepository.getTtsApiUrl().trim().ifBlank { MIMO_DEFAULT_BASE_URL }.trimEnd('/')
        val activityChainId: String = UUID.randomUUID().toString()
        val displayPrompt: String = buildTtsDisplayPrompt(request)
        if (apiKey.isBlank() || request.model.isBlank() || request.text.isBlank()) {
            val errorMessage: String = "错误：TTS API Key、模型或文本为空"
            logTtsActivity(activityChainId, "${request.description} (失败)", displayPrompt, errorMessage, AiActivityStatus.FAILED)
            return TtsSynthesisResult(null, null, request.model, request.voiceId, request.isStreaming, errorMessage)
        }
        logTtsActivity(activityChainId, "${request.description} (请求中)", displayPrompt, "", AiActivityStatus.PROCESSING)
        return withContext(Dispatchers.IO) {
            try {
                val requestBody: Map<String, Any> = buildTtsRequestBody(request)
                val httpRequest: Request = Request.Builder()
                    .url("$apiUrl/chat/completions")
                    .header("api-key", normalizeApiKey(apiKey))
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                    .build()
                executeRequest(activityChainId, httpRequest).use { response: Response ->
                    val responseBody: String = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("TTS 请求失败: ${response.code} - $responseBody")
                    }
                    val isVoiceDesign: Boolean = request.model.contains("voicedesign", ignoreCase = true)
                    // voicedesign 不支持流式输出,强制使用非流式解析
                    val audioBytes: ByteArray = if (request.isStreaming && !isVoiceDesign) {
                        extractStreamingTtsPcmBytes(responseBody)
                    } else {
                        extractNonStreamingTtsAudioBytes(responseBody)
                    }
                    if (audioBytes.isEmpty()) {
                        throw IOException("TTS 响应中没有音频数据")
                    }
                    val audioFile: File = createTtsAudioFile("wav")
                    val finalBytes: ByteArray = if (request.isStreaming && !isVoiceDesign) {
                        wrapPcm16ToWav(audioBytes, MIMO_TTS_SAMPLE_RATE, MIMO_TTS_CHANNEL_COUNT)
                    } else {
                        audioBytes
                    }
                    audioFile.writeBytes(finalBytes)
                    val durationMillis: Long = estimateTtsDurationMillis(audioBytes, request.isStreaming && !isVoiceDesign)
                    logTtsActivity(activityChainId, "${request.description} (成功)", displayPrompt, "音频生成成功：${audioFile.absolutePath}", AiActivityStatus.SUCCESS)
                    TtsSynthesisResult(audioFile, durationMillis, request.model, request.voiceId, request.isStreaming)
                }
            } catch (e: Exception) {
                if (isRequestCancelled(activityChainId)) {
                    return@withContext TtsSynthesisResult(null, null, request.model, request.voiceId, request.isStreaming, "请求已取消")
                }
                val errorMessage: String = "错误：TTS 语音合成失败: ${e.message}"
                Log.e("AiRequestService", "TTS请求失败", e)
                logTtsActivity(activityChainId, "${request.description} (失败)", displayPrompt, errorMessage, AiActivityStatus.FAILED)
                TtsSynthesisResult(null, null, request.model, request.voiceId, request.isStreaming, errorMessage)
            }
        }
    }

    /**
     * 通过 Brain 悬浮窗链路执行小米 MiMo TTS 流式语音合成。
     * 该方法会在 HTTP 响应行到达时立即解析 PCM 片段并回调给 UI，同时保留最终 WAV 缓存文件。
     */
    override suspend fun synthesizeSpeechStreamingWithLogging(
        request: TtsSynthesisRequest,
        callback: TtsStreamingCallback
    ): TtsSynthesisResult {
        if (!request.isStreaming) {
            val result: TtsSynthesisResult = synthesizeSpeechWithLogging(request)
            if (result.errorMessage == null) {
                callback.onCompleted(result)
            } else {
                callback.onFailed(result)
            }
            return result
        }

        val settingsRepository = AiDataApi.getSettingsRepository()
        val apiKey: String = settingsRepository.getTtsApiKey().trim()
        val apiUrl: String = settingsRepository.getTtsApiUrl().trim().ifBlank { MIMO_DEFAULT_BASE_URL }.trimEnd('/')
        val activityChainId: String = UUID.randomUUID().toString()
        val displayPrompt: String = buildTtsDisplayPrompt(request)
        if (apiKey.isBlank() || request.model.isBlank() || request.text.isBlank()) {
            val errorMessage: String = "错误：TTS API Key、模型或文本为空"
            val result = TtsSynthesisResult(null, null, request.model, request.voiceId, request.isStreaming, errorMessage)
            logTtsActivity(activityChainId, "${request.description} (失败)", displayPrompt, errorMessage, AiActivityStatus.FAILED)
            callback.onFailed(result)
            return result
        }

        logTtsActivity(activityChainId, "${request.description} (流式请求中)", displayPrompt, "", AiActivityStatus.PROCESSING)
        return withContext(Dispatchers.IO) {
            try {
                val requestBody: Map<String, Any> = buildTtsRequestBody(request)
                val httpRequest: Request = Request.Builder()
                    .url("$apiUrl/chat/completions")
                    .header("api-key", normalizeApiKey(apiKey))
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                    .build()
                executeRequest(activityChainId, httpRequest).use { response: Response ->
                    if (!response.isSuccessful) {
                        val responseBody: String = response.body?.string().orEmpty()
                        throw IOException("TTS 流式请求失败: ${response.code} - $responseBody")
                    }
                    val source = response.body?.source() ?: throw IOException("TTS 流式响应体为空")
                    val outputStream = ByteArrayOutputStream()
                    while (!source.exhausted()) {
                        val line: String = source.readUtf8Line()?.trim().orEmpty()
                        if (!line.startsWith("data:")) {
                            continue
                        }
                        val data: String = line.removePrefix("data:").trim()
                        if (data.isBlank() || data == "[DONE]") {
                            continue
                        }
                        runCatching {
                            val audioBytes: ByteArray = extractStreamingTtsDataPcmBytes(data)
                            if (audioBytes.isNotEmpty()) {
                                outputStream.write(audioBytes)
                                callback.onPcmChunk(audioBytes)
                            }
                        }.onFailure { throwable: Throwable ->
                            Log.w("AiRequestService", "跳过无法解析的 TTS 流式片段: ${throwable.message}")
                        }
                    }
                    val pcmBytes: ByteArray = outputStream.toByteArray()
                    if (pcmBytes.isEmpty()) {
                        throw IOException("TTS 流式响应中没有音频数据")
                    }
                    val audioFile: File = createTtsAudioFile("wav")
                    val finalBytes: ByteArray = wrapPcm16ToWav(pcmBytes, MIMO_TTS_SAMPLE_RATE, MIMO_TTS_CHANNEL_COUNT)
                    audioFile.writeBytes(finalBytes)
                    val durationMillis: Long = estimateTtsDurationMillis(pcmBytes, isStreaming = true)
                    val result = TtsSynthesisResult(audioFile, durationMillis, request.model, request.voiceId, isStreaming = true)
                    logTtsActivity(activityChainId, "${request.description} (流式成功)", displayPrompt, "音频生成成功：${audioFile.absolutePath}", AiActivityStatus.SUCCESS)
                    callback.onCompleted(result)
                    result
                }
            } catch (e: Exception) {
                val errorMessage: String = if (isRequestCancelled(activityChainId)) {
                    "请求已取消"
                } else {
                    "错误：TTS 流式语音合成失败: ${e.message}"
                }
                val result = TtsSynthesisResult(null, null, request.model, request.voiceId, request.isStreaming, errorMessage)
                Log.e("AiRequestService", "TTS流式请求失败", e)
                logTtsActivity(activityChainId, "${request.description} (流式失败)", displayPrompt, errorMessage, AiActivityStatus.FAILED)
                callback.onFailed(result)
                result
            }
        }
    }

    private fun extractTtsModelIds(responseBody: String): List<String> {
        val rootObject: JsonObject = JsonParser.parseString(responseBody).asJsonObject
        val dataArray = rootObject.getAsJsonArray("data") ?: return emptyList()
        return dataArray.mapNotNull { element ->
            if (!element.isJsonObject) {
                return@mapNotNull null
            }
            element.asJsonObject.get("id")?.takeIf { !it.isJsonNull }?.asString
        }.filter { modelId: String ->
            modelId.contains("tts", ignoreCase = true)
        }
    }

    private fun buildTtsRequestBody(request: TtsSynthesisRequest): Map<String, Any> {
        val isVoiceDesign: Boolean = request.model.contains("voicedesign", ignoreCase = true)

        Log.d("AiRequestService", "构建TTS请求: model=${request.model}, isVoiceDesign=$isVoiceDesign, description=${request.description}")

        // voicedesign 模型的请求格式不同
        if (isVoiceDesign) {
            val messages: MutableList<Map<String, String>> = mutableListOf()
            // user 消息是音色描述
            if (request.description.isNotBlank()) {
                messages.add(mapOf("role" to "user", "content" to request.description))
            }
            // assistant 消息是要合成的文本
            messages.add(mapOf("role" to "assistant", "content" to request.text))

            Log.d("AiRequestService", "voicedesign请求: messages数量=${messages.size}")
            return mapOf(
                "model" to request.model,
                "messages" to messages,
                "audio" to mapOf("format" to "wav")
            )
        }

        // 普通 TTS 模型的请求格式
        val audioPayload: MutableMap<String, Any> = mutableMapOf(
            "format" to if (request.isStreaming) "pcm16" else "wav",
            "voice" to request.voiceId
        )
        val body: MutableMap<String, Any> = mutableMapOf(
            "model" to request.model,
            "messages" to listOf(
                mapOf("role" to "assistant", "content" to request.text)
            ),
            "audio" to audioPayload
        )
        if (request.isStreaming) {
            body["stream"] = true
        }
        return body
    }

    private fun buildTtsDisplayPrompt(request: TtsSynthesisRequest): String {
        return prettyPrintGson.toJson(buildTtsRequestBody(request))
    }

    private fun extractStreamingTtsPcmBytes(responseBody: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        responseBody.lineSequence()
            .map { line: String -> line.trim() }
            .filter { line: String -> line.startsWith("data:") }
            .map { line: String -> line.removePrefix("data:").trim() }
            .filter { data: String -> data.isNotBlank() && data != "[DONE]" }
            .forEach { data: String ->
                runCatching {
                    val audioBytes: ByteArray = extractStreamingTtsDataPcmBytes(data)
                    if (audioBytes.isNotEmpty()) {
                        outputStream.write(audioBytes)
                    }
                }.onFailure { throwable: Throwable ->
                    Log.w("AiRequestService", "跳过无法解析的 TTS 流式片段: ${throwable.message}")
                }
            }
        return outputStream.toByteArray()
    }

    private fun extractStreamingTtsDataPcmBytes(data: String): ByteArray {
        val jsonObject: JsonObject = JsonParser.parseString(data).asJsonObject
        return extractAudioBytesFromTtsJson(jsonObject, preferDelta = true)
    }

    private fun extractNonStreamingTtsAudioBytes(responseBody: String): ByteArray {
        val jsonObject: JsonObject = JsonParser.parseString(responseBody).asJsonObject
        return extractAudioBytesFromTtsJson(jsonObject, preferDelta = false)
    }

    private fun extractAudioBytesFromTtsJson(jsonObject: JsonObject, preferDelta: Boolean): ByteArray {
        val directAudioBytes: ByteArray = extractAudioBytesFromJsonObject(jsonObject)
        if (directAudioBytes.isNotEmpty()) {
            return directAudioBytes
        }
        val choices = jsonObject.getAsJsonArray("choices") ?: return ByteArray(0)
        val outputStream = ByteArrayOutputStream()
        choices.forEach { choiceElement ->
            if (!choiceElement.isJsonObject) {
                return@forEach
            }
            val choiceObject: JsonObject = choiceElement.asJsonObject
            val candidateObjects: List<JsonObject?> = if (preferDelta) {
                listOf(
                    choiceObject.getAsJsonObject("delta"),
                    choiceObject.getAsJsonObject("message"),
                    choiceObject
                )
            } else {
                listOf(
                    choiceObject.getAsJsonObject("message"),
                    choiceObject.getAsJsonObject("delta"),
                    choiceObject
                )
            }
            candidateObjects.forEach { candidateObject: JsonObject? ->
                val audioBytes: ByteArray = extractAudioBytesFromJsonObject(candidateObject)
                if (audioBytes.isNotEmpty()) {
                    outputStream.write(audioBytes)
                }
            }
        }
        return outputStream.toByteArray()
    }

    private fun extractAudioBytesFromJsonObject(jsonObject: JsonObject?): ByteArray {
        if (jsonObject == null) {
            return ByteArray(0)
        }
        val audioObject: JsonObject? = jsonObject.getAsJsonObject("audio")
        val audioBytesFromObject: ByteArray = extractAudioBytesFromAudioObject(audioObject)
        if (audioBytesFromObject.isNotEmpty()) {
            return audioBytesFromObject
        }
        val directBase64Audio: String = listOf("audio", "audio_data", "data", "b64_json", "base64")
            .firstNotNullOfOrNull { fieldName: String -> getJsonString(jsonObject, fieldName).takeIf { value: String -> value.isNotBlank() } }
            .orEmpty()
        return decodeTtsBase64Audio(directBase64Audio)
    }

    private fun extractAudioBytesFromAudioObject(audioObject: JsonObject?): ByteArray {
        if (audioObject == null) {
            return ByteArray(0)
        }
        val base64Audio: String = listOf("data", "audio_data", "b64_json", "base64")
            .firstNotNullOfOrNull { fieldName: String -> getJsonString(audioObject, fieldName).takeIf { value: String -> value.isNotBlank() } }
            .orEmpty()
        return decodeTtsBase64Audio(base64Audio)
    }

    private fun decodeTtsBase64Audio(base64Audio: String): ByteArray {
        if (base64Audio.isBlank()) {
            return ByteArray(0)
        }
        val normalizedAudio: String = base64Audio.substringAfterLast(',').trim()
        return Base64.decode(normalizedAudio, Base64.DEFAULT)
    }

    private fun createTtsAudioFile(extension: String): File {
        val voiceDirectory = File(AiDataApi.getContext().filesDir, "voice_messages")
        if (!voiceDirectory.exists()) {
            voiceDirectory.mkdirs()
        }
        return File(voiceDirectory, "tts_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
    }

    private fun wrapPcm16ToWav(pcmBytes: ByteArray, sampleRate: Int, channelCount: Int): ByteArray {
        val byteRate: Int = sampleRate * channelCount * PCM16_BYTES_PER_SAMPLE
        val totalDataLength: Int = pcmBytes.size + WAV_HEADER_SIZE - 8
        val buffer: ByteBuffer = ByteBuffer.allocate(WAV_HEADER_SIZE + pcmBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalDataLength)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(channelCount.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channelCount * PCM16_BYTES_PER_SAMPLE).toShort())
        buffer.putShort(16.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmBytes.size)
        buffer.put(pcmBytes)
        return buffer.array()
    }

    private fun estimateTtsDurationMillis(audioBytes: ByteArray, isStreaming: Boolean): Long {
        if (!isStreaming) {
            return 0L
        }
        val bytesPerSecond: Int = MIMO_TTS_SAMPLE_RATE * MIMO_TTS_CHANNEL_COUNT * PCM16_BYTES_PER_SAMPLE
        return (audioBytes.size.toLong() * 1000L / bytesPerSecond).coerceAtLeast(1L)
    }

    /**
     * 获取图片生成预览信息
     */
    override fun getGenerationPreview(
        prompt: String,
        profile: PersonProfile
    ): com.susking.ephone_s.aidata.api.GenerationPreview? {
        val novelAiPreview = NovelAiService.getGenerationPreview(prompt, profile) ?: return null
        return com.susking.ephone_s.aidata.api.GenerationPreview(
            requestBody = novelAiPreview.requestBody,
            finalUrl = novelAiPreview.finalUrl,
            model = novelAiPreview.model
        )
    }
    private companion object {
        private const val MAX_EMBEDDING_LOG_TEXT_LENGTH: Int = 1200
        private const val MIMO_DEFAULT_BASE_URL: String = "https://api.xiaomimimo.com/v1"
        private const val MIMO_TTS_SAMPLE_RATE: Int = 24000
        private const val MIMO_TTS_CHANNEL_COUNT: Int = 1
        private const val PCM16_BYTES_PER_SAMPLE: Int = 2
        private const val WAV_HEADER_SIZE: Int = 44
        private const val ASR_VERBOSE_RESPONSE_FORMAT: String = "verbose_json"
        private const val FORM_FIELD_RESPONSE_FORMAT: String = "response_format"
        private const val SILICON_FLOW_LEGACY_HOST: String = "api.siliconflow.com"
        private const val SILICON_FLOW_OFFICIAL_HOST: String = "api.siliconflow.cn"
        private const val MINIMUM_CHAT_REQUEST_TIMEOUT_SECONDS: Int = 1
        // IP 定位接口地址（ip-api.com 免费版，无需密钥，仅请求必要字段）
        private const val IP_LOCATION_URL: String = "http://ip-api.com/json/?fields=status,lat,lon,city,regionName"
        // IP 定位请求超时秒数
        private const val IP_LOCATION_TIMEOUT_SECONDS: Int = 10
        // Open-Meteo 当前天气查询基础地址（免费版，无需密钥）
        private const val WEATHER_BASE_URL: String = "https://api.open-meteo.com/v1/forecast"
        // 天气查询请求超时秒数
        private const val WEATHER_TIMEOUT_SECONDS: Int = 10
    }
}