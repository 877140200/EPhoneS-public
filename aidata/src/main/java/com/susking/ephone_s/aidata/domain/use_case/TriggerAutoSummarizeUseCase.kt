package com.susking.ephone_s.aidata.domain.use_case

import android.util.Log
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.core.util.EventBus
import javax.inject.Inject

/**
 * 自动总结触发检查器UseCase
 *
 * 在插入新消息时检查是否需要触发自动总结
 * 如果满足条件，发送事件通知Brain模块执行总结
 */
class TriggerAutoSummarizeUseCase @Inject constructor(
    private val personProfileRepository: PersonProfileRepository
) {
    
    companion object {
        private const val TAG = "TriggerAutoSummarize"
        private const val MIN_SUMMARY_INTERVAL: Int = 1
        // 指数退避：每多失败一次，要求积累的新增消息数翻倍，最多翻到 2^MAX_BACKOFF_EXPONENT 倍。
        // 失败时不再每条新消息都全量重试，避免持续失败时刷爆悬浮窗、空烧 token。
        private const val MAX_BACKOFF_EXPONENT: Int = 3
    }

    /**
     * 检查是否需要触发自动总结
     *
     * @param contactId 联系人ID
     * @param totalMessageCount 总消息数
     * @param newMessageCountSinceLastSummary 上次成功提取结构化事件之后新增的消息数
     * @param newMessageTimestamp 新消息的时间戳
     * @return 是否需要触发总结
     */
    suspend fun shouldTrigger(
        contactId: String,
        totalMessageCount: Int,
        newMessageCountSinceLastSummary: Int,
        newMessageTimestamp: Long
    ): Boolean {
        try {
            // 1. 获取联系人配置
            val contact = personProfileRepository.getPersonProfileById(contactId) ?: run {
                Log.d(TAG, "联系人不存在: $contactId")
                return false
            }

            Log.d(
                TAG,
                "自动总结触发检查: contactId=$contactId, 联系人=${contact.remarkName}, enabled=${contact.autoSummaryEnabled}, totalMessageCount=$totalMessageCount, newMessageCountSinceLastSummary=$newMessageCountSinceLastSummary, summaryInterval=${contact.summaryInterval}, failureCount=${contact.autoSummaryFailureCount}, lastSummaryTimestamp=${contact.lastSummaryTimestamp}, newMessageTimestamp=$newMessageTimestamp"
            )

            // 2. 检查是否启用自动总结
            if (!contact.autoSummaryEnabled) {
                Log.d(TAG, "自动总结跳过: 联系人=${contact.remarkName}, 原因=联系人未开启自动提取")
                return false
            }

            // 3. 检查总消息数是否足够
            if (totalMessageCount < 5) {
                Log.d(TAG, "自动总结跳过: 联系人=${contact.remarkName}, 原因=总消息数少于5, totalMessageCount=$totalMessageCount")
                return false
            }

            // 4. 检查上次成功提取后新增的消息数是否达到（退避后的）总结间隔。
            // 旧逻辑使用 totalMessageCount % summaryInterval，会被历史总数偏移影响，导致新增消息已经足够却不触发。
            // 退避：连续失败 N 次时，要求积累的新增消息数 = 间隔 × 2^min(N, MAX_BACKOFF_EXPONENT)。
            val normalizedSummaryInterval: Int = contact.summaryInterval.coerceAtLeast(MIN_SUMMARY_INTERVAL)
            val backoffExponent: Int = contact.autoSummaryFailureCount.coerceIn(0, MAX_BACKOFF_EXPONENT)
            val effectiveInterval: Int = normalizedSummaryInterval shl backoffExponent
            val shouldTrigger: Boolean = newMessageCountSinceLastSummary >= effectiveInterval
            Log.d(
                TAG,
                "自动总结新增消息结果: 联系人=${contact.remarkName}, shouldTrigger=$shouldTrigger, newMessageCountSinceLastSummary=$newMessageCountSinceLastSummary, effectiveInterval=$effectiveInterval, normalizedSummaryInterval=$normalizedSummaryInterval, backoffExponent=$backoffExponent"
            )

            if (shouldTrigger) {
                Log.i(TAG, "触发自动总结: 联系人=${contact.remarkName}, 总消息数=$totalMessageCount, 新增消息数=$newMessageCountSinceLastSummary, 有效间隔=$effectiveInterval")
                // 发送自动总结事件
                EventBus.post(AutoSummarizeRequestEvent(contactId, contact.remarkName))
                Log.d(TAG, "自动总结事件已投递: contactId=$contactId, contactName=${contact.remarkName}")
            }
            
            return shouldTrigger
            
        } catch (e: Exception) {
            Log.e(TAG, "自动总结检查失败", e)
            return false
        }
    }
    
    /**
     * 自动总结请求事件
     * Brain模块监听此事件并执行总结
     */
    data class AutoSummarizeRequestEvent(
        val contactId: String,
        val contactName: String
    )
}