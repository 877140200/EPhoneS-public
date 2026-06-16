package com.susking.ephone_s.aidata.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 这是一个密封类（sealed class），用于表示AI在对话中可以执行的所有可能行动。
 * 它的结构严格遵循“触发AI响应的核心逻辑”文件中定义的JSON指令集。
 *
 * @property type 每个行动都必须有的类型标识符。
 */
sealed class AiAction {
    abstract val type: String

    /**
     * AI的思考过程，总是在行动列表的第一个。
     * @property analysis 对用户输入的分析。
     * @property strategy 本轮回复的总体策略。
     * @property characterThoughts 角色的内心活动。
     */
    data class ThoughtChain(
        override val type: String = "thought_chain",
        val analysis: String?,
        val strategy: String?,
        @SerializedName("character_thoughts") val characterThoughts: Map<String, String>?
    ) : AiAction()

    /**
     * AI在对话后的内心反思，总是在行动列表的最后一个。
     * @property heartfeltVoice 一句话核心心声。
     * @property randomJottings 50字以上的详细散记。
     */
    data class UpdateThoughts(
        override val type: String = "update_thoughts",
        @SerializedName("heartfelt_voice") val heartfeltVoice: String?,
        @SerializedName("random_jottings") val randomJottings: String?
    ) : AiAction()

    /**
     * 单个语义账本字段的维护动作。
     *
     * action 支持 keep、append、update、archive、prune。
     * keep 保持现有内容，append 去重追加，update 稳定覆盖，archive 将字段内容降级保存在历史线索中，prune 只裁剪低价值临时内容。
     */
    data class SemanticStateFieldAction(
        val action: String?,
        val content: String?
    )

    /**
     * 维护联系人分层语义上下文账本。
     *
     * 该动作只更新每个联系人的召回线索缓存，不写入向量库，也不作为长期记忆对象保存。
     * 已结束但仍有召回价值的话题应进入 historicalRecallAnchors 或 resolvedEventAnchors，而不是被直接删除。
     */
    data class UpdateSemanticState(
        override val type: String = "update_semantic_state",
        val activeSemanticContext: SemanticStateFieldAction?,
        val historicalRecallAnchors: SemanticStateFieldAction?,
        val resolvedEventAnchors: SemanticStateFieldAction?,
        val semanticKeywords: SemanticStateFieldAction?,
        val lifecycleNotes: SemanticStateFieldAction?,
        val confidenceScore: Float? = null
    ) : AiAction()

    /**
     * 发送普通文本消息。
     * @property content 消息的文本内容。
     */
    data class Text(
        override val type: String = "text",
        val content: String?
    ) : AiAction()

    /**
     * 错误消息，用于显示API请求失败、超时等错误信息。
     * 这类消息只显示给用户，不会作为上下文发送给模型。
     * @property content 错误信息内容。
     */
    data class ErrorMessage(
        override val type: String = "error",
        val content: String?
    ) : AiAction()

    /**
     * 发送表情。
     * @property meaning 表情的含义，用于从表情库中查找。
     */
    data class Sticker(
        override val type: String = "sticker",
        val meaning: String?
    ) : AiAction()
    
    /**
     * 发布动态（说说）。
     * 支持三种类型：
     * 1. shuoshuo: 纯文本说说，使用content字段
     * 2. text_image: 带NovelAI图片的动态，使用publicText、hiddenContent、prompt字段
     *
     * @property postType 动态类型："shuoshuo"或"text_image"，默认"shuoshuo"
     * @property content 纯文本说说的内容(postType="shuoshuo"时使用)
     * @property publicText 图片动态的配文(postType="text_image"时使用，可选)
     * @property hiddenContent 图片描述，可以是String或List<String>(postType="text_image"时使用)
     * @property prompt NovelAI提示词，可以是String或List<String>(postType="text_image"时使用)
     */
    data class QzonePost(
        override val type: String = "qzone_post",
        val postType: String? = "shuoshuo",
        val content: String? = null,
        val publicText: String? = null,
        val hiddenContent: Any? = null,  // String 或 List<String>
        val prompt: Any? = null  // String 或 List<String>
    ) : AiAction()

    /**
     * 发送一张 NovelAI 生成的图片。
     * @param prompt 用于生成图片的提示词。
     */
    data class NaiImage(
        override val type: String = "naiimag",
        val prompt: String?
    ) : AiAction()

    data class QzoneLike(
        override val type: String = "qzone_like",
        @SerializedName(value = "post_id", alternate = ["postId"]) val postId: Int
    ) : AiAction()

    data class QzoneComment(
        override val type: String = "qzone_comment",
        @SerializedName(value = "post_id", alternate = ["postId"]) val postId: Int,
        val name: String?,
        @SerializedName(value = "content", alternate = ["commentText"]) val commentText: String?,
        @SerializedName(value = "sticker_meaning", alternate = ["stickerMeaning"]) val stickerMeaning: String? = null
    ) : AiAction()

    data class QzoneDeletePost(
        override val type: String = "qzone_delete_post",
        @SerializedName(value = "post_id", alternate = ["postId"]) val postId: Int
    ) : AiAction()

    data class QzoneSharePost(
        override val type: String = "qzone_share_post",
        @SerializedName(value = "post_id", alternate = ["postId"]) val postId: Int
    ) : AiAction()

    /**
     * 更新角色的在线状态。
     * @param statusText 新的状态文本，例如“正在输入...”、“摸鱼中”。
     * @param isBusy AI是否正忙于某项活动，这会影响其状态显示。
     */
    data class UpdateStatus(
        override val type: String = "update_status",
        @SerializedName("status_text") val statusText: String?,
        @SerializedName("is_busy") val isBusy: Boolean?
    ) : AiAction()

    /**
     * AI主动为用户下单外卖。
     * @param productInfo 商品的描述信息。
     * @param amount 商品金额。
     * @param greeting 一句完整的问候语，例如“我为你点了杯奶茶”。
     * @param senderName 下单人的昵称。
     * @param recipientName 接收人的昵称，如果为空则默认为是用户本人。
     */
    data class WaimaiOrder(
        override val type: String = "waimai_order",
        @SerializedName("productInfo") val productInfo: String?,
        @SerializedName("amount") val amount: Double?,
        @SerializedName("greeting") val greeting: String?,
        @SerializedName("senderName") val senderName: String?,
        @SerializedName("recipientName") val recipientName: String?
    ) : AiAction()

    /**
     * AI发起外卖请求，让用户代为支付。
     * @param productInfo 商品的描述信息。
     * @param amount 商品金额。
     */
    data class WaimaiRequest(
        override val type: String = "waimai_request",
        @SerializedName("productInfo") val productInfo: String?,
        val amount: Double?
    ) : AiAction()

    /**
     * AI主动发起转账。
     * @param amount 转账金额。
     * @param note 转账备注。
     */
    data class Transfer(
        override val type: String = "transfer",
        val amount: Double?,
        val note: String?
    ) : AiAction()

    /**
     * AI接受转账。
     * @param forTimestamp 对应原始转账消息的时间戳。
     */
    data class AcceptTransfer(
        override val type: String = "accept_transfer",
        @SerializedName("for_timestamp") val forTimestamp: Long
    ) : AiAction()

    /**
     * AI拒绝或退还转账。
     * @param forTimestamp 对应原始转账消息的时间戳。
     */
    data class DeclineTransfer(
        override val type: String = "decline_transfer",
        @SerializedName("for_timestamp") val forTimestamp: Long
    ) : AiAction()

    /**
     * AI分享一个位置。
     * @property content 位置的名称或描述。
     */
    data class LocationShare(
        override val type: String = "location_share",
        val content: String?
    ) : AiAction()

    /**
     * AI 主动发起视频通话请求。
     */
    data class VideoCallRequest(
        override val type: String = "video_call_request"
    ) : AiAction()

    /**
     * AI同意接听视频通话。
     */
    data class AcceptCall(
        override val type: String = "accept_call"
    ) : AiAction()

    /**
     * AI拒绝接听视频通话。
     * @property reason 拒绝通话的理由，将显示给用户。
     */
    data class DeclineCall(
        override val type: String = "decline_call",
        val reason: String?
    ) : AiAction()
 
    /**
     * AI主动结束正在进行的视频通话。
     * @property reason 结束通话的理由，可以用于生成通话结束后的消息。
     */
    data class EndCall(
        override val type: String = "end_call",
        val reason: String?
    ) : AiAction()

    /**
     * AI修改对用户的备注。
     * @param newName 新的备注名称。
     */
    data class ChangeUserNickname(
        override val type: String = "change_user_nickname",
        @SerializedName("new_name") val newName: String?
    ) : AiAction()

    /**
     * AI引用回复消息。
     * @param targetTimestamp 被引用消息的时间戳。
     * @param replyContent 回复的内容。
     */
    data class QuoteReply(
        override val type: String = "quote_reply",
        @SerializedName("target_timestamp") val targetTimestamp: Long,
        @SerializedName("reply_content") val replyContent: String?
    ) : AiAction()

    /**
     * AI同意用户查看ta的购物app。
     */
    data class ApproveShoppingAccess(
        override val type: String = "approve_shopping_access"
    ) : AiAction()

    /**
     * AI拒绝用户查看ta的购物app。
     * @property reason 拒绝的理由。
     */
    data class RejectShoppingAccess(
        override val type: String = "reject_shopping_access",
        val reason: String?
    ) : AiAction()

    /**
     * AI送礼物给用户。
     * @property giftName 礼物名称
     * @property giftValue 礼物价值
     * @property giftNote 送礼物的原因/备注
     * @property imagePrompt 用于生成礼物图片的英文提示词
     */
    data class Gift(
        override val type: String = "gift",
        val giftName: String?,
        val giftValue: Double?,
        val giftNote: String?,
        @SerializedName("image_prompt") val imagePrompt: String?
    ) : AiAction()

    /**
     * AI发送语音消息。
     * @property content 语音消息的文字内容，当用户点击时显示
     */
    data class VoiceMessage(
        override val type: String = "voice_message",
        val content: String?
    ) : AiAction()

    /**
     * AI创建倒计时。
     * @property title 倒计时标题
     * @property date 倒计时目标日期时间（ISO 8601格式字符串）
     */
    data class CreateCountdown(
        override val type: String = "create_countdown",
        val title: String?,
        val date: String?
    ) : AiAction()

    /**
     * AI创建回忆。
     * @property description 回忆的详细描述
     */
    data class CreateMemory(
        override val type: String = "create_memory",
        val description: String?
    ) : AiAction()

    /**
     * AI戳一戳用户。
     * @property suffix 戳的部位描述，例如"的小脑袋并顺了顺毛"
     */
    data class PatUser(
        override val type: String = "pat_user",
        val suffix: String?
    ) : AiAction()

    /**
     * AI发送一条消息后立即撤回。
     * 该消息会先显示为普通接收气泡，0.5-1.5秒后变成系统提醒"对方撤回了一条消息"。
     * 用户可以点击系统提醒查看原始内容。
     * @property content 消息的原始内容
     */
    data class SendAndRecall(
        override val type: String = "send_and_recall",
        val content: String?
    ) : AiAction()

    /**
     * AI申请线下见面。
     * @property location 线下见面的地点
     * @property reason 线下见面的理由
     */
    data class OfflineRequest(
        override val type: String = "offline_request",
        val location: String?,
        val reason: String?
    ) : AiAction()

    /**
     * AI对图片进行分析并生成描述。
     * 当用户发送新图片时，AI会生成此动作来描述图片内容。
     * @property messageId 图片消息的ID
     * @property description 图片的详细描述
     */
    data class ImageAnalysis(
        override val type: String = "image_analysis",
        @SerializedName("messageId") val messageId: String?,
        val description: String?
    ) : AiAction()

     /**
       * 未知或不支持的行动类型，用于安全地处理AI返回的未知指令。
       * @property rawContent 原始JSON内容，用于显示给用户
       */
    data class Unknown(
        override val type: String = "unknown",
        val rawContent: String? = null
    ) : AiAction()
}