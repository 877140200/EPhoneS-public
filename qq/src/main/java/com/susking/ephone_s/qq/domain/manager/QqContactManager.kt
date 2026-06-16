
package com.susking.ephone_s.qq.domain.manager

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.repository.ChatRepositoryImpl.ContactsChangedEvent
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.core.util.Event
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.qq.domain.model.ContactWithLatestMessage
import com.susking.ephone_s.qq.domain.use_case.contact.AcceptFriendRequestUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.DeclineFriendRequestUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.DeleteContactUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.LoadContactsUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.ToggleBlockStatusUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.UpdateContactUseCase
import com.susking.ephone_s.qq.ui.contactList.ContactListFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QQ 联系人管理器
 * 
 * 合并了以下Manager的功能:
 * - ContactManager: 联系人增删改查、状态管理
 * - GroupManager: 联系人分组管理
 * - UserProfileManager: 用户资料管理
 * 
 * 职责:
 * 1. 联系人管理(增删改查、置顶、拉黑、未读)
 * 2. 分组管理(创建、删除、排序、展开/折叠)
 * 3. 用户资料管理(加载、更新)
 * 4. 好友申请处理
 * 
 * 通信:
 * - 监听 ContactsChanged 事件,自动刷新联系人列表
 * - 监听 MessageSent 事件,更新最新消息时间
 */
@Singleton
class QqContactManager @Inject constructor(
    private val loadContactsUseCase: LoadContactsUseCase,
    private val updateContactUseCase: UpdateContactUseCase,
    private val deleteContactUseCase: DeleteContactUseCase,
    private val toggleBlockStatusUseCase: ToggleBlockStatusUseCase,
    private val acceptFriendRequestUseCase: AcceptFriendRequestUseCase,
    private val declineFriendRequestUseCase: DeclineFriendRequestUseCase,
    private val personProfileRepository: PersonProfileRepository,
    private val chatRepository: ChatRepository,
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson,
    private val coroutineScope: CoroutineScope
) {

    companion object {
        private const val TAG = "QqContactManager"
    }

    // ==================== 联系人状态 ====================
    
    private val _contacts = MutableLiveData<List<PersonProfile>>()
    val contacts: LiveData<List<PersonProfile>> = _contacts

    // 消息列表联系人(按最后消息时间排序,包含最新消息信息)
    private val _messageListContacts = MutableLiveData<List<ContactWithLatestMessage>>()
    val messageListContacts: LiveData<List<ContactWithLatestMessage>> = _messageListContacts

    private val _totalUnreadCount = MutableLiveData<Int>(0)
    val totalUnreadCount: LiveData<Int> = _totalUnreadCount

    // ==================== 分组状态 ====================
    
    // 默认分组顺序
    private val groupOrder = listOf("我的好友", "家人", "同学", "同事")
    
    // 分组展开状态
    private val groupExpansionState = mutableMapOf<String, Boolean>()
    
    // ContactListFormatter实例
    private val contactListFormatter = ContactListFormatter()
    
    // 所有联系人(用于分组显示)
    private val _allContacts = MutableLiveData<List<PersonProfile>>(emptyList())
    
    // 所有分组名称
    private val _allGroupNames = MutableLiveData<List<String>>(emptyList())
    val allGroupNames: LiveData<List<String>> = _allGroupNames
    
    // 联系人列表项(用于分组显示)
    private val _contactListItems = MutableLiveData<List<Any>>(emptyList())
    val contactListItems: LiveData<List<Any>> = _contactListItems

    // ==================== 用户资料状态 ====================
    
    // 当前用户资料
    private val _userProfile = MutableLiveData<UserProfile>()
    val userProfile: LiveData<UserProfile> = _userProfile

    // ==================== 通用状态 ====================
    
    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    init {
        // 加载初始数据
        loadContacts()
        loadAllGroups()
        loadUserProfile()
        
        // 监听事件
        observeEvents()
        
        // 监听联系人数据变化
        coroutineScope.launch {
            personProfileRepository.getPersonProfilesFlow().collect { contacts ->
                _allContacts.postValue(contacts)
                // 联系人数据变化时,自动刷新当前显示的列表
                if (_allGroupNames.value != null) {
                    loadGroupedContacts()
                }
            }
        }
    }

    /**
     * 监听事件总线
     */
    private fun observeEvents() {
        coroutineScope.launch {
            EventBus.events.collect { event ->
                when (event) {
                    is QqEvent.ContactsChanged -> loadContacts()
                    is QqEvent.MessageSent -> loadContacts()
                    is QqEvent.AiResponseCompleted -> loadContacts()
                    is ContactsChangedEvent -> loadContacts()
                    else -> {}
                }
            }
        }
    }

    // ==================== 联系人管理功能 ====================

    /**
     * 加载联系人列表
     */
    fun loadContacts() {
        coroutineScope.launch {
            loadContactsUseCase()
                .onSuccess { sortedContactsWithMessages ->
                    // sortedContactsWithMessages 已经按照置顶状态和最新消息时间排序
                    val profiles = sortedContactsWithMessages.map { it.profile }
                    _contacts.postValue(profiles)
                    _messageListContacts.postValue(sortedContactsWithMessages)
                    updateTotalUnreadCount(profiles)
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("加载失败: ${error.message}"))
                }
        }
    }

    /**
     * 添加新联系人
     */
    fun addContact(contact: PersonProfile) {
        coroutineScope.launch {
            // 检查联系人是否已存在
            val currentContacts = _contacts.value ?: emptyList()
            if (currentContacts.any { it.id == contact.id }) {
                Log.w(TAG, "尝试添加重复的联系人ID: ${contact.id}")
                _toastEvent.postValue(Event("联系人已存在"))
                return@launch
            }

            try {
                // 1. 保存新联系人
                personProfileRepository.savePersonProfiles(listOf(contact))

                // 2. 为新联系人添加初始消息
                chatRepository.insertMessage(
                    ChatMessage(
                        contactId = contact.id,
                        content = "你好,我是 ${contact.realName}",
                        role = "assistant"
                    )
                )
                chatRepository.insertMessage(
                    ChatMessage(
                        contactId = contact.id,
                        content = "很高兴认识你!",
                        role = "assistant"
                    )
                )

                // 3. 重新加载并排序联系人列表
                loadContacts()
                Log.d(TAG, "添加联系人成功: ${contact.realName}")
            } catch (e: Exception) {
                Log.e(TAG, "添加联系人失败", e)
                _toastEvent.postValue(Event("添加失败: ${e.message}"))
            }
        }
    }

    /**
     * 更新联系人
     */
    fun updateContact(contact: PersonProfile) {
        coroutineScope.launch {
            updateContactUseCase(contact)
                .onSuccess {
                    loadContacts()
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("更新失败: ${error.message}"))
                }
        }
    }

    /**
     * 删除联系人
     */
    fun deleteContact(contactId: String) {
        coroutineScope.launch {
            deleteContactUseCase(contactId)
                .onSuccess {
                    loadContacts()
                    _toastEvent.postValue(Event("已删除联系人"))
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("删除失败: ${error.message}"))
                }
        }
    }

    /**
     * 从聊天列表隐藏联系人（不删除好友关系）
     */
    fun hideFromChatList(contactId: String) {
        Log.d(TAG, "hideFromChatList called for contactId: $contactId")
        coroutineScope.launch {
            try {
                // 直接从数据库读取以确保获取最新数据
                val contact = personProfileRepository.getPersonProfileById(contactId)
                if (contact == null) {
                    Log.e(TAG, "Contact not found with id: $contactId")
                    _toastEvent.postValue(Event("未找到联系人"))
                    return@launch
                }
                
                Log.d(TAG, "Found contact: ${contact.remarkName}, hiding from chat list")
                
                // 更新联系人状态
                val updatedContact = contact.copy(isHiddenFromChatList = true)
                personProfileRepository.updatePersonProfile(updatedContact)
                
                Log.d(TAG, "Contact updated in database, waiting for sync...")
                
                // 等待数据库同步（给一个小延迟确保数据已提交）
                kotlinx.coroutines.delay(50)
                
                // 重新加载联系人列表以刷新UI
                loadContacts()
                _toastEvent.postValue(Event("已从聊天列表移除"))
                
                Log.d(TAG, "hideFromChatList completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error in hideFromChatList", e)
                _toastEvent.postValue(Event("操作失败: ${e.message}"))
            }
        }
    }

    /**
     * 在聊天列表显示联系人
     */
    fun showInChatList(contactId: String) {
        coroutineScope.launch {
            val contact = personProfileRepository.getPersonProfileById(contactId) ?: return@launch
            
            if (contact.isHiddenFromChatList) {
                updateContact(contact.copy(isHiddenFromChatList = false))
            }
        }
    }

    /**
     * 置顶联系人
     */
    fun pinContact(contactId: String) {
        coroutineScope.launch {
            updateContactUseCase.togglePin(contactId)
                .onSuccess {
                    loadContacts()
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("操作失败: ${error.message}"))
                }
        }
    }

    /**
     * 切换置顶状态
     */
    fun togglePin(contactId: String) {
        pinContact(contactId)
    }

    /**
     * 切换拉黑状态
     */
    fun toggleBlock(contactId: String) {
        coroutineScope.launch {
            toggleBlockStatusUseCase(contactId)
                .onSuccess {
                    loadContacts()
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("操作失败: ${error.message}"))
                }
        }
    }

    /**
     * 重置未读消息数
     */
    fun resetUnreadCount(contactId: String) {
        coroutineScope.launch {
            val currentContacts = _contacts.value ?: return@launch
            val contact = currentContacts.find { it.id == contactId } ?: return@launch

            if (contact.unreadMessageCount > 0) {
                updateContact(contact.copy(unreadMessageCount = 0))
            }
        }
    }

    private fun updateTotalUnreadCount(contacts: List<PersonProfile>) {
        val total = contacts.sumOf { it.unreadMessageCount }
        _totalUnreadCount.postValue(total)
    }

    // ==================== 分组管理功能 ====================

    /**
     * 加载所有分组
     */
    fun loadAllGroups() {
        val json = sharedPreferences.getString("group_order", null)
        val allGroups = if (json != null) {
            gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
        } else {
            // 如果没有保存的顺序,则使用默认顺序
            groupOrder.toMutableList().also {
                val customGroups = sharedPreferences.getStringSet("custom_groups", emptySet()) ?: emptySet()
                it.addAll(customGroups)
            }.distinct()
        }
        _allGroupNames.postValue(allGroups)
        
        // 分组加载完成后,如果已经有联系人数据,则刷新显示
        if (_allContacts.value?.isNotEmpty() == true) {
            loadGroupedContacts()
        }
    }

    /**
     * 添加分组
     */
    fun addGroup(groupName: String) {
        val currentOrderJson = sharedPreferences.getString("group_order", null)
        val currentOrder = if (currentOrderJson != null) {
            gson.fromJson<MutableList<String>>(currentOrderJson, object : TypeToken<MutableList<String>>() {}.type)
        } else {
            groupOrder.toMutableList().also {
                val existingCustomGroups = sharedPreferences.getStringSet("custom_groups", emptySet()) ?: emptySet()
                it.addAll(existingCustomGroups)
            }.distinct().toMutableList()
        }

        if (currentOrder.contains(groupName)) {
            return // 分组已存在
        }

        // 添加到顺序列表
        currentOrder.add(groupName)
        sharedPreferences.edit().putString("group_order", gson.toJson(currentOrder)).apply()

        // 同时添加到custom_groups
        val customGroups = sharedPreferences.getStringSet("custom_groups", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        customGroups.add(groupName)
        sharedPreferences.edit().putStringSet("custom_groups", customGroups).apply()

        // 重新加载分组
        loadAllGroups()
    }

    /**
     * 删除分组
     */
    fun deleteGroup(groupName: String) {
        coroutineScope.launch {
            // 从group_order和custom_groups中移除
            val currentOrderJson = sharedPreferences.getString("group_order", null)
            val currentOrder = if (currentOrderJson != null) {
                gson.fromJson<MutableList<String>>(currentOrderJson, object : TypeToken<MutableList<String>>() {}.type)
            } else {
                _allGroupNames.value?.toMutableList() ?: mutableListOf()
            }

            val customGroups = sharedPreferences.getStringSet("custom_groups", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

            val wasRemovedFromOrder = currentOrder.remove(groupName)
            val wasRemovedFromCustom = customGroups.remove(groupName)

            if (wasRemovedFromOrder || wasRemovedFromCustom) {
                // 保存更新后的顺序和自定义分组
                sharedPreferences.edit()
                    .putString("group_order", gson.toJson(currentOrder))
                    .putStringSet("custom_groups", customGroups)
                    .apply()

                // 将该分组下的联系人移动到"我的好友"
                val contactsToUpdate = _allContacts.value?.filter { it.group == groupName }
                if (!contactsToUpdate.isNullOrEmpty()) {
                    val updatedContacts = contactsToUpdate.map { it.copy(group = null) }
                    personProfileRepository.savePersonProfiles(updatedContacts)
                }
                loadAllGroups()
                loadGroupedContacts() // 刷新联系人列表
            }
        }
    }

    /**
     * 更新分组顺序
     */
    fun updateGroupOrder(groups: List<String>) {
        sharedPreferences.edit().putString("group_order", gson.toJson(groups)).apply()
        _allGroupNames.postValue(groups)
    }

    /**
     * 切换分组展开/折叠状态
     */
    fun toggleGroupExpansion(groupName: String, currentTabPosition: Int) {
        val isExpanded = groupExpansionState.getOrPut(groupName) { true }
        groupExpansionState[groupName] = !isExpanded

        when (currentTabPosition) {
            0 -> loadGroupedContacts()
            1 -> loadAllFriends()
            2 -> loadGroupedChats()
        }
    }
    
    /**
     * 加载分组后的联系人
     */
    fun loadGroupedContacts() {
        val contacts = _allContacts.value ?: return
        val allGroups = _allGroupNames.value ?: groupOrder
        _contactListItems.postValue(contactListFormatter.formatGroupedContacts(contacts, allGroups, groupExpansionState))
    }
    
    /**
     * 加载所有好友(按首字母排序)
     */
    fun loadAllFriends() {
        val contacts = _allContacts.value ?: return
        _contactListItems.postValue(contactListFormatter.formatAllFriends(contacts))
    }
    /**
     * 加载群聊列表
     */
    fun loadGroupedChats() {
        val contacts = _allContacts.value ?: return
        _contactListItems.postValue(contactListFormatter.formatGroupedChats(contacts, groupExpansionState))
    }

    // ==================== 用户资料管理功能 ====================

    /**
     * 加载用户资料
     */
    private fun loadUserProfile() {
        coroutineScope.launch {
            _userProfile.postValue(personProfileRepository.getUserProfile())
        }
    }
    
    /**
     * 刷新用户资料
     */
    fun refreshUserProfile() {
        loadUserProfile()
    }
    
    /**
     * 更新用户资料
     */
    fun updateUserProfile(profile: UserProfile) {
        coroutineScope.launch {
            personProfileRepository.saveUserProfile(profile)
            _userProfile.postValue(profile)
        }
    }
    
    /**
     * 获取当前用户资料（同步方法，用于回调）
     */
    fun getCurrentUserProfile(): UserProfile? {
        return _userProfile.value
    }
}
        