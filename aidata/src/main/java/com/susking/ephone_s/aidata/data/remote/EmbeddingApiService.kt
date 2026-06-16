package com.susking.ephone_s.aidata.data.remote

import com.susking.ephone_s.aidata.data.remote.dto.EmbeddingRequest
import com.susking.ephone_s.aidata.data.remote.dto.EmbeddingResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit Service 接口，用于调用在线 Embedding API
 */
interface EmbeddingApiService {

    @POST
    suspend fun getEmbeddings(
        @Url url: String,
        @Header("Authorization") authToken: String,
        @Body request: EmbeddingRequest
    ): Response<EmbeddingResponse>
}
