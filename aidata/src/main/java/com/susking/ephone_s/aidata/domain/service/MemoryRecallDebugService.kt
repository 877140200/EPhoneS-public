package com.susking.ephone_s.aidata.domain.service

import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugRecordWithEntries
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallQuery

/**
 * 记忆召回调试服务。
 * 负责保存和读取召回过程，供大脑活动详情和后续调试面板使用。
 */
interface MemoryRecallDebugService {
    suspend fun saveRecallDebugRecord(query: MemoryRecallQuery, context: MemoryRecallContext): String
    suspend fun getLatestRecordForContact(contactId: String): MemoryRecallDebugRecordWithEntries?
    suspend fun getLatestRecordForActivity(activityChainId: String): MemoryRecallDebugRecordWithEntries?
    suspend fun getLatestRecord(): MemoryRecallDebugRecordWithEntries?
}
