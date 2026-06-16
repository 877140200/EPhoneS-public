package com.susking.ephone_s.aidata.domain.repository

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
import kotlinx.coroutines.flow.Flow

/**
 * CPhone数据仓库接口
 * 提供查手机数据的业务操作
 */
interface CPhoneRepository {
    
    /**
     * 获取指定联系人的CPhone数据（Flow）
     */
    fun getCPhoneData(contactId: String): Flow<CPhoneData?>
    
    /**
     * 获取指定联系人的CPhone数据（挂起函数）
     */
    suspend fun getCPhoneDataSuspend(contactId: String): CPhoneData?
    
    /**
     * 保存完整的CPhone数据
     */
    suspend fun saveCPhoneData(cphoneData: CPhoneData)
    
    /**
     * 更新相册数据
     */
    suspend fun updateAlbumPhotos(contactId: String, photos: List<AlbumPhoto>)
    
    /**
     * 更新浏览器历史
     */
    suspend fun updateBrowserHistory(contactId: String, history: List<BrowserRecord>)
    
    /**
     * 更新淘宝数据
     */
    suspend fun updateTaobaoData(contactId: String, data: TaobaoData?)
    
    /**
     * 更新淘宝购买记录
     */
    suspend fun updateTaobaoPurchases(contactId: String, purchases: List<TaobaoPurchase>)
    
    /**
     * 更新备忘录
     */
    suspend fun updateMemos(contactId: String, memos: List<Memo>)
    
    /**
     * 更新日记
     */
    suspend fun updateDiaryEntries(contactId: String, entries: List<DiaryEntry>)
    
    /**
     * 更新高德地图足迹
     */
    suspend fun updateAmapFootprints(contactId: String, footprints: List<AmapFootprint>)
    
    /**
     * 更新App使用记录
     */
    suspend fun updateAppUsageRecords(contactId: String, records: List<AppUsageRecord>)
    
    /**
     * 更新音乐歌曲
     */
    suspend fun updateMusicTracks(contactId: String, tracks: List<MusicTrack>)
    
    /**
     * 更新QQ对话
     */
    suspend fun updateQQConversations(contactId: String, conversations: List<QQConversation>)
    
    /**
     * 删除指定联系人的CPhone数据
     */
    suspend fun deleteCPhoneData(contactId: String)
    
    /**
     * 清空所有CPhone数据
     */
    suspend fun deleteAllCPhoneData()
}