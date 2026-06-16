package com.susking.ephone_s.aidata.domain.tracker

/**
 * 活跃联系人追踪器接口
 * 用于追踪用户当前正在查看哪个联系人的聊天界面
 * 
 * 当用户在某个联系人的聊天界面时，该联系人的新消息不应增加未读计数
 */
interface ActiveContactTracker {
    
    /**
     * 设置当前活跃的联系人
     * @param contactId 联系人ID，null表示没有活跃联系人
     */
    fun setActiveContact(contactId: String?)
    
    /**
     * 检查指定联系人是否是当前活跃联系人
     * @param contactId 要检查的联系人ID
     * @return true 如果该联系人是当前活跃联系人
     */
    fun isActiveContact(contactId: String): Boolean
    
    /**
     * 获取当前活跃的联系人ID
     * @return 当前活跃的联系人ID，如果没有则返回null
     */
    fun getActiveContactId(): String?
}