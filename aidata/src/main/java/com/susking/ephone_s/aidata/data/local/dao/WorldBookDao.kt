package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldBookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorldBook(worldBook: WorldBookEntity): Long // 插入世界书，如果存在则替换，并返回新插入行的ID

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(worldBooks: List<WorldBookEntity>) // 批量插入世界书

    @Update
    suspend fun updateWorldBook(worldBook: WorldBookEntity) // 更新现有世界书

    @Delete
    suspend fun deleteWorldBook(worldBook: WorldBookEntity) // 删除指定世界书

    @Query("UPDATE world_books SET `order` = :newOrder WHERE worldBookId = :worldBookId")
    suspend fun updateWorldBookOrder(worldBookId: Long, newOrder: Int) // 更新世界书的排序

    @Query("SELECT * FROM world_books ORDER BY isSystem DESC, `order` ASC, updatedAt DESC")
    fun getAllWorldBooks(): Flow<List<WorldBookEntity>> // 获取所有世界书列表，系统世界书置顶，然后按order升序，最后按更新时间降序排列

    @Query("SELECT * FROM world_books WHERE worldBookId = :worldBookId")
    suspend fun getWorldBookById(worldBookId: Long): WorldBookEntity? // 根据世界书ID获取单个世界书

    @Query("SELECT * FROM world_books WHERE category = :category")
    suspend fun getWorldBookByCategory(category: String): WorldBookEntity? // 根据分类名称获取单个世界书

    @Query("SELECT * FROM world_books WHERE isSystem = 1 LIMIT 1")
    suspend fun getSystemWorldBook(): WorldBookEntity?
    
    // ===== 以下方法用于数据迁移 =====
    
    /**
     * 获取世界书数量(用于迁移检查)
     */
    @Query("SELECT COUNT(*) FROM world_books")
    suspend fun getWorldBookCount(): Int
    
    /**
     * 获取所有世界书(同步方法,用于迁移)
     */
    @Query("SELECT * FROM world_books")
    suspend fun getAllWorldBooksSync(): List<WorldBookEntity>
    
    /**
     * 批量插入世界书(用于迁移,别名方法)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWorldBooks(worldBooks: List<WorldBookEntity>)
    
    /**
     * 删除所有世界书(用于迁移清理)
     */
    @Query("DELETE FROM world_books")
    suspend fun deleteAllWorldBooks()
    
    /**
     * 获取所有世界书ID(用于冲突检测)
     */
    @Query("SELECT worldBookId FROM world_books")
    suspend fun getAllWorldBookIds(): List<Long>
}