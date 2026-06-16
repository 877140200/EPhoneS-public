package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 联系人的分层语义上下文账本。
 *
 * 这张表只保存每个联系人一份可滚动维护的召回线索，不直接进入向量库，也不等同于长期事实。
 * 语义账本用于把当前仍在推进的互动、已经结束但值得召回的事件锚点、关键词和生命周期说明稳定地注入下一轮召回查询。
 */
@Entity(
    tableName = "contact_semantic_states",
    indices = [Index(value = ["updatedAt"])]
)
data class ContactSemanticStateEntity(
    @PrimaryKey val contactId: String,
    val activeSemanticContext: String = "",
    val historicalRecallAnchors: String = "",
    val resolvedEventAnchors: String = "",
    val semanticKeywords: String = "",
    val lifecycleNotes: String = "",
    val lastSourceMessageId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val rawUpdateJson: String? = null,
    val confidenceScore: Float? = null,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /**
     * 上一态快照（写前的整条状态序列化 JSON，内部 previousStateJson 置空避免无限嵌套）。
     * 用于在“重试/重说”丢弃某一轮回复时，把这一轮造成的语义更新回退一步。
     * 这是临时撤销态：仅本设备本会话有意义，跨备份导入时会被清空。
     */
    val previousStateJson: String? = null,
    /**
     * 产生当前态的 AI 回复轮次 ID。回退时据此判断当前态是否由“被丢弃的那一轮”写入，
     * 避免误退掉用户保留的合法轮次。
     */
    val lastUpdateAiTurnId: String? = null
) {
    /**
     * 旧备份兼容字段：只供 Gson 读取旧 JSON 后迁移，不参与 Room 表结构。
     */
    @Ignore
    var recentSemanticSummary: String = ""

    /**
     * 旧备份兼容字段：只供 Gson 读取旧 JSON 后迁移，不参与 Room 表结构。
     */
    @Ignore
    var userCurrentState: String = ""

    /**
     * 旧备份兼容字段：只供 Gson 读取旧 JSON 后迁移，不参与 Room 表结构。
     */
    @Ignore
    var charCurrentState: String = ""

    /**
     * 旧备份兼容字段：只供 Gson 读取旧 JSON 后迁移，不参与 Room 表结构。
     */
    @Ignore
    var stateValidityNote: String = ""

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}
