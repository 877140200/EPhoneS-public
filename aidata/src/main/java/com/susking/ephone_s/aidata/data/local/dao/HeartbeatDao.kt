package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartbeatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartbeat(heartbeat: HeartbeatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(heartbeats: List<HeartbeatEntity>)

    @Update
    suspend fun updateHeartbeat(heartbeat: HeartbeatEntity)

    @Query("SELECT * FROM heartbeats WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getHeartbeatsForContact(contactId: String): Flow<List<HeartbeatEntity>>

    @Query("SELECT * FROM heartbeats ORDER BY timestamp DESC")
    fun getAllHeartbeats(): Flow<List<HeartbeatEntity>>

    @Query("DELETE FROM heartbeats WHERE aiTurnId = :aiTurnId")
    suspend fun deleteHeartbeatsByAiTurnId(aiTurnId: String)

    @Query("DELETE FROM heartbeats WHERE contactId = :contactId")
    suspend fun deleteHeartbeatsForContact(contactId: String)

    @Query("DELETE FROM heartbeats")
    suspend fun clearAll()
}