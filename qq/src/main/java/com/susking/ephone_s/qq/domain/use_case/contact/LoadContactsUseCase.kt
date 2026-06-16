package com.susking.ephone_s.qq.domain.use_case.contact

import android.util.Log
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.qq.domain.model.ContactWithLatestMessage
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 加载联系人列表的UseCase
 * 包含排序和最新消息更新逻辑
 * 返回包含最新消息信息的联系人列表
 */
class LoadContactsUseCase @Inject constructor(
    private val personProfileRepository: PersonProfileRepository,
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(): Result<List<ContactWithLatestMessage>> {
        return try {
            var contacts = personProfileRepository.getPersonProfiles()
            Log.d("LoadContactsUseCase", "Initial contacts count: ${contacts.size}")
            
            // 如果没有联系人,创建默认联系人
            if (contacts.isEmpty()) {
                Log.d("LoadContactsUseCase", "No contacts found, creating default contact")
                try {
                    val defaultContact1 = PersonProfile(
                        id = "default_ai_contact_1",
                        remarkName = "小爱", realName = "小爱",
                        persona = "你是一个智能AI助手，乐于助人。", isPinned = true
                    )
                    val defaultContact2 = PersonProfile(
                        id = "default_ai_contact_2",
                        remarkName = "舍友", realName = "张初",
                        persona = "你是一个活泼开朗的大学生，喜欢和朋友聊天。用户是你的同性别舍友，你和用户是好朋友，你暗恋用户。", isPinned = false
                    )
                    val defaultContact3 = PersonProfile(
                        id = "default_ai_contact_3",
                        remarkName = "舍敌", realName = "陆行然",
                        persona = "你是一个孤僻的大学生，平时沉浸在自己的世界里，不怎么和人聊天。用户是你的同性别舍友，你和用户之前产生了一些摩擦，导致你们的关系现在不是很好。但是你暗恋用户已经很久了。", isPinned = false
                    )
                    personProfileRepository.savePersonProfiles(listOf(defaultContact1, defaultContact2, defaultContact3))

                    Log.d("LoadContactsUseCase", "Default contact saved")
                    
                    // 添加初始消息
                    chatRepository.insertMessage(ChatMessage(contactId = "default_ai_contact_1", content = "你好,我是 小爱", role = "assistant"))
                    chatRepository.insertMessage(ChatMessage(contactId = "default_ai_contact_1", content = "很高兴认识你!", role = "assistant"))

                    // 添加舍友初始消息
                    chatRepository.insertMessage(ChatMessage(contactId = "default_ai_contact_2", content = "新的一天！", role = "assistant"))
                    chatRepository.insertMessage(ChatMessage(contactId = "default_ai_contact_2", content = "有什么有趣的事情可以分享给我吗？", role = "assistant"))

                    // 添加舍敌初始消息
                    chatRepository.insertMessage(ChatMessage(contactId = "default_ai_contact_3", content = "……", role = "assistant"))
                    chatRepository.insertMessage(ChatMessage(contactId = "default_ai_contact_3", content = "有事吗？", role = "assistant"))

                    // 重新加载联系人
                    contacts = personProfileRepository.getPersonProfiles()
                    Log.d("LoadContactsUseCase", "Reloaded contacts, count: ${contacts.size}")
                    
                    if (contacts.isEmpty()) {
                        Log.e("LoadContactsUseCase", "Failed to create default contact!")
                        return Result.failure(Exception("Failed to create default contact"))
                    }
                } catch (e: Exception) {
                    Log.e("LoadContactsUseCase", "Error creating default contact", e)
                    return Result.failure(e)
                }
            }
            
            // 过滤掉被隐藏的联系人（仅在聊天列表中不显示）
            val hiddenCount = contacts.count { it.isHiddenFromChatList }
            Log.d("LoadContactsUseCase", "Before filter: total=${contacts.size}, hidden=$hiddenCount")
            contacts.forEach { contact ->
                if (contact.isHiddenFromChatList) {
                    Log.d("LoadContactsUseCase", "Hidden contact found: ${contact.remarkName} (${contact.id})")
                }
            }
            val visibleContacts = contacts.filterNot { it.isHiddenFromChatList }
            Log.d("LoadContactsUseCase", "Filtered out ${contacts.size - visibleContacts.size} hidden contacts")
            
            val updatedContacts = updateContactsWithLatestMessages(visibleContacts)
            val sortedContacts = sortContacts(updatedContacts)
            Log.d("LoadContactsUseCase", "Returning ${sortedContacts.size} sorted contacts")
            Result.success(sortedContacts)
        } catch (e: Exception) {
            Log.e("LoadContactsUseCase", "Error in invoke", e)
            Result.failure(e)
        }
    }

    private suspend fun updateContactsWithLatestMessages(contacts: List<PersonProfile>): List<ContactWithLatestMessage> {
        return contacts.map { contact ->
            val lastMessage = chatRepository.getMessagesForContact(contact.id)
                .first()
                .filterNot { it.isHidden }
                .lastOrNull()

            ContactWithLatestMessage(
                profile = contact,
                latestMessage = lastMessage?.content,
                latestMessageType = lastMessage?.type ?: "text",
                latestMessageTime = lastMessage?.let { formatTimestamp(it.timestamp) },
                latestMessageTimestamp = lastMessage?.timestamp ?: 0L
            )
        }
    }

    private fun sortContacts(contacts: List<ContactWithLatestMessage>): List<ContactWithLatestMessage> {
        return contacts.sortedWith(
            compareByDescending<ContactWithLatestMessage> { it.profile.isPinned }
                .thenByDescending { it.latestMessageTimestamp }
        )
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val sdfToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sdfOtherDay = SimpleDateFormat("M月d日", Locale.getDefault())
        val cal1 = java.util.Calendar.getInstance()
        val cal2 = java.util.Calendar.getInstance()
        cal1.time = Date()
        cal2.time = date
        return if (cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)) {
            sdfToday.format(date)
        } else {
            sdfOtherDay.format(date)
        }
    }
}
