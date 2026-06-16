package com.susking.ephone_s.qq.ui.contactList

import com.github.promeg.pinyinhelper.Pinyin
import com.susking.ephone_s.aidata.domain.model.PersonProfile

class ContactListFormatter {

    fun formatGroupedContacts(
        contacts: List<PersonProfile>,
        allGroups: List<String>, // 使用包含所有分组的列表
        groupExpansionState: Map<String, Boolean>
    ): List<ContactListItem> {
        val grouped = contacts.groupBy { it.group }
        val result = mutableListOf<ContactListItem>()

        // 遍历所有分组，确保包括自定义分组
        allGroups.forEach { groupName ->
            var contactsInGroup = grouped[groupName] ?: emptyList()
            // "我的好友"分组特殊处理，包含未分组的联系人
            if (groupName == "我的好友") {
                contactsInGroup = contactsInGroup + (grouped[null] ?: emptyList()) + (grouped["未分组"] ?: emptyList())
            }

            // 按拼音首字母排序
            val distinctContacts = contactsInGroup.distinctBy { it.id }.sortedBy { getPinyin(it.remarkName) }

            // 即使分组为空，也显示分组标题
            val isExpanded = groupExpansionState[groupName] ?: true
            result.add(ContactListItem.HeaderItem(groupName, distinctContacts.size, isExpanded))
            if (isExpanded) {
                distinctContacts.forEach { contact -> result.add(ContactListItem.ContactItem(contact)) }
            }
        }
        return result
    }

    fun formatAllFriends(contacts: List<PersonProfile>): List<ContactListItem> {
        val result = mutableListOf<ContactListItem>()
        val sortedFriends = contacts.sortedBy { getPinyin(it.remarkName) }

        var lastInitial = ""
        sortedFriends.forEach { contact ->
            val initial = getPinyin(contact.remarkName).first().uppercaseChar().toString()
            if (initial != lastInitial) {
                result.add(ContactListItem.HeaderItem(initial, 0, true)) // count is not used here
                lastInitial = initial
            }
            result.add(ContactListItem.ContactItem(contact))
        }
        return result
    }

    fun formatGroupedChats(
        contacts: List<PersonProfile>,
        groupExpansionState: Map<String, Boolean>
    ): List<ContactListItem> {
        val groupChats = contacts.filter { it.isGroupChat }
        val result = mutableListOf<ContactListItem>()

        val createdChats = groupChats.filter { it.groupChatRole == "creator" }
        val managedChats = groupChats.filter { it.groupChatRole == "admin" }
        val joinedChats = groupChats.filter { it.groupChatRole == "member" }

        // 我创建的群聊
        val createdExpanded = groupExpansionState["我创建的群聊"] ?: true
        result.add(ContactListItem.HeaderItem("我创建的群聊", createdChats.size, createdExpanded))
        if (createdExpanded) {
            createdChats.forEach { result.add(ContactListItem.ContactItem(it)) }
        }

        // 我管理的群聊
        val managedExpanded = groupExpansionState["我管理的群聊"] ?: true
        result.add(ContactListItem.HeaderItem("我管理的群聊", managedChats.size, managedExpanded))
        if (managedExpanded) {
            managedChats.forEach { result.add(ContactListItem.ContactItem(it)) }
        }

        // 我加入的群聊
        val joinedExpanded = groupExpansionState["我加入的群聊"] ?: true
        result.add(ContactListItem.HeaderItem("我加入的群聊", joinedChats.size, joinedExpanded))
        if (joinedExpanded) {
            joinedChats.forEach { result.add(ContactListItem.ContactItem(it)) }
        }
        return result
    }

    private fun getPinyin(chinese: String): String {
        if (chinese.isEmpty()) {
            return ""
        }
        val stringBuilder = StringBuilder()
        for (char in chinese) {
            if (Pinyin.isChinese(char)) {
                stringBuilder.append(Pinyin.toPinyin(char))
            } else {
                stringBuilder.append(char)
            }
        }
        return stringBuilder.toString()
    }
}