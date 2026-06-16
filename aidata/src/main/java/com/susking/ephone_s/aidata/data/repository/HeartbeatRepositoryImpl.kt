package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.HeartbeatDao
import com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class HeartbeatRepositoryImpl(private val heartbeatDao: HeartbeatDao) : HeartbeatRepository {
    override suspend fun createHeartbeat(contactId: String, content: String, aiTurnId: String?, sourceMessageId: String?) = withContext(Dispatchers.IO) {
        val heartbeat = HeartbeatEntity(
            contactId = contactId,
            content = content,
            timestamp = System.currentTimeMillis(),
            aiTurnId = aiTurnId,
            sourceMessageId = sourceMessageId
        )
        heartbeatDao.insertHeartbeat(heartbeat)
    }

    override fun getLatestHeartbeatForContact(contactId: String): Flow<HeartbeatEntity?> {
        return heartbeatDao.getHeartbeatsForContact(contactId).map { it.firstOrNull() }
    }

    override fun getAllHeartbeatsForContact(contactId: String): Flow<List<HeartbeatEntity>> {
        return heartbeatDao.getHeartbeatsForContact(contactId)
    }

    override fun getAllHeartbeats(): Flow<List<HeartbeatEntity>> {
        return heartbeatDao.getAllHeartbeats()
    }

    override suspend fun updateHeartbeat(heartbeat: HeartbeatEntity) = withContext(Dispatchers.IO) {
        heartbeatDao.updateHeartbeat(heartbeat)
    }

    override suspend fun deleteHeartbeatsByAiTurnId(aiTurnId: String) = withContext(Dispatchers.IO) {
        heartbeatDao.deleteHeartbeatsByAiTurnId(aiTurnId)
    }

    override suspend fun deleteHeartbeatsForContact(contactId: String) = withContext(Dispatchers.IO) {
        heartbeatDao.deleteHeartbeatsForContact(contactId)
    }
}