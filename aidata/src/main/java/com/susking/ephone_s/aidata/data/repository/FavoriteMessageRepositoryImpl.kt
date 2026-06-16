package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.aidata.domain.repository.FavoriteMessageRepository
import kotlinx.coroutines.flow.Flow

/**
 * 收藏消息 Repository 实现
 */
class FavoriteMessageRepositoryImpl(
    private val database: AiDataDatabase
) : FavoriteMessageRepository {
    
    private val favoriteMessageDao = database.favoriteMessageDao()
    
    override fun getAllFavorites(): Flow<List<FavoriteMessageEntity>> {
        return favoriteMessageDao.getAllFavorites()
    }
    
    override suspend fun getAllFavoritesNonFlow(): List<FavoriteMessageEntity> {
        return favoriteMessageDao.getAllFavoritesNonFlow()
    }
    
    override suspend fun addFavorite(favorite: FavoriteMessageEntity) {
        favoriteMessageDao.insertFavorite(favorite)
    }
    
    override suspend fun addFavorites(favorites: List<FavoriteMessageEntity>) {
        favoriteMessageDao.insertFavorites(favorites)
    }
    
    override suspend fun removeFavorite(favorite: FavoriteMessageEntity) {
        favoriteMessageDao.deleteFavorite(favorite)
    }
    
    override suspend fun removeFavorites(favorites: List<FavoriteMessageEntity>) {
        favoriteMessageDao.deleteFavorites(favorites)
    }
    
    override suspend fun updateFavoriteMessagesWithContactInfo(
        contactId: String,
        newName: String,
        newAvatar: String?
    ) {
        favoriteMessageDao.updateSenderInfoByContactId(contactId, newName, newAvatar)
    }
    
    override fun getFavoriteByMessageId(messageId: String): Flow<FavoriteMessageEntity?> {
        return favoriteMessageDao.getFavoriteByMessageId(messageId)
    }
    
    override suspend fun getFavoriteByMessageIdNonFlow(messageId: String): FavoriteMessageEntity? {
        return favoriteMessageDao.getFavoriteById(messageId)
    }
    
    override fun getFavoriteMessageIdsForContact(contactId: String): Flow<List<String>> {
        return favoriteMessageDao.getFavoriteMessageIdsForContact(contactId)
    }
    
    override suspend fun getFavoriteMessageIdsForContactNonFlow(contactId: String): List<String> {
        return favoriteMessageDao.getFavoriteMessageIdsForContactNonFlow(contactId)
    }
    
    override suspend fun clearAll() {
        favoriteMessageDao.clearAll()
    }
}