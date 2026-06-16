package com.susking.ephone_s.aidata.prompt

import android.util.Base64
import com.google.gson.Gson
import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallItem
import com.susking.ephone_s.aidata.domain.model.UserProfile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 消息组类型
 */
enum class MessageGroupType {
    CONVERSATION,      // 对话组（包含AI和用户两方）
    MONOLOGUE         // 自说自话组（只有一方）
}

/**
 * 消息分组
 *
 * @param type 消息组类型
 * @param messages 该组内的消息列表（按时间正序排列）
 * @param startTime 第一条消息的时间戳
 * @param endTime 最后一条消息的时间戳
 * @param duration 持续时间（毫秒）
 * @param participants 参与者角色集合
 */
@Parcelize
data class MessageGroup(
    val type: MessageGroupType,
    val messages: List<ChatMessage>,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val participants: Set<String>
) : Parcelable

/**
 * 消息分组分析结果
 *
 * @param conversationGroup 最近的完整对话组
 * @param monologueGroups 对话组之后的自说自话组列表（按时间倒序，最新的在前）
 * @param totalDuration 所有组的总持续时间
 * @param groupCount 总组数
 */
@Parcelize
data class MessageGroupAnalysis(
    val conversationGroup: MessageGroup?,
    val monologueGroups: List<MessageGroup>,
    val totalDuration: Long,
    val groupCount: Int
) : Parcelable

/**
 * 历史记录构建结果
 *
 * @param payloads 聊天消息载荷列表
 * @param hasUnanalyzedImages 是否存在未分析的图片（有imageUrl但imageDescription为空）
 */
data class HistoryBuildResult(
    val payloads: List<ChatMessagePayload>,
    val hasUnanalyzedImages: Boolean
)

/**
 * Prompt组件构建器
 *
 * 提供所有PromptBuilder共用的标准化内容块构建函数
 * 统一管理长期记忆、倒计时、回忆、世界书等重复出现的提示词组件
 */
object PromptComponentBuilder {
    private const val VIDEO_CALL_STRUCTURED_EVENT_COMPATIBLE_MEMORY_SOURCE: String = "VideoCallStructuredEventCompatibility"
    
    /**
     * 旧原子事件提示词兼容入口。
     * 原子事件已改为只读纪念记录，不再作为提示词上下文注入。
     */
    fun buildLongTermMemorySection(
        longTermMemories: List<LongTermMemory>,
        userNickname: String? = null
    ): String {
        return ""
    }
    
    /**
     * 构建结构化记忆召回内容块。
     * 用于替代单一原子事件列表，把相关事件、有效事实、待完成承诺和时间线摘要分区注入。
     */
    fun buildMemoryRecallContextSection(
        recallContext: MemoryRecallContext,
        userNickname: String? = null
    ): String {
        val activeEvents: List<MemoryRecallItem> = recallContext.relevantEvents.filter { item: MemoryRecallItem ->
            item.status == null || item.status == MemoryEventStatus.ACTIVE || item.status == MemoryEventStatus.PENDING
        }
        val historicalEvents: List<MemoryRecallItem> = recallContext.relevantEvents.filter { item: MemoryRecallItem ->
            item.status == MemoryEventStatus.RESOLVED || item.status == MemoryEventStatus.ARCHIVED
        }
        val sections: List<String> = listOf(
            buildRelevantMemorySection(activeEvents, userNickname),
            buildHistoricalMemorySection(historicalEvents, userNickname),
            buildActiveFactsSection(recallContext.activeFacts),
            buildPendingCommitmentsSection(recallContext.pendingCommitments),
            buildRelationshipTimelinesSection(recallContext.relationshipTimelines),
            buildTimelineSummariesSection(recallContext.timelineSummaries)
        ).filter { section: String -> section.isNotBlank() }
        if (sections.isEmpty()) return ""
        return sections.joinToString(separator = "\n\n")
    }

    /**
     * 构建本轮相关结构化事件内容块。
     */
    fun buildRelevantMemorySection(
        items: List<MemoryRecallItem>,
        userNickname: String? = null
    ): String {
        if (items.isEmpty()) return ""
        val title: String = if (userNickname.isNullOrBlank()) "# 本轮相关结构化事件" else "# 本轮相关结构化事件（关于$userNickname）"
        val content: String = items.joinToString("\n") { item: MemoryRecallItem ->
            "- ${item.text}${buildMemoryItemSuffix(item)}"
        }
        return """

$title
$content
        """.trimIndent()
    }

    /**
     * 构建历史相关结构化事件内容块。
     */
    fun buildHistoricalMemorySection(
        items: List<MemoryRecallItem>,
        userNickname: String? = null
    ): String {
        if (items.isEmpty()) return ""
        val title: String = if (userNickname.isNullOrBlank()) "# 历史相关结构化事件" else "# 历史相关结构化事件（关于$userNickname）"
        val content: String = items.joinToString("\n") { item: MemoryRecallItem ->
            "- ${item.text}${buildMemoryItemSuffix(item)}"
        }
        return """

$title
这些事件可能已经结束或归档，但仍可用于复盘、玩梗、关系延续和语义召回；不要把“已结束”理解为从未发生。
$content
        """.trimIndent()
    }

    /**
     * 构建当前有效事实内容块。
     */
    fun buildActiveFactsSection(items: List<MemoryRecallItem>): String {
        if (items.isEmpty()) return ""
        val content: String = items.joinToString("\n") { item: MemoryRecallItem ->
            "- ${item.text}${buildMemoryItemSuffix(item)}"
        }
        return """

# 当前有效事实
$content
        """.trimIndent()
    }

    /**
     * 构建待完成承诺内容块。
     */
    fun buildPendingCommitmentsSection(items: List<MemoryRecallItem>): String {
        if (items.isEmpty()) return ""
        val content: String = items.joinToString("\n") { item: MemoryRecallItem ->
            "- ${item.text}${buildMemoryItemSuffix(item)}"
        }
        return """

# 待完成承诺与约定
$content
        """.trimIndent()
    }

    /**
     * 构建关系时间线内容块。
     */
    fun buildRelationshipTimelinesSection(items: List<MemoryRecallItem>): String {
        if (items.isEmpty()) return ""
        val content: String = items.joinToString("\n") { item: MemoryRecallItem ->
            "- ${item.text}${buildMemoryItemSuffix(item)}"
        }
        return """

# 关系时间线与变化
以下关系只基于明确证据抽取。证据可靠度仅表示抽取证据可靠程度，不表示关系强弱。
$content
        """.trimIndent()
    }

    /**
     * 构建时间线摘要内容块。
     */
    fun buildTimelineSummariesSection(items: List<MemoryRecallItem>): String {
        if (items.isEmpty()) return ""
        val content: String = items.joinToString("\n") { item: MemoryRecallItem ->
            "- ${item.text}"
        }
        return """

# 时间线摘要
$content
        """.trimIndent()
    }

    private fun buildMemoryItemSuffix(item: MemoryRecallItem): String {
        val statusText: String = item.status?.toPromptText().orEmpty()
        val scoreText: String = if (item.finalScore > 0.0f) "，相关度: ${String.format(Locale.CHINA, "%.2f", item.finalScore)}，抽取置信度: ${String.format(Locale.CHINA, "%.2f", item.confidenceScore)}" else ""
        val detailText: String = listOf(statusText, scoreText).filter { text: String -> text.isNotBlank() }.joinToString(separator = "")
        return if (detailText.isBlank()) "" else "（$detailText）"
    }

    private fun MemoryEventStatus.toPromptText(): String {
        return when (this) {
            MemoryEventStatus.ACTIVE -> "状态: 有效"
            MemoryEventStatus.PENDING -> "状态: 待完成"
            MemoryEventStatus.RESOLVED -> "状态: 已解决"
            MemoryEventStatus.CANCELLED -> "状态: 已取消"
            MemoryEventStatus.EXPIRED -> "状态: 已过期"
            MemoryEventStatus.SUPERSEDED -> "状态: 已被替代"
            MemoryEventStatus.ARCHIVED -> "状态: 归档"
        }
    }
    
    /**
     * 构建倒计时内容块
     * 
     * @param appointments 约定倒计时列表
     * @return 格式化的倒计时内容,如果列表为空则返回空字符串
     */
    fun buildAppointmentsSection(appointments: List<AppointmentEntity>): String {
        if (appointments.isEmpty()) return ""
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val appointmentList = appointments.joinToString("\n") { appointment ->
            val dateStr = dateFormat.format(Date(appointment.appointmentDate))
            "- ${appointment.title} (时间: $dateStr)"
        }
        
        return """
        
# 你们的约定倒计时
$appointmentList
        """.trimIndent()
    }
    
    /**
     * 构建回忆内容块
     * 
     * @param generalMemories 回忆列表
     * @return 格式化的回忆内容,如果列表为空则返回空字符串
     */
    fun buildGeneralMemoriesSection(generalMemories: List<GeneralMemoryEntity>): String {
        if (generalMemories.isEmpty()) return ""
        
        val memoryList = generalMemories.joinToString("\n") { memory ->
            "- ${memory.description}"
        }
        
        return """
        
# 你珍藏的回忆
$memoryList
        """.trimIndent()
    }
    
    /**
     * 构建世界书内容块
     * 
     * @param worldBookPrompts 世界书设定列表
     * @param useListFormat 是否使用列表格式(带"-"前缀),默认true
     * @return 格式化的世界书内容,如果列表为空则返回空字符串
     */
    fun buildWorldBookSection(
        worldBookPrompts: List<String>,
        useListFormat: Boolean = true
    ): String {
        if (worldBookPrompts.isEmpty()) return ""
        
        val content = if (useListFormat) {
            worldBookPrompts.joinToString("\n") { "- $it" }
        } else {
            worldBookPrompts.joinToString("\n")
        }
        
        return content
    }
    
    /**
     * 构建文风内容块
     *
     * @param writingStyleContent 文风内容字符串
     * @return 格式化的文风内容,如果内容为空则返回空字符串
     */
    fun buildWritingStyleSection(
        writingStyleContent: String?
    ): String {
        if (writingStyleContent.isNullOrBlank()) return ""

        val title = "# 文风 (你必须严格遵守)"
        return """
$title
$writingStyleContent
        """.trimIndent()
    }

    /**
     * 构建动态列表内容块
     *
     * @param allRecentFeeds 最近的动态列表
     * @param contactRealName 当前联系人的真实姓名，用于标记"这是你的帖子"/"你的评论"
     * @return 格式化的动态列表内容，如果列表为空则返回提示文本
     */
    fun buildFeedsSection(
        allRecentFeeds: List<com.susking.ephone_s.aidata.data.local.entity.FeedEntity>,
        contactRealName: String
    ): String {
        if (allRecentFeeds.isEmpty()) {
            return "\n(暂无动态)"
        }
        val postsContextBuilder = StringBuilder("\n")
        allRecentFeeds.take(10).forEach { post ->
            val authorName = post.authorName
            val contentSummary = post.content.take(50) + "..."
            postsContextBuilder.append("- (ID: ${post.id}) 作者: $authorName, 内容: \"$contentSummary\"")
            if (post.authorName == contactRealName) {
                postsContextBuilder.append(" (这是你的帖子)")
            }
            postsContextBuilder.append("\n")

            if (post.comments.isNotEmpty()) {
                val commentDetails = post.comments.take(5).joinToString("\n") { c ->
                    val commenterDisplayName = c.commenterName
                    val commentContent = when {
                        c.commentText != null -> c.commentText.take(30)
                        c.stickerMeaning != null -> "[表情: ${c.stickerMeaning}]"
                        else -> ""
                    }
                    var commentLine = "  - 评论 (时间戳: ${c.timestamp}): $commenterDisplayName: $commentContent"
                    if (c.commenterName == contactRealName) {
                        commentLine += " (你的评论)"
                    }
                    commentLine
                }
                postsContextBuilder.append(commentDetails).append("\n")
            }
        }
        return postsContextBuilder.toString()
    }

    /**
     * 构建时间间隔前缀
     *
     * @param previousTimestamp 上一条消息的时间戳
     * @param currentTimestamp 当前消息的时间戳
     * @param thresholdMinutes 时间间隔阈值(分钟),默认20分钟
     * @return 时间间隔前缀,如果不足阈值返回空字符串
     */
    private fun buildTimeGapPrefix(
        previousTimestamp: Long,
        currentTimestamp: Long,
        thresholdMinutes: Int = 20
    ): String {
        val timeGapMillis = currentTimestamp - previousTimestamp
        val timeGapMinutes = timeGapMillis / (60 * 1000)
        
        if (timeGapMinutes <= thresholdMinutes) {
            return ""
        }
        
        // 格式化时间间隔
        val hours = timeGapMinutes / 60
        val minutes = timeGapMinutes % 60
        val days = hours / 24
        val remainingHours = hours % 24
        
        return when {
            days > 0 -> "(${days}天${remainingHours}小时后)"
            hours > 0 -> "(${hours}小时${minutes}分钟后)"
            else -> "(${minutes}分钟后)"
        }
    }

    /**
     * 根据对话历史记录转换为 ChatMessagePayload 列表
     *
     * @param history 对话历史记录列表
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param longTermMemories 长期记忆列表（用于识别视频通话结构化事件召回状态）
     * @param isPropel 是否为推进模式(默认false)
     * @param timeGapThresholdMinutes 时间间隔阈值（分钟）
     * @return HistoryBuildResult 包含消息载荷列表和是否有未分析图片的标记
     */
    fun buildHistoryPayloads(
        history: List<ChatMessage>,
        contact: PersonProfile,
        userProfile: UserProfile,
        longTermMemories: List<LongTermMemory> = emptyList(),
        isPropel: Boolean = false,
        timeGapThresholdMinutes: Int = 20
    ): HistoryBuildResult {
        val gson = Gson()
        val filteredHistory = history.takeLast(contact.shortTermMemoryLimit)
            .filterNot { it.type == "naiimag" } // 过滤掉 naiimag 类型的历史消息
        
        // 【图片分析检测】检查是否存在未分析的图片
        var hasUnanalyzedImages = false
        for (msg in filteredHistory) {
            if (msg.type == "image_url" && !msg.imageUrl.isNullOrBlank() && msg.imageDescription.isNullOrBlank()) {
                hasUnanalyzedImages = true
                break
            }
        }
        
        // 【视频通话结构化事件识别】用于把已抽取结构化事件附加到对应视频通话记录。
        // 说明：短期上下文内需要保留“通话记录 + 已抽取结构化事件”作为上下文；
        // 但附加内容必须明确标记为“已抽取结构化事件”，避免后续总结任务把它伪装成普通聊天再次抽取。
        val videoCallRecordIdsInShortContext: Set<Long> = filteredHistory
            .filter { message: ChatMessage -> message.type == "video_call_record" }
            .mapNotNull { message: ChatMessage ->
                try {
                    val contentMap = gson.fromJson(message.content ?: "", Map::class.java)
                    (contentMap["videoCallId"] as? Number)?.toLong()
                } catch (e: Exception) {
                    null
                }
            }
            .toSet()
        val recalledVideoCallStructuredEvents = longTermMemories.filter { memory: LongTermMemory ->
            memory.sourceModule == VIDEO_CALL_STRUCTURED_EVENT_COMPATIBLE_MEMORY_SOURCE && memory.videoCallId != null
        }.sortedWith(compareBy<LongTermMemory> { memory: LongTermMemory -> memory.timestamp }.thenBy { memory: LongTermMemory -> memory.id })
        android.util.Log.d("PromptBuilder", "总长期记忆数量: ${longTermMemories.size}")
        android.util.Log.d("PromptBuilder", "视频通话结构化事件召回数量: ${recalledVideoCallStructuredEvents.size}, 短期上下文内视频通话ID: $videoCallRecordIdsInShortContext")
        
        val payloads = filteredHistory.mapIndexedNotNull { index, msg ->
            // 【过滤错误消息】错误消息不应该发送给模型作为上下文
            if (msg.role == "error") {
                return@mapIndexedNotNull null
            }

            // 计算与上一条消息的时间间隔
            val timeGapPrefix = if (index > 0) {
                val previousMsg = filteredHistory[index - 1]
                buildTimeGapPrefix(previousMsg.timestamp, msg.timestamp, timeGapThresholdMinutes)
            } else {
                ""
            }
            
            // 【视频通话记录处理】短期上下文内附加已抽取结构化事件，但明确标注为“已抽取结果”。
            // 这样它仍然能作为当前对话上下文被理解，又不会被误当作普通聊天内容再次抽取。
            if (msg.type == "video_call_record") {
                val prefix = "${timeGapPrefix}(Timestamp: ${msg.timestamp}) "
                val rawContent = msg.content ?: ""
                var videoCallId: Long? = null
                var displayText: String = rawContent
                try {
                    val contentMap = gson.fromJson(rawContent, Map::class.java)
                    videoCallId = (contentMap["videoCallId"] as? Number)?.toLong()
                    displayText = (contentMap["text"] as? String) ?: rawContent
                } catch (e: Exception) {
                    // 如果不是JSON格式，使用原始内容，保证旧消息仍可正常进入上下文。
                }
                val matchedStructuredEvents: List<LongTermMemory> = if (videoCallId != null) {
                    recalledVideoCallStructuredEvents.filter { memory: LongTermMemory -> memory.videoCallId == videoCallId }
                } else {
                    val timeWindow: Long = 5 * 60 * 1000L
                    recalledVideoCallStructuredEvents.filter { memory: LongTermMemory ->
                        kotlin.math.abs(memory.timestamp - msg.timestamp) <= timeWindow
                    }
                }
                val structuredEventSection: String = buildVideoCallStructuredEventSection(matchedStructuredEvents)
                val finalContent: String = if (structuredEventSection.isBlank()) {
                    "$prefix$displayText"
                } else {
                    "$prefix$displayText\n\n$structuredEventSection"
                }
                return@mapIndexedNotNull ChatMessagePayload(role = msg.role, content = finalContent)
            }
            
            if (msg.type == "pat_message") {
                val prefix = "${timeGapPrefix}(Timestamp: ${msg.timestamp}) "
                return@mapIndexedNotNull ChatMessagePayload(role = msg.role, content = "$prefix${msg.content}")
            }
            val role = msg.role
            val prefix = "${timeGapPrefix}(Timestamp: ${msg.timestamp}) "

            if (role == "user") {
                val finalContent: Any
                // 统一数据模型，优先处理多模态消息
                if (msg.type == "image_url") {
                    val contentList = mutableListOf<ContentPart>()
                    // 构建带时间戳前缀的文本内容
                    val textContent = msg.content ?: ""
                    val textWithTimestamp = "$prefix$textContent".trim()
                    // 即使是图片消息，也可能附带文本
                    if (textWithTimestamp.isNotEmpty()) {
                        contentList.add(TextContentPart(text = textWithTimestamp))
                    }
                    
                    // 【图片描述优化】优先使用AI生成的图片描述，避免重复发送Base64
                    if (!msg.imageDescription.isNullOrBlank()) {
                        // 如果已有图片描述，使用描述文本替代Base64
                        contentList.add(TextContentPart(text = "[对方发送了一张图片，图片内容描述: ${msg.imageDescription}]"))
                    } else if (!msg.imageUrl.isNullOrBlank()) {
                        // 如果没有描述，才发送Base64图片（首次分析）
                        // 【关键修复】在图片前添加消息ID，让AI知道这张图片对应哪个messageId
                        contentList.add(TextContentPart(text = "[图片消息ID: ${msg.id}]"))
                        try {
                            val imageFile = File(msg.imageUrl)
                            if (imageFile.exists()) {
                                val imageBytes = imageFile.readBytes()
                                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                                contentList.add(
                                    ImageContentPart(
                                        imageUrl = ImageUrlPayload(
                                            url = "data:image/jpeg;base64,$base64Image"
                                        )
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // 图片编码失败，添加错误提示文本
                            contentList.add(TextContentPart(text = "[图片加载失败: ${e.message}]"))
                        }
                    }
                    // 最终内容是一个ContentPart列表
                    finalContent = contentList
                } else {
                    // 检查是否有引用消息
                    if (msg.quotedMessage != null) {
                        val quoted = msg.quotedMessage
                        val quotedContent = quoted.content.take(50)
                        val replyContent = msg.content ?: ""
                        // 构建包含引用信息的描述性文本
                        finalContent = "$prefix[引用回复] (回复你的消息：\"${quotedContent}\"): \"${replyContent}\""
                    } else {
                        // 对于所有其他类型的用户消息，我们将其视为纯文本
                        val contentStr = msg.content ?: ""
                        val descriptiveText = when (msg.type) {
                            "text", "offline_text" -> contentStr
                            "user_photo", "ai_image", "image" -> contentStr
                            "voice_message" -> "[${userProfile.nickname}发送了一条语音消息，内容是：'${contentStr}']"
                            "transfer" -> "[系统提示：${userProfile.nickname}于时间戳 ${msg.timestamp} 向你发起了转账: ${msg.amount}元, 备注: ${msg.notes}。等待对方处理。]"
                            "waimai_request" -> "[系统提示：${userProfile.nickname}于时间戳 ${msg.timestamp} 发起了外卖代付请求，商品是“${msg.productInfo}”，金额是 ${msg.amount} 元。]"
                            "gift" -> "[系统提示：${userProfile.nickname}送给了你一份礼物，礼物是${msg.giftName ?: "未知礼物"}，价值${msg.giftValue ?: 0.0}元，赠礼备注为“${msg.giftNote ?: "无"}”]"
                            "sticker" -> "[${userProfile.nickname}发送了一个表情，意思是：'${msg.stickerName ?: "未知表情"}']"
                            "shopping_access_request" -> "[系统提示：${userProfile.nickname}于时间戳 ${msg.timestamp} 申请查看你的购物app页面。如果你同意，请回复approve_shopping_access指令。如果拒绝，请回复reject_shopping_access指令并说明原因。这是一个需要你明确回应的系统请求。]"
                            "location_share" -> {
                                if (isPropel) {
                                    "[系统指令：${userProfile.nickname}分享了一个位置“${contentStr}”，请你主动根据这个地点发起一段对话。]"
                                } else {
                                    "[${userProfile.nickname}分享了一个位置：“${contentStr}”]"
                                }
                            }
                            "friend_application" -> "[系统提示：${userProfile.nickname}于时间戳 ${msg.timestamp} 向你发起了好友申请，理由是：“${msg.content}”。你尚未处理。]"
                            else -> contentStr
                        }
                        // 最终内容是一个带时间戳前缀的字符串
                        finalContent = "$prefix$descriptiveText"
                    }
                }
                ChatMessagePayload(role = role, content = finalContent)
            }
            else { // role == "assistant"
                // 检查 AI 是否发送了引用回复消息
                if (msg.quotedMessage != null) {
                    val quoted = msg.quotedMessage
                    val quotedContent = quoted.content.take(50)
                    val replyContent = msg.content ?: ""
                    val descriptiveText = "[引用回复] 你引用了用户说的: \"${quotedContent}\" | 你回复: \"${replyContent}\""
                    ChatMessagePayload(role = role, content = "$prefix$descriptiveText")
                } else if (msg.type == "text" || msg.type == "offline_text") {
                    // AI的普通文本消息为纯字符串，而不是JSON
                    ChatMessagePayload(role = role, content = "$prefix${msg.content ?: ""}")
                } else {
                    // 对于其他动作类型（sticker, transfer等），格式化为JSON字符串
                    val assistantMsgObject = mutableMapOf<String, Any?>("type" to (msg.type ?: "text"))
                    when (msg.type) {
                        "sticker" -> {
                            assistantMsgObject["url"] = msg.imageUrl
                            assistantMsgObject["meaning"] = msg.stickerName
                        }
                        "transfer" -> {
                            assistantMsgObject["amount"] = msg.amount
                            assistantMsgObject["note"] = msg.notes
                        }
                        "waimai_request" -> {
                            assistantMsgObject["productInfo"] = msg.productInfo
                            assistantMsgObject["amount"] = msg.amount
                        }
                        "gift" -> {
                            val senderName = contact.realName ?: contact.remarkName
                            val giftName = msg.giftName ?: "未知礼物"
                            val giftValue = msg.giftValue ?: 0.0
                            val giftNote = msg.giftNote ?: "无"
                            assistantMsgObject["content"] = "[系统提示：你送给了${userProfile.nickname}一份礼物，礼物是${giftName}，价值${giftValue}元，赠礼备注为“${giftNote}”]"
                        }
                        "location_share" -> {
                            val senderName = contact.realName ?: contact.remarkName
                            val locationName = msg.content ?: "一个地点"
                            assistantMsgObject["content"] = "[系统提示：${senderName} 分享了Ta的位置：“${locationName}”]"
                        }
                        "friend_application" -> {
                            val systemHint = when (msg.status) {
                                "accepted" -> "[系统提示：你于时间戳 ${msg.timestamp} 同意了对方的好友申请。你们现在是好友了。]"
                                "declined" -> "[系统提示：你于时间戳 ${msg.timestamp} 拒绝了对方的好友申请。]"
                                else -> "[系统提示：你于时间戳 ${msg.timestamp} 向对方发起了好友申请，理由是：“${msg.content}”。等待对方处理。]"
                            }
                            assistantMsgObject["content"] = systemHint
                        }
                        "offline_request" -> {
                            val statusHint = when (msg.status) {
                                "accepted" -> "[系统提示：你于时间戳 ${msg.timestamp} 向${userProfile.nickname}发起了线下见面请求，地点是${msg.offlineLocation}，理由：“${msg.offlineReason}”。对方已同意。]"
                                "rejected" -> "[系统提示：你于时间戳 ${msg.timestamp} 向${userProfile.nickname}发起了线下见面请求，地点是${msg.offlineLocation}，理由：“${msg.offlineReason}”。对方已拒绝。]"
                                else -> "[系统提示：你于时间戳 ${msg.timestamp} 向${userProfile.nickname}发起了线下见面请求，地点是${msg.offlineLocation}，理由：“${msg.offlineReason}”。正在等待对方处理。]"
                            }
                            assistantMsgObject["content"] = statusHint
                        }
                        // 其他所有被视为"动作"的类型
                        else -> {
                            assistantMsgObject["content"] = msg.content
                        }
                    }
                    val assistantContentJson = gson.toJson(listOf(assistantMsgObject.filterValues { it != null }))
                    ChatMessagePayload(role = role, content = "$prefix$assistantContentJson")
                }
            }
        }
        
        return HistoryBuildResult(
            payloads = payloads,
            hasUnanalyzedImages = hasUnanalyzedImages
        )
    }

    /**
     * 构建视频通话已抽取结构化事件块。
     *
     * 该块只用于解释视频通话记录，不应作为普通聊天内容再次抽取。
     */
    private fun buildVideoCallStructuredEventSection(memories: List<LongTermMemory>): String {
        if (memories.isEmpty()) return ""
        val eventLines: String = memories.joinToString(separator = "\n") { memory: LongTermMemory ->
            "- ${memory.memoryText}"
        }
        return """
            [视频通话已抽取结构化事件]
            说明：以下内容是系统已经从这条视频通话记录中抽取并保存过的结构化事件，只能作为理解本次通话记录的上下文；不要把这些条目当作普通聊天消息再次抽取或重复保存。
            $eventLines
        """.trimIndent()
    }

    /**
     * 构建简化版对话历史摘要
     *
     * 将消息列表转换为简洁的"发送者：内容"格式,每条消息一行
     * 完整解析所有消息类型,包括文本、图片、语音、转账、表情、引用回复等
     *
     * @param history 对话历史记录列表
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param maxLength 每条消息内容的最大长度(默认50字符)
     * @return 格式化的历史摘要文本,每行一条消息
     */
    fun buildSimplifiedHistorySummary(
        history: List<ChatMessage>,
        contact: PersonProfile,
        userProfile: UserProfile,
        timeGapThresholdMinutes: Int = 20
    ): String {
        if (history.isEmpty()) return ""

        val filteredHistory = history.filterNot { it.type == "naiimag" }
        return filteredHistory.mapIndexed { index, msg ->
            // 计算与上一条消息的时间间隔
            val timeGapPrefix = if (index > 0) {
                val previousMsg = filteredHistory[index - 1]
                buildTimeGapPrefix(previousMsg.timestamp, msg.timestamp, timeGapThresholdMinutes)
            } else {
                ""
            }
            
            // 处理系统消息(拍一拍等)
            if (msg.type == "pat_message") {
                return@mapIndexed "${timeGapPrefix}${msg.content ?: ""}"
            }

            // 确定发送者名称
            val senderName = if (msg.role == "user") userProfile.nickname else contact.realName

            // 根据消息类型生成内容描述
            val contentDescription = when (msg.type) {
                "text", "offline_text" -> {
                    msg.content ?: "[空消息]"
                }

                "image_url", "user_photo", "ai_image", "image" -> {
                    val textPart = msg.content?.takeIf { it.isNotBlank() }?.let { "($it)" } ?: ""
                    "[图片]$textPart"
                }

                "voice_message" -> {
                    val transcript = msg.content?.takeIf { it.isNotBlank() } ?: "未转录"
                    "[语音消息：$transcript]"
                }

                "sticker" -> {
                    val stickerName = msg.stickerName ?: "未知表情"
                    "[表情：$stickerName]"
                }

                "transfer" -> {
                    val amount = msg.amount ?: 0.0
                    val note = msg.notes?.takeIf { it.isNotBlank() }?.let { ",备注:$it" } ?: ""
                    "[转账：${amount}元$note]"
                }

                "waimai_request" -> {
                    val product = msg.productInfo ?: "商品"
                    val amount = msg.amount ?: 0.0
                    "[外卖代付请求：$product,${amount}元]"
                }

                "gift" -> {
                    val giftName = msg.giftName ?: "礼物"
                    val value = msg.giftValue ?: 0.0
                    val note = msg.giftNote?.takeIf { it.isNotBlank() }?.let { ",\"$it\"" } ?: ""
                    "[赠送礼物：$giftName(${value}元)$note]"
                }

                "location_share" -> {
                    val location = msg.content ?: "某个位置"
                    "[分享位置：$location]"
                }

                "shopping_access_request" -> {
                    "[申请查看购物app]"
                }

                "friend_application" -> {
                    val reason = msg.content ?: "无"
                    val statusText = when (msg.status) {
                        "accepted" -> "(已同意)"
                        "declined" -> "(已拒绝)"
                        else -> "(待处理)"
                    }
                    "[好友申请：$reason]$statusText"
                }

                "offline_request" -> {
                    val location = msg.offlineLocation ?: "某地"
                    val reason = msg.offlineReason ?: ""
                    val statusText = when (msg.status) {
                        "accepted" -> "(已同意)"
                        "rejected" -> "(已拒绝)"
                        else -> "(待处理)"
                    }
                    "[线下见面请求：$location,$reason]$statusText"
                }

                else -> {
                    // 其他未知类型
                    msg.content ?: "[${msg.type}]"
                }
            }

            // 处理引用回复消息
            if (msg.quotedMessage != null) {
                val quoted = msg.quotedMessage
                val quotedContent = quoted.content.take(50)
                val quotedSender = quoted.senderName
                val replyContent = when {
                    msg.content != null -> msg.content
                    msg.type == "sticker" -> "[表情：${msg.stickerName}]"
                    else -> contentDescription
                }
                return@mapIndexed "$timeGapPrefix$senderName: [引用回复 $quotedSender 说的\"$quotedContent\"] $replyContent"
            }

            // 返回标准格式（带时间戳）
            "$timeGapPrefix$senderName: (Timestamp: ${msg.timestamp}) $contentDescription"
        }.joinToString("\n")
    }



    /**
     * 分析最近的消息分组
     *
     * 倒序查询消息，找到最新的完整对话组及其后的自说自话组。
     * 查询规则：
     * 1. 相邻两条消息时间间隔 ≤ 20分钟，视为同一组
     * 2. 相邻两条消息时间间隔 > 20分钟，开启新组
     * 3. 对话组：包含AI和用户两个发言方
     * 4. 自说自话组：只有AI或用户一个发言方
     * 5. 倒序查询直到找到第一个完整对话组，以及该对话组之前（时间上更早）的分界点
     *
     * @param allMessages 所有消息列表（按时间正序）
     * @param timeGapThresholdMinutes 时间间隔阈值（分钟），默认20分钟
     * @return 消息分组分析结果
     */
    fun analyzeRecentMessageGroups(
        allMessages: List<ChatMessage>,
        timeGapThresholdMinutes: Int = 20
    ): MessageGroupAnalysis {
        if (allMessages.isEmpty()) {
            return MessageGroupAnalysis(
                conversationGroup = null,
                monologueGroups = emptyList(),
                totalDuration = 0L,
                groupCount = 0
            )
        }
        
        val timeGapMillis = timeGapThresholdMinutes * 60 * 1000L
        
        // 倒序处理消息（从最新到最旧）
        val reversedMessages = allMessages.sortedByDescending { it.timestamp }
        
        // 1. 将消息分组（按20分钟间隔）
        val groups = mutableListOf<MessageGroup>()
        var currentGroup = mutableListOf<ChatMessage>()
        
        for (i in reversedMessages.indices) {
            val msg = reversedMessages[i]
            
            if (currentGroup.isEmpty()) {
                currentGroup.add(msg)
            } else {
                val lastMsg = currentGroup.last()
                val timeDiff = lastMsg.timestamp - msg.timestamp
                
                if (timeDiff <= timeGapMillis) {
                    // 同一组
                    currentGroup.add(msg)
                } else {
                    // 新组开始，先保存当前组
                    groups.add(createMessageGroup(currentGroup))
                    currentGroup = mutableListOf(msg)
                }
            }
        }
        
        // 保存最后一组
        if (currentGroup.isNotEmpty()) {
            groups.add(createMessageGroup(currentGroup))
        }
        
        // 2. 查找第一个对话组
        val conversationGroupIndex = groups.indexOfFirst {
            it.type == MessageGroupType.CONVERSATION
        }
        
        if (conversationGroupIndex == -1) {
            // 没有找到对话组，返回空结果
            return MessageGroupAnalysis(
                conversationGroup = null,
                monologueGroups = emptyList(),
                totalDuration = 0L,
                groupCount = 0
            )
        }
        
        // 3. 提取对话组及其之前的自说自话组（时间上在对话组之后的组）
        val conversationGroup = groups[conversationGroupIndex]
        val monologueGroups = groups.subList(0, conversationGroupIndex)
        
        // 4. 计算总持续时间
        val totalDuration = calculateTotalDuration(conversationGroup, monologueGroups)
        
        return MessageGroupAnalysis(
            conversationGroup = conversationGroup,
            monologueGroups = monologueGroups,
            totalDuration = totalDuration,
            groupCount = 1 + monologueGroups.size
        )
    }
    
    /**
     * 创建消息组
     *
     * @param messages 消息列表（倒序）
     * @return 消息组对象
     */
    private fun createMessageGroup(messages: List<ChatMessage>): MessageGroup {
        // 将消息按时间正序排列
        val sortedMessages = messages.sortedBy { it.timestamp }
        val participants = messages.map { it.role }.filter { it != "system" }.toSet()

        val type = if (participants.size > 1) {
            MessageGroupType.CONVERSATION
        } else {
            MessageGroupType.MONOLOGUE
        }
        
        val startTime = sortedMessages.first().timestamp
        val endTime = sortedMessages.last().timestamp
        
        return MessageGroup(
            type = type,
            messages = sortedMessages,
            startTime = startTime,
            endTime = endTime,
            duration = endTime - startTime,
            participants = participants
        )
    }
    
    /**
     * 计算总持续时间
     *
     * @param conversationGroup 对话组
     * @param monologueGroups 自说自话组列表
     * @return 总持续时间（毫秒）
     */
    private fun calculateTotalDuration(
        conversationGroup: MessageGroup,
        monologueGroups: List<MessageGroup>
    ): Long {
        val allGroups = listOf(conversationGroup) + monologueGroups
        if (allGroups.isEmpty()) return 0L
        
        val earliestTime = allGroups.minOf { it.startTime }
        val latestTime = allGroups.maxOf { it.endTime }
        
        return latestTime - earliestTime
    }
    
    /**
     * 格式化时间戳为可读格式
     *
     * @param timestamp 时间戳（毫秒）
     * @return 格式化的时间字符串
     */
    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * 格式化持续时间为可读格式
     *
     * @param durationMillis 持续时间（毫秒）
     * @return 格式化的持续时间字符串
     */
    private fun formatDuration(durationMillis: Long): String {
        if (durationMillis == 0L) return "0秒"
        
        val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
        
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}小时")
        if (minutes > 0) parts.add("${minutes}分钟")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}秒")
        
        return parts.joinToString("")
    }
    
    /**
     * 检测当前时间与上一条消息的时间间隔是否超过阈值
     *
     * @param lastMessageTimestamp 上一条消息的时间戳
     * @param currentTimeMillis 当前时间戳
     * @param thresholdMinutes 时间间隔阈值（分钟），默认20分钟
     * @return 是否超过阈值
     */
    fun isLongTimeSinceLastMessage(
        lastMessageTimestamp: Long,
        currentTimeMillis: Long,
        thresholdMinutes: Int = 20
    ): Boolean {
        if (lastMessageTimestamp == 0L) return false
        val timeGapMillis = (currentTimeMillis - lastMessageTimestamp)
        val timeGapMinutes = timeGapMillis / (60 * 1000)
        return timeGapMinutes > thresholdMinutes
    }
    
    /**
     * 构建时间感知的历史记录（用于超过20分钟未联系的情况）
     *
     * 格式：
     * - 你们上次互发消息是在 x 天 x 点 x 分
     * - 你们上次聊天的内容是：消息组X
     * - 最后一轮对话结束后：（若存在独白组）
     *   - x 小时后（x 月 x 日 xx:xx），role发送了：（独白组 A 内容）（持续等待 x 分钟）
     *   - 又过了 x 小时（x 月 x 日 xx:xx），role发送了：（独白组 B 内容）（持续等待 x 分钟）
     * - 现在是 x 月 x 日 xx:xx，距离某某上次回复某某，已经过去了 x 天 x 小时。你必须感受这些时间差。
     *
     * @param allMessages 所有消息列表
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param longTermMemories 长期记忆列表（用于提取视频总结）
     * @param currentTimeMillis 当前时间戳
     * @param isPropel 是否为推进模式
     * @return ChatMessagePayload列表（包含普通历史+时间感知总结）
     */
    fun buildTimeAwareHistoryPayloads(
        allMessages: List<ChatMessage>,
        contact: PersonProfile,
        userProfile: UserProfile,
        longTermMemories: List<LongTermMemory> = emptyList(),
        currentTimeMillis: Long,
        isPropel: Boolean = false
    ): HistoryBuildResult {
        if (allMessages.isEmpty()) {
            return HistoryBuildResult(payloads = emptyList(), hasUnanalyzedImages = false)
        }
        
        // 1. 分析消息组
        val analysis = analyzeRecentMessageGroups(allMessages)
        
        if (analysis.conversationGroup == null) {
            // 没有找到对话组，使用普通构建方法
            return buildHistoryPayloads(allMessages, contact, userProfile, longTermMemories, isPropel)
        }
        
        val result = mutableListOf<ChatMessagePayload>()
        
        // 2. 获取对话组之前的所有消息（不包括独白组内的消息，但包括对话组）
        val monologueMessages = mutableSetOf<String>()
        analysis.monologueGroups.forEach { group ->
            monologueMessages.addAll(group.messages.map { it.id })
        }
        
        val messagesBeforeMonologues = allMessages.filter { msg ->
            msg.id !in monologueMessages
        }
        
        // 3. 构建消息组之前的历史记录（包含对话组）
        var hasUnanalyzedImages = false
        if (messagesBeforeMonologues.isNotEmpty()) {
            val beforeMonologuesResult = buildHistoryPayloads(
                messagesBeforeMonologues,
                contact,
                userProfile,
                longTermMemories,
                isPropel
            )
            result.addAll(beforeMonologuesResult.payloads)
            hasUnanalyzedImages = beforeMonologuesResult.hasUnanalyzedImages
        }
        
        // 4. 构建时间感知总结
        val timeAwareSummary = buildTimeAwareSummary(
            analysis,
            contact,
            userProfile,
            currentTimeMillis
        )
        
        result.add(ChatMessagePayload(
            role = "user",
            content = timeAwareSummary
        ))
        
        return HistoryBuildResult(
            payloads = result,
            hasUnanalyzedImages = hasUnanalyzedImages
        )
    }
    
    /**
     * 构建时间感知总结文本
     *
     * @param analysis 消息分组分析结果
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param currentTimeMillis 当前时间戳
     * @return 格式化的时间感知总结文本
     */
    private fun buildTimeAwareSummary(
        analysis: MessageGroupAnalysis,
        contact: PersonProfile,
        userProfile: UserProfile,
        currentTimeMillis: Long
    ): String {
        val conversationGroup = analysis.conversationGroup ?: return ""
        val monologueGroups = analysis.monologueGroups.reversed() // 从旧到新排序
        
        val sb = StringBuilder()
        sb.appendLine("# 【重要】时间感知与对话回顾")
        sb.appendLine()
        
        // 1. 上次互发消息时间
        val lastMessageTime = conversationGroup.endTime
        val lastMessageDate = Date(lastMessageTime)
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
        sb.appendLine("## 上次互发消息")
        sb.appendLine("你们上次互发消息是在：${dateFormat.format(lastMessageDate)}")
        sb.appendLine()
        
        // 2. 对话后的独白组（如果存在）
        if (monologueGroups.isNotEmpty()) {
            sb.appendLine("## 最后一轮对话结束后")
            sb.appendLine()
            
            var previousEndTime = conversationGroup.endTime
            
            monologueGroups.forEachIndexed { index, group ->
                val speaker = if (group.participants.contains("assistant")) {
                    contact.realName
                } else {
                    userProfile.nickname
                }
                
                // 计算与上一个组的时间间隔
                val timeSincePrevious = group.startTime - previousEndTime
                val hoursSince = timeSincePrevious / (1000 * 60 * 60)
                val minutesSince = (timeSincePrevious / (1000 * 60)) % 60
                
                val timeGapText = when {
                    hoursSince >= 24 -> "${hoursSince / 24}天${hoursSince % 24}小时后"
                    hoursSince > 0 -> "${hoursSince}小时${minutesSince}分钟后"
                    else -> "${minutesSince}分钟后"
                }
                
                val groupStartDate = Date(group.startTime)
                val groupStartFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
                
//                sb.appendLine("### 独白组 ${index + 1}")
                sb.append(if (index == 0) "$timeGapText" else "又过了$timeGapText")
                sb.appendLine("（${groupStartFormat.format(groupStartDate)}），${speaker}发送了：")
                
                // 显示该独白组的消息内容
                val monologueSummary = buildSimplifiedHistorySummary(group.messages, contact, userProfile)
                if (monologueSummary.isNotEmpty()) {
                    monologueSummary.lines().forEach { line ->
                        sb.appendLine("  - $line")
                    }
                }
                
                // 显示持续等待时间
                val waitDuration = formatDuration(group.duration)
                sb.appendLine("（${speaker}持续等待了 ${waitDuration}）")
                sb.appendLine()
                
                previousEndTime = group.endTime
            }
        }
        
        // 4. 当前时间与距离上次回复的时间差
        sb.appendLine("以上是历史会话记录。")
        sb.appendLine("## 当前时间与时间流逝")
        val currentDate = Date(currentTimeMillis)
        sb.appendLine("现在是：${dateFormat.format(currentDate)}")
        sb.appendLine()
        
        // 获取所有相关消息，并找到最后一条消息
        val allGroupMessagesList = (conversationGroup.messages + monologueGroups.flatMap { it.messages }).sortedBy { it.timestamp }
        val lastMessage = allGroupMessagesList.lastOrNull() ?: conversationGroup.messages.last()

        val lastSender = if (lastMessage.role == "assistant") "你" else userProfile.nickname
        val lastReceiver = if (lastMessage.role == "assistant") userProfile.nickname else "你"

        // 根据最后一条消息的发送者，决定计算时间差的基准
        val timeSinceLastReply = if (lastMessage.role == "user") {
            // 如果最后是用户发言，计算从AI上次回复到现在的时间
            val lastAssistantMessage = allGroupMessagesList.lastOrNull { it.role == "assistant" }
            currentTimeMillis - (lastAssistantMessage?.timestamp ?: conversationGroup.startTime)
        } else {
            // 如果最后是AI发言，计算从用户上次回复到现在的时间
            val lastUserMessage = allGroupMessagesList.lastOrNull { it.role == "user" }
            currentTimeMillis - (lastUserMessage?.timestamp ?: conversationGroup.startTime)
        }
        val daysSince = timeSinceLastReply / (1000 * 60 * 60 * 24)
        val hoursSince = (timeSinceLastReply / (1000 * 60 * 60)) % 24
        val minutesSince = (timeSinceLastReply / (1000 * 60)) % 60
        
        val timeElapsedText = buildString {
            if (daysSince > 0) append("${daysSince}天")
            if (hoursSince > 0) append("${hoursSince}小时")
            if (minutesSince > 0 || (daysSince == 0L && hoursSince == 0L)) {
                append("${minutesSince}分钟")
            }
        }
        
        if (monologueGroups.isEmpty()) {
            sb.appendLine("距离你们上次聊天，已经过去了 ${timeElapsedText}。")
        } else {
            sb.appendLine("距离${lastReceiver}上次回复${lastSender}，已经过去了 ${timeElapsedText}。")
        }
        sb.appendLine("- 你的回复必须符合这个时间跨度，不能表现得像刚刚才分开一样。注意：本次输入不代表${userProfile.nickname}出现")
        
        return sb.toString()
    }
}