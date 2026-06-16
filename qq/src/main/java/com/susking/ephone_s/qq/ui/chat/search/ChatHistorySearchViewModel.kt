package com.susking.ephone_s.qq.ui.chat.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ChatHistorySearchUiState(
    val isLoading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val datesWithMessages: Set<LocalDate> = emptySet(),
    val selectedDate: LocalDate? = null,
    val contact: PersonProfile? = null,
    val userProfile: UserProfile? = null
)

@HiltViewModel
class ChatHistorySearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val personProfileRepository: PersonProfileRepository
) : ViewModel() {

    val contactId: String = savedStateHandle.get<String>(ARG_CONTACT_ID)!!

    private val _uiState = MutableStateFlow(ChatHistorySearchUiState())
    val uiState: StateFlow<ChatHistorySearchUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val userProfile = MutableStateFlow<UserProfile?>(null)

    init {
        val allMessagesFlow = chatRepository.getMessagesForContact(contactId)
        val datesWithMessagesFlow = chatRepository.getDatesWithMessages(contactId)
        val contactFlow = personProfileRepository.getPersonProfileByIdFlow(contactId)
        val profileFlow = combine(contactFlow, userProfile) { contact, currentUserProfile ->
            contact to currentUserProfile
        }

        viewModelScope.launch {
            userProfile.value = personProfileRepository.getUserProfile()
        }

        combine(
            allMessagesFlow,
            datesWithMessagesFlow,
            searchQuery,
            selectedDate,
            profileFlow
        ) { allMessages, datesMillis, query, date, profiles ->
            val contact = profiles.first
            val currentUserProfile = profiles.second
            val datesWithMessages = datesMillis.map {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSet()

            val filteredMessages = allMessages.filter { message ->
                val contentMatches = query.isBlank() || getSearchableMessageText(message).contains(query, ignoreCase = true)

                val dateMatches = date == null ||
                        Instant.ofEpochMilli(message.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == date

                contentMatches && dateMatches
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                messages = filteredMessages,
                datesWithMessages = datesWithMessages,
                selectedDate = date,
                contact = contact,
                userProfile = currentUserProfile
            )
        }.launchIn(viewModelScope)
    }

    private fun getSearchableMessageText(message: ChatMessage): String {
        return listOfNotNull(
            message.content,
            message.imageDescription,
            message.stickerName,
            message.productInfo,
            message.notes,
            message.giftName,
            message.giftNote,
            message.offlineLocation,
            message.offlineReason,
            message.senderName,
            message.recipientName
        ).joinToString(separator = " ")
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onDateSelected(date: LocalDate?) {
        // 如果再次选择同一个日期，则取消选择
        if (selectedDate.value == date) {
            selectedDate.value = null
        } else {
            selectedDate.value = date
        }
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"
    }
}
