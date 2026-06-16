package com.susking.ephone_s.qq.ui.forward

/**
 * 转发/选择联系人回调接口
 * 
 * 用于处理用户选择联系人后的操作
 */
interface ForwardCallback {
    /**
     * 当用户选择完联系人并点击发送时调用
     * 
     * @param contactIds 选中的联系人ID列表
     * @param contentType 内容类型（如：shopping_request, message_forward, share_content等）
     * @param contentId 内容ID（可选）
     */
    fun onContactsSelected(
        contactIds: List<String>,
        contentType: String?,
        contentId: String?
    )
    
    /**
     * 当用户取消选择时调用（可选实现）
     */
    fun onCancelled() {}
}