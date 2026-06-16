package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import kotlinx.coroutines.flow.Flow

interface JottingRepository {
    suspend fun createJotting(contactId: String, title: String, content: String, aiTurnId: String? = null, sourceMessageId: String? = null)
    fun getLatestJottingForContact(contactId: String): Flow<JottingEntity?>
    fun getAllJottingsForContact(contactId: String): Flow<List<JottingEntity>>
    fun getAllJottings(): Flow<List<JottingEntity>>
    suspend fun updateJotting(jotting: JottingEntity)
    suspend fun deleteJottingsByAiTurnId(aiTurnId: String)
    suspend fun deleteJottingsForContact(contactId: String)
}