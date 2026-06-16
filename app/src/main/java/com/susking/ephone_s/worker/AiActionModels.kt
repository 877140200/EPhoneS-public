package com.susking.ephone_s.worker

import com.google.gson.annotations.SerializedName

/**
 * AI行动的基类和具体行动的数据类模型。
 * 命名和结构严格遵循源JS文件的JSON格式。
 */

// AI返回的JSON是一个包含多个Action的数组
data class AiAction(
    @SerializedName("type") val type: String,
    // --- 通用 ---
    @SerializedName("content") val content: String? = null,
    // --- qzone_post ---
    @SerializedName("postType") val postType: String? = null,
    // --- qzone_comment ---
    @SerializedName("postId") val postId: Int? = null,
    @SerializedName("commentText") val commentText: String? = null,
    // --- text (私信) ---
    // 'content' 字段已在通用部分定义
)