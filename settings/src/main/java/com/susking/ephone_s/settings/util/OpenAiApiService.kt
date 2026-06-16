package com.susking.ephone_s.settings.util

import retrofit2.Response
import retrofit2.http.GET

interface OpenAiApiService {
    @GET("v1/models")
    suspend fun getModels(): Response<ModelApiResponse> // 这里不需要改，因为 ModelApiResponse 内部引用已更新
}