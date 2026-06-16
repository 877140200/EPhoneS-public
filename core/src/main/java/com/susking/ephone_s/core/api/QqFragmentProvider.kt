package com.susking.ephone_s.core.api

import androidx.fragment.app.Fragment

/**
 * QQ Fragment 提供者接口
 * 
 * 定义了 QQ 模块对外提供的所有 Fragment 创建方法
 * 由 app 模块提供具体实现,实现依赖倒置
 * 
 * 设计思路:
 * - core 模块定义接口(api 层)
 * - app 模块实现接口(提供具体的 Fragment 实例)
 * - shopping 等其他模块通过 QqApi 获取 Fragment
 */
interface QqFragmentProvider {
    
    /**
     * 获取 QQ 主界面 Fragment
     * 包含消息列表、联系人、动态、我的等 Tab
     * 
     * @return QQ 主界面 Fragment 实例
     */
    fun getQqMainFragment(): Fragment
    
    /**
     * 获取 QQ 聊天界面 Fragment
     * 
     * @param contactId 联系人 ID
     * @return QQ 聊天界面 Fragment 实例
     */
    fun getQqChatFragment(contactId: String): Fragment
    
    /**
     * 获取联系人资料界面 Fragment
     * 
     * @param contactId 联系人 ID
     * @return 联系人资料 Fragment 实例
     */
    fun getContactProfileFragment(contactId: String): Fragment
    
    /**
     * 获取 QQ 空间动态界面 Fragment
     *
     * @return QQ 空间 Fragment 实例
     */
    fun getQqSpaceFragment(): Fragment
    
    /**
     * 获取转发选择器 Fragment
     * 用于选择联系人进行转发、分享、发送申请等操作
     *
     * @param contentType 内容类型（如：shopping_request, message_forward等）
     * @param contentId 内容ID（可选）
     * @param onContactsSelected 选择完成回调：(contactIds: List<String>, contentType: String?, contentId: String?) -> Unit
     * @param onCancelled 取消回调（可选）
     * @return 转发选择器 Fragment 实例
     */
    fun getForwardSelectorFragment(
        contentType: String?,
        contentId: String?,
        onContactsSelected: (List<String>, String?, String?) -> Unit,
        onCancelled: (() -> Unit)? = null
    ): Fragment
}