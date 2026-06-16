package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * 角色设定和用户设定 Repository 接口
 */
interface PersonProfileRepository {
    // 响应式查询
    fun getPersonProfilesFlow(): Flow<List<PersonProfile>>
    fun getPersonProfileByIdFlow(id: String): Flow<PersonProfile?>
    
    // 一次性查询
    suspend fun getPersonProfiles(): List<PersonProfile>
    suspend fun getPersonProfileById(id: String): PersonProfile?
    
    // 增删改
    suspend fun savePersonProfiles(personProfiles: List<PersonProfile>)
    suspend fun updatePersonProfile(personProfile: PersonProfile)
    suspend fun updatePersonProfiles(profiles: List<PersonProfile>)
    suspend fun deletePersonProfile(id: String)
    
    // 用户配置
    suspend fun getUserProfile(): UserProfile
    suspend fun saveUserProfile(userProfile: UserProfile)
    
    // 辅助方法
    suspend fun updateUnreadMessageCount(id: String, count: Int)
    suspend fun updatePinnedStatus(id: String, isPinned: Boolean)
}