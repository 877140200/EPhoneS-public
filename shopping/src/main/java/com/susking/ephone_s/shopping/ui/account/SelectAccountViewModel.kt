package com.susking.ephone_s.shopping.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.model.AuthorizedAccount
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingAuthorizedAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 选择账号对话框ViewModel
 */
@HiltViewModel
class SelectAccountViewModel @Inject constructor(
    private val authorizedAccountRepository: ShoppingAuthorizedAccountRepository,
    private val personProfileRepository: PersonProfileRepository
) : ViewModel() {
    
    // 已授权账号列表
    private val _authorizedAccounts = MutableStateFlow<List<AuthorizedAccount>>(emptyList())
    val authorizedAccounts: StateFlow<List<AuthorizedAccount>> = _authorizedAccounts.asStateFlow()
    
    // 联系人Map (contactId -> PersonProfile)
    private val _contactsMap = MutableStateFlow<Map<String, PersonProfile>>(emptyMap())
    val contactsMap: StateFlow<Map<String, PersonProfile>> = _contactsMap.asStateFlow()
    
    init {
        loadAuthorizedAccounts()
        loadContacts()
    }
    
    /**
     * 加载已授权账号列表
     */
    private fun loadAuthorizedAccounts() {
        viewModelScope.launch {
            authorizedAccountRepository.getAllAuthorizedAccounts().collect { accounts ->
                _authorizedAccounts.value = accounts
            }
        }
    }
    
    /**
     * 加载所有联系人
     */
    private fun loadContacts() {
        viewModelScope.launch {
            personProfileRepository.getPersonProfilesFlow().collect { profiles ->
                _contactsMap.value = profiles.associateBy { it.id }
            }
        }
    }
    
    /**
     * 删除账号
     */
    fun removeAccount(contactId: String) {
        viewModelScope.launch {
            authorizedAccountRepository.removeAuthorizedAccount(contactId)
        }
    }
    
    /**
     * 删除账号(可选择是否保留商品)
     * @param contactId 联系人ID
     * @param keepProducts 是否保留该账号内的商品
     */
    fun removeAccount(contactId: String, keepProducts: Boolean) {
        viewModelScope.launch {
            authorizedAccountRepository.removeAuthorizedAccount(contactId, keepProducts)
        }
    }
}