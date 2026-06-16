package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.ContactSemanticStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * 联系人语义状态数据访问对象。
 *
 * 每个联系人只保留一条状态记录，更新时覆盖原记录，保证召回查询始终读取当前语义状态。
 */
@Dao
interface ContactSemanticStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemanticState(semanticState: ContactSemanticStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(semanticStates: List<ContactSemanticStateEntity>)

    @Update
    suspend fun updateSemanticState(semanticState: ContactSemanticStateEntity)

    @Query("SELECT * FROM contact_semantic_states WHERE contactId = :contactId LIMIT 1")
    fun getSemanticStateForContact(contactId: String): Flow<ContactSemanticStateEntity?>

    @Query("SELECT * FROM contact_semantic_states WHERE contactId = :contactId LIMIT 1")
    suspend fun getSemanticStateSnapshotForContact(contactId: String): ContactSemanticStateEntity?

    @Query("SELECT * FROM contact_semantic_states ORDER BY updatedAt DESC")
    fun getAllSemanticStates(): Flow<List<ContactSemanticStateEntity>>

    @Query("DELETE FROM contact_semantic_states WHERE contactId = :contactId")
    suspend fun deleteSemanticStateForContact(contactId: String)

    @Query("DELETE FROM contact_semantic_states")
    suspend fun clearAll()
}
