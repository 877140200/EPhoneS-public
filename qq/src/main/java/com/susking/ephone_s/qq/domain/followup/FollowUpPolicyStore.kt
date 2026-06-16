package com.susking.ephone_s.qq.domain.followup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 联系人级追问策略存储。
 *
 * 这里使用应用偏好保存追问策略，避免为软策略新增数据库表导致迁移、导入导出范围继续扩大。
 * 追问策略只控制自动追问调度，不影响聊天历史本身。
 */
@Singleton
class FollowUpPolicyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun savePolicy(contactId: String, anchorMessageId: String?, policy: FollowUpPolicy): Unit {
        val safeAnchorMessageId: String = anchorMessageId.orEmpty()
        val currentCount: Int = getFollowUpCountAfterLastUserMessage(contactId)
        preferences.edit()
            .putBoolean(getShouldFollowUpKey(contactId), policy.shouldFollowUpIfUserSilentTooLong)
            .putString(getFollowUpHintKey(contactId), policy.followUpHint.orEmpty())
            .putString(getAnchorMessageIdKey(contactId), safeAnchorMessageId)
            .putLong(getPolicySavedAtKey(contactId), System.currentTimeMillis())
            .putInt(getFollowUpCountKey(contactId), currentCount)
            .apply()
    }

    fun resetAfterUserMessage(contactId: String): Unit {
        preferences.edit()
            .putInt(getFollowUpCountKey(contactId), 0)
            .remove(getAnchorMessageIdKey(contactId))
            .remove(getShouldFollowUpKey(contactId))
            .remove(getFollowUpHintKey(contactId))
            .remove(getPolicySavedAtKey(contactId))
            .apply()
    }

    fun cancelPolicy(contactId: String): Unit {
        preferences.edit()
            .remove(getAnchorMessageIdKey(contactId))
            .remove(getShouldFollowUpKey(contactId))
            .remove(getFollowUpHintKey(contactId))
            .remove(getPolicySavedAtKey(contactId))
            .apply()
    }

    fun getPolicySnapshot(contactId: String): FollowUpPolicySnapshot {
        val shouldFollowUp: Boolean = preferences.getBoolean(getShouldFollowUpKey(contactId), false)
        val hint: String = getFollowUpHint(contactId)
        val count: Int = getFollowUpCountAfterLastUserMessage(contactId)
        val anchorMessageId: String = getLastFollowUpAnchorMessageId(contactId)
        val savedAtMillis: Long = preferences.getLong(getPolicySavedAtKey(contactId), 0L)
        return FollowUpPolicySnapshot(
            shouldFollowUp = shouldFollowUp,
            followUpHint = hint,
            followUpCount = count,
            anchorMessageId = anchorMessageId,
            canFollowUp = shouldFollowUp && count < MAX_FOLLOW_UP_COUNT,
            savedAtMillis = savedAtMillis
        )
    }

    fun canFollowUp(contactId: String): Boolean {
        val shouldFollowUp: Boolean = preferences.getBoolean(getShouldFollowUpKey(contactId), false)
        val count: Int = getFollowUpCountAfterLastUserMessage(contactId)
        return shouldFollowUp && count < MAX_FOLLOW_UP_COUNT
    }

    fun markFollowUpSent(contactId: String): Unit {
        val nextCount: Int = (getFollowUpCountAfterLastUserMessage(contactId) + 1).coerceAtMost(MAX_FOLLOW_UP_COUNT)
        preferences.edit()
            .putInt(getFollowUpCountKey(contactId), nextCount)
            .apply()
    }

    fun getFollowUpHint(contactId: String): String {
        return preferences.getString(getFollowUpHintKey(contactId), "") ?: ""
    }

    fun getFollowUpCountAfterLastUserMessage(contactId: String): Int {
        return preferences.getInt(getFollowUpCountKey(contactId), 0)
    }

    fun getLastFollowUpAnchorMessageId(contactId: String): String {
        return preferences.getString(getAnchorMessageIdKey(contactId), "") ?: ""
    }

    private fun getShouldFollowUpKey(contactId: String): String = "${contactId}_should_follow_up"

    private fun getFollowUpHintKey(contactId: String): String = "${contactId}_follow_up_hint"

    private fun getFollowUpCountKey(contactId: String): String = "${contactId}_follow_up_count"

    private fun getAnchorMessageIdKey(contactId: String): String = "${contactId}_anchor_message_id"

    private fun getPolicySavedAtKey(contactId: String): String = "${contactId}_policy_saved_at"

    private companion object {
        private const val PREFERENCES_NAME: String = "qq_follow_up_policy"
        private const val MAX_FOLLOW_UP_COUNT: Int = 2
    }
}

data class FollowUpPolicy(
    val shouldFollowUpIfUserSilentTooLong: Boolean,
    val followUpHint: String?
)

data class FollowUpPolicySnapshot(
    val shouldFollowUp: Boolean,
    val followUpHint: String,
    val followUpCount: Int,
    val anchorMessageId: String,
    val canFollowUp: Boolean,
    val savedAtMillis: Long
)
