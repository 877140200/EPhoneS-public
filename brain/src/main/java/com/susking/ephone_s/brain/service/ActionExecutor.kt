package com.susking.ephone_s.brain.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.BackpackItemEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.model.AiAction
import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.QuotedMessage
import com.susking.ephone_s.aidata.domain.repository.ActionRepository
import com.susking.ephone_s.aidata.domain.repository.ContactSemanticStateRepository
import com.susking.ephone_s.aidata.domain.repository.BackpackRepository
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.domain.repository.MemoriesRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.StickerRepository
import com.susking.ephone_s.aidata.domain.use_case.GenerateShoppingProductUseCase
import com.susking.ephone_s.brain.api.ActivityLogger
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * 相册服务接口 - 用于跨模块通信
 * 由 app 模块提供实现并注入
 */
interface AlbumServiceCallback {
    suspend fun addPhotoToAlbum(albumName: String, photoPath: String)
}

/**
 * 定义 ActionExecutor 执行后的返回结果。
 * @param messages 要在聊天中显示的消息列表。
 * @param incomingCallContactId 如果AI发起了视频通话，这里会包含联系人的ID。
 * @param asyncTasks 需要在后台异步执行的任务列表（如生图、耗时操作）
 */
data class ActionExecutionResult(
    val messages: List<ChatMessage>,
    val incomingCallContactId: String? = null,
    val asyncTasks: List<suspend () -> Unit> = emptyList()
)

/**
 * AI行动执行器。
 * 它的职责是解释AI的行动指令：
 * 1. 执行所有非聊天消息的副作用操作（如发动态、记录心声）。
 * 2. 将所有应显示在聊天界面上的指令（如文本、表情）转换为ChatMessage对象列表并返回。
 *
 * @param actionRepository 用于获取和更新角色信息、消息状态等
 * @param heartbeatRepository 用于存储AI的心声
 * @param jottingRepository 用于存储AI的散记
 * @param feedRepository 用于发布AI的动态
 * @param albumServiceCallback 用于保存图片到相册的回调接口
 * @param activityLogger 用于记录 AI 活动日志
 */
class ActionExecutor(
    private val actionRepository: ActionRepository,
    private val heartbeatRepository: HeartbeatRepository,
    private val jottingRepository: JottingRepository,
    private val feedRepository: FeedRepository,
    private val albumServiceCallback: AlbumServiceCallback?,
    private val stickerRepository: StickerRepository,
    private val alipayRepository: AlipayRepository,
    private val activityLogger: ActivityLogger,
    private val gson: Gson,
    private val shoppingProductRepository: ShoppingProductRepository? = null,
    private val generateShoppingProductUseCase: GenerateShoppingProductUseCase? = null,
    private val backpackRepository: BackpackRepository? = null,
    private val memoriesRepository: MemoriesRepository? = null,
    private val chatRepository: ChatRepository? = null,
    private val contactSemanticStateRepository: ContactSemanticStateRepository? = null
) {

    /**
     * 执行AI的行动指令列表。
     * @param actions 从AI响应中解析出的行动指令列表。
     * @param contactId 当前聊天的联系人ID。
     * @param aiTurnId 当前AI回复轮次的唯一ID，用于关联心声和散记。
     * @return 返回一个 ActionExecutionResult 对象，包含聊天消息和一个可选的来电事件。
     */
    suspend fun executeActions(
        context: Context,
        actions: List<AiAction>,
        contactId: String,
        aiTurnId: String,
        overrideTimestamp: Long? = null,
        sourceMessageId: String? = null // 源消息ID，用于关联心声和散记
    ): ActionExecutionResult {
        val newMessages = mutableListOf<ChatMessage>()
        val asyncTasks = mutableListOf<suspend () -> Unit>()
        var incomingCallContactId: String? = null
        val personProfile = actionRepository.getPersonProfile(contactId) ?: return ActionExecutionResult(emptyList())

        // 优先使用覆盖时间戳，否则使用当前时间。
        // 同一轮模型回复可能生成多条气泡；每条气泡必须拥有递增时间戳，避免数据库按时间重载后顺序不稳定。
        val timestamp = overrideTimestamp ?: System.currentTimeMillis()
        var messageOrderIndex: Long = 0L
        fun nextMessageTimestamp(): Long {
            val messageTimestamp: Long = timestamp + messageOrderIndex
            messageOrderIndex += 1L
            return messageTimestamp
        }
        
        // 使用timestamp作为sourceMessageId来关联心声和散记
        val actualSourceMessageId = timestamp.toString()

        for (action in actions) {
            Log.d("ActionExecutor", "处理action: type=${action.type}, action类=${action.javaClass.simpleName}")
            when (action) {
                is AiAction.ThoughtChain -> {
                    Log.d("ActionExecutor", "AI Thought Chain: Analysis=${action.analysis}, Strategy=${action.strategy}")
                }
                is AiAction.Text -> {
                    action.content?.let {
                        val message = ChatMessage(contactId = contactId, content = it, role = "assistant", timestamp = nextMessageTimestamp())
                        newMessages.add(message)
                        Log.i("ActionExecutor", "准备消息 [私信]: ${action.content}")
                    }
                }
                is AiAction.ErrorMessage -> {
                    action.content?.let {
                        val message = ChatMessage(contactId = contactId, type = "error", content = it, role = "error", timestamp = nextMessageTimestamp())
                        newMessages.add(message)
                        Log.i("ActionExecutor", "准备消息 [错误]: ${action.content}")
                    }
                }
                is AiAction.Sticker -> {
                    action.meaning?.let { stickerName ->
                        // 从仓库查找表情
                        val stickers = stickerRepository.getAllStickersSuspend()
                        val sticker = stickers.find { it.name == stickerName }
                        if (sticker != null) {
                            // 找到了表情，创建表情消息
                            val message = ChatMessage(
                                contactId = contactId,
                                type = "sticker",
                                content = null, // 表情消息没有文本
                                stickerName = sticker.name,
                                stickerUrl = sticker.url,
                                role = "assistant",
                                timestamp = nextMessageTimestamp()
                            )
                            newMessages.add(message)
                            Log.i("ActionExecutor", "准备消息 [表情]: ${sticker.name} (URL: ${sticker.url})")
                        } else {
                            // 找不到表情，作为普通文本发送
                            val stickerText = "[表情: $stickerName]"
                            val message = ChatMessage(contactId = contactId, content = stickerText, role = "assistant", timestamp = nextMessageTimestamp())
                            newMessages.add(message)
                            Log.w("ActionExecutor", "准备消息 [表情]: 找不到名为 '${stickerName}' 的表情，作为文本发送。")
                        }
                    }
                }
                is AiAction.NaiImage -> {
                    action.prompt?.let { prompt ->
                        Log.i("ActionExecutor", "执行行动 [生成NovelAI图片]: $prompt - 将异步执行")
                        
                        // 1. 创建占位消息
                        val messageId = UUID.randomUUID().toString()
                        val placeholderMessage = ChatMessage(
                            id = messageId,
                            contactId = contactId,
                            type = "naiimag",
                            content = prompt, // 保留提示词
                            imageUrl = "generating_placeholder",
                            role = "assistant",
                            timestamp = nextMessageTimestamp()
                        )
                        newMessages.add(placeholderMessage)
                        
                        // 2. 添加异步生成任务
                        asyncTasks.add {
                            val activityChainId = UUID.randomUUID().toString()
                            activityLogger.log(
                                AiActivity(
                                    activityChainId = activityChainId,
                                    description = "NovelAI 图片生成中...",
                                    prompt = prompt,
                                    rawResponse = "",
                                    timestamp = System.currentTimeMillis(),
                                    status = AiActivityStatus.PROCESSING
                                )
                            )
                            try {
                                val imageBase64 = NovelAiService.generateImage(prompt, personProfile, gson)
                                if (imageBase64 != null) {
                                    // 更新消息为真实图片
                                    if (chatRepository != null) {
                                        // 构造一个新的ChatMessage覆盖
                                        val updatedMessage = placeholderMessage.copy(
                                            imageUrl = imageBase64
                                        )
                                        chatRepository.updateMessage(updatedMessage)
                                        Log.i("ActionExecutor", "异步任务 [NovelAI图片]: 生成并更新成功")
                                        
                                        activityLogger.log(
                                            AiActivity(
                                                activityChainId = activityChainId,
                                                description = "NovelAI 图片生成成功",
                                                prompt = prompt,
                                                rawResponse = "图片URL: $imageBase64",
                                                timestamp = System.currentTimeMillis(),
                                                status = AiActivityStatus.SUCCESS
                                            )
                                        )
                                    } else {
                                        Log.e("ActionExecutor", "异步任务 [NovelAI图片]: ChatRepository未注入，无法更新消息")
                                    }
                                } else {
                                    // 更新为错误占位图
                                    if (chatRepository != null) {
                                        val errorMessage = placeholderMessage.copy(imageUrl = "error_placeholder")
                                        chatRepository.updateMessage(errorMessage)
                                    }
                                    Log.e("ActionExecutor", "异步任务 [NovelAI图片]: 生成失败 (null)")
                                    activityLogger.log(
                                        AiActivity(
                                            activityChainId = activityChainId,
                                            description = "NovelAI 图片生成失败",
                                            prompt = prompt,
                                            rawResponse = "API Key未配置或返回为空",
                                            timestamp = System.currentTimeMillis(),
                                            status = AiActivityStatus.FAILED
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                // 更新为错误占位图
                                if (chatRepository != null) {
                                    val errorMessage = placeholderMessage.copy(imageUrl = "error_placeholder")
                                    chatRepository.updateMessage(errorMessage)
                                }
                                Log.e("ActionExecutor", "异步任务 [NovelAI图片]: 生成异常", e)
                                activityLogger.log(
                                    AiActivity(
                                        activityChainId = activityChainId,
                                        description = "NovelAI 图片生成异常",
                                        prompt = prompt,
                                        rawResponse = e.message ?: "未知错误",
                                        timestamp = System.currentTimeMillis(),
                                        status = AiActivityStatus.FAILED
                                    )
                                )
                            }
                        }
                    }
                }
                is AiAction.QzonePost -> {
                    val postType = action.postType ?: "shuoshuo"
                    Log.i("ActionExecutor", "执行行动 [发动态]: postType=$postType")
                    
                    when (postType) {
                        "shuoshuo" -> {
                            // 纯文本说说
                            action.content?.let { content ->
                                feedRepository.createFeed(contactId, personProfile.realName, content)
                                val currentCount = actionRepository.getNewFeedsCount()
                                actionRepository.setNewFeedsCount(currentCount + 1)
                                Log.i("ActionExecutor", "执行行动 [发纯文本动态]: $content")
                            }
                        }
                        "text_image" -> {
                            // 带NovelAI图片的动态 - 移入异步任务
                            val publicText = action.publicText ?: ""
                            val hiddenContentList = when (val hidden = action.hiddenContent) {
                                is String -> listOf(hidden)
                                is List<*> -> hidden.filterIsInstance<String>()
                                else -> emptyList()
                            }
                            val promptList = when (val p = action.prompt) {
                                is String -> listOf(p)
                                is List<*> -> p.filterIsInstance<String>()
                                else -> emptyList()
                            }
                            
                            Log.i("ActionExecutor", "执行行动 [发图片动态]: 配文=$publicText, 图片数=${promptList.size} - 将异步执行")
                            
                            asyncTasks.add {
                                // 生成所有图片
                                val imageUrls = mutableListOf<String>()
                                promptList.forEachIndexed { index, prompt ->
                                    try {
                                        val activityChainId = UUID.randomUUID().toString()
                                        activityLogger.log(
                                            AiActivity(
                                                activityChainId = activityChainId,
                                                description = "动态图片生成中 [${index + 1}/${promptList.size}]...",
                                                prompt = prompt,
                                                rawResponse = "",
                                                timestamp = System.currentTimeMillis(),
                                                status = AiActivityStatus.PROCESSING
                                            )
                                        )
                                        
                                        val imageBase64 = NovelAiService.generateImage(prompt, personProfile, gson)
                                        if (imageBase64 != null) {
                                            imageUrls.add(imageBase64)
                                            Log.i("ActionExecutor", "异步任务 - 动态图片 [${index + 1}/${promptList.size}] 生成成功")
                                            activityLogger.log(
                                                AiActivity(
                                                    activityChainId = activityChainId,
                                                    description = "动态图片生成成功 [${index + 1}/${promptList.size}]",
                                                    prompt = prompt,
                                                    rawResponse = "图片URL: $imageBase64",
                                                    timestamp = System.currentTimeMillis(),
                                                    status = AiActivityStatus.SUCCESS
                                                )
                                            )
                                        } else {
                                            imageUrls.add("error_placeholder")
                                            Log.e("ActionExecutor", "异步任务 - 动态图片 [${index + 1}/${promptList.size}] 生成失败")
                                            activityLogger.log(
                                                AiActivity(
                                                    activityChainId = activityChainId,
                                                    description = "动态图片生成失败 [${index + 1}/${promptList.size}]",
                                                    prompt = prompt,
                                                    rawResponse = "API Key未配置或返回为空",
                                                    timestamp = System.currentTimeMillis(),
                                                    status = AiActivityStatus.FAILED
                                                )
                                            )
                                        }
                                    } catch (e: Exception) {
                                        imageUrls.add("error_placeholder")
                                        Log.e("ActionExecutor", "异步任务 - 动态图片 [${index + 1}/${promptList.size}] 生成异常", e)
                                        val activityChainId = UUID.randomUUID().toString()
                                        activityLogger.log(
                                            AiActivity(
                                                activityChainId = activityChainId,
                                                description = "动态图片生成异常 [${index + 1}/${promptList.size}]",
                                                prompt = prompt,
                                                rawResponse = e.message ?: "未知错误",
                                                timestamp = System.currentTimeMillis(),
                                                status = AiActivityStatus.FAILED
                                            )
                                        )
                                    }
                                }
                                
                                // 创建带图片的动态
                                feedRepository.createFeedWithImages(
                                    contactId = contactId,
                                    authorName = personProfile.realName,
                                    content = publicText,
                                    imageUrls = imageUrls,
                                    imagePrompts = promptList,
                                    imageDescriptions = hiddenContentList
                                )
                                val currentCount = actionRepository.getNewFeedsCount()
                                actionRepository.setNewFeedsCount(currentCount + 1)
                                Log.i("ActionExecutor", "异步任务 - 执行行动 [发图片动态完成]")
                            }
                        }
                        else -> {
                            Log.w("ActionExecutor", "未知的动态类型: $postType")
                        }
                    }
                }
                is AiAction.QzoneLike -> {
                    feedRepository.toggleLike(action.postId, contactId)
                    Log.i("ActionExecutor", "执行行动 [点赞动态]: postId=${action.postId}")
                }
                is AiAction.QzoneComment -> {
                    val commenterName = action.name ?: personProfile.realName
                    if (action.stickerMeaning != null) {
                        // 这是一条表情评论
                        val sticker = stickerRepository.getAllStickersSuspend().find { it.name == action.stickerMeaning }
                        if (sticker != null) {
                            feedRepository.addComment(
                                feedId = action.postId,
                                commenterId = personProfile.id,
                                commenterName = commenterName,
                                stickerUrl = sticker.url,
                                stickerMeaning = sticker.name
                            )
                            Log.i("ActionExecutor", "执行行动 [表情评论动态]: postId=${action.postId}, commenter=${commenterName}, sticker=${sticker.name}")
                        } else {
                            // 如果找不到表情，就当作一条文本评论 "[表情: xxx]"
                            val fallbackText = "[表情: ${action.stickerMeaning}]"
                            feedRepository.addComment(action.postId, personProfile.id, commenterName, commentText = fallbackText)
                            Log.w("ActionExecutor", "执行行动 [表情评论动态]: postId=${action.postId}, commenter=${commenterName}, sticker='${action.stickerMeaning}' not found. Fallback to text.")
                        }
                    } else if (action.commentText != null) {
                        // 这是一条文本评论
                        feedRepository.addComment(action.postId, personProfile.id, commenterName, commentText = action.commentText)
                        Log.i("ActionExecutor", "执行行动 [文本评论动态]: postId=${action.postId}, commenter=${commenterName}, content=${action.commentText}")
                    }
                }
                is AiAction.QzoneDeletePost -> {
                    feedRepository.deleteFeedById(action.postId)
                    Log.i("ActionExecutor", "执行行动 [删除动态]: postId=${action.postId}")
                }
                is AiAction.QzoneSharePost -> {
                    val originalFeed = feedRepository.getFeedById(action.postId)
                    if (originalFeed != null) {
                        feedRepository.shareFeed(originalFeed, personProfile.id, personProfile.realName)
                        Log.i("ActionExecutor", "执行行动 [转发动态]: postId=${action.postId}")
                    } else {
                        Log.w("ActionExecutor", "试图转发一个不存在的动态: postId=${action.postId}")
                    }
                }
                is AiAction.UpdateThoughts -> {
                    // 异步执行心声和散记的写入
                    asyncTasks.add {
                        // 使用当前timestamp作为源消息ID
                        action.heartfeltVoice?.let {
                            heartbeatRepository.createHeartbeat(contactId, it, aiTurnId, actualSourceMessageId)
                            Log.i("ActionExecutor", "异步任务 - 执行行动 [更新心声]: $it")
                        }
                        action.randomJottings?.let {
                            val title = "来自对话的反思"
                            jottingRepository.createJotting(contactId, title, it, aiTurnId, actualSourceMessageId)
                            Log.i("ActionExecutor", "异步任务 - 执行行动 [更新散记]: $it")
                        }
                    }
                }
                is AiAction.UpdateSemanticState -> {
                    asyncTasks.add {
                        contactSemanticStateRepository?.applySemanticStateUpdate(
                            contactId = contactId,
                            action = action,
                            sourceMessageId = actualSourceMessageId,
                            rawUpdateJson = gson.toJson(action),
                            aiTurnId = aiTurnId
                        )
                        Log.i("ActionExecutor", "异步任务 - 执行行动 [更新语义状态]: contactId=$contactId")
                    }
                }
                is AiAction.UpdateStatus -> {
                    action.statusText?.let { newStatus ->
                        // 只有当状态实际发生变化时才执行
                        if (personProfile.statusText != newStatus) {
                            val updatedProfile = personProfile.copy(statusText = newStatus, isBusy = action.isBusy ?: personProfile.isBusy)
                            actionRepository.updatePersonProfile(updatedProfile)
                            Log.i("ActionExecutor", "执行行动 [更新状态]: newStatus=${newStatus}, isBusy=${action.isBusy}")

                            // 创建系统消息
                            val statusText = if (newStatus.isBlank()) "离线" else newStatus
                            // 使用 personProfile.remarkName 因为这是用户看到的昵称
                            val messageText = "[${personProfile.remarkName}的状态已更新为: $statusText]"
                            val systemMessage = ChatMessage(
                                contactId = contactId,
                                content = messageText,
                                type = "pat_message",
                                role = "assistant",
                                timestamp = nextMessageTimestamp()
                            )
                            newMessages.add(systemMessage)
                        } else {
                            Log.i("ActionExecutor", "状态未改变 (${personProfile.statusText} -> $newStatus)，跳过更新和系统消息。")
                        }
                    }
                }
                is AiAction.WaimaiOrder -> {
                    val message = ChatMessage(
                        contactId = contactId,
                        type = "waimai_order",
                        content = action.productInfo ?: "一份外卖",
                        productInfo = action.productInfo,
                        amount = action.amount,
                        greeting = action.greeting,
                        senderName = action.senderName,
                        recipientName = action.recipientName,
                        role = "assistant",
                        timestamp = nextMessageTimestamp()
                    )
                    newMessages.add(message)
                    Log.i("ActionExecutor", "准备消息 [外卖订单]: ${action.productInfo} from ${action.senderName} to ${action.recipientName}")
                }
                is AiAction.WaimaiRequest -> {
                    val message = ChatMessage(
                        contactId = contactId,
                        type = "waimai_request",
                        content = action.productInfo ?: "一份外卖",
                        productInfo = action.productInfo,
                        amount = action.amount,
                        status = "pending", // AI发起的请求初始状态都是pending
                        role = "assistant",
                        timestamp = nextMessageTimestamp()
                    )
                    newMessages.add(message)
                    Log.i("ActionExecutor", "准备消息 [外卖请求]: ${action.productInfo}")
                }
                is AiAction.Transfer -> {
                    val message = ChatMessage(
                        contactId = contactId,
                        type = "transfer",
                        content = action.note ?: "转账",
                        amount = action.amount,
                        notes = action.note,
                        status = "pending", // AI发起的转账初始状态为pending
                        role = "assistant",
                        timestamp = nextMessageTimestamp()
                    )
                    newMessages.add(message)
                    Log.i("ActionExecutor", "准备消息 [转账]: ${action.amount} - ${action.note}")
                }
                is AiAction.LocationShare -> {
                    val message = ChatMessage(
                        contactId = contactId,
                        type = "location_share",
                        content = action.content,
                        role = "assistant",
                        timestamp = nextMessageTimestamp()
                    )
                    newMessages.add(message)
                    Log.i("ActionExecutor", "准备消息 [位置分享]: ${action.content}")
                }
                is AiAction.Unknown -> {
                    // 将无法识别的JSON内容作为文本消息显示给用户
                    action.rawContent?.let { content ->
                        val message = ChatMessage(
                            contactId = contactId,
                            content = content,
                            role = "assistant",
                            timestamp = nextMessageTimestamp()
                        )
                        newMessages.add(message)
                        Log.w("ActionExecutor", "收到未知的行动类型 (${action.type})，已作为文本显示: $content")
                    } ?: run {
                        Log.w("ActionExecutor", "收到未知的行动类型 (${action.type})，但无原始内容，已跳过。")
                    }
                }
               is AiAction.AcceptTransfer -> {
                   val originalMessage = actionRepository.getMessageByTimestamp(contactId, action.forTimestamp)
                   if (originalMessage != null) {
                       // 用户在发起转账时已经扣款，AI接收时只需更新状态
                       actionRepository.updateMessageStatusByTimestamp(contactId, action.forTimestamp, "accepted", "（已被接收）")
                       val systemMessage = ChatMessage(
                           contactId = contactId,
                           type = "pat_message",
                           content = "[${personProfile.remarkName}已收款]",
                           role = "assistant",
                           timestamp = nextMessageTimestamp()
                       )
                       newMessages.add(systemMessage)
                       Log.i("ActionExecutor", "执行行动 [接受转账]: for_timestamp=${action.forTimestamp}")
                   } else {
                       Log.w("ActionExecutor", "无法处理接受转账，原始消息未找到: for_timestamp=${action.forTimestamp}")
                   }
               }

               is AiAction.DeclineTransfer -> {
                   val originalMessage = actionRepository.getMessageByTimestamp(contactId, action.forTimestamp)
                   val declineAmount = originalMessage?.amount
                   if (originalMessage != null && declineAmount != null) {
                       // 用户给AI转账,AI退款时需要把钱退还给用户
                       alipayRepository.performTransaction(
                           BigDecimal.valueOf(declineAmount),
                           "refund",
                           "收到 ${personProfile.remarkName} 的退款",
                           personProfile.id
                       )
                       actionRepository.updateMessageStatusByTimestamp(contactId, action.forTimestamp, "declined", "（已被退还）")
                       val systemMessage = ChatMessage(
                           contactId = contactId,
                           type = "pat_message",
                           content = "[${personProfile.remarkName}已退还转账]",
                           role = "assistant",
                           timestamp = nextMessageTimestamp()
                       )
                       newMessages.add(systemMessage)
                       Log.i("ActionExecutor", "执行行动 [拒绝转账]: for_timestamp=${action.forTimestamp}")
                   } else {
                       Log.w("ActionExecutor", "无法处理拒绝转账，原始消息未找到或金额为空: for_timestamp=${action.forTimestamp}")
                   }
               }
               is AiAction.VideoCallRequest -> {
                   // 收到视频通话请求，不再创建消息，而是通过返回值通知ViewModel
                   incomingCallContactId = contactId
                   Log.i("ActionExecutor", "检测到AI发起的视频通话请求，将通知ViewModel。")
               }
               is AiAction.AcceptCall -> {
                   // 此行动由ViewModel处理以改变通话状态。
                   // ActionExecutor的职责是生成聊天消息或执行数据库写入等副作用。
                   // 因此我们只在这里记录日志。
                   Log.i("ActionExecutor", "收到AcceptCall行动，将由ViewModel处理。")
               }
               is AiAction.DeclineCall -> {
                   Log.i("ActionExecutor", "收到DeclineCall行动，将由ViewModel处理。原因: ${action.reason}")
               }
               is AiAction.EndCall -> {
                   Log.i("ActionExecutor", "收到EndCall行动，将由ViewModel处理。原因: ${action.reason}")
               }
               is AiAction.ChangeUserNickname -> {
                   action.newName?.let { newNickname ->
                       if (personProfile.nicknameForUser != newNickname) {
                           val updatedProfile = personProfile.copy(nicknameForUser = newNickname)
                           actionRepository.updatePersonProfile(updatedProfile)
                           Log.i("ActionExecutor", "执行行动 [修改用户昵称]: newName=${newNickname}")
                           val systemMessage = ChatMessage(
                               contactId = contactId,
                               type = "pat_message",
                               content = "对方将你对Ta的备注修改为 “$newNickname”",
                               role = "assistant",
                               timestamp = nextMessageTimestamp()
                           )
                           newMessages.add(systemMessage)
                       } else {
                           Log.i("ActionExecutor", "用户昵称未改变 ($newNickname)，跳过更新。")
                       }
                   }
               }
               is AiAction.QuoteReply -> {
                   action.replyContent?.let { content ->
                       // 根据target_timestamp查找被引用的消息
                       val quotedMsg = actionRepository.getMessageByTimestamp(contactId, action.targetTimestamp)
                       val quotedMessage = quotedMsg?.let {
                           QuotedMessage(
                               messageId = it.id,
                               senderName = if (it.role == "user") "用户" else personProfile.remarkName,
                               content = it.content ?: "[消息]"
                           )
                       }
                       val message = ChatMessage(
                           contactId = contactId,
                           content = content,
                           role = "assistant",
                           timestamp = nextMessageTimestamp(),
                           quotedMessage = quotedMessage
                       )
                       newMessages.add(message)
                       Log.i("ActionExecutor", "准备消息 [引用回复]: $content, 引用消息时间戳: ${action.targetTimestamp}")
                   }
               }
               is AiAction.ApproveShoppingAccess -> {
                   // AI同意查看购物app的指令
                   try {
                       // 1. 添加到已授权列表
                       actionRepository.addShoppingAuthorizedAccount(contactId, "AI已同意授权")
                       
                       // 2. 更新原始shopping_access_request消息的状态为approved
                       actionRepository.updateLatestMessageStatusByType(contactId, "shopping_access_request", "approved")
                       
                       // 3. 添加系统消息通知用户
                       val systemMessage = ChatMessage(
                           contactId = contactId,
                           type = "pat_message",
                           content = "[${personProfile.remarkName}已同意你查看Ta的购物app]",
                           role = "assistant",
                           timestamp = nextMessageTimestamp()
                       )
                       newMessages.add(systemMessage)
                       Log.i("ActionExecutor", "执行行动 [同意购物访问]: contactId=$contactId")
                       
                       // 4. 异步检查并生成商品
                       asyncTasks.add {
                           if (shoppingProductRepository != null && generateShoppingProductUseCase != null) {
                               try {
                                   val existingProducts = shoppingProductRepository.getProductsByContactId(contactId).first()
                                   if (existingProducts.isEmpty()) {
                                       Log.i("ActionExecutor", "异步任务 - 联系人 $contactId 没有商品，开始自动生成...")
                                       generateShoppingProductUseCase.generateWithAi(context, contactId)
                                           .onSuccess { count ->
                                               Log.i("ActionExecutor", "自动生成商品成功，共 $count 个商品")
                                           }
                                           .onFailure { e ->
                                               Log.e("ActionExecutor", "自动生成商品失败", e)
                                           }
                                   } else {
                                       Log.i("ActionExecutor", "异步任务 - 联系人 $contactId 已有 ${existingProducts.size} 个商品，跳过生成")
                                   }
                               } catch (e: Exception) {
                                   Log.e("ActionExecutor", "异步任务 - 自动生成商品出错", e)
                               }
                           }
                       }
                   } catch (e: Exception) {
                       Log.e("ActionExecutor", "处理ApproveShoppingAccess时出错", e)
                   }
               }
               is AiAction.RejectShoppingAccess -> {
                   // AI拒绝查看购物app的指令
                   try {
                       // 1. 更新原始shopping_access_request消息的状态为rejected
                       actionRepository.updateLatestMessageStatusByType(contactId, "shopping_access_request", "rejected")
                       
                       // 2. 添加系统消息通知用户
                       val reason = action.reason ?: "未说明原因"
                       val systemMessage = ChatMessage(
                           contactId = contactId,
                           type = "pat_message",
                           content = "[${personProfile.remarkName}已拒绝你查看Ta的购物app：$reason]",
                           role = "assistant",
                           timestamp = nextMessageTimestamp()
                       )
                       newMessages.add(systemMessage)
                       Log.i("ActionExecutor", "执行行动 [拒绝购物访问]: contactId=$contactId, reason=$reason")
                   } catch (e: Exception) {
                       Log.e("ActionExecutor", "处理RejectShoppingAccess时出错", e)
                   }
               }
               is AiAction.Gift -> {
                   // AI送礼物给用户
                   action.giftName?.let { giftName ->
                       val giftValue = action.giftValue ?: 0.0
                       val giftNote = action.giftNote
                       val imagePrompt = action.imagePrompt
                       
                       Log.i("ActionExecutor", "执行行动 [送礼物]: $giftName, 价值: $giftValue, 备注: $giftNote")
                       
                       // 使用 Pollinations AI 生成礼物图片 (同步, 因为是本地字符串操作)
                       val giftImageUrl = if (imagePrompt != null) {
                           try {
                               val encodedPrompt = java.net.URLEncoder.encode(imagePrompt, "UTF-8")
                               val pollinationsUrl = "https://image.pollinations.ai/prompt/$encodedPrompt"
                               Log.i("ActionExecutor", "礼物图片URL生成成功: $pollinationsUrl")
                               pollinationsUrl
                           } catch (e: Exception) {
                               Log.e("ActionExecutor", "礼物图片URL生成失败", e)
                               "error_placeholder"
                           }
                       } else {
                           null
                       }
                       
                       // 创建礼物消息
                       val message = ChatMessage(
                           contactId = contactId,
                           type = "gift",
                           content = "赠送了礼物",
                           giftName = giftName,
                           giftImageUrl = giftImageUrl,
                           giftValue = giftValue,
                           giftNote = giftNote,
                           role = "assistant",
                           timestamp = nextMessageTimestamp()
                       )
                       newMessages.add(message)
                       Log.i("ActionExecutor", "准备消息 [礼物]: $giftName")

                       // 异步将礼物添加到背包
                       if (backpackRepository != null && giftImageUrl != null) {
                           asyncTasks.add {
                               try {
                                   val backpackItem = BackpackItemEntity(
                                       productName = giftName,
                                       imageUrl = giftImageUrl,
                                       price = giftValue,
                                       source = "来自${personProfile.remarkName}的礼物",
                                       obtainedTime = timestamp,
                                       orderId = null,
                                       isDiscarded = false,
                                       operationType = "normal",
                                       operationTime = null,
                                       giftRecipient = null
                                   )
                                   backpackRepository.addItem(backpackItem)
                                   Log.i("ActionExecutor", "异步任务 - 礼物已添加到背包: $giftName")
                               } catch (e: Exception) {
                                   Log.e("ActionExecutor", "异步任务 - 添加礼物到背包失败", e)
                               }
                           }
                       }
                   }
               }
               is AiAction.VoiceMessage -> {
                   action.content?.let { content ->
                       val message = ChatMessage(
                           contactId = contactId,
                           type = "voice_message",
                           content = content,
                           role = "assistant",
                           timestamp = nextMessageTimestamp()
                       )
                       newMessages.add(message)
                       Log.i("ActionExecutor", "准备消息 [语音]: 时长4s")
                   }
               }
               is AiAction.CreateCountdown -> {
                   // AI创建倒计时
                   val title = action.title
                   val date = action.date
                   if (title != null && date != null) {
                       try {
                           // 解析ISO 8601日期格式
                           val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                           val appointmentDate = dateFormat.parse(date)?.time
                           
                           if (appointmentDate != null && memoriesRepository != null) {
                                val appointment = AppointmentEntity(
                                    contactId = contactId,
                                    title = title,
                                    appointmentDate = appointmentDate
                                )
                               memoriesRepository.insert(appointment)
                               
                               // 创建可见的系统消息(用户端显示)
                               val visibleMessage = ChatMessage(
                                   contactId = contactId,
                                   type = "system",
                                   content = "[${personProfile.remarkName}创建了一个倒计时]",
                                   role = "assistant",
                                   timestamp = nextMessageTimestamp()
                               )
                               newMessages.add(visibleMessage)
                               
                               // 创建隐藏的系统消息(AI端提示词)
                               val hiddenMessage = ChatMessage(
                                   contactId = contactId,
                                   type = "system",
                                   content = "[系统消息：你创建了一个倒计时，标题是${title}，日期是${date}]",
                                   role = "assistant",
                                   timestamp = nextMessageTimestamp(),
                                   isHidden = true
                               )
                               newMessages.add(hiddenMessage)
                               
                               Log.i("ActionExecutor", "执行行动 [创建倒计时]: 标题=${title}, 日期=${date}")
                           } else {
                               Log.e("ActionExecutor", "无法创建倒计时: 日期解析失败或Repository未注入")
                           }
                       } catch (e: Exception) {
                           Log.e("ActionExecutor", "创建倒计时时出错", e)
                       }
                   } else {
                       Log.w("ActionExecutor", "创建倒计时失败: 标题或日期为空")
                   }
               }
               is AiAction.CreateMemory -> {
                   // AI创建回忆
                   val description = action.description
                   if (description != null && memoriesRepository != null) {
                       try {
                           val memory = GeneralMemoryEntity(
                               contactId = contactId,
                               description = description,
                               createdDate = timestamp
                           )
                           memoriesRepository.insertMemory(memory)
                           
                           // 创建可见的系统消息(用户端显示)
                           val visibleMessage = ChatMessage(
                               contactId = contactId,
                               type = "system",
                               content = "[${personProfile.remarkName}创建了一个回忆]",
                               role = "assistant",
                               timestamp = nextMessageTimestamp()
                           )
                           newMessages.add(visibleMessage)
                           
                           // 创建隐藏的系统消息(AI端提示词)
                           val hiddenMessage = ChatMessage(
                               contactId = contactId,
                               type = "system",
                               content = "[系统消息：你创建了一个回忆，描述是${description}]",
                               role = "assistant",
                               timestamp = nextMessageTimestamp(),
                               isHidden = true
                           )
                           newMessages.add(hiddenMessage)
                           
                           Log.i("ActionExecutor", "执行行动 [创建回忆]: ${description}")
                       } catch (e: Exception) {
                           Log.e("ActionExecutor", "创建回忆时出错", e)
                       }
                   } else {
                       Log.w("ActionExecutor", "创建回忆失败: 描述为空或Repository未注入")
                   }
               }
               is AiAction.PatUser -> {
                   // AI戳一戳用户
                   val suffix = action.suffix ?: ""
                   val message = ChatMessage(
                       contactId = contactId,
                       type = "pat_message",
                       content = "[${personProfile.remarkName}戳了戳你$suffix]",
                       role = "assistant",
                       timestamp = nextMessageTimestamp()
                   )
                   newMessages.add(message)
                   Log.i("ActionExecutor", "准备消息 [戳一戳]: ${personProfile.remarkName}戳了戳你$suffix")
               }
               is AiAction.SendAndRecall -> {
                   // AI发送并撤回消息
                   action.content?.let { content ->
                       val message = ChatMessage(
                           contactId = contactId,
                           type = "send_and_recall",
                           content = content,
                           role = "assistant",
                           timestamp = nextMessageTimestamp()
                       )
                       newMessages.add(message)
                       Log.i("ActionExecutor", "准备消息 [发送并撤回]: $content")
                   }
               }
               is AiAction.OfflineRequest -> {
                   // AI申请线下见面
                   val message = ChatMessage(
                       contactId = contactId,
                       type = "offline_request",
                       content = "线下见面申请",
                       offlineLocation = action.location,
                       offlineReason = action.reason,
                       status = "pending", // AI发起的请求初始状态为pending
                       role = "assistant",
                       timestamp = nextMessageTimestamp()
                   )
                   newMessages.add(message)
                   Log.i("ActionExecutor", "准备消息 [线下见面请求]: 地点=${action.location}, 理由=${action.reason}")
               }
               is AiAction.ImageAnalysis -> {
                   // AI对图片进行分析并生成描述
                   val messageId = action.messageId
                   val description = action.description
                   
                   if (messageId != null && description != null && chatRepository != null) {
                       // 异步更新图片描述到数据库
                       asyncTasks.add {
                           try {
                               chatRepository.updateImageDescription(messageId, description)
                               Log.i("ActionExecutor", "异步任务 - 图片分析完成: messageId=$messageId, 描述长度=${description.length}")
                           } catch (e: Exception) {
                               Log.e("ActionExecutor", "异步任务 - 更新图片描述失败: messageId=$messageId", e)
                           }
                       }
                   } else {
                       Log.w("ActionExecutor", "图片分析动作缺少必要参数: messageId=$messageId, description=${description?.take(50)}, chatRepository=${chatRepository != null}")
                   }
               }
           }
       }
       return ActionExecutionResult(
           messages = newMessages,
           incomingCallContactId = incomingCallContactId,
           asyncTasks = asyncTasks
       )
   }
}
