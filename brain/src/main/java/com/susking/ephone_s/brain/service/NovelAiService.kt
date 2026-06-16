package com.susking.ephone_s.brain.service

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.brain.service.novelai.NovelAIRequestV3
import com.susking.ephone_s.brain.service.novelai.NovelAIRequestV4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.random.Random

/**
 * 负责与 NovelAI 图像生成 API 进行通信的服务。
 */
object NovelAiService {

    private const val TAG = "NovelAiService"

    // 新增一个数据类来包装预览信息
    data class GenerationPreview(val requestBody: Any, val finalUrl: String, val model: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()


    /**
     * 新增：获取生成图片的预览信息，包括请求体和最终URL，但不执行请求。
     * @param aiPrompt AI提供的核心提示词。
     * @param profile 当前聊天的角色档案。
     * @return GenerationPreview 实例，如果失败则返回 null。
     */
    fun getGenerationPreview(aiPrompt: String, profile: PersonProfile): GenerationPreview? {
        if (AiDataApi.getSettingsRepository().getNovelAiApiKey().isBlank()) {
            Log.e(TAG, "无法获取预览：NovelAI API Key 未配置。")
            return null
        }
        val (finalPositivePrompt, finalNegativePrompt) = buildFinalPrompts(aiPrompt, profile)
        val uiModelName = AiDataApi.getSettingsRepository().getNovelAiModel()
        val apiModelId = uiModelName.toApiModelId()
        val requestBody = buildRequestBody(apiModelId, finalPositivePrompt, finalNegativePrompt)
        val apiUrl = if (apiModelId.contains("nai-diffusion-4")) "https://image.novelai.net/ai/generate-image-stream" else "https://image.novelai.net/ai/generate-image"
        val finalUrl = buildFinalUrl(apiUrl)
        return GenerationPreview(requestBody, finalUrl, uiModelName)
    }


    /**
     * 根据给定的提示词和设置，调用 NovelAI API 生成一张图片。
     * @param aiPrompt AI提供的核心提示词。
     * @param profile 当前聊天的角色档案，用于获取角色专属提示词。
     * @return 返回图片的 Base64 编码字符串，如果失败则返回 null。
     */
    suspend fun generateImage(aiPrompt: String, profile: PersonProfile, gson: Gson): String? = withContext(Dispatchers.IO) {
        val apiKey = AiDataApi.getSettingsRepository().getNovelAiApiKey() // 恢复从仓库读取

        // --- 新增的诊断日志 ---
        Log.d(TAG, "从 Repository 读取的 API Key: \"$apiKey\"")
        Log.d(TAG, "API Key 的长度: ${apiKey.length}")
        // 打印每个字符的 ASCII/Unicode 码，以暴露不可见字符
        val keyChars = apiKey.map { it.code }.joinToString(separator = ", ") { it.toString() }
        Log.d(TAG, "API Key 的字符码: [$keyChars]")
        // --- 诊断日志结束 ---

        if (apiKey.isBlank()) {
            Log.e(TAG, "API Key 为空，无法生成图片。")
            throw Exception("API Key 为空")
        }

        try {
            val (finalPositivePrompt, finalNegativePrompt) = buildFinalPrompts(aiPrompt, profile)
            val uiModelName = AiDataApi.getSettingsRepository().getNovelAiModel()
            val apiModelId = uiModelName.toApiModelId()
            val requestBody = buildRequestBody(apiModelId, finalPositivePrompt, finalNegativePrompt)
            val jsonBody = gson.toJson(requestBody)
            val apiUrl = if (apiModelId.contains("nai-diffusion-4")) "https://image.novelai.net/ai/generate-image-stream" else "https://image.novelai.net/ai/generate-image"
            val finalUrl = buildFinalUrl(apiUrl)

            Log.d(TAG, "Requesting URL: $finalUrl")
            Log.d(TAG, "Request Body: $jsonBody")

            val request = Request.Builder()
                .url(finalUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            Log.d(TAG, "Response Code: ${response.code}, Headers: ${response.headers}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "API请求失败: ${response.code} - $errorBody")
                throw Exception("API请求失败: ${response.code}")
            }

            val contentType = response.header("Content-Type")
            val responseBodyBytes = response.body?.bytes() ?: throw Exception("响应体为空。")

            if (contentType?.contains("text/event-stream") == true) {
                Log.d(TAG, "检测到 SSE (text/event-stream) 响应，开始解析...")
                val sseResponse = String(responseBodyBytes, Charsets.UTF_8)
                var imageData: String? = null
                for (line in sseResponse.lines().reversed()) {
                    if (line.startsWith("data:")) {
                        val dataJson = line.substring(5).trim()
                        if (dataJson.equals("[DONE]", ignoreCase = true)) continue
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val dataObject = gson.fromJson(dataJson, Map::class.java) as? Map<String, Any>
                            if (dataObject?.get("event_type") == "final" && dataObject["image"] is String) {
                                imageData = dataObject["image"] as String
                                Log.d(TAG, "成功从 SSE 'final' 事件中提取图片。")
                                break
                            }
                        } catch (e: Exception) {
                            imageData = dataJson
                            Log.w(TAG, "SSE data 不是 JSON 对象, 将其作为原始 base64 处理。")
                            break
                        }
                    }
                }
                return@withContext "data:image/png;base64,$imageData"
            } else {
                Log.d(TAG, "将响应作为 ZIP 文件处理。")
                return@withContext extractImageFromZip(responseBodyBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成图片时发生异常", e)
            throw e // 向上抛出异常，让 ViewModel 捕获并处理
        }
    }

    private fun buildFinalPrompts(aiPrompt: String, profile: PersonProfile): Pair<String, String> {
        val positive = if (profile.naiPromptSource == "character" && profile.naiPositivePrompt?.isNotBlank() == true) {
            "$aiPrompt, ${profile.naiPositivePrompt}"
        } else {
            "$aiPrompt, ${AiDataApi.getSettingsRepository().getNovelAiPositivePrompt()}"
        }
        val negative = if (profile.naiPromptSource == "character" && profile.naiNegativePrompt?.isNotBlank() == true) {
            profile.naiNegativePrompt!!
        } else {
            AiDataApi.getSettingsRepository().getNovelAiNegativePrompt()
        }
        return positive to negative
    }

    private fun buildFinalUrl(baseUrl: String): String {
        val proxy = AiDataApi.getSettingsRepository().getNovelAiCorsProxy()
        if (proxy == "直连 (无代理)") return baseUrl
        val proxyUrl = when (proxy) {
            "corsproxy.io (推荐)" -> "https://corsproxy.io/?"
            "allorigins.win" -> "https://api.allorigins.win/raw?url="
            "cors-anywhere (需激活)" -> "https://cors-anywhere.herokuapp.com/"
            "自定义代理" -> AiDataApi.getSettingsRepository().getNovelAiCustomCorsProxyUrl().ifBlank { "" }
            else -> ""
        }
        return if (proxyUrl.isNotBlank()) proxyUrl + baseUrl else baseUrl
    }

    private fun extractImageFromZip(zipBytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".png", ignoreCase = true)) {
                    val baos = ByteArrayOutputStream()
                    zis.copyTo(baos)
                    val imageBytes = baos.toByteArray()
                    Log.d(TAG, "成功从 ZIP 文件 (${entry.name}) 中解压图片。")
                    return "data:image/png;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                }
                entry = zis.nextEntry
            }
        }
        throw Exception("在 ZIP 响应中未找到 .png 图片文件。")
    }

    private fun buildRequestBody(modelId: String, positive: String, negative: String): Any {
        val settings = AiDataApi.getSettingsRepository()
        val (width, height) = settings.getNovelAiResolution().substringAfter('(').substringBefore(')').split('x').map { it.trim().toInt() }
        val seed = if (settings.getNovelAiSeed() == -1) Random.nextLong(0, 4294967295L) else settings.getNovelAiSeed().toLong()

        return if (modelId.contains("nai-diffusion-4")) {
            NovelAIRequestV4(
                input = positive,
                model = modelId,
                parameters = NovelAIRequestV4.Parameters(
                    paramsVersion = 3,
                    width = width,
                    height = height,
                    scale = settings.getNovelAiCfgScale(),
                    sampler = settings.getNovelAiSampler().toApiSampler(),
                    steps = settings.getNovelAiSteps(),
                    seed = seed,
                    nSamples = 1,
                    ucPreset = settings.getNovelAiUcPreset().toApiUcPreset(),
                    qualityToggle = settings.hasNovelAiQualityTags(),
                    v4Prompt = NovelAIRequestV4.V4Prompt(
                        caption = NovelAIRequestV4.V4Caption(
                            baseCaption = positive
                        )
                    ),
                    v4NegativePrompt = NovelAIRequestV4.V4NegativePrompt(
                        caption = NovelAIRequestV4.V4Caption(
                            baseCaption = negative
                        )
                    ),
                    negativePrompt = negative,
                    noiseSchedule = "karras",
                    legacyV3Extend = false,
                    addOriginalImage = true,
                    legacy = false,
                    cfgRescale = 0.0f,
                    controlnetStrength = 1.0f,
                    dynamicThresholding = false
                )
            )
        } else {
            NovelAIRequestV3(
                input = positive, model = modelId,
                parameters = NovelAIRequestV3.Parameters(
                    width = width,
                    height = height,
                    scale = settings.getNovelAiCfgScale(),
                    sampler = settings.getNovelAiSampler().toApiSampler(),
                    steps = settings.getNovelAiSteps(),
                    seed = seed,
                    ucPreset = settings.getNovelAiUcPreset().toApiUcPreset(),
                    qualityToggle = settings.hasNovelAiQualityTags(),
                    sm = settings.useNovelAiSmea(),
                    smDyn = settings.useNovelAiSmeaDyn(),
                    negativePrompt = negative
                )
            )
        }
    }

    private fun String.toApiModelId(): String = when(this) {
        "NAI Diffusion V4.5 Full（完整版含nsfw）" -> "nai-diffusion-4-5-full"
        "NAI Diffusion V4.5 Curated (精选版无nsfw)" -> "nai-diffusion-4-curated-preview"
        "NAI Diffusion Anime V3（旧版）" -> "nai-diffusion-3"
        "NAI Diffusion Furry V3（旧旧版）" -> "nai-diffusion-furry-3"
        else -> "nai-diffusion-4-5-full"
    }

    private fun String.toApiSampler(): String = when(this) {
        "Euler" -> "k_euler"
        "Euler Ancestral" -> "k_euler_ancestral"
        "DPM++ 2S Ancestral" -> "k_dpmpp_2s_ancestral"
        "DPM++ 2M" -> "k_dpmpp_2m"
        "DPM++ SDE" -> "k_dpmpp_sde"
        "DDIM" -> "ddim"
        else -> "k_euler_ancestral" // 提供一个安全的默认值
    }

    private fun String.toApiUcPreset(): Int = when {
        this.contains("Preset 0") -> 0
        this.contains("Preset 1") -> 1
        this.contains("Preset 2") -> 2
        this.contains("Preset 3") -> 3
        else -> 1 // 提供一个安全的默认值
    }
}