package com.susking.ephone_s.qq.domain.manager

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 活跃联系人追踪器实现
 * 用于追踪用户当前正在查看哪个联系人的聊天界面
 *
 * 当用户在某个联系人的聊天界面时，该联系人的新消息不应增加未读计数
 */
@Singleton
class ActiveContactTrackerImpl @Inject constructor() : com.susking.ephone_s.aidata.domain.tracker.ActiveContactTracker {
    
    /**
     * 当前活跃的联系人ID
     * 当用户打开某个联系人的聊天界面时设置，离开时清空
     */
    @Volatile
    private var activeContactId: String? = null
    
    /**
     * 设置当前活跃的联系人
     * @param contactId 联系人ID，null表示没有活跃联系人
     */
    override fun setActiveContact(contactId: String?) {
        activeContactId = contactId
    }
    
    /**
     * 检查指定联系人是否是当前活跃联系人
     * @param contactId 要检查的联系人ID
     * @return true 如果该联系人是当前活跃联系人
     */
    override fun isActiveContact(contactId: String): Boolean {
        return activeContactId == contactId
    }
    
    /**
     * 获取当前活跃的联系人ID
     * @return 当前活跃的联系人ID，如果没有则返回null
     */
    override fun getActiveContactId(): String? {
        return activeContactId
    }
}