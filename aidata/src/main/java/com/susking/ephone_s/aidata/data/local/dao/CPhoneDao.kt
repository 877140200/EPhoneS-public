package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.susking.ephone_s.aidata.data.local.entity.CPhoneDataEntity
import kotlinx.coroutines.flow.Flow

/**
 * CPhone数据访问对象（DAO）
 * 定义Room数据库的CRUD操作方法
 */
@Dao
interface CPhoneDao {
    
    /**
     * 插入或更新CPhone数据
     * 如果characterId已存在，则替换整条记录
     * 
     * @param data CPhone数据实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(data: CPhoneDataEntity)
    
    /**
     * 查询指定角色的CPhone数据
     * 
     * @param characterId 角色ID
     * @return CPhone数据实体，如果不存在则返回null
     */
    @Query("SELECT * FROM cphone_data WHERE characterId = :characterId")
    suspend fun getDataByCharacterId(characterId: String): CPhoneDataEntity?
    
    /**
     * 以Flow形式观察指定角色的CPhone数据
     * UI层可以订阅此Flow以实时更新
     * 
     * @param characterId 角色ID
     * @return CPhone数据实体的Flow
     */
    @Query("SELECT * FROM cphone_data WHERE characterId = :characterId")
    fun observeDataByCharacterId(characterId: String): Flow<CPhoneDataEntity?>
    
    /**
     * 删除指定角色的CPhone数据
     * 
     * @param characterId 角色ID
     */
    @Query("DELETE FROM cphone_data WHERE characterId = :characterId")
    suspend fun deleteByCharacterId(characterId: String)
    
    /**
     * 更新指定角色的相册数据
     * 
     * @param characterId 角色ID
     * @param albumDataJson 相册数据JSON字符串
     */
    @Query("UPDATE cphone_data SET albumDataJson = :albumDataJson, lastUpdated = :timestamp WHERE characterId = :characterId")
    suspend fun updateAlbumData(characterId: String, albumDataJson: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * 更新指定角色的浏览器数据
     * 
     * @param characterId 角色ID
     * @param browserDataJson 浏览器数据JSON字符串
     */
    @Query("UPDATE cphone_data SET browserDataJson = :browserDataJson, lastUpdated = :timestamp WHERE characterId = :characterId")
    suspend fun updateBrowserData(characterId: String, browserDataJson: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * 更新指定角色的淘宝数据
     * 
     * @param characterId 角色ID
     * @param taobaoDataJson 淘宝数据JSON字符串
     */
    @Query("UPDATE cphone_data SET taobaoDataJson = :taobaoDataJson, lastUpdated = :timestamp WHERE characterId = :characterId")
    suspend fun updateTaobaoData(characterId: String, taobaoDataJson: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * 更新指定角色的备忘录数据
     * 
     * @param characterId 角色ID
     * @param memoDataJson 备忘录数据JSON字符串
     */
    @Query("UPDATE cphone_data SET memoDataJson = :memoDataJson, lastUpdated = :timestamp WHERE characterId = :characterId")
    suspend fun updateMemoData(characterId: String, memoDataJson: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * 更新指定角色的日记数据
     * 
     * @param characterId 角色ID
     * @param diaryDataJson 日记数据JSON字符串
     */
    @Query("UPDATE cphone_data SET diaryDataJson = :diaryDataJson, lastUpdated = :timestamp WHERE characterId = :characterId")
    suspend fun updateDiaryData(characterId: String, diaryDataJson: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * 更新指定角色的高德地图数据
     * 
     * @param characterId 角色ID
     * @param amapDataJson 高德地图数据JSON字符串
     */
    @Query("UPDATE cphone_data SET amapDataJson = :amapDataJson, lastUpdated = :timestamp WHERE characterId = :characterId")
    suspend fun updateAmapData(characterId: String, amapDataJson: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * 更新指定角色的App使用数据
     * 
     * @param characterId 角色ID
     * @param appUsageDataJson App使用数据JSON字符串
     */
    @Query("UPDATE cphone_data SET appUsageDataJson = :appUsageDataJson, lastUpdated = :timestamp WHERE characterId = :characterId")
    suspend fun updateAppUsageData(characterId: String, appUsageDataJson: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * 更新指定角色的音乐数据
     * 
     * @param characterId 角色ID
     * @param musicDataJson 音乐数据JSON字符串
     */
    @Query("UPDATE cphone_data SET musicDataJson = :musicDataJson, lastUpdated = :timestamp WHERE characterId = :characterId")
    suspend fun updateMusicData(characterId: String, musicDataJson: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * 更新指定角色的QQ数据
     * 
     * @param characterId 角色ID
     * @param qqDataJson QQ数据JSON字符串
     */
    @Query("UPDATE cphone_data SET qqDataJson = :qqDataJson, lastUpdated = :timestamp WHERE characterId = :characterId")
    suspend fun updateQQData(characterId: String, qqDataJson: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * 获取所有角色的CPhone数据
     * 用于数据管理和备份
     * 
     * @return 所有CPhone数据实体列表
     */
    @Query("SELECT * FROM cphone_data")
    suspend fun getAllData(): List<CPhoneDataEntity>
    
    /**
     * 清空所有CPhone数据
     * 谨慎使用！
     */
    @Query("DELETE FROM cphone_data")
    suspend fun clearAll()
}