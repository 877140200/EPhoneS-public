package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.susking.ephone_s.aidata.data.local.converters.VideoCallMessageListConverter

/**
 * 视频通话历史记录实体类
 * 用于存储每次视频通话的完整信息
 */
@Entity(tableName = "video_call_history")
@TypeConverters(VideoCallMessageListConverter::class)
data class VideoCallHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 联系人ID
     */
    val contactId: String,
    
    /**
     * 通话开始时间戳（毫秒）
     */
    val timestamp: Long,
    
    /**
     * 通话时长（秒）
     */
    val duration: Long,
    
    /**
     * 通话消息列表（JSON格式存储）
     */
    val messages: List<VideoCallMessageEntity> = emptyList(),
    
    /**
     * 是否由用户发起
     */
    val wasInitiatedByUser: Boolean = true,
    
    /**
     * 通话结束原因
     */
    val terminationReason: String? = null,
    
    /**
     * 通话状态
     * - "in_progress": 通话进行中（可恢复）
     * - "completed": 正常结束（不可恢复）
     * - "interrupted": 异常中断/超时（不可恢复）
     */
    val callStatus: String = "in_progress",
    
    /**
     * 最后更新时间戳（毫秒）
     * 用于判断通话是否超时
     */
    val lastUpdateTime: Long = System.currentTimeMillis()
)

/**
 * 视频通话消息实体类
 * 用于存储通话过程中的每条消息
 */
data class VideoCallMessageEntity(
    val id: String,
    val content: String,
    val timestamp: Long,
    val role: String // "user" 或 "assistant"
)