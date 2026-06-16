package com.susking.ephone_s.features.theme.domain.model

/**
 * 代表 Brain 全局悬浮窗的外观样式。
 *
 * 这里同时包含图片和颜色，方便 Brain 模块通过 provider 一次性读取完整外观。
 */
data class FloatingWindowStyle(
    val id: String,
    val name: String,
    val defaultImageUri: String,
    val draggingImageUri: String,
    val dockedImageUri: String,
    val backgroundColor: Int,
    val textColor: Int,
    val accentColor: Int,
    val cardBackgroundColor: Int
)