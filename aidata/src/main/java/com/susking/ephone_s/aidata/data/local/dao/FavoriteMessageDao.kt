package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(message: FavoriteMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(messages: List<FavoriteMessageEntity>)

    @Delete
    suspend fun deleteFavorite(message: FavoriteMessageEntity)

    @Delete
    suspend fun deleteFavorites(messages: List<FavoriteMessageEntity>)

    @Query("SELECT * FROM QQfavorites ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteMessageEntity>>

   @Query("SELECT * FROM QQfavorites ORDER BY timestamp DESC")
   suspend fun getAllFavoritesNonFlow(): List<FavoriteMessageEntity>

   @Query("SELECT * FROM QQfavorites WHERE messageId = :messageId")
   suspend fun getFavoriteById(messageId: String): FavoriteMessageEntity?

    @Query("SELECT * FROM QQfavorites WHERE messageId = :messageId")
    fun getFavoriteByMessageId(messageId: String): Flow<FavoriteMessageEntity?>

    @Query("SELECT messageId FROM QQfavorites WHERE contactId = :contactId")
    fun getFavoriteMessageIdsForContact(contactId: String): Flow<List<String>>

    @Query("SELECT messageId FROM QQfavorites WHERE contactId = :contactId")
    suspend fun getFavoriteMessageIdsForContactNonFlow(contactId: String): List<String>

    @Query("UPDATE QQfavorites SET senderName = :newName, senderAvatar = :newAvatar WHERE contactId = :contactId")
    suspend fun updateSenderInfoByContactId(contactId: String, newName: String, newAvatar: String?)

    @Query("DELETE FROM QQfavorites")
    suspend fun clearAll()
}