package com.susking.ephone_s.qq.domain.repository

import com.susking.ephone_s.aidata.domain.model.PersonProfile
import kotlinx.coroutines.flow.Flow

/**
 * QQ联系人数据仓库接口
 * 
 * 定义QQ联系人相关的数据访问接口
 */
interface QqContactRepository {
    
    /**
     * 获取所有联系人Flow
     */
    fun getPersonProfilesFlow(): Flow<List<PersonProfile>>
    
    /**
     * 根据ID获取联系人
     */
    suspend fun getPersonProfileById(id: String): PersonProfile?
    
    /**
     * 更新联系人
     */
    suspend fun updatePersonProfile(profile: PersonProfile)
    
    /**
     * 删除联系人
     */
    suspend fun deletePersonProfile(id: String)
}