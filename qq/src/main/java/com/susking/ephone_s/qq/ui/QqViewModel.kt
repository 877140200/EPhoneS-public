package com.susking.ephone_s.qq.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.core.util.Event
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.ui.chat.videoCall.VideoCallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * QQ主ViewModel - 协调层
 * 负责协调各Manager和管理全局导航状态
 */
@HiltViewModel
class QqViewModel @Inject constructor(
    // 注入新的QqContactManager
    val qqContactManager: QqContactManager,
    val videoCallManager: VideoCallManager,
    private val settingsRepository: com.susking.ephone_s.aidata.domain.repository.SettingsRepository
) : ViewModel() {
    
    // 为了兼容性,暂时保留旧的属性名(指向新Manager)
    @Deprecated("使用qqContactManager代替", ReplaceWith("qqContactManager"))
    val contactManager: QqContactManager get() = qqContactManager
    
    @Deprecated("使用qqContactManager.userProfile代替", ReplaceWith("qqContactManager.userProfile"))
    val userProfileManager get() = object {
        val userProfile = qqContactManager.userProfile
    }

    // ==================== 全局导航状态 ====================
    
    private val _selectedContactId = MutableLiveData<String?>()
    val selectedContactId: LiveData<String?> = _selectedContactId

    private val _navigateToChat = MutableLiveData<Event<PersonProfile>>()
    val navigateToChat: LiveData<Event<PersonProfile>> = _navigateToChat

    // ==================== 后台活动状态 ====================
    
    private val _isBackgroundActivityEnabled = MutableLiveData<Boolean>(false)
    val isBackgroundActivityEnabled: LiveData<Boolean> = _isBackgroundActivityEnabled

    // ==================== 导航方法 ====================
    
    fun setActiveContact(contactId: String?) {
        _selectedContactId.value = contactId
    }

    fun navigateToChat(contact: PersonProfile) {
        _navigateToChat.value = Event(contact)
    }

    // ==================== 后台活动 ====================
    
    fun setBackgroundActivityEnabled(enabled: Boolean) {
        _isBackgroundActivityEnabled.value = enabled
    }
    
    // ==================== 设置相关 ====================
    
    /**
     * 清除新动态计数
     */
    fun clearNewFeedsCount() {
        settingsRepository.clearNewFeedsCount()
    }
}
