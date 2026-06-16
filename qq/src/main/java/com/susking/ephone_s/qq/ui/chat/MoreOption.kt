package com.susking.ephone_s.qq.ui.chat

import androidx.annotation.DrawableRes

data class MoreOption(
    val text: String,
    @DrawableRes val iconResId: Int,
    var action: (() -> Unit)? = null
)