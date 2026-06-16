package com.susking.ephone_s.aidata.domain.model.import_export

import com.google.gson.annotations.SerializedName

// 根对象
data class EPhoneSChat(
    @SerializedName("type") val type: String,
    @SerializedName("version") val version: Int,
    @SerializedName("chatData") val chatData: ChatData
)

// ChatData 包含所有聊天相关信息
data class ChatData(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("originalName") val originalName: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("signature") val signature: String? = null,
    @SerializedName("nicknameForUser") val nicknameForUser: String? = null,
    // 将个人信息字段组织在一起
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("zodiacSign") val zodiacSign: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("companyOrSchool") val companyOrSchool: String? = null,
    @SerializedName("profession") val profession: String? = null,

    @SerializedName("group") val group: String?,
    @SerializedName("isGroup") val isGroup: Boolean,
    @SerializedName("relationship") val relationship: Relationship,
    @SerializedName("status") val status: Status,
    @SerializedName("") val settings: Settings,
    @SerializedName("history") val history: List<HistoryMessage>,
    @SerializedName("longTermMemory") val longTermMemory: List<LongTermMemoryEntry>,
    @SerializedName("unreadCount") val unreadCount: Int,
    @SerializedName("isPinned") val isPinned: Boolean,
    @SerializedName("lastMemorySummaryTimestamp") val lastMemorySummaryTimestamp: Long?,
    @SerializedName("heartfeltVoice") val heartfeltVoice: String?,
    @SerializedName("randomJottings") val randomJottings: String?,
    @SerializedName("thoughtsHistory") val thoughtsHistory: List<ThoughtsHistoryEntry>
)

// 关系
data class Relationship(
    @SerializedName("status") val status: String,
    @SerializedName("applicationReason") val applicationReason: String?,
    @SerializedName("blockedTimestamp") val blockedTimestamp: Long?
)

// 状态
data class Status(
    @SerializedName("text") val text: String?,
    @SerializedName("lastUpdate") val lastUpdate: Long?,
    @SerializedName("isBusy") val isBusy: Boolean
)

// 设置
data class Settings(
    @SerializedName("aiPersona") val aiPersona: String,
    @SerializedName("maxMemory") val maxMemory: Int,
    @SerializedName("aiAvatar") val aiAvatar: String,
    @SerializedName("background") val background: String?,
    @SerializedName("chatBackground") val chatBackground: String?,
    @SerializedName("selectedPhotos") val selectedPhotos: List<String>?,
    @SerializedName("actionCooldownMinutes") val actionCooldownMinutes: Int,
    @SerializedName("enableAutoMemory") val enableAutoMemory: Boolean,
    @SerializedName("autoMemoryInterval") val autoMemoryInterval: Int,
    @SerializedName("isOfflineMode") val isOfflineMode: Boolean,
    @SerializedName("charAlbumRefreshCount") val charAlbumRefreshCount: Int,
    @SerializedName("enableTimePerception") val enableTimePerception: Boolean,
    @SerializedName("enableBackgroundActivity") val enableBackgroundActivity: Boolean,
    @SerializedName("lastBackgroundActionTimestamp") val lastBackgroundActionTimestamp: Long? = null,
    @SerializedName("injectLatestThought") val injectLatestThought: Boolean,
    @SerializedName("offlinePresetId") val offlinePresetId: String?,
    @SerializedName("naiPromptSource") val naiPromptSource: String?,
    @SerializedName("naiPositivePrompt") val naiPositivePrompt: String?,
    @SerializedName("naiNegativePrompt") val naiNegativePrompt: String?,
    @SerializedName("shortTermMemoryLimit") val shortTermMemoryLimit: Int?,
    @SerializedName("attachMemoryLimit") val attachMemoryLimit: Int?,
    @SerializedName("privacyModeEnabled") val privacyModeEnabled: Boolean? = false,
    @SerializedName("ttsVoiceId") val ttsVoiceId: String? = null,
    @SerializedName("voiceDescription") val voiceDescription: String? = null
)

// 聊天记录消息
data class HistoryMessage(
    @SerializedName("role") val role: String,
    @SerializedName("type") val type: String?, // "recalled_message" 等
    @SerializedName("content") val content: Any?, // content可以是String或List<HistoryContentPart>
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("recalledData") val recalledData: RecalledData?,
    @SerializedName("displayContent") val displayContent: String?,
    @SerializedName("senderName") val senderName: String?,
    @SerializedName("meaning") val meaning: String?,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("prompt") val prompt: String?,
    @SerializedName("fullPrompt") val fullPrompt: String?,
    @SerializedName("isHidden") val isHidden: Boolean? = null,
    
    // 外卖、转账等消息类型的字段
    @SerializedName("productInfo") val productInfo: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("greeting") val greeting: String? = null,
    @SerializedName("recipientName") val recipientName: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("notes") val notes: String? = null,
    
    // 礼物相关字段
    @SerializedName("giftName") val giftName: String? = null,
    @SerializedName("giftValue") val giftValue: Double? = null,
    @SerializedName("giftNote") val giftNote: String? = null,
    @SerializedName("giftImageUrl") val giftImageUrl: String? = null,
    
    // 线下见面相关字段
    @SerializedName("offlineLocation") val offlineLocation: String? = null,
    @SerializedName("offlineReason") val offlineReason: String? = null,
    
    // 引用回复相关字段
    @SerializedName("targetTimestamp") val targetTimestamp: Long? = null,
    @SerializedName("replyContent") val replyContent: String? = null,
    @SerializedName("quotedMessage") val quotedMessage: QuotedMessageData? = null,
    
    // 视频通话相关字段
    @SerializedName("decision") val decision: String? = null, // "accept" or "reject"
    @SerializedName("reason") val reason: String? = null,
    
    // 戳一戳相关字段
    @SerializedName("suffix") val suffix: String? = null,
    
    // 撤回相关字段
    @SerializedName("isRecalled") val isRecalled: Boolean? = null,
    @SerializedName("recalledContent") val recalledContent: String? = null,
    @SerializedName("recallTimestamp") val recallTimestamp: Long? = null,
    
    // QQ空间相关字段
    @SerializedName("postType") val postType: String? = null, // "shuoshuo", "text_image", "naiimag"
    @SerializedName("publicText") val publicText: String? = null,
    @SerializedName("hiddenContent") val hiddenContent: String? = null,
    @SerializedName("postId") val postId: Int? = null,
    @SerializedName("commentText") val commentText: String? = null,
    @SerializedName("stickerMeaning") val stickerMeaning: String? = null,
    @SerializedName("replyTo") val replyTo: String? = null,
    
    // 状态更新相关字段
    @SerializedName("statusText") val statusText: String? = null,
    @SerializedName("isBusy") val isBusy: Boolean? = null,
    
    // 昵称/头像更改相关字段
    @SerializedName("newName") val newName: String? = null,
    @SerializedName("avatarName") val avatarName: String? = null,
    
    // 分享链接相关字段
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("sourceName") val sourceName: String? = null,
    
    // 倒计时相关字段
    @SerializedName("countdownTitle") val countdownTitle: String? = null,
    @SerializedName("countdownDate") val countdownDate: String? = null,
    
    // 回忆相关字段
    @SerializedName("memoryDescription") val memoryDescription: String? = null,
    
    // 音乐相关字段
    @SerializedName("songName") val songName: String? = null,
    
    // 思维链相关字段
    @SerializedName("analysis") val analysis: String? = null,
    @SerializedName("strategy") val strategy: String? = null,
    @SerializedName("characterThoughts") val characterThoughts: Map<String, String>? = null,
    @SerializedName("timeAwareness") val timeAwareness: String? = null,
    
    // 内心独白相关字段
    @SerializedName("heartfeltVoice") val heartfeltVoice: String? = null,
    @SerializedName("randomJottings") val randomJottings: String? = null,
    
    // 转发时间戳（用于回应类消息）
    @SerializedName("forTimestamp") val forTimestamp: Long? = null,

    // 语音文件与 TTS 元数据字段。单聊导入导出依赖这些字段恢复语音缓存。
    @SerializedName("voiceAudioPath") val voiceAudioPath: String? = null,
    @SerializedName("voiceDurationMillis") val voiceDurationMillis: Long? = null,
    @SerializedName("ttsGenerationStatus") val ttsGenerationStatus: String? = null,
    @SerializedName("ttsModelId") val ttsModelId: String? = null,
    @SerializedName("ttsVoiceId") val ttsVoiceId: String? = null,
    @SerializedName("ttsGeneratedAt") val ttsGeneratedAt: Long? = null,
    @SerializedName("ttsErrorMessage") val ttsErrorMessage: String? = null,
    @SerializedName("ttsIsStreaming") val ttsIsStreaming: Boolean? = null,

    // 自动回复可见性字段。旧导入文件缺失该字段时默认视为已被 AI 看见。
    @SerializedName("hasBeenSeenByAi") val hasBeenSeenByAi: Boolean? = true
)

// 引用消息的数据结构
data class QuotedMessageData(
    @SerializedName("messageId") val messageId: String,
    @SerializedName("senderName") val senderName: String,
    @SerializedName("content") val content: String
)

// 用于处理图片消息的ContentPart
data class HistoryContentPart(
    @SerializedName("type") val type: String,
    @SerializedName("image_url") val imageUrl: ImageUrl?
)

data class ImageUrl(
    @SerializedName("url") val url: String
)


// 撤回消息的数据
data class RecalledData(
    @SerializedName("originalType") val originalType: String,
    @SerializedName("originalContent") val originalContent: String
)

// 长期记忆条目
data class LongTermMemoryEntry(
    @SerializedName("content") val content: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("source") val source: String
)

// 思考历史条目
data class ThoughtsHistoryEntry(
    @SerializedName("heartfeltVoice") val heartfeltVoice: String?,
    @SerializedName("randomJottings") val randomJottings: String?,
    @SerializedName("timestamp") val timestamp: Long
)