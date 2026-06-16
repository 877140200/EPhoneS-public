package com.susking.ephone_s.aidata.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 调用在线 Embedding API 的请求体
 * 遵循 OpenAI 的格式，具有良好的兼容性
 */
data class EmbeddingRequest(
    @SerializedName("input")
    val input: Any, // 可以是单个字符串或字符串列表

    @SerializedName("model")
    val model: String,

    @SerializedName("encoding_format")
    val encodingFormat: String = "float"
)

/**
 * 在线 Embedding API 的响应体
 */
data class EmbeddingResponse(
    @SerializedName("object")
    val objectType: String,

    @SerializedName("data")
    val data: List<EmbeddingDataObject>,

    @SerializedName("model")
    val model: String,

    @SerializedName("usage")
    val usage: Usage
)

/**
 * 响应体中的向量数据对象
 */
data class EmbeddingDataObject(
    @SerializedName("object")
    val objectType: String,

    @SerializedName("embedding")
    val embedding: FloatArray,

    @SerializedName("index")
    val index: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddingDataObject

        if (objectType != other.objectType) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = objectType.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + index
        return result
    }
}

/**
 * 响应体中的Token使用情况
 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,

    @SerializedName("total_tokens")
    val totalTokens: Int
)
