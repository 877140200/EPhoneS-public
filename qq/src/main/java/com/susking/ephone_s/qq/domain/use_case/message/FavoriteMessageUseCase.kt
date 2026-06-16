package com.susking.ephone_s.qq.domain.use_case.message

import android.content.Context
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.repository.FavoriteMessageRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * 收藏消息的UseCase
 * 处理消息收藏和图片复制
 */
class FavoriteMessageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteMessageRepository: FavoriteMessageRepository,
    private val personProfileRepository: PersonProfileRepository
) {
    suspend operator fun invoke(message: ChatMessage, userProfile: UserProfile): Result<Boolean> {
        return try {
            val existingFavorite = favoriteMessageRepository.getFavoriteByMessageId(message.id).first()

            if (existingFavorite != null) {
                // 取消收藏
                favoriteMessageRepository.removeFavorite(existingFavorite)
                Result.success(false)
            } else {
                // 添加收藏
                val favoriteEntity = mapToFavoriteEntity(message, userProfile)

                // 如果是图片,复制到安全目录
                val finalEntity = if (!favoriteEntity.imageUrl.isNullOrBlank()) {
                    val newImagePath = copyImageToFavoritesDir(favoriteEntity.imageUrl!!)
                    if (newImagePath != null) {
                        favoriteEntity.copy(imageUrl = newImagePath)
                    } else {
                        favoriteEntity
                    }
                } else {
                    favoriteEntity
                }

                favoriteMessageRepository.addFavorite(finalEntity)
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun mapToFavoriteEntity(
        message: ChatMessage,
        userProfile: UserProfile
    ): FavoriteMessageEntity {
        val contact = personProfileRepository.getPersonProfileById(message.contactId)

        val (senderName, senderAvatar) = if (message.role == "user") {
            userProfile.nickname to userProfile.avatarUri
        } else {
            (contact?.remarkName ?: contact?.realName ?: "未知") to contact?.avatarUri
        }

        return FavoriteMessageEntity(
            messageId = message.id,
            contactId = message.contactId,
            text = message.content,
            content = message.content,
            senderName = senderName,
            senderAvatar = senderAvatar,
            source = "聊天",
            timestamp = message.timestamp,
            imageUrl = message.imageUrl,
            type = message.type,
            stickerUrl = message.stickerUrl,
            stickerName = message.stickerName,
            amount = message.amount,
            productInfo = message.productInfo,
            notes = message.notes,
            status = message.status,
            greeting = message.greeting,
            recipientName = message.recipientName
        )
    }

    private fun copyImageToFavoritesDir(originalPath: String): String? {
        return try {
            val sourceFile = File(originalPath)
            if (!sourceFile.exists()) return null

            val favoritesDir = File(context.filesDir, "favorites").apply { mkdirs() }
            val destinationFile = File(favoritesDir, sourceFile.name)

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            destinationFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
