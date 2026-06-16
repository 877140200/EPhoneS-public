package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.StickerCategoryEntity
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import kotlinx.coroutines.flow.Flow

interface StickerRepository {

    // --- 表情操作 ---

    fun getAllStickers(): Flow<List<StickerEntity>>

    // 【新增】提供一个挂起函数版本，方便在协程中直接获取列表
    suspend fun getAllStickersSuspend(): List<StickerEntity>

    fun getStickersByCategoryId(categoryId: Int): Flow<List<StickerEntity>>

    fun getUncategorizedStickers(): Flow<List<StickerEntity>>

    suspend fun insertSticker(sticker: StickerEntity)

    suspend fun insertStickers(stickers: List<StickerEntity>)

    suspend fun updateStickers(stickers: List<StickerEntity>)

    suspend fun deleteStickers(stickers: List<StickerEntity>)

    suspend fun deleteStickersByCategoryId(categoryId: Int)

    // --- 分类操作 ---

    fun getAllCategories(): Flow<List<StickerCategoryEntity>>

    suspend fun insertCategory(category: StickerCategoryEntity)

    suspend fun deleteCategory(category: StickerCategoryEntity)
}