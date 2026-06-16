package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldBookEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WorldBookEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WorldBookEntryEntity>) // 批量插入世界书条目

    @Update
    suspend fun updateEntry(entry: WorldBookEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: WorldBookEntryEntity)

    @Query("SELECT * FROM world_book_entries WHERE worldBookId = :worldBookId ORDER BY isSystemEntry DESC, displayOrder ASC")
    fun getEntriesForWorldBook(worldBookId: Long): Flow<List<WorldBookEntryEntity>>

    @Update
    suspend fun updateEntries(entries: List<WorldBookEntryEntity>)

    @Query("SELECT * FROM world_book_entries WHERE isEnabled = 1 ORDER BY worldBookId, displayOrder ASC")
    suspend fun getAllEnabledEntries(): List<WorldBookEntryEntity>

    @Query("SELECT * FROM world_book_entries WHERE worldBookId = :worldBookId AND name = :name")
    suspend fun getEntryByWorldBookIdAndName(worldBookId: Long, name: String): WorldBookEntryEntity?
    
    // ===== 以下方法用于数据迁移 =====
    
    /**
     * 获取所有条目(同步方法,用于迁移)
     */
    @Query("SELECT * FROM world_book_entries")
    suspend fun getAllEntriesSync(): List<WorldBookEntryEntity>
    
    /**
     * 批量插入条目(用于迁移,别名方法)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllEntries(entries: List<WorldBookEntryEntity>)
    
    /**
     * 删除所有条目(用于迁移清理)
     */
    @Query("DELETE FROM world_book_entries")
    suspend fun deleteAllEntries()
}