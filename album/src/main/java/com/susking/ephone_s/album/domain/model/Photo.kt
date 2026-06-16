package com.susking.ephone_s.album.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 照片领域模型 (纯 Kotlin 数据类,无框架依赖)
 * @param id 照片的唯一标识符
 * @param uri 照片的存储路径
 * @param albumId 所属相册的 ID
 * @param dateAdded 添加日期的时间戳
 * @param isFavorited 是否收藏
 */
@Parcelize
data class Photo(
    val id: Long = 0,
    val uri: String,
    val albumId: Long,
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorited: Boolean = false
) : Parcelable