package com.susking.ephone_s.album.domain.model

/**
 * 相册领域模型 (纯 Kotlin 数据类,无框架依赖)
 * @param id 相册的唯一标识符
 * @param name 相册的名称
 * @param createdAt 创建时间戳
 * @param coverImagePath 封面图片的路径
 * @param photoCount 照片数量
 */
data class Album(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val coverImagePath: String? = null,
    val photoCount: Int = 0
)