package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.susking.ephone_s.aidata.data.local.entity.AiResponseVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiResponseVersionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(versions: List<AiResponseVersionEntity>)

    @Query("SELECT * FROM ai_response_versions WHERE chatMessageId = :chatMessageId")
    fun getVersionsForMessage(chatMessageId: String): Flow<List<AiResponseVersionEntity>>

    @Query("DELETE FROM ai_response_versions WHERE chatMessageId = :chatMessageId")
    suspend fun deleteVersionsForMessage(chatMessageId: String)
}