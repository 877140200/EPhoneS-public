package com.susking.ephone_s.aidata.prompt

import com.google.gson.annotations.SerializedName

/**
 * AI聊天API的请求和响应数据模型
 * 从 OpenAiChatModels.kt 迁移到 aidata 模块
 */

// 用于强制API返回特定格式（如JSON）
data class ResponseFormat(
    val type: String // 例如: "json_object"
)

// 请求体
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessagePayload>,
    val temperature: Float,
    @SerializedName("response_format")
    val responseFormat: ResponseFormat? = null
)

// 单条消息的载荷
data class ChatMessagePayload(
    val role: String, // "system", "user", or "assistant"
    val content: Any // 将 content 从 String 改为 Any，支持多模态内容
)

// 用于多模态内容的数据类
sealed class ContentPart

data class TextContentPart(
    val type: String = "text",
    val text: String
) : ContentPart()

data class ImageContentPart(
    val type: String = "image_url",
    @SerializedName("image_url")
    val imageUrl: ImageUrlPayload
) : ContentPart()

data class ImageUrlPayload(
    val url: String,
    val detail: String = "auto" // "low", "high", "auto"
)

// 响应体
data class ChatCompletionResponse(
    val id: String?,
    @SerializedName("object")
    val objectType: String?,
    val created: Long?,
    val model: String?,
    val choices: List<Choice>,
    val usage: Usage?
)

// 响应中的选项
data class Choice(
    val index: Int,
    val message: ChatMessagePayload,
    @SerializedName("finish_reason")
    val finishReason: String?
)

// Token使用情况
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)