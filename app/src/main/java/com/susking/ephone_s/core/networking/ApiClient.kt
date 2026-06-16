package com.susking.ephone_s.core.networking

import com.susking.ephone_s.settings.util.OpenAiApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    fun create(baseUrl: String, apiKey: String): OpenAiApiService {
        // 创建一个拦截器来记录网络请求，方便调试
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // 创建一个 OkHttp 客户端，并添加拦截器
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .method(original.method, original.body)
                
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor) // 添加日志拦截器
            .build()

        // 创建 Retrofit 实例
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(OpenAiApiService::class.java)
    }
}