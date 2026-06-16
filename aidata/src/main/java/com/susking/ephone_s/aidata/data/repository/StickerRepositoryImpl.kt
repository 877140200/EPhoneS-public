package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.StickerDao
import com.susking.ephone_s.aidata.data.local.entity.StickerCategoryEntity
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import com.susking.ephone_s.aidata.domain.repository.StickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class StickerRepositoryImpl(private val stickerDao: StickerDao) : StickerRepository {

    // --- 表情操作 ---

    override fun getAllStickers(): Flow<List<StickerEntity>> = stickerDao.getAllStickers()

    // 【新增】提供一个挂起函数版本，方便在协程中直接获取列表
    override suspend fun getAllStickersSuspend(): List<StickerEntity> {
        return stickerDao.getAllStickers().first()
    }

    override fun getStickersByCategoryId(categoryId: Int): Flow<List<StickerEntity>> =
        stickerDao.getStickersByCategoryId(categoryId)

    override fun getUncategorizedStickers(): Flow<List<StickerEntity>> =
        stickerDao.getUncategorizedStickers()

    override suspend fun insertSticker(sticker: StickerEntity) {
        withContext(Dispatchers.IO) {
            stickerDao.insertSticker(sticker)
        }
    }

    override suspend fun insertStickers(stickers: List<StickerEntity>) {
        withContext(Dispatchers.IO) {
            stickerDao.insertStickers(stickers)
        }
    }

    override suspend fun updateStickers(stickers: List<StickerEntity>) {
        withContext(Dispatchers.IO) {
            stickerDao.updateStickers(stickers)
        }
    }

    override suspend fun deleteStickers(stickers: List<StickerEntity>) {
        withContext(Dispatchers.IO) {
            stickerDao.deleteStickers(stickers)
        }
    }

    override suspend fun deleteStickersByCategoryId(categoryId: Int) {
        withContext(Dispatchers.IO) {
            stickerDao.deleteStickersByCategoryId(categoryId)
        }
    }

    // --- 分类操作 ---

    override fun getAllCategories(): Flow<List<StickerCategoryEntity>> = stickerDao.getAllCategories()

    override suspend fun insertCategory(category: StickerCategoryEntity) {
        withContext(Dispatchers.IO) {
            stickerDao.insertCategory(category)
        }
    }

    override suspend fun deleteCategory(category: StickerCategoryEntity) {
        withContext(Dispatchers.IO) {
            stickerDao.deleteCategory(category)
        }
    }
}
