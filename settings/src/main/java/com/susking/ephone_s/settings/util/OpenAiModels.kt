package com.susking.ephone_s.settings.util

import com.google.gson.annotations.SerializedName

/**
 * Represents the entire JSON response from the /v1/models endpoint.
 * 代表从 /v1/models 端点返回的整个JSON响应。
 */
data class ModelListResponse(
    val data: List<Model>
)

/**
 * Represents a single model object within the data list.
 * 代表 data 列表中的单个模型对象。
 */
data class Model(
    val id: String,
    @SerializedName("owned_by")
    val ownedBy: String
)