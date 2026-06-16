package com.susking.ephone_s.features.theme.domain.model

/**
 * 代表一套桌面图标包。
 *
 * 图标包只保存应用名称到稳定 URI 的映射，桌面模块通过应用名称读取对应图标。
 * fallbackIconUri 用于处理新增应用或主题包缺少某个图标时的兜底显示。
 */
data class IconPack(
    val id: String,
    val name: String,
    val icons: Map<String, String>,
    val fallbackIconUri: String
)