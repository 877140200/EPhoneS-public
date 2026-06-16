package com.susking.ephone_s.aidata.data.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.local.entity.CPhoneDataEntity
import com.susking.ephone_s.aidata.domain.model.AlbumPhoto
import com.susking.ephone_s.aidata.domain.model.AmapFootprint
import com.susking.ephone_s.aidata.domain.model.AppUsageRecord
import com.susking.ephone_s.aidata.domain.model.BrowserRecord
import com.susking.ephone_s.aidata.domain.model.DiaryEntry
import com.susking.ephone_s.aidata.domain.model.Memo
import com.susking.ephone_s.aidata.domain.model.MusicTrack
import com.susking.ephone_s.aidata.domain.model.QQConversation
import com.susking.ephone_s.aidata.domain.model.TaobaoPurchase

/**
 * CPhone数据映射器
 * 负责Entity（数据库实体）与Domain Model（领域模型）之间的转换
 * 
 * @property gson Gson实例，用于JSON序列化和反序列化
 */
class CPhoneDataMapper(private val gson: Gson) {
    
    // ==================== Entity -> Domain Model ====================
    
    /**
     * 将JSON字符串转换为相册照片列表
     * 
     * @param json JSON字符串
     * @return 相册照片列表，解析失败返回空列表
     */
    fun jsonToAlbumPhotos(json: String?): List<AlbumPhoto> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AlbumPhoto>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 将JSON字符串转换为浏览器历史列表
     * 
     * @param json JSON字符串
     * @return 浏览器历史列表，解析失败返回空列表
     */
    fun jsonToBrowserHistory(json: String?): List<BrowserRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<BrowserRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 将JSON字符串转换为淘宝购物历史列表
     * 
     * @param json JSON字符串
     * @return 淘宝购物历史列表，解析失败返回空列表
     */
    fun jsonToTaobaoHistory(json: String?): List<TaobaoPurchase> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<TaobaoPurchase>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 将JSON字符串转换为备忘录列表
     * 
     * @param json JSON字符串
     * @return 备忘录列表，解析失败返回空列表
     */
    fun jsonToMemos(json: String?): List<Memo> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Memo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 将JSON字符串转换为日记列表
     * 
     * @param json JSON字符串
     * @return 日记列表，解析失败返回空列表
     */
    fun jsonToDiaryEntries(json: String?): List<DiaryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<DiaryEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 将JSON字符串转换为高德地图足迹列表
     * 
     * @param json JSON字符串
     * @return 高德地图足迹列表，解析失败返回空列表
     */
    fun jsonToAmapFootprints(json: String?): List<AmapFootprint> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AmapFootprint>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 将JSON字符串转换为App使用记录列表
     * 
     * @param json JSON字符串
     * @return App使用记录列表，解析失败返回空列表
     */
    fun jsonToAppUsageRecords(json: String?): List<AppUsageRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AppUsageRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 将JSON字符串转换为音乐歌曲列表
     * 
     * @param json JSON字符串
     * @return 音乐歌曲列表，解析失败返回空列表
     */
    fun jsonToMusicTracks(json: String?): List<MusicTrack> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<MusicTrack>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 将JSON字符串转换为QQ模拟对话列表
     * 
     * @param json JSON字符串
     * @return QQ模拟对话列表，解析失败返回空列表
     */
    fun jsonToQQConversations(json: String?): List<QQConversation> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<QQConversation>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // ==================== Domain Model -> Entity ====================
    
    /**
     * 将相册照片列表转换为JSON字符串
     * 
     * @param photos 相册照片列表
     * @return JSON字符串
     */
    fun albumPhotosToJson(photos: List<AlbumPhoto>): String {
        return gson.toJson(photos)
    }
    
    /**
     * 将浏览器历史列表转换为JSON字符串
     * 
     * @param history 浏览器历史列表
     * @return JSON字符串
     */
    fun browserHistoryToJson(history: List<BrowserRecord>): String {
        return gson.toJson(history)
    }
    
    /**
     * 将淘宝购物历史列表转换为JSON字符串
     * 
     * @param history 淘宝购物历史列表
     * @return JSON字符串
     */
    fun taobaoHistoryToJson(history: List<TaobaoPurchase>): String {
        return gson.toJson(history)
    }
    
    /**
     * 将备忘录列表转换为JSON字符串
     * 
     * @param memos 备忘录列表
     * @return JSON字符串
     */
    fun memosToJson(memos: List<Memo>): String {
        return gson.toJson(memos)
    }
    
    /**
     * 将日记列表转换为JSON字符串
     * 
     * @param entries 日记列表
     * @return JSON字符串
     */
    fun diaryEntriesToJson(entries: List<DiaryEntry>): String {
        return gson.toJson(entries)
    }
    
    /**
     * 将高德地图足迹列表转换为JSON字符串
     * 
     * @param footprints 高德地图足迹列表
     * @return JSON字符串
     */
    fun amapFootprintsToJson(footprints: List<AmapFootprint>): String {
        return gson.toJson(footprints)
    }
    
    /**
     * 将App使用记录列表转换为JSON字符串
     * 
     * @param records App使用记录列表
     * @return JSON字符串
     */
    fun appUsageRecordsToJson(records: List<AppUsageRecord>): String {
        return gson.toJson(records)
    }
    
    /**
     * 将音乐歌曲列表转换为JSON字符串
     * 
     * @param tracks 音乐歌曲列表
     * @return JSON字符串
     */
    fun musicTracksToJson(tracks: List<MusicTrack>): String {
        return gson.toJson(tracks)
    }
    
    /**
     * 将QQ对话列表转换为JSON字符串
     * 
     * @param conversations QQ对话列表
     * @return JSON字符串
     */
    fun qqConversationsToJson(conversations: List<QQConversation>): String {
        return gson.toJson(conversations)
    }
    
    // ==================== 便捷方法 ====================
    
    /**
     * 从Entity提取所有数据到领域模型
     * 
     * @param entity CPhone数据实体
     * @return 包含所有数据的Map
     */
    fun entityToAllData(entity: CPhoneDataEntity): Map<String, Any> {
        return mapOf(
            "album" to jsonToAlbumPhotos(entity.albumDataJson),
            "browser" to jsonToBrowserHistory(entity.browserDataJson),
            "taobao" to jsonToTaobaoHistory(entity.taobaoDataJson),
            "memo" to jsonToMemos(entity.memoDataJson),
            "diary" to jsonToDiaryEntries(entity.diaryDataJson),
            "amap" to jsonToAmapFootprints(entity.amapDataJson),
            "appUsage" to jsonToAppUsageRecords(entity.appUsageDataJson),
            "music" to jsonToMusicTracks(entity.musicDataJson),
            "qq" to jsonToQQConversations(entity.qqDataJson)
        )
    }
}