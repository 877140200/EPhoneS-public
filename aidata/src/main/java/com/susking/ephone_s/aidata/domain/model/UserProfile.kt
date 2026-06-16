package com.susking.ephone_s.aidata.domain.model

data class UserProfile(
    val id: String,
    val nickname: String,
    val signature: String,
    val avatarUri: String?,
    val backgroundUri: String?,
    val persona: String,
    val status: String = "在线",
    val statusIconUri: String? = null,
    val feedsHeaderBackgroundUri: String? = null
)