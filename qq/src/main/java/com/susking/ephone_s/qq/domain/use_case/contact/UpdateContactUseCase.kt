package com.susking.ephone_s.qq.domain.use_case.contact

import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import javax.inject.Inject

/**
 * 更新联系人信息的UseCase
 * 封装联系人更新的业务逻辑
 */
class UpdateContactUseCase @Inject constructor(
    private val personProfileRepository: PersonProfileRepository
) {
    suspend operator fun invoke(updatedContact: PersonProfile): Result<Unit> {
        return try {
            personProfileRepository.updatePersonProfile(updatedContact)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 切换置顶状态
     */
    suspend fun togglePin(contactId: String): Result<Unit> {
        return try {
            val contact = personProfileRepository.getPersonProfileById(contactId)
                ?: return Result.failure(Exception("联系人不存在"))

            val updated = contact.copy(isPinned = !contact.isPinned)
            personProfileRepository.updatePersonProfile(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
