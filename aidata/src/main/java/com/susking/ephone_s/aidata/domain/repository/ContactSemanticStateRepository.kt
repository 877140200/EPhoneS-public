package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.ContactSemanticStateEntity
import com.susking.ephone_s.aidata.domain.model.AiAction
import kotlinx.coroutines.flow.Flow

/**
 * 联系人语义状态仓库。
 *
 * 负责把模型输出的维护动作落库，也负责为提示词召回和 QQ 内页提供当前状态。
 */
interface ContactSemanticStateRepository {
    fun getSemanticStateForContact(contactId: String): Flow<ContactSemanticStateEntity?>
    suspend fun getSemanticStateSnapshotForContact(contactId: String): ContactSemanticStateEntity?
    fun getAllSemanticStates(): Flow<List<ContactSemanticStateEntity>>
    suspend fun upsertSemanticState(semanticState: ContactSemanticStateEntity)
    suspend fun updateSemanticState(semanticState: ContactSemanticStateEntity)
    suspend fun applySemanticStateUpdate(
        contactId: String,
        action: AiAction.UpdateSemanticState,
        sourceMessageId: String?,
        rawUpdateJson: String?,
        aiTurnId: String? = null
    )

    /**
     * 回退被丢弃那一轮造成的语义更新。
     *
     * 当“重试/重说”要丢弃某些 AI 回复轮时调用：若当前语义态正是由这些轮次之一写入
     * （lastUpdateAiTurnId 命中 discardedAiTurnIds），且存在写前快照，则把状态还原为上一态。
     * 否则视为无需回退的安全空操作（例如被丢弃轮根本没动过语义账本）。
     *
     * @return 实际发生回退返回 true，否则 false。
     */
    suspend fun revertSemanticStateForTurns(contactId: String, discardedAiTurnIds: Set<String>): Boolean

    suspend fun deleteSemanticStateForContact(contactId: String)
}
