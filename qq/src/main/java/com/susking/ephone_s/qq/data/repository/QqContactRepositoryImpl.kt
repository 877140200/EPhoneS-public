package com.susking.ephone_s.qq.data.repository

import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.qq.domain.repository.QqContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * QQ联系人数据仓库实现
 * 
 * 桥接aidata模块的PersonProfileRepository到qq模块
 * 实现依赖倒置和模块解耦
 */
class QqContactRepositoryImpl @Inject constructor(
    private val personProfileRepository: PersonProfileRepository
) : QqContactRepository {
    
    override fun getPersonProfilesFlow(): Flow<List<PersonProfile>> {
        return personProfileRepository.getPersonProfilesFlow()
    }
    
    override suspend fun getPersonProfileById(id: String): PersonProfile? {
        return personProfileRepository.getPersonProfileById(id)
    }
    
    override suspend fun updatePersonProfile(profile: PersonProfile) {
        personProfileRepository.updatePersonProfile(profile)
    }
    
    override suspend fun deletePersonProfile(id: String) {
        personProfileRepository.deletePersonProfile(id)
    }
}