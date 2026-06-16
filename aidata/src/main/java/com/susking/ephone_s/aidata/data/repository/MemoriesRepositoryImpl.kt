package com.susking.ephone_s.aidata.data.repository

import androidx.lifecycle.LiveData
import com.susking.ephone_s.aidata.data.local.dao.AppointmentDao
import com.susking.ephone_s.aidata.data.local.dao.GeneralMemoryDao
import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.domain.repository.FavoriteMessageRepository
import com.susking.ephone_s.aidata.domain.repository.MemoriesRepository

/**
 * 记忆（约会和回忆）Repository 实现
 */
class MemoriesRepositoryImpl(
    private val appointmentDao: AppointmentDao,
    private val generalMemoryDao: GeneralMemoryDao,
    private val favoriteMessageRepository: FavoriteMessageRepository
) : MemoriesRepository {

    override val allAppointments: LiveData<List<AppointmentEntity>> =
        appointmentDao.getAllAppointments()

    override suspend fun insert(appointment: AppointmentEntity) {
        appointmentDao.insert(appointment)
    }
    
    override suspend fun update(appointment: AppointmentEntity) {
        appointmentDao.update(appointment)
    }
    
    override suspend fun delete(appointment: AppointmentEntity) {
        appointmentDao.delete(appointment)
    }

    override suspend fun getAllAppointmentsSuspend(): List<AppointmentEntity> {
        return appointmentDao.getAllAppointmentsSuspend()
    }

    override fun getAppointmentsByContactId(contactId: String): LiveData<List<AppointmentEntity>> {
        return appointmentDao.getAppointmentsByContactId(contactId)
    }

    override suspend fun getAppointmentsByContactIdSuspend(contactId: String): List<AppointmentEntity> {
        return appointmentDao.getAppointmentsByContactIdSuspend(contactId)
    }

    override suspend fun getAllFavoriteMessages(): List<FavoriteMessageEntity> {
        return favoriteMessageRepository.getAllFavoritesNonFlow()
    }
    
    override suspend fun insertMemory(memory: GeneralMemoryEntity) {
        generalMemoryDao.insert(memory)
    }
    
    override suspend fun updateMemory(memory: GeneralMemoryEntity) {
        generalMemoryDao.update(memory)
    }
    
    override suspend fun deleteMemory(memory: GeneralMemoryEntity) {
        generalMemoryDao.delete(memory)
    }
    
    override fun getAllMemories(): LiveData<List<GeneralMemoryEntity>> {
        return generalMemoryDao.getAllMemories()
    }

    override fun getMemoriesByContactId(contactId: String): LiveData<List<GeneralMemoryEntity>> {
        return generalMemoryDao.getMemoriesByContactIdLiveData(contactId)
    }
    
    override suspend fun getAllMemoriesSuspend(): List<GeneralMemoryEntity> {
        return generalMemoryDao.getAllMemoriesSuspend()
    }

    override suspend fun getMemoriesByContactIdSuspend(contactId: String): List<GeneralMemoryEntity> {
        return generalMemoryDao.getMemoriesByContactId(contactId)
    }
}