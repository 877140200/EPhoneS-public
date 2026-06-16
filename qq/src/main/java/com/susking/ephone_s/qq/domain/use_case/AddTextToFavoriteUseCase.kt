package com.susking.ephone_s.qq.domain.use_case

import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.aidata.domain.repository.FavoriteMessageRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import java.util.UUID
import javax.inject.Inject

/**
 * 添加外部文本到收藏的用例
 * 用于处理跨应用文本选择功能
 */
class AddTextToFavoriteUseCase @Inject constructor(
    private val favoriteMessageRepository: FavoriteMessageRepository,
    private val personProfileRepository: PersonProfileRepository
) {
    /**
     * 执行添加文本到收藏
     * @param text 要收藏的文本内容
     * @param source 来源描述
     * @return 是否成功
     */
    suspend operator fun invoke(text: String, source: String): Result<Unit> {
        return try {
            // 获取用户信息
            val userProfile = personProfileRepository.getUserProfile()
            val favoriteEntity = FavoriteMessageEntity(
                messageId = generateMessageId(),
                contactId = "system", // 系统来源，非联系人
                text = text,
                content = text,
                senderName = userProfile.nickname,
                senderAvatar = userProfile.avatarUri,
                source = source.ifEmpty { "外部应用" },
                timestamp = System.currentTimeMillis(),
                type = "text"
            )
            favoriteMessageRepository.addFavorite(favoriteEntity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateMessageId(): String {
        return "process_text_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }
}