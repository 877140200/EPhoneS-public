package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity
import kotlinx.coroutines.flow.Flow

interface HeartbeatRepository {
    suspend fun createHeartbeat(contactId: String, content: String, aiTurnId: String? = null, sourceMessageId: String? = null)
    fun getLatestHeartbeatForContact(contactId: String): Flow<HeartbeatEntity?>
    fun getAllHeartbeatsForContact(contactId: String): Flow<List<HeartbeatEntity>>
    fun getAllHeartbeats(): Flow<List<HeartbeatEntity>>
    suspend fun updateHeartbeat(heartbeat: HeartbeatEntity)
    suspend fun deleteHeartbeatsByAiTurnId(aiTurnId: String)
    suspend fun deleteHeartbeatsForContact(contactId: String)
}