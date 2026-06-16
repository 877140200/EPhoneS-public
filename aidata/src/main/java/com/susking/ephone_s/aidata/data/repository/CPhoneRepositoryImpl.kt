package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.CPhoneDao
import com.susking.ephone_s.aidata.data.local.entity.CPhoneDataEntity
import com.susking.ephone_s.aidata.data.mapper.CPhoneDataMapper
import com.susking.ephone_s.aidata.domain.model.AlbumPhoto
import com.susking.ephone_s.aidata.domain.model.AmapFootprint
import com.susking.ephone_s.aidata.domain.model.AppUsageRecord
import com.susking.ephone_s.aidata.domain.model.BrowserRecord
import com.susking.ephone_s.aidata.domain.model.CPhoneData
import com.susking.ephone_s.aidata.domain.model.DiaryEntry
import com.susking.ephone_s.aidata.domain.model.Memo
import com.susking.ephone_s.aidata.domain.model.MusicTrack
import com.susking.ephone_s.aidata.domain.model.QQConversation
import com.susking.ephone_s.aidata.domain.model.TaobaoData
import com.susking.ephone_s.aidata.domain.model.TaobaoPurchase
import com.susking.ephone_s.aidata.domain.repository.CPhoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * CPhone数据仓库实现
 * 负责CPhone数据的存储和读取
 * 
 * 注意：使用characterId而不是contactId作为主键
 */
class CPhoneRepositoryImpl(
    private val cphoneDao: CPhoneDao,
    private val mapper: CPhoneDataMapper
) : CPhoneRepository {
    
    override fun getCPhoneData(contactId: String): Flow<CPhoneData?> {
        // contactId就是characterId
        return cphoneDao.observeDataByCharacterId(contactId).map { entity ->
            entity?.toDomain()
        }
    }
    
    override suspend fun getCPhoneDataSuspend(contactId: String): CPhoneData? {
        // contactId就是characterId
        return cphoneDao.getDataByCharacterId(contactId)?.toDomain()
    }
    
    override suspend fun saveCPhoneData(cphoneData: CPhoneData) {
        cphoneDao.insertOrUpdate(cphoneData.toEntity())
    }
    
    override suspend fun updateAlbumPhotos(contactId: String, photos: List<AlbumPhoto>) {
        val json = mapper.albumPhotosToJson(photos)
        cphoneDao.updateAlbumData(contactId, json, System.currentTimeMillis())
    }
    
    override suspend fun updateBrowserHistory(contactId: String, history: List<BrowserRecord>) {
        val json = mapper.browserHistoryToJson(history)
        cphoneDao.updateBrowserData(contactId, json, System.currentTimeMillis())
    }
    
    override suspend fun updateTaobaoData(contactId: String, data: TaobaoData?) {
        val json = mapper.taobaoHistoryToJson(data?.purchases ?: emptyList())
        cphoneDao.updateTaobaoData(contactId, json, System.currentTimeMillis())
    }
    
    override suspend fun updateTaobaoPurchases(contactId: String, purchases: List<TaobaoPurchase>) {
        // 获取现有淘宝数据
        val existingData = getCPhoneDataSuspend(contactId)
        val updatedTaobaoData = existingData?.taobaoData?.copy(purchases = purchases)
            ?: TaobaoData(purchases = purchases)
        
        // 更新整个淘宝数据
        updateTaobaoData(contactId, updatedTaobaoData)
    }
    
    override suspend fun updateMemos(contactId: String, memos: List<Memo>) {
        val json = mapper.memosToJson(memos)
        cphoneDao.updateMemoData(contactId, json, System.currentTimeMillis())
    }
    
    override suspend fun updateDiaryEntries(contactId: String, entries: List<DiaryEntry>) {
        val json = mapper.diaryEntriesToJson(entries)
        cphoneDao.updateDiaryData(contactId, json, System.currentTimeMillis())
    }
    
    override suspend fun updateAmapFootprints(contactId: String, footprints: List<AmapFootprint>) {
        val json = mapper.amapFootprintsToJson(footprints)
        cphoneDao.updateAmapData(contactId, json, System.currentTimeMillis())
    }
    
    override suspend fun updateAppUsageRecords(contactId: String, records: List<AppUsageRecord>) {
        val json = mapper.appUsageRecordsToJson(records)
        cphoneDao.updateAppUsageData(contactId, json, System.currentTimeMillis())
    }
    
    override suspend fun updateMusicTracks(contactId: String, tracks: List<MusicTrack>) {
        val json = mapper.musicTracksToJson(tracks)
        cphoneDao.updateMusicData(contactId, json, System.currentTimeMillis())
    }
    
    override suspend fun updateQQConversations(contactId: String, conversations: List<QQConversation>) {
        val json = mapper.qqConversationsToJson(conversations)
        cphoneDao.updateQQData(contactId, json, System.currentTimeMillis())
    }
    
    override suspend fun deleteCPhoneData(contactId: String) {
        cphoneDao.deleteByCharacterId(contactId)
    }
    
    override suspend fun deleteAllCPhoneData() {
        cphoneDao.clearAll()
    }
    
    // ========== 数据转换 ==========
    
    /**
     * Entity转Domain
     */
    private fun CPhoneDataEntity.toDomain(): CPhoneData {
        val taobaoPurchases = mapper.jsonToTaobaoHistory(taobaoDataJson)
        
        return CPhoneData(
            contactId = characterId, // 注意：Domain层仍然使用contactId字段名
            albumPhotos = mapper.jsonToAlbumPhotos(albumDataJson),
            browserHistory = mapper.jsonToBrowserHistory(browserDataJson),
            taobaoData = if (taobaoPurchases.isEmpty()) null else TaobaoData(purchases = taobaoPurchases),
            memos = mapper.jsonToMemos(memoDataJson),
            diaryEntries = mapper.jsonToDiaryEntries(diaryDataJson),
            amapFootprints = mapper.jsonToAmapFootprints(amapDataJson),
            appUsageRecords = mapper.jsonToAppUsageRecords(appUsageDataJson),
            musicTracks = mapper.jsonToMusicTracks(musicDataJson),
            qqConversations = mapper.jsonToQQConversations(qqDataJson),
            lastUpdated = lastUpdated
        )
    }
    
    /**
     * Domain转Entity
     */
    private fun CPhoneData.toEntity(): CPhoneDataEntity {
        return CPhoneDataEntity(
            characterId = contactId, // 注意：Domain层的contactId对应Entity层的characterId
            albumDataJson = mapper.albumPhotosToJson(albumPhotos),
            browserDataJson = mapper.browserHistoryToJson(browserHistory),
            taobaoDataJson = mapper.taobaoHistoryToJson(taobaoData?.purchases ?: emptyList()),
            memoDataJson = mapper.memosToJson(memos),
            diaryDataJson = mapper.diaryEntriesToJson(diaryEntries),
            amapDataJson = mapper.amapFootprintsToJson(amapFootprints),
            appUsageDataJson = mapper.appUsageRecordsToJson(appUsageRecords),
            musicDataJson = mapper.musicTracksToJson(musicTracks),
            qqDataJson = mapper.qqConversationsToJson(qqConversations),
            lastUpdated = lastUpdated
        )
    }
}