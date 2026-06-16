package com.susking.ephone_s.aidata.domain.model.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import java.util.UUID

/**
 * 记忆向量索引实体。
 * 第一版兼容旧 memoryId，同时新增统一索引对象字段。
 */
@Entity(
    tableName = "memory_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = LongTermMemory::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["memoryId"]),
        Index(value = ["contactId"]),
        Index(value = ["contactId", "indexedObjectType"]),
        Index(value = ["indexedObjectType", "indexedObjectId"])
    ]
)
data class MemoryEmbedding(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // 兼容旧向量逻辑；第一版原子事件仍由 long_term_memories 承载
    val memoryId: String,

    // 统一索引对象类型
    val indexedObjectType: MemoryIndexedObjectType = MemoryIndexedObjectType.EVENT,

    // 统一索引对象 ID
    val indexedObjectId: String = memoryId,

    // 关联的联系人ID，用于快速查询
    val contactId: String,

    // 向量维度
    val dimension: Int,

    // 存储向量的二进制数据
    val embeddingBlob: ByteArray,

    // 向量内容的哈希值，用于快速比较和去重
    val embeddingHash: String,

    // 生成该向量的模型名称
    val modelName: String,

    // 模型版本
    val modelVersion: String,

    // 记录创建时间
    val createdAt: Long = System.currentTimeMillis(),

    // 记录更新时间
    val updatedAt: Long = System.currentTimeMillis(),

    // 标记该向量是否有效（用于模型迁移）
    val isActive: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryEmbedding

        if (id != other.id) return false
        if (memoryId != other.memoryId) return false
        if (indexedObjectType != other.indexedObjectType) return false
        if (indexedObjectId != other.indexedObjectId) return false
        if (contactId != other.contactId) return false
        if (dimension != other.dimension) return false
        if (!embeddingBlob.contentEquals(other.embeddingBlob)) return false
        if (embeddingHash != other.embeddingHash) return false
        if (modelName != other.modelName) return false
        if (modelVersion != other.modelVersion) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (isActive != other.isActive) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + memoryId.hashCode()
        result = 31 * result + indexedObjectType.hashCode()
        result = 31 * result + indexedObjectId.hashCode()
        result = 31 * result + contactId.hashCode()
        result = 31 * result + dimension
        result = 31 * result + embeddingBlob.contentHashCode()
        result = 31 * result + embeddingHash.hashCode()
        result = 31 * result + modelName.hashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + isActive.hashCode()
        return result
    }
}
