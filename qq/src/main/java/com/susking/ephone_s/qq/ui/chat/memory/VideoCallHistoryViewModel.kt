package com.susking.ephone_s.qq.ui.chat.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.repository.VideoCallHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视频通话历史记录ViewModel
 * 负责管理视频通话历史记录的数据
 */
@HiltViewModel
class VideoCallHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoCallHistoryRepository: VideoCallHistoryRepository
) : ViewModel() {

    private val contactId: String = savedStateHandle.get<String>("contactId")
        ?: throw IllegalArgumentException("contactId is required")

    /**
     * 获取当前联系人的所有视频通话历史记录
     * 将数据库实体转换为UI所需的VideoCallHistoryItem
     */
    val videoCallHistories = videoCallHistoryRepository.getVideoCallHistoryByContactId(contactId)
        .map { entities ->
            entities.map { entity ->
                VideoCallHistoryItem(
                    id = entity.id.toString(),
                    timestamp = entity.timestamp,
                    duration = entity.duration,
                    messages = entity.messages.map { messageEntity ->
                        VideoCallMessage(
                            id = messageEntity.id,
                            content = messageEntity.content,
                            timestamp = messageEntity.timestamp,
                            isFromUser = messageEntity.role == "user"
                        )
                    }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 根据ID获取单个视频通话历史记录
     * @param historyId 视频通话历史记录的ID
     * @return VideoCallHistoryItem或null
     */
    suspend fun getVideoCallHistoryById(historyId: String): VideoCallHistoryItem? {
        val id = historyId.toLongOrNull() ?: return null
        val entity = videoCallHistoryRepository.getVideoCallHistoryById(id)
        return entity?.let {
            VideoCallHistoryItem(
                id = it.id.toString(),
                timestamp = it.timestamp,
                duration = it.duration,
                messages = it.messages.map { messageEntity ->
                    VideoCallMessage(
                        id = messageEntity.id,
                        content = messageEntity.content,
                        timestamp = messageEntity.timestamp,
                        isFromUser = messageEntity.role == "user"
                    )
                }
            )
        }
    }

    /**
     * 删除视频通话历史记录
     * @param historyId 要删除的历史记录ID
     */
    fun deleteVideoCallHistory(historyId: String) {
        viewModelScope.launch {
            val id = historyId.toLongOrNull() ?: return@launch
            videoCallHistoryRepository.deleteVideoCallHistoryById(id)
        }
    }

    /**
     * 删除某个联系人的所有视频通话历史记录
     */
    fun deleteAllVideoCallHistoriesForContact() {
        viewModelScope.launch {
            videoCallHistoryRepository.deleteAllVideoCallHistoryByContactId(contactId)
        }
    }
}