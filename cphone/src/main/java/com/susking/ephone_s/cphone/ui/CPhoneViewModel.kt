package com.susking.ephone_s.cphone.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * CPhone模块的ViewModel
 * 管理AI联系人数据
 */
@HiltViewModel
class CPhoneViewModel @Inject constructor(
    private val personProfileRepository: PersonProfileRepository
) : ViewModel() {

    /**
     * 获取所有AI联系人（非群组）
     */
    val aiContacts: LiveData<List<PersonProfile>> = personProfileRepository
        .getPersonProfilesFlow()
        .asLiveData()
}