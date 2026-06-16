package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.StickerCategoryEntity
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {

    // --- 表情操作 ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSticker(sticker: StickerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Update
    suspend fun updateSticker(sticker: StickerEntity)

    @Update
    suspend fun updateStickers(stickers: List<StickerEntity>)

    @Delete
    suspend fun deleteSticker(sticker: StickerEntity)

    @Delete
    suspend fun deleteStickers(stickers: List<StickerEntity>)

    @Query("SELECT * FROM stickers")
    fun getAllStickers(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE categoryId = :categoryId")
    fun getStickersByCategoryId(categoryId: Int): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE categoryId IS NULL")
    fun getUncategorizedStickers(): Flow<List<StickerEntity>>

    @Query("DELETE FROM stickers WHERE categoryId = :categoryId")
    suspend fun deleteStickersByCategoryId(categoryId: Int)

    @Query("DELETE FROM stickers")
    suspend fun clearAllStickers()

    // --- 分类操作 ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: StickerCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<StickerCategoryEntity>)

    @Update
    suspend fun updateCategory(category: StickerCategoryEntity)

    @Delete
    suspend fun deleteCategory(category: StickerCategoryEntity)

    @Query("SELECT * FROM sticker_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<StickerCategoryEntity>>

    @Query("DELETE FROM sticker_categories")
    suspend fun clearAllCategories()
}