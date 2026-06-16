package com.susking.ephone_s.aidata.data.service

import com.susking.ephone_s.aidata.api.AiRequestService
import com.susking.ephone_s.aidata.domain.service.OnlineEmbeddingService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineEmbeddingServiceImpl @Inject constructor(
    private val aiRequestService: AiRequestService
) : OnlineEmbeddingService {

    override suspend fun generateEmbedding(text: String): Result<OnlineEmbeddingService.EmbeddingResult> {
        return try {
            val result: OnlineEmbeddingService.EmbeddingResult? = aiRequestService.generateEmbeddingWithLogging(
                text = text,
                description = "记忆向量化请求"
            )
            if (result == null) {
                Result.failure(Exception("Embedding API未返回向量数据"))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<Result<OnlineEmbeddingService.EmbeddingResult>> {
        // 简单的批量实现，后续可优化为单个API调用（如果API支持）
        return texts.map { generateEmbedding(it) }
    }
}
