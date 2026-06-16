package com.susking.ephone_s.settings.util

import com.google.gson.annotations.SerializedName

data class ModelApiResponse(
    @SerializedName("data")
    val data: List<ApiModel> // <--- 修改点
)

data class ApiModel( // <--- 修改点
    @SerializedName("id")
    val id: String
)