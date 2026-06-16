package com.susking.ephone_s.core.ui.dialog

/**
 * 用于在 ConfirmAiPromptDialogFragment 中表示一个可折叠的提示词部分
 *
 * @param title 该部分的标题, 例如 "System (1)"
 * @param content 该部分的格式化后 JSON 内容
 * @param isExpanded 该部分当前是否展开
 */
data class PromptSection(
    val title: String,
    val content: String,
    var isExpanded: Boolean = false
)