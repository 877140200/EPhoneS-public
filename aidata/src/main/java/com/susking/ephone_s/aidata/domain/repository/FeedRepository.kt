package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.FeedEntity
import kotlinx.coroutines.flow.Flow

/**
 * 动态 Repository 接口
 */
interface FeedRepository {
    suspend fun createFeed(contactId: String, authorName: String, content: String)
    
    /**
     * 创建带图片的动态
     * @param imageUrls 图片URL列表（可以是Base64或文件路径）
     * @param imagePrompts NovelAI提示词列表
     * @param imageDescriptions 图片描述列表(hiddenContent)
     */
    suspend fun createFeedWithImages(
        contactId: String,
        authorName: String,
        content: String,
        imageUrls: List<String>,
        imagePrompts: List<String> = emptyList(),
        imageDescriptions: List<String> = emptyList()
    )
    
    /**
     * 获取所有动态
     */
    fun getAllFeeds(): Flow<List<FeedEntity>>
    
    /**
     * 根据ID获取动态
     */
    suspend fun getFeedById(feedId: Int): FeedEntity?
    
    /**
     * 插入动态
     */
    suspend fun updateFeed(feed: FeedEntity)

    /**
     * 删除动态
     */
    suspend fun deleteFeed(feed: FeedEntity)

    /**
     * 删除指定联系人的所有动态
     */
    suspend fun deleteFeedsForContact(contactId: String)
    suspend fun shareFeed(originalFeed: FeedEntity, sharedByContactId: String, sharedByContactName: String)
    suspend fun toggleLike(feedId: Int, contactId: String)
    suspend fun addComment(feedId: Int, commenterId: String, commenterName: String, commentText: String? = null, stickerUrl: String? = null, stickerMeaning: String? = null)
    suspend fun deleteFeedById(feedId: Int)
}