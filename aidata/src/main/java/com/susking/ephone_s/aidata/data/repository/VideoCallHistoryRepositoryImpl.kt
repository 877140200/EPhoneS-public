package com.susking.ephone_s.aidata.data.repository

import com.google.gson.Gson
import com.susking.ephone_s.aidata.data.local.dao.VideoCallHistoryDao
import com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity
import com.susking.ephone_s.aidata.domain.repository.VideoCallHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频通话历史记录Repository实现类
 * 负责管理视频通话历史记录的数据访问
 */
@Singleton
class VideoCallHistoryRepositoryImpl @Inject constructor(
    private val videoCallHistoryDao: VideoCallHistoryDao
) : VideoCallHistoryRepository {
    
    private val gson = Gson()
    
    override suspend fun insertVideoCallHistory(history: VideoCallHistoryEntity): Long {
        return videoCallHistoryDao.insertVideoCallHistory(history)
    }
    
    override suspend fun updateVideoCallHistory(history: VideoCallHistoryEntity) {
        videoCallHistoryDao.updateVideoCallHistory(history)
    }
    
    override suspend fun deleteVideoCallHistory(history: VideoCallHistoryEntity) {
        videoCallHistoryDao.deleteVideoCallHistory(history)
    }
    
    override suspend fun deleteVideoCallHistoryById(historyId: Long) {
        videoCallHistoryDao.deleteVideoCallHistoryById(historyId)
    }
    
    override suspend fun deleteAllVideoCallHistoryByContactId(contactId: String) {
        videoCallHistoryDao.deleteAllVideoCallHistoryByContactId(contactId)
    }
    
    override suspend fun deleteAllVideoCallHistory() {
        videoCallHistoryDao.deleteAllVideoCallHistory()
    }
    
    override suspend fun getVideoCallHistoryById(historyId: Long): VideoCallHistoryEntity? {
        return videoCallHistoryDao.getVideoCallHistoryById(historyId)
    }
    
    override fun getVideoCallHistoryByContactId(contactId: String): Flow<List<VideoCallHistoryEntity>> {
        return videoCallHistoryDao.getVideoCallHistoryByContactId(contactId)
    }
    
    override suspend fun getVideoCallHistoryByContactIdSuspend(contactId: String): List<VideoCallHistoryEntity> {
        return videoCallHistoryDao.getVideoCallHistoryByContactIdSuspend(contactId)
    }
    
    override fun getAllVideoCallHistory(): Flow<List<VideoCallHistoryEntity>> {
        return videoCallHistoryDao.getAllVideoCallHistory()
    }
    
    override suspend fun getAllVideoCallHistorySuspend(): List<VideoCallHistoryEntity> {
        return videoCallHistoryDao.getAllVideoCallHistorySuspend()
    }
    
    override suspend fun getVideoCallHistoryCountByContactId(contactId: String): Int {
        return videoCallHistoryDao.getVideoCallHistoryCountByContactId(contactId)
    }
    
    override suspend fun getTotalVideoCallHistoryCount(): Int {
        return videoCallHistoryDao.getTotalVideoCallHistoryCount()
    }
    
    override suspend fun getInProgressCalls(): List<VideoCallHistoryEntity> {
        return videoCallHistoryDao.getInProgressCalls()
    }
    
    override suspend fun updateCallStatus(id: Long, callStatus: String, terminationReason: String?, duration: Long?) {
        videoCallHistoryDao.updateCallStatus(id, callStatus, terminationReason, duration)
    }
    
    override suspend fun updateVideoCallHistoryFields(
        id: Long,
        messages: List<com.susking.ephone_s.aidata.data.local.entity.VideoCallMessageEntity>,
        duration: Long,
        callStatus: String,
        lastUpdateTime: Long
    ) {
        // 将 List<VideoCallMessageEntity> 转换为 JSON 字符串
        val messagesJson = gson.toJson(messages)
        videoCallHistoryDao.updateVideoCallHistoryFields(id, messagesJson, duration, callStatus, lastUpdateTime)
    }
}