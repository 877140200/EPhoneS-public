package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.PersonProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * PersonProfile 数据访问对象
 * 提供对角色设定数据的增删改查操作
 */
@Dao
interface PersonProfileDao {
    
    /**
     * 获取所有角色设定（响应式）
     */
    @Query("SELECT * FROM person_profiles ORDER BY isPinned DESC")
    fun getAllPersonProfiles(): Flow<List<PersonProfileEntity>>
    
    /**
     * 获取所有角色设定（非响应式，用于一次性查询）
     */
    @Query("SELECT * FROM person_profiles ORDER BY isPinned DESC")
    suspend fun getAllPersonProfilesNonFlow(): List<PersonProfileEntity>
    
    /**
     * 根据 ID 获取角色设定
     */
    @Query("SELECT * FROM person_profiles WHERE id = :id")
    suspend fun getPersonProfileById(id: String): PersonProfileEntity?
    
    /**
     * 根据 ID 获取角色设定（响应式）
     */
    @Query("SELECT * FROM person_profiles WHERE id = :id")
    fun getPersonProfileByIdFlow(id: String): Flow<PersonProfileEntity?>
    
    /**
     * 插入角色设定，如果已存在则替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonProfile(personProfile: PersonProfileEntity)
    
    /**
     * 批量插入角色设定
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPersonProfiles(personProfiles: List<PersonProfileEntity>)
    
    /**
     * 更新角色设定
     */
    @Update
    suspend fun updatePersonProfile(personProfile: PersonProfileEntity)
    
    /**
     * 批量更新角色设定
     */
    @Update
    suspend fun updatePersonProfiles(personProfiles: List<PersonProfileEntity>)
    
    /**
     * 删除角色设定
     */
    @Delete
    suspend fun deletePersonProfile(personProfile: PersonProfileEntity)
    
    /**
     * 根据 ID 删除角色设定
     */
    @Query("DELETE FROM person_profiles WHERE id = :id")
    suspend fun deletePersonProfileById(id: String)
    
    /**
     * 删除所有角色设定
     */
    @Query("DELETE FROM person_profiles")
    suspend fun deleteAllPersonProfiles()
    
    /**
     * 获取角色设定数量
     */
    @Query("SELECT COUNT(*) FROM person_profiles")
    suspend fun getPersonProfileCount(): Int
    
    /**
     * 根据分组获取角色设定
     */
    @Query("SELECT * FROM person_profiles WHERE `group` = :group ORDER BY isPinned DESC")
    fun getPersonProfilesByGroup(group: String): Flow<List<PersonProfileEntity>>
    
    /**
     * 获取置顶的角色设定
     */
    @Query("SELECT * FROM person_profiles WHERE isPinned = 1")
    fun getPinnedPersonProfiles(): Flow<List<PersonProfileEntity>>
    
    /**
     * 更新未读消息数
     */
    @Query("UPDATE person_profiles SET unreadMessageCount = :count WHERE id = :id")
    suspend fun updateUnreadMessageCount(id: String, count: Int)
    
    /**
     * 更新置顶状态
     */
    @Query("UPDATE person_profiles SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinnedStatus(id: String, isPinned: Boolean)
    
    /**
     * 搜索角色设定（根据备注名或真实姓名）
     */
    @Query("""
        SELECT * FROM person_profiles
        WHERE remarkName LIKE '%' || :query || '%'
           OR realName LIKE '%' || :query || '%'
        ORDER BY isPinned DESC
    """)
    fun searchPersonProfiles(query: String): Flow<List<PersonProfileEntity>>
}