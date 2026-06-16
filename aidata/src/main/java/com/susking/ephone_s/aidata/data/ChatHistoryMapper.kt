package com.susking.ephone_s.aidata.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.QuotedMessage
import com.susking.ephone_s.aidata.domain.model.import_export.ChatData
import com.susking.ephone_s.aidata.domain.model.import_export.EPhoneSChat
import com.susking.ephone_s.aidata.domain.model.import_export.HistoryMessage
import com.susking.ephone_s.aidata.domain.model.import_export.LongTermMemoryEntry
import com.susking.ephone_s.aidata.domain.model.import_export.QuotedMessageData
import com.susking.ephone_s.aidata.domain.model.import_export.Relationship
import com.susking.ephone_s.aidata.domain.model.import_export.Settings
import com.susking.ephone_s.aidata.domain.model.import_export.Status
import com.susking.ephone_s.aidata.domain.model.import_export.ThoughtsHistoryEntry
import java.io.InputStream
import java.io.InputStreamReader

/**
 * 负责在 JSON 导入/导出模型和 App 内部数据模型之间进行转换的映射器。
 */
object ChatHistoryMapper {

    /**
     * 将 App 内部数据模型转换为用于导出的 EPhoneSChat DTO。
     *
     * @param contact 联系人信息。
     * @param chatHistory 聊天记录。
     * @param longTermMemories 长期记忆。
     * @param jottings 心声和散记。
     * @return 转换后的 EPhoneSChat 对象。
     */
    fun toEPhoneSChat(
        contact: PersonProfile,
        chatHistory: List<ChatMessage>,
        longTermMemories: List<LongTermMemory>,
        heartbeats: List<com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity>,
        jottings: List<JottingEntity>
    ): EPhoneSChat {
        // ... 具体的映射逻辑将在这里实现 ...
        // 为了简洁，此处暂时返回一个 placeholder
        return EPhoneSChat(
            type = "EPhone_S_SingleChat",
            version = 1,
            chatData = ChatData(
                id = contact.id,
                name = contact.remarkName,
                originalName = contact.realName,
                description = contact.description,
                signature = contact.signature,
                nicknameForUser = contact.nicknameForUser,
                gender = contact.gender,
                age = contact.age,
                birthday = contact.birthday,
                zodiacSign = contact.zodiacSign,
                location = contact.location,
                companyOrSchool = contact.companyOrSchool,
                profession = contact.profession,
                group = contact.group,
                isGroup = false,
                relationship = Relationship("friend", null, null), // 默认值
                status = Status(
                    contact.statusText,
                    System.currentTimeMillis(),
                    contact.isBusy
                ), // 映射status
                settings = Settings(
                    aiPersona = contact.persona,
                    maxMemory = 200, // 默认值
                    aiAvatar = contact.avatarUri ?: "",
                    background = contact.backgroundUri,
                    chatBackground = contact.chatBackgroundUri,
                    selectedPhotos = contact.selectedPhotos,
                    actionCooldownMinutes = contact.actionCooldownMinutes,
                    enableAutoMemory = contact.autoSummaryEnabled,
                    autoMemoryInterval = contact.summaryInterval,
                    isOfflineMode = contact.offlineModeEnabled,
                    charAlbumRefreshCount = 8, // 默认值
                    enableTimePerception = contact.timeAwarenessEnabled,
                    enableBackgroundActivity = contact.backgroundActivityEnabled,
                    lastBackgroundActionTimestamp = contact.lastBackgroundActionTimestamp,
                    injectLatestThought = contact.injectThoughts,
                    offlinePresetId = null, // 默认值
                    naiPromptSource = contact.naiPromptSource,
                    naiPositivePrompt = contact.naiPositivePrompt,
                    naiNegativePrompt = contact.naiNegativePrompt,
                    shortTermMemoryLimit = contact.shortTermMemoryLimit,
                    attachMemoryLimit = contact.attachMemoryLimit,
                    privacyModeEnabled = contact.privacyModeEnabled,
                    ttsVoiceId = contact.ttsVoiceId,
                    voiceDescription = contact.voiceDescription
                ),
                history = chatHistory.map { toHistoryMessage(it, contact) },
                longTermMemory = longTermMemories.map { toLongTermMemoryEntry(it) },
                unreadCount = contact.unreadMessageCount,
                isPinned = contact.isPinned,
                lastMemorySummaryTimestamp = contact.lastSummaryTimestamp,
                heartfeltVoice = heartbeats.lastOrNull()?.content,
                randomJottings = jottings.lastOrNull()?.content,
                thoughtsHistory = toThoughtsHistory(heartbeats, jottings)
            )
        )
    }

    /**
     * 将 JSON 字符串解析为 EPhoneSChat DTO，并识别出未匹配的字段。
     *
     * @param jsonString 包含聊天记录的 JSON 字符串。
     * @return 一个包含 EPhoneSChat 对象和未识别字段列表的 Pair。
     * @throws com.google.gson.JsonSyntaxException 如果 JSON 格式不正确。
     */
    fun fromJson(jsonString: String): Pair<EPhoneSChat, List<String>> {
        val gson = Gson()
        val ephoneSChat = gson.fromJson(jsonString, EPhoneSChat::class.java)

        // 识别未定义字段
        val originalMap = gson.fromJson<Map<String, Any>>(jsonString, object : TypeToken<Map<String, Any>>() {}.type)
        val serializedMap = gson.fromJson<Map<String, Any>>(gson.toJson(ephoneSChat), object : TypeToken<Map<String, Any>>() {}.type)
        val unrecognizedKeys = mutableListOf<String>()

        fun findUnrecognized(original: Map<*, *>, serialized: Map<*, *>, prefix: String) {
            original.keys.forEach { key ->
                val currentKey = "$prefix.$key"
                if (!serialized.containsKey(key)) {
                    unrecognizedKeys.add(currentKey)
                } else if (original[key] is Map<*, *> && serialized[key] is Map<*, *>) {
                    findUnrecognized(original[key] as Map<*, *>, serialized[key] as Map<*, *>, currentKey)
                }
            }
        }

        findUnrecognized(originalMap, serializedMap, "root")


        return Pair(ephoneSChat, unrecognizedKeys.map { it.removePrefix("root.") })
    }

    /**
     * 【新增】通过流式解析处理大型JSON文件，避免内存溢出。
     * 注意：此版本为了性能和稳定性，暂时禁用了“未识别字段”的检测功能。
     */
    fun fromJson(inputStream: InputStream): Pair<EPhoneSChat, List<String>> {
        val gson = Gson()
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val ephoneSChat = gson.fromJson<EPhoneSChat>(reader, EPhoneSChat::class.java)
        // 在流式解析中，难以高效地检测未识别字段，因此暂时返回一个空列表。
        return Pair(ephoneSChat, emptyList())
    }

    /**
     * 将 EPhoneSChat DTO 转换为 PersonProfile
     *
     * @param ephoneSChat 从 JSON 解析的 DTO。
     * @return 转换后的 PersonProfile 对象。
     */
    fun toPersonProfile(ephoneSChat: EPhoneSChat): PersonProfile {
        val chatData = ephoneSChat.chatData
        val settings = chatData.settings

        // 注意：这里仅映射了QqContact的核心字段，其他关联数据（如聊天记录）需要分开处理
        return PersonProfile(
            id = chatData.id,
            remarkName = chatData.name,
            realName = chatData.originalName,
            persona = settings.aiPersona,
            avatarUri = settings.aiAvatar,
            backgroundUri = settings.background,
            chatBackgroundUri = settings.chatBackground,
            selectedPhotos = settings.selectedPhotos ?: emptyList(),
            statusText = chatData.status.text,
            isBusy = chatData.status.isBusy,
            isPinned = chatData.isPinned,
            unreadMessageCount = chatData.unreadCount,
            autoSummaryEnabled = settings.enableAutoMemory,
            summaryInterval = settings.autoMemoryInterval,
            lastSummaryTimestamp = chatData.lastMemorySummaryTimestamp,
            offlineModeEnabled = settings.isOfflineMode,
            timeAwarenessEnabled = settings.enableTimePerception,
            backgroundActivityEnabled = settings.enableBackgroundActivity,
            actionCooldownMinutes = settings.actionCooldownMinutes,
            lastBackgroundActionTimestamp = settings.lastBackgroundActionTimestamp,
            injectThoughts = settings.injectLatestThought,
            naiPromptSource = settings.naiPromptSource ?: "system",
            naiPositivePrompt = settings.naiPositivePrompt,
            naiNegativePrompt = settings.naiNegativePrompt,
            shortTermMemoryLimit = settings.shortTermMemoryLimit ?: 20,
            attachMemoryLimit = settings.attachMemoryLimit ?: 10,
            privacyModeEnabled = settings.privacyModeEnabled ?: false,
            ttsVoiceId = settings.ttsVoiceId,
            voiceDescription = settings.voiceDescription,
            gender = chatData.gender,
            age = chatData.age,
            birthday = chatData.birthday,
            zodiacSign = chatData.zodiacSign,
            location = chatData.location,
            companyOrSchool = chatData.companyOrSchool,
            profession = chatData.profession,
            // description = chatData.description, // Not in PersonProfile constructor
            signature = chatData.signature,
            nicknameForUser = chatData.nicknameForUser,
            group = chatData.group,
            // 为旧数据提供默认的 timeSensitivityConfig
            timeSensitivityConfig = com.susking.ephone_s.aidata.domain.model.TimeSensitivityConfig()
        )
    }

    /**
     * 将导入的 HistoryMessage DTO 转换为 App 内部的 ChatMessage 模型。
     */
    fun fromHistoryMessage(historyMessage: HistoryMessage, contactId: String): ChatMessage {
        val role = when (historyMessage.role) {
            "user", "assistant", "system", "error" -> historyMessage.role
            else -> "assistant" // 对未知或null的role，默认为assistant
        }

        var text: String? = null
        var imageUrl: String? = null
        var stickerUrl: String? = null
        var stickerName: String? = null
        var productInfo: String? = null
        var amount: Double? = null
        var status: String? = null
        var greeting: String? = null
        var senderName: String? = historyMessage.senderName
        var recipientName: String? = null
        var notes: String? = null
        
        // 新增字段
        var giftName: String? = historyMessage.giftName
        var giftValue: Double? = historyMessage.giftValue
        var giftNote: String? = historyMessage.giftNote
        var giftImageUrl: String? = historyMessage.giftImageUrl
        var offlineLocation: String? = historyMessage.offlineLocation
        var offlineReason: String? = historyMessage.offlineReason
        var quotedMessage: QuotedMessage? = historyMessage.quotedMessage?.let {
            QuotedMessage(
                messageId = it.messageId,
                senderName = it.senderName,
                content = it.content
            )
        }
        var isRecalled: Boolean = historyMessage.isRecalled ?: false
        var recalledContent: String? = historyMessage.recalledContent
        var recallTimestamp: Long? = historyMessage.recallTimestamp

        var messageType = historyMessage.type
        // 如果 type 字段缺失，则根据 content 内容进行推断
        if (messageType == null) {
            when (val content = historyMessage.content) {
                is String -> {
                    if (content.startsWith("http") && (content.endsWith(".jpg") || content.endsWith(".png") || content.endsWith(".gif"))) {
                        messageType = "sticker"
                    } else {
                        messageType = "text"
                    }
                }
                is List<*> -> {
                    val firstPart = content.firstOrNull()
                    if (firstPart is Map<*, *> && firstPart["type"] == "image_url") {
                        messageType = "image_url"
                    }
                }
            }
        }
        messageType = messageType ?: "text"


        when (messageType) {
            "text", "offline_text", "waimai_response", "system", "video_call_record", "pat_message" -> {
                text = historyMessage.content as? String
            }
            "image", "image_url" -> {
                messageType = "image_url" // 统一类型
                // content 应该是一个 List<HistoryContentPart>
                if (historyMessage.content is List<*>) {
                    val firstPart = (historyMessage.content as List<*>).firstOrNull()
                    if (firstPart is Map<*, *>) {
                        val imageUrlMap = firstPart["image_url"] as? Map<*, *>
                        imageUrl = imageUrlMap?.get("url") as? String
                    }
                } else if (historyMessage.content is String) {
                    // 兼容旧的直接URL格式
                    imageUrl = historyMessage.content
                }

                // 【健壮性修复】如果 content 中没有找到 URL，则尝试从顶层的 imageUrl 字段获取
                if (imageUrl.isNullOrBlank() && !historyMessage.imageUrl.isNullOrBlank()) {
                    imageUrl = historyMessage.imageUrl
                }

                // 【关键修复】为所有图片消息设置文本占位符，避免显示"[消息内容为空]"
                if (!imageUrl.isNullOrBlank()) {
                    text = "[图片]"
                }
            }
            "naiimag", "ai_image" -> {
                messageType = "naiimag" // 统一类型
                imageUrl = historyMessage.imageUrl
                text = historyMessage.prompt
            }
            "sticker" -> {
                stickerUrl = historyMessage.content as? String
                stickerName = historyMessage.meaning
            }
            "location_share" -> {
                text = historyMessage.content as? String
            }
            "transfer" -> {
                amount = historyMessage.amount
                notes = historyMessage.notes
                status = historyMessage.status
                text = historyMessage.notes ?: "转账"
            }
            "accept_transfer" -> {
                text = "已接受转账"
            }
            "decline_transfer" -> {
                text = "已拒绝转账"
            }
            "waimai_request" -> {
                productInfo = historyMessage.productInfo
                amount = historyMessage.amount
                status = historyMessage.status
                text = historyMessage.productInfo
            }
            "waimai_order" -> {
                greeting = historyMessage.greeting
                recipientName = historyMessage.recipientName
                productInfo = historyMessage.productInfo
                amount = historyMessage.amount
                status = historyMessage.status
                text = historyMessage.productInfo
                if (historyMessage.senderName != null) {
                    senderName = historyMessage.senderName
                }
            }
            "gift" -> {
                text = historyMessage.giftNote ?: "收到礼物"
            }
            "offline_request" -> {
                text = "申请线下见面"
            }
            "voice_message" -> {
                text = historyMessage.content as? String
            }
            "quote_reply" -> {
                text = historyMessage.replyContent
                // 【修复】提取引用消息信息
                quotedMessage = historyMessage.quotedMessage?.let {
                    QuotedMessage(
                        messageId = it.messageId,
                        senderName = it.senderName,
                        content = it.content
                    )
                }
            }
            "send_and_recall" -> {
                text = historyMessage.content as? String
                // 【修复】send_and_recall 类型的消息本质上就是已撤回的，无条件设置为 true
                isRecalled = true
                recalledContent = historyMessage.recalledContent ?: historyMessage.content as? String
                recallTimestamp = historyMessage.recallTimestamp ?: historyMessage.timestamp
            }
            "video_call_request" -> {
                text = "发起视频通话"
            }
            "video_call_response" -> {
                // 导入decision和reason字段到ChatMessage
                text = when (historyMessage.decision) {
                    "accept" -> "接受视频通话"
                    "reject" -> if (!historyMessage.reason.isNullOrBlank()) {
                        "拒绝视频通话：${historyMessage.reason}"
                    } else {
                        "拒绝视频通话"
                    }
                    else -> historyMessage.content as? String ?: "视频通话响应"
                }
                // decision和reason字段现在会被保存到ChatMessage中
            }
            "end_call" -> {
                text = "结束通话"
            }
            "pat_user" -> {
                text = "戳了戳你"
            }
            "share_link" -> {
                text = historyMessage.title ?: "分享链接"
            }
            "qzone_post" -> {
                // 导入QQ空间相关字段到ChatMessage
                // 生成显示文本，同时保留原始字段数据
                text = when (historyMessage.postType) {
                    "shuoshuo" -> historyMessage.content as? String
                    "text_image" -> {
                        val publicPart = historyMessage.publicText ?: "发布了图片动态"
                        val hiddenPart = if (!historyMessage.hiddenContent.isNullOrBlank()) {
                            "\n[仅自己可见：${historyMessage.hiddenContent}]"
                        } else ""
                        publicPart + hiddenPart
                    }
                    "naiimag" -> {
                        val publicPart = historyMessage.publicText ?: "发布了AI图片"
                        val hiddenPart = if (!historyMessage.hiddenContent.isNullOrBlank()) {
                            "\n[仅自己可见：${historyMessage.hiddenContent}]"
                        } else ""
                        publicPart + hiddenPart
                    }
                    else -> "发布了动态"
                }
                // postType, publicText, hiddenContent, postId字段现在会被保存到ChatMessage中
            }
            "repost" -> {
                text = "转发了动态"
            }
            "qzone_comment" -> {
                // 导入评论相关字段到ChatMessage
                text = if (!historyMessage.commentText.isNullOrBlank()) {
                    if (!historyMessage.replyTo.isNullOrBlank()) {
                        "回复 ${historyMessage.replyTo}：${historyMessage.commentText}"
                    } else {
                        historyMessage.commentText
                    }
                } else {
                    "发表了评论"
                }
                // commentText和replyTo字段现在会被保存到ChatMessage中
            }
            "qzone_like" -> {
                text = "点赞了动态"
            }
            "qzone_delete_post" -> {
                text = "删除了动态"
            }
            "update_status" -> {
                text = historyMessage.statusText ?: "更新了状态"
            }
            "change_remark_name" -> {
                text = "修改了自己的昵称"
            }
            "change_user_nickname" -> {
                text = "修改了你的昵称"
            }
            "change_avatar" -> {
                text = "更换了头像"
            }
            "change_user_avatar" -> {
                text = "更换了你的头像"
            }
            "friend_request_response" -> {
                text = if (historyMessage.decision == "accept") "接受了好友申请" else "拒绝了好友申请"
            }
            "block_user" -> {
                text = "拉黑了你"
            }
            "create_memory" -> {
                text = "创建了回忆"
            }
            "create_countdown" -> {
                text = "创建了倒计时"
            }
            "change_music" -> {
                text = "切换了歌曲"
            }
            "approve_shopping_access" -> {
                text = "同意查看购物记录"
            }
            "reject_shopping_access" -> {
                text = "拒绝查看购物记录"
            }
            "thought_chain" -> {
                text = historyMessage.analysis ?: "思考中..."
            }
            "update_thoughts" -> {
                text = historyMessage.heartfeltVoice ?: "更新了内心独白"
            }
        }

        return ChatMessage(
            contactId = contactId,
            type = if (role == "system" && historyMessage.isHidden != true) "pat_message" else messageType,
            content = text,
            timestamp = historyMessage.timestamp,
            role = role,
            imageUrl = imageUrl,
            stickerUrl = stickerUrl,
            stickerName = stickerName,
            voiceAudioPath = historyMessage.voiceAudioPath,
            voiceDurationMillis = historyMessage.voiceDurationMillis,
            ttsGenerationStatus = historyMessage.ttsGenerationStatus,
            ttsModelId = historyMessage.ttsModelId,
            ttsVoiceId = historyMessage.ttsVoiceId,
            ttsGeneratedAt = historyMessage.ttsGeneratedAt,
            ttsErrorMessage = historyMessage.ttsErrorMessage,
            ttsIsStreaming = historyMessage.ttsIsStreaming,
            productInfo = productInfo,
            amount = amount,
            status = status,
            greeting = greeting,
            senderName = senderName,
            recipientName = recipientName,
            notes = notes,
            giftName = giftName,
            giftValue = giftValue,
            giftNote = giftNote,
            giftImageUrl = giftImageUrl,
            offlineLocation = offlineLocation,
            offlineReason = offlineReason,
            quotedMessage = quotedMessage,
            isRecalled = isRecalled,
            recalledContent = recalledContent,
            recallTimestamp = recallTimestamp,
            isHidden = historyMessage.isHidden ?: false,
            hasBeenSeenByAi = historyMessage.hasBeenSeenByAi ?: true
        )
    }

    // --- 私有辅助转换方法 ---

    private fun toHistoryMessage(chatMessage: ChatMessage, contact: PersonProfile): HistoryMessage {
        val role = chatMessage.role
        var senderName = if (chatMessage.role == "user") null else contact.remarkName

        var messageType: String? = chatMessage.type
        var messageContent: Any? = null
        var messageMeaning: String? = null
        var messageImageUrl: String? = null
        var messagePrompt: String? = null
        var messageFullPrompt: String? = null
        var messageProductInfo: String? = null
        var messageAmount: Double? = null
        var messageGreeting: String? = null
        var messageRecipientName: String? = null
        var messageStatus: String? = null
        var messageNotes: String? = null
        
        // 新增字段
        var messageGiftName: String? = null
        var messageGiftValue: Double? = null
        var messageGiftNote: String? = null
        var messageGiftImageUrl: String? = null
        var messageOfflineLocation: String? = null
        var messageOfflineReason: String? = null
        var messageQuotedMessage: com.susking.ephone_s.aidata.domain.model.import_export.QuotedMessageData? = null
        var messageIsRecalled: Boolean? = if (chatMessage.isRecalled) true else null
        var messageRecalledContent: String? = chatMessage.recalledContent
        var messageRecallTimestamp: Long? = chatMessage.recallTimestamp

        // 修复：优先判断用户发送的图片消息，避免其被错误地归类为文本
        if (chatMessage.role == "user" && !chatMessage.imageUrl.isNullOrBlank()) {
            messageType = "image_url"
            messageContent = null
            messageImageUrl = chatMessage.imageUrl
        } else {
            when (chatMessage.type) {
                "text", "offline_text", "waimai_response", "system", "video_call_record", "pat_message" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content ?: ""
                }
                "image", "image_url" -> {
                    messageType = "image_url"
                    messageContent = null
                    messageImageUrl = chatMessage.imageUrl ?: ""
                }
                "naiimag", "ai_image" -> {
                    messageType = "naiimag"
                    messageImageUrl = chatMessage.imageUrl
                    messagePrompt = chatMessage.content
                    messageContent = null
                }
                "sticker" -> {
                    messageType = "sticker"
                    messageMeaning = chatMessage.stickerName
                    messageContent = chatMessage.stickerUrl ?: ""
                }
                "location_share" -> {
                    messageType = "location_share"
                    messageContent = chatMessage.content
                }
                "transfer" -> {
                    messageType = "transfer"
                    messageContent = null
                    messageAmount = chatMessage.amount
                    messageNotes = chatMessage.notes
                    messageStatus = chatMessage.status
                }
                "accept_transfer", "decline_transfer" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content
                }
                "waimai_request" -> {
                    messageType = "waimai_request"
                    messageContent = null
                    messageProductInfo = chatMessage.productInfo
                    messageAmount = chatMessage.amount
                    messageStatus = chatMessage.status
                }
                "waimai_order" -> {
                    messageType = "waimai_order"
                    messageContent = null
                    messageGreeting = chatMessage.greeting
                    messageRecipientName = chatMessage.recipientName
                    messageProductInfo = chatMessage.productInfo
                    messageAmount = chatMessage.amount
                    messageStatus = chatMessage.status
                    senderName = chatMessage.senderName
                }
                "gift" -> {
                    messageType = "gift"
                    messageContent = chatMessage.content
                    messageGiftName = chatMessage.giftName
                    messageGiftValue = chatMessage.giftValue
                    messageGiftNote = chatMessage.giftNote
                    messageGiftImageUrl = chatMessage.giftImageUrl
                }
                "offline_request" -> {
                    messageType = "offline_request"
                    messageContent = chatMessage.content
                    messageOfflineLocation = chatMessage.offlineLocation
                    messageOfflineReason = chatMessage.offlineReason
                }
                "voice_message" -> {
                    messageType = "voice_message"
                    messageContent = chatMessage.content
                }
                "quote_reply" -> {
                    messageType = "quote_reply"
                    messageContent = chatMessage.content
                    if (chatMessage.quotedMessage != null) {
                        messageQuotedMessage = com.susking.ephone_s.aidata.domain.model.import_export.QuotedMessageData(
                            messageId = chatMessage.quotedMessage.messageId,
                            senderName = chatMessage.quotedMessage.senderName,
                            content = chatMessage.quotedMessage.content
                        )
                    }
                }
                "send_and_recall" -> {
                    messageType = "send_and_recall"
                    messageContent = chatMessage.content
                    // 【修复】确保导出时 isRecalled 为 true，即使数据库中的值为 false
                    messageIsRecalled = true
                    messageRecalledContent = chatMessage.recalledContent ?: chatMessage.content
                    messageRecallTimestamp = chatMessage.recallTimestamp
                }
                "video_call_request", "end_call" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content
                }
                "video_call_response" -> {
                    messageType = "video_call_response"
                    messageContent = chatMessage.content
                    // 注意：ChatMessage中没有decision和reason字段
                    // 这些字段在导出时会为null，需要在HistoryMessage中补充
                }
                "pat_user" -> {
                    messageType = "pat_user"
                    messageContent = chatMessage.content
                }
                "share_link" -> {
                    messageType = "share_link"
                    messageContent = chatMessage.content
                }
                "qzone_post", "repost", "qzone_comment", "qzone_like", "qzone_delete_post" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content
                    // 注意：ChatMessage中没有QQ空间的特定字段
                    // 这些字段在导出时会为null，需要在HistoryMessage中补充
                }
                "update_status", "change_remark_name", "change_user_nickname" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content
                }
                "change_avatar", "change_user_avatar" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content
                }
                "friend_request_response", "block_user" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content
                }
                "create_memory", "create_countdown", "change_music" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content
                }
                "approve_shopping_access", "reject_shopping_access" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content
                }
                "thought_chain", "update_thoughts" -> {
                    messageType = chatMessage.type
                    messageContent = chatMessage.content
                }
                else -> {
                    messageType = "text"
                    messageContent = chatMessage.content ?: ""
                }
            }
        }

        return HistoryMessage(
            role = role,
            type = messageType,
            content = messageContent,
            timestamp = chatMessage.timestamp,
            senderName = senderName,
            recalledData = null,
            displayContent = null,
            meaning = messageMeaning,
            imageUrl = messageImageUrl,
            prompt = messagePrompt,
            fullPrompt = messageFullPrompt,
            isHidden = if (chatMessage.isHidden) true else null,
            productInfo = messageProductInfo,
            amount = messageAmount,
            greeting = messageGreeting,
            recipientName = messageRecipientName,
            status = messageStatus,
            notes = messageNotes,
            giftName = messageGiftName,
            giftValue = messageGiftValue,
            giftNote = messageGiftNote,
            giftImageUrl = messageGiftImageUrl,
            offlineLocation = messageOfflineLocation,
            offlineReason = messageOfflineReason,
            quotedMessage = messageQuotedMessage,
            isRecalled = messageIsRecalled,
            recalledContent = messageRecalledContent,
            recallTimestamp = messageRecallTimestamp,
            voiceAudioPath = chatMessage.voiceAudioPath,
            voiceDurationMillis = chatMessage.voiceDurationMillis,
            ttsGenerationStatus = chatMessage.ttsGenerationStatus,
            ttsModelId = chatMessage.ttsModelId,
            ttsVoiceId = chatMessage.ttsVoiceId,
            ttsGeneratedAt = chatMessage.ttsGeneratedAt,
            ttsErrorMessage = chatMessage.ttsErrorMessage,
            ttsIsStreaming = chatMessage.ttsIsStreaming,
            hasBeenSeenByAi = chatMessage.hasBeenSeenByAi
        )
    }

    private fun toLongTermMemoryEntry(longTermMemory: LongTermMemory): LongTermMemoryEntry {
        return LongTermMemoryEntry(
            content = longTermMemory.memoryText,
            timestamp = longTermMemory.timestamp,
            source = "unknown" // 默认值
        )
    }

    /**
     * 将heartbeats和jottings合并为thoughtsHistory
     * 按时间戳排序，确保时间顺序正确
     */
    private fun toThoughtsHistory(
        heartbeats: List<com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity>,
        jottings: List<JottingEntity>
    ): List<ThoughtsHistoryEntry> {
        // 创建所有条目的列表
        val allEntries = mutableListOf<ThoughtsHistoryEntry>()
        
        // 添加heartbeats（内心独白）
        heartbeats.forEach { heartbeat ->
            allEntries.add(ThoughtsHistoryEntry(
                heartfeltVoice = heartbeat.content,
                randomJottings = null,
                timestamp = heartbeat.timestamp
            ))
        }
        
        // 添加jottings（随机散记）
        jottings.forEach { jotting ->
            allEntries.add(ThoughtsHistoryEntry(
                heartfeltVoice = null,
                randomJottings = jotting.content,
                timestamp = jotting.timestamp
            ))
        }
        
        // 按时间戳排序
        return allEntries.sortedBy { it.timestamp }
    }
}