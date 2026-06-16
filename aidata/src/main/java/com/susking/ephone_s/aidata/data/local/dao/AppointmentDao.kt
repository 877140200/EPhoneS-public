package com.susking.ephone_s.aidata.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity

@Dao
interface AppointmentDao {
    @Insert
    suspend fun insert(appointment: AppointmentEntity)

    @Insert
    suspend fun insertAll(appointments: List<AppointmentEntity>)
    
    @Update
    suspend fun update(appointment: AppointmentEntity)
    
    @Delete
    suspend fun delete(appointment: AppointmentEntity)

    @Query("SELECT * FROM appointments ORDER BY appointmentDate DESC")
    fun getAllAppointments(): LiveData<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE contactId = :contactId ORDER BY appointmentDate DESC")
    fun getAppointmentsByContactId(contactId: String): LiveData<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments ORDER BY appointmentDate DESC")
    suspend fun getAllAppointmentsSuspend(): List<AppointmentEntity>

    @Query("SELECT * FROM appointments WHERE contactId = :contactId ORDER BY appointmentDate DESC")
    suspend fun getAppointmentsByContactIdSuspend(contactId: String): List<AppointmentEntity>

    @Query("DELETE FROM appointments")
    suspend fun clearAll()
}