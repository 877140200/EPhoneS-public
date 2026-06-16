package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.FeedDao
import com.susking.ephone_s.aidata.data.local.entity.FeedComment
import com.susking.ephone_s.aidata.data.local.entity.FeedEntity
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.use_case.SaveImageFromBase64UseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val saveImageFromBase64UseCase: SaveImageFromBase64UseCase
) : FeedRepository {
    override suspend fun createFeed(contactId: String, authorName: String, content: String) = withContext(Dispatchers.IO) {
        val feed = FeedEntity(
            contactId = contactId,
            authorName = authorName,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        feedDao.insertFeed(feed)
    }
    
    override suspend fun createFeedWithImages(
        contactId: String,
        authorName: String,
        content: String,
        imageUrls: List<String>,
        imagePrompts: List<String>,
        imageDescriptions: List<String>
    ) = withContext(Dispatchers.IO) {
        // 将base64图片转换为文件路径
        val convertedImageUrls = imageUrls.map { imageUrl ->
            saveImageFromBase64UseCase(imageUrl) ?: imageUrl
        }
        
        val feed = FeedEntity(
            contactId = contactId,
            authorName = authorName,
            content = content,
            timestamp = System.currentTimeMillis(),
            imageUrls = convertedImageUrls,
            imagePrompts = imagePrompts,
            imageDescriptions = imageDescriptions
        )
        feedDao.insertFeed(feed)
    }

    override fun getAllFeeds(): Flow<List<FeedEntity>> {
        return feedDao.getAllFeeds()
    }

    override suspend fun getFeedById(feedId: Int): FeedEntity? = withContext(Dispatchers.IO) {
        feedDao.getFeedById(feedId)
    }

    override suspend fun updateFeed(feed: FeedEntity) = withContext(Dispatchers.IO) {
        feedDao.insertFeed(feed)
    }

    override suspend fun deleteFeed(feed: FeedEntity) = withContext(Dispatchers.IO) {
        feedDao.deleteFeed(feed)
    }

    override suspend fun deleteFeedsForContact(contactId: String) = withContext(Dispatchers.IO) {
        feedDao.deleteFeedsForContact(contactId)
    }

    override suspend fun shareFeed(originalFeed: FeedEntity, sharedByContactId: String, sharedByContactName: String) = withContext(Dispatchers.IO) {
        val sharedFeed = FeedEntity(
            contactId = sharedByContactId,
            authorName = sharedByContactName,
            content = "转发了 ${originalFeed.authorName} 的动态：\n${originalFeed.content}",
            timestamp = System.currentTimeMillis(),
            originalFeedId = originalFeed.id
        )
        feedDao.insertFeed(sharedFeed)
    }

    override suspend fun toggleLike(feedId: Int, contactId: String) = withContext(Dispatchers.IO) {
        val feed = feedDao.getFeedById(feedId)
        if (feed != null) {
            val newLikes = feed.likes.toMutableList()
            if (newLikes.contains(contactId)) {
                newLikes.remove(contactId)
            } else {
                newLikes.add(contactId)
            }
            feedDao.insertFeed(feed.copy(likes = newLikes))
        }
    }

    override suspend fun addComment(feedId: Int, commenterId: String, commenterName: String, commentText: String?, stickerUrl: String?, stickerMeaning: String?) = withContext(Dispatchers.IO) {
        val feed = feedDao.getFeedById(feedId)
        if (feed != null) {
            val newComment = FeedComment(
                commenterId = commenterId,
                commenterName = commenterName,
                commentText = commentText,
                stickerUrl = stickerUrl,
                stickerMeaning = stickerMeaning,
                timestamp = System.currentTimeMillis()
            )
            val newComments = feed.comments.toMutableList().apply { add(newComment) }
            feedDao.insertFeed(feed.copy(comments = newComments))
        }
    }

    override suspend fun deleteFeedById(feedId: Int) = withContext(Dispatchers.IO) {
        feedDao.deleteFeedById(feedId)
    }
}