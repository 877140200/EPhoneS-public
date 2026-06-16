package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.susking.ephone_s.aidata.data.local.entity.FeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: FeedEntity)

    @Delete
    suspend fun deleteFeed(feed: FeedEntity)

    @Query("SELECT * FROM feeds ORDER BY timestamp DESC")
    fun getAllFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE id = :feedId")
    suspend fun getFeedById(feedId: Int): FeedEntity?

    @Query("DELETE FROM feeds WHERE contactId = :contactId")
    suspend fun deleteFeedsForContact(contactId: String)

    @Query("DELETE FROM feeds WHERE id = :feedId")
    suspend fun deleteFeedById(feedId: Int)

    @Query("DELETE FROM feeds")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(feeds: List<FeedEntity>)
}