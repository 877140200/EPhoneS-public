package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JottingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJotting(jotting: JottingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(jottings: List<JottingEntity>)

    @Update
    suspend fun updateJotting(jotting: JottingEntity)

    @Query("SELECT * FROM jottings WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getJottingsForContact(contactId: String): Flow<List<JottingEntity>>

    @Query("SELECT * FROM jottings ORDER BY timestamp DESC")
    fun getAllJottings(): Flow<List<JottingEntity>>

    @Query("DELETE FROM jottings WHERE aiTurnId = :aiTurnId")
    suspend fun deleteJottingsByAiTurnId(aiTurnId: String)

    @Query("DELETE FROM jottings WHERE contactId = :contactId")
    suspend fun deleteJottingsForContact(contactId: String)

    @Query("DELETE FROM jottings")
    suspend fun clearAll()

}