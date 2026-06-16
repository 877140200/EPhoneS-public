package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 收藏消息 Repository 接口
 * 管理用户收藏的消息
 */
interface FavoriteMessageRepository {
    
    /**
     * 获取所有收藏消息(Flow)
     */
    fun getAllFavorites(): Flow<List<FavoriteMessageEntity>>
    
    /**
     * 获取所有收藏消息(非Flow,用于导出)
     */
    suspend fun getAllFavoritesNonFlow(): List<FavoriteMessageEntity>
    
    /**
     * 添加单个收藏
     */
    suspend fun addFavorite(favorite: FavoriteMessageEntity)
    
    /**
     * 批量添加收藏
     */
    suspend fun addFavorites(favorites: List<FavoriteMessageEntity>)
    
    /**
     * 删除单个收藏
     */
    suspend fun removeFavorite(favorite: FavoriteMessageEntity)
    
    /**
     * 批量删除收藏
     */
    suspend fun removeFavorites(favorites: List<FavoriteMessageEntity>)
    
    /**
     * 更新收藏消息的联系人信息
     * 当联系人的名称或头像更新时调用
     */
    suspend fun updateFavoriteMessagesWithContactInfo(
        contactId: String,
        newName: String,
        newAvatar: String?
    )
    
    /**
     * 根据消息ID获取收藏(Flow)
     */
    fun getFavoriteByMessageId(messageId: String): Flow<FavoriteMessageEntity?>
    
    /**
     * 根据消息ID获取收藏(非Flow)
     */
    suspend fun getFavoriteByMessageIdNonFlow(messageId: String): FavoriteMessageEntity?
    
    /**
     * 获取某联系人的所有收藏消息ID(Flow)
     */
    fun getFavoriteMessageIdsForContact(contactId: String): Flow<List<String>>
    
    /**
     * 获取某联系人的所有收藏消息ID(非Flow)
     */
    suspend fun getFavoriteMessageIdsForContactNonFlow(contactId: String): List<String>
    
    /**
     * 清空所有收藏
     */
    suspend fun clearAll()
}