package com.susking.ephone_s.qq.ui.forward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 转发选择器ViewModel
 * 
 * 负责加载联系人列表、搜索过滤等业务逻辑
 */
@HiltViewModel
class ForwardSelectorViewModel @Inject constructor() : ViewModel() {

    private val personProfileRepository = AiDataApi.getPersonProfileRepository()

    /**
     * 所有联系人列表
     */
    private val _allContacts = MutableStateFlow<List<PersonProfile>>(emptyList())

    /**
     * 显示的联系人列表（经过搜索过滤）
     */
    private val _displayedContacts = MutableStateFlow<List<PersonProfile>>(emptyList())
    val displayedContacts: StateFlow<List<PersonProfile>> = _displayedContacts.asStateFlow()

    /**
     * 是否多选模式
     */
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    /**
     * 当前搜索关键词
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadContacts()
    }

    /**
     * 加载所有联系人
     */
    private fun loadContacts() {
        viewModelScope.launch {
            try {
                personProfileRepository.getPersonProfilesFlow().collect { contacts ->
                    _allContacts.value = contacts
                    // 初次加载时应用当前搜索条件
                    filterContacts(_searchQuery.value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 切换多选模式
     */
    fun toggleMultiSelectMode() {
        _isMultiSelectMode.value = !_isMultiSelectMode.value
    }

    /**
     * 设置多选模式
     */
    fun setMultiSelectMode(enabled: Boolean) {
        _isMultiSelectMode.value = enabled
    }

    /**
     * 搜索联系人
     * 
     * @param query 搜索关键词
     */
    fun searchContacts(query: String) {
        _searchQuery.value = query
        filterContacts(query)
    }

    /**
     * 过滤联系人列表
     */
    private fun filterContacts(query: String) {
        val filtered = if (query.isEmpty()) {
            _allContacts.value
        } else {
            _allContacts.value.filter { contact ->
                // 搜索备注名和真实姓名
                contact.remarkName.contains(query, ignoreCase = true) ||
                contact.realName.contains(query, ignoreCase = true)
            }
        }
        _displayedContacts.value = filtered
    }

    /**
     * 清空搜索
     */
    fun clearSearch() {
        searchContacts("")
    }
}