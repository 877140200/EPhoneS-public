package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.JottingDao
import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class JottingRepositoryImpl(private val jottingDao: JottingDao) : JottingRepository {
    override suspend fun createJotting(contactId: String, title: String, content: String, aiTurnId: String?, sourceMessageId: String?) = withContext(Dispatchers.IO) {
        val jotting = JottingEntity(
            contactId = contactId,
            title = title,
            content = content,
            timestamp = System.currentTimeMillis(),
            aiTurnId = aiTurnId,
            sourceMessageId = sourceMessageId
        )
        jottingDao.insertJotting(jotting)
    }

    override fun getLatestJottingForContact(contactId: String): Flow<JottingEntity?> {
        return jottingDao.getJottingsForContact(contactId).map { it.firstOrNull() }
    }

    override fun getAllJottingsForContact(contactId: String): Flow<List<JottingEntity>> {
        return jottingDao.getJottingsForContact(contactId)
    }

    override fun getAllJottings(): Flow<List<JottingEntity>> {
        return jottingDao.getAllJottings()
    }

    override suspend fun updateJotting(jotting: JottingEntity) = withContext(Dispatchers.IO) {
        jottingDao.updateJotting(jotting)
    }

    override suspend fun deleteJottingsByAiTurnId(aiTurnId: String) = withContext(Dispatchers.IO) {
        jottingDao.deleteJottingsByAiTurnId(aiTurnId)
    }

    override suspend fun deleteJottingsForContact(contactId: String) = withContext(Dispatchers.IO) {
        jottingDao.deleteJottingsForContact(contactId)
    }
}