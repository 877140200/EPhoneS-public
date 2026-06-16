package com.susking.ephone_s.qq.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 用于在线下模式中，将一个版本的文本和图片URL绑定在一起存入版本历史。
 * @param textContent 该版本的文本内容。
 * @param imageUrl 该版本生成的图片URL（Base64）。
 */
@Parcelize
data class OfflineVersionData(
    val textContent: String?,
    val imageUrl: String?
) : Parcelable