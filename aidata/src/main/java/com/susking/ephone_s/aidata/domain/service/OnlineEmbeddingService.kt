package com.susking.ephone_s.aidata.domain.service

/**
 * 在线向量生成服务接口
 * 负责调用外部API将文本转换为向量
 */
interface OnlineEmbeddingService {

    /**
     * 为单个文本生成向量
     * @param text 需要生成向量的文本
     * @return 生成的向量结果，包含向量数据、模型信息等
     */
    suspend fun generateEmbedding(text: String): Result<EmbeddingResult>

    /**
     * 批量为多个文本生成向量
     * @param texts 需要生成向量的文本列表
     * @return 一系列生成结果的列表
     */
    suspend fun generateEmbeddings(texts: List<String>): List<Result<EmbeddingResult>>

    /**
     * 向量生成结果的数据类
     */
    data class EmbeddingResult(
        val embedding: FloatArray,
        val dimension: Int,
        val modelName: String,
        val modelVersion: String,
        val totalTokens: Int // 记录本次API调用消耗的token数
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EmbeddingResult

            if (!embedding.contentEquals(other.embedding)) return false
            if (dimension != other.dimension) return false
            if (modelName != other.modelName) return false
            if (modelVersion != other.modelVersion) return false
            if (totalTokens != other.totalTokens) return false

            return true
        }

        override fun hashCode(): Int {
            var result = embedding.contentHashCode()
            result = 31 * result + dimension
            result = 31 * result + modelName.hashCode()
            result = 31 * result + modelVersion.hashCode()
            result = 31 * result + totalTokens
            return result
        }
    }
}
