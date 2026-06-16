package com.susking.ephone_s.aidata.prompt

import com.google.gson.Gson
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryGraphDao
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphNode
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphRelation
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookEntryRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class SummarizeChatHistoryPromptBuilder @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val worldBookRepository: WorldBookRepository,
    private val worldBookEntryRepository: WorldBookEntryRepository,
    private val memoryGraphDao: MemoryGraphDao,
    private val memoryEventDao: MemoryEventDao
) {
    /**
     * 自动分批提取的准备结果。
     *
     * 自动提取必须给窗口封顶并按已处理消息推进游标，否则一旦某次失败、游标卡住，
     * 窗口会随积压消息无限增大，导致后续提取注定超 token 而再次失败（雪球死循环）。
     *
     * @param promptRequest 已构建好的提取请求。
     * @param batchMaxTimestamp 本批参与提取的消息中的最大时间戳。成功保存后游标应推进到该值，
     *        而不是 System.currentTimeMillis()，以避免提取期间新到的消息被跳过。
     * @param batchSize 本批参与提取的消息条数。
     * @param hasMoreBacklog 本批之后是否仍有未处理的积压消息，用于驱动继续分批偿还。
     */
    data class AutoBatchPrompt(
        val promptRequest: AiPromptRequest,
        val batchMaxTimestamp: Long,
        val batchSize: Int,
        val hasMoreBacklog: Boolean
    )

    /**
     * 为自动结构化事件提取准备一个封顶的消息批次。
     *
     * 与 [prepare] 不同，本方法只取"游标之后最旧的 [maxBatchSize] 条"消息，窗口大小固定，
     * 并回传本批最大时间戳与是否还有积压，供执行器按已处理消息推进游标、分批偿还。
     *
     * @param contactId 联系人ID。
     * @param maxBatchSize 单批最多处理的消息条数（窗口硬上限）。
     * @param startTimestamp 上次成功提取的游标时间戳；null 表示从头开始。
     */
    suspend fun prepareAutoBatch(
        contactId: String,
        userNickname: String,
        characterName: String,
        characterPersona: String,
        userPersona: String,
        startTimestamp: Long?,
        maxBatchSize: Int = DEFAULT_AUTO_BATCH_SIZE
    ): Result<AutoBatchPrompt> {
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: return Result.failure(Exception("联系人不存在"))

        val userProfile: UserProfile = buildUserProfile(userNickname, userPersona)

        val allMessages: List<ChatMessage> = chatRepository.getMessagesForContact(contactId).firstOrNull() ?: emptyList()
        if (allMessages.size < MIN_MESSAGE_COUNT) {
            return Result.failure(Exception("对话记录过少，无法进行有效总结。"))
        }

        // 取游标之后的全部积压消息（已按 timestamp ASC 排序）
        val pendingMessages: List<ChatMessage> = if (startTimestamp != null) {
            allMessages.filter { message: ChatMessage -> message.timestamp > startTimestamp }
        } else {
            allMessages
        }

        if (pendingMessages.size < MIN_MESSAGE_COUNT) {
            return Result.failure(Exception("自上次总结后的新消息过少（少于${MIN_MESSAGE_COUNT}条），暂不进行总结。"))
        }

        // 窗口封顶：只取最旧的 maxBatchSize 条，斩断雪球
        val normalizedBatchSize: Int = maxBatchSize.coerceAtLeast(MIN_MESSAGE_COUNT)
        val batchMessages: List<ChatMessage> = pendingMessages.take(normalizedBatchSize)
        val batchMaxTimestamp: Long = batchMessages.maxOf { message: ChatMessage -> message.timestamp }
        val hasMoreBacklog: Boolean = pendingMessages.size > batchMessages.size

        val promptRequest: AiPromptRequest = buildExtractionPromptRequest(
            contactId = contactId,
            contact = contact,
            userProfile = userProfile,
            characterName = characterName,
            characterPersona = characterPersona,
            userPersona = userPersona,
            messagesToSummarize = batchMessages
        )
        return Result.success(
            AutoBatchPrompt(
                promptRequest = promptRequest,
                batchMaxTimestamp = batchMaxTimestamp,
                batchSize = batchMessages.size,
                hasMoreBacklog = hasMoreBacklog
            )
        )
    }

    suspend fun prepare(contactId: String, userNickname: String, characterName: String, characterPersona: String, userPersona: String, startTimestamp: Long?, endTimestamp: Long? = null): Result<AiPromptRequest> {
        // 获取联系人信息
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: return Result.failure(Exception("联系人不存在"))

        // 创建用户资料对象
        val userProfile = buildUserProfile(userNickname, userPersona)

        // 获取所有消息
        val allMessages: List<ChatMessage> = chatRepository.getMessagesForContact(contactId).firstOrNull() ?: emptyList()
        if (allMessages.size < MIN_MESSAGE_COUNT) {
            return Result.failure(Exception("对话记录过少，无法进行有效总结。"))
        }

        // 根据传入的 startTimestamp 和 endTimestamp 筛选需要总结的消息
        val messagesToSummarize = if (startTimestamp != null) {
            // 手动或有明确起点的总结：筛选在 startTimestamp 和 endTimestamp 之间的消息
            allMessages.filter { message ->
                val afterStart = message.timestamp > startTimestamp
                val beforeEnd = endTimestamp == null || message.timestamp < endTimestamp
                afterStart && beforeEnd
            }
        } else {
            // 自动总结：如果没有起始时间戳，使用summaryInterval参数
            allMessages.takeLast(contact.summaryInterval)
        }

        if (messagesToSummarize.size < MIN_MESSAGE_COUNT) {
            return Result.failure(Exception("自上次总结后的新消息过少（少于${MIN_MESSAGE_COUNT}条），暂不进行总结。"))
        }

        val promptRequest: AiPromptRequest = buildExtractionPromptRequest(
            contactId = contactId,
            contact = contact,
            userProfile = userProfile,
            characterName = characterName,
            characterPersona = characterPersona,
            userPersona = userPersona,
            messagesToSummarize = messagesToSummarize
        )
        return Result.success(promptRequest)
    }

    private fun buildUserProfile(userNickname: String, userPersona: String): UserProfile {
        return UserProfile(
            id = "user",
            nickname = userNickname,
            signature = "",
            avatarUri = null,
            backgroundUri = null,
            persona = userPersona
        )
    }

    /**
     * 构建结构化事件、节点和关系提取请求。被自动分批提取与手动提取共用，确保提示词完全一致。
     */
    private suspend fun buildExtractionPromptRequest(
        contactId: String,
        contact: com.susking.ephone_s.aidata.domain.model.PersonProfile,
        userProfile: UserProfile,
        characterName: String,
        characterPersona: String,
        userPersona: String,
        messagesToSummarize: List<ChatMessage>
    ): AiPromptRequest {
        val messages = mutableListOf<ChatMessagePayload>()

        // 1. 获取当前日期
        val today = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
            .format(Date())

        // 2. 格式化对话历史（使用统一的组件构建器）
        val formattedHistory = PromptComponentBuilder.buildSimplifiedHistorySummary(
            messagesToSummarize,
            contact,
            userProfile
        )

        // 3. 构建结构化事件、节点和关系提取提示词。
        val existingGraphContext: String = buildExistingGraphContext(contactId)
        val prompt1 = """
# 你的任务
你是角色"$characterName"的结构化事件和事实图谱抽取器。请你回顾刚才和"${userProfile.nickname}"的对话，提取可长期保存的结构化事件、事实节点和事实关系。

# 输入边界
只能从给定对话历史中抽取明确内容，不要编造、推测或补全对话外的信息。如果材料只是寒暄、重复表达、纯情绪宣泄或没有后续价值的闲聊，对应数组应返回空数组。

# 结构化事件定义
结构化事件是小手机记忆中心的主要记忆对象。每条只记录一件事、一个决定、一个偏好、一个承诺、一个禁忌、一个关系变化、一个观点或一个重要事实。

# 事件类型与状态
- eventType 只能使用：COMMITMENT、PREFERENCE、PROHIBITION、ANNIVERSARY、RELATIONSHIP、FACT、OPINION、OTHER。
- status 只能使用：ACTIVE、PENDING、RESOLVED、CANCELLED、EXPIRED、SUPERSEDED、ARCHIVED。
- COMMITMENT 表示承诺、约定、待办；未完成默认 PENDING，明确完成用 RESOLVED，明确取消用 CANCELLED，明确过期用 EXPIRED。
- PREFERENCE、PROHIBITION、RELATIONSHIP、FACT、ANNIVERSARY 默认 ACTIVE。

# 事件抽取规则
1. title 和 content 必须使用客观第三人称来写。
2. 一条结构化事件只写一件事，不要把多个事件合并成一段大总结。
3. 优先提取重要事件、关键决定、未来计划、待办承诺、偏好、禁忌、关系变化、重要事实、观点和具体时间点。
4. 如果对话中提到了相对时间（如“明天”“后天”），必须结合“今天是$today”转换为具体的公历日期。
5. title 建议 8 到 24 个字，content 建议 15 到 80 个字。
6. importanceScore 必须是 1 到 10 的整数；confidenceScore 必须是 0 到 1 的小数。
7. rawEvidenceText 必须是对话历史中的原文片段，不允许改写。
8. dedupeKey 必须稳定，格式建议为“类型|主体|谓词|客体”，不要使用随机值、证据 id 或时间戳。

# 节点和关系抽取规则
1. nodes 只输出“既有节点”中不存在的新实体。若对话只提到已有节点或已有别名，nodes 必须返回空数组。
2. relations 只输出新关系，或明确结束、转变、纠正旧关系的变化声明。
3. 如果“既有关系”已有相同 from、to、relationType 且 status 为 ACTIVE 或 PENDING，不要输出 ASSERT_ACTIVE；重复佐证由服务端证据链处理。
4. 如果对话只是继续证明既有 ACTIVE 或 PENDING 关系仍然成立，例如再次称呼恋人、拥抱、亲吻、陪伴、守护、做饭，但没有关系开始、结束、转变或纠正，则 relations 必须返回空数组。
5. 关系可以多维并存，不要根据常识合并或互斥关系；例如敌人和暧昧可以同时成立。
6. relationType 使用英文短语 snake_case，例如 romantic_partner、guardian_and_housekeeper、likes、dislikes、is_friend_of、related_to。

# 关系变化规则
- changeAction 只能是 ASSERT_ACTIVE、ASSERT_ENDED、TRANSITION_FROM、CORRECT_PREVIOUS、UNCLEAR。
- ASSERT_ACTIVE 只用于输出既有关系中不存在的新关系成立。
- ASSERT_ENDED 表示证据明确说某条关系结束；必须提供 previousRelationId 或 previousRelationHint。
- TRANSITION_FROM 表示证据明确说从旧关系变成新关系；必须提供 previousRelationId 或 previousRelationHint。
- CORRECT_PREVIOUS 表示证据明确纠正旧抽取；必须提供 previousRelationId 或 previousRelationHint。
- UNCLEAR 表示关系变化不清楚，只记录证据，不关闭旧关系。
- effectiveFrom 是关系开始生效时间；effectiveTo 是关系结束时间；未知时用 null。validitySource 写 explicit、message_time 或 unknown。
- confidenceScore 表示证据可靠度，不表示关系强弱或亲密程度。

# 强制去重规则
- 如果“既有事件”已有同一语义 dedupeKey，除非本次对话明确推翻、完成、取消或结束旧事件，否则不要再次输出该事件。
- 如果本次对话与既有事件只是同义、近义、延续或补充证据，events 不要输出同一 dedupeKey。
- 如果“既有关系”已有同一语义关系，即使 endpointKey 没有给出或你无法精确计算，只要 from、to、relationType 相同且状态为 ACTIVE 或 PENDING，就不要再次输出 ASSERT_ACTIVE。
- 只有本次对话明确出现关系开始、关系结束、关系转变、关系纠正、新关系对象或新关系类型时，才允许输出 relations。

# 输出格式
回复必须且只能是一个 JSON 对象，事件按时间顺序输出，格式如下：
{
  "events":[{"eventType":"FACT","status":"ACTIVE","statusReason":null,"title":"某人记住了一条事实","content":"某人记住了这条可以长期使用的重要事实。","eventTime":时间戳或null,"importanceScore":5,"confidenceScore":0.8,"dedupeKey":"类型|主体|谓词|客体","rawEvidenceText":"来源原文片段"}],
  "nodes":[{"entityType":"Person或Location或Organization或Item或Concept","name":"实体名","aliases":[]}],
  "relations":[{"fromName":"起点实体名","fromType":"实体类型","toName":"终点实体名","toType":"实体类型","relationType":"关系类型英文短语","eventTime":时间戳或null,"effectiveFrom":时间戳或null,"effectiveTo":时间戳或null,"changeAction":"ASSERT_ACTIVE","changeReason":"原文说明的变化原因或null","previousRelationId":"上一关系id或null","previousRelationHint":"上一关系描述或null","validitySource":"explicit或message_time或unknown","confidenceScore":0.8}]
}
        """.trimIndent()
        val prompt2 = """
# 既有结构化图谱
$existingGraphContext

# 你的角色设定
$characterPersona

# 你的聊天对象（用户）的人设
$userPersona

# 待提取结构化事件、节点和关系的对话历史
$formattedHistory

现在开始提取。只输出指定 JSON，不要输出 Markdown 或解释文字。
        """.trimIndent()

        messages.add(ChatMessagePayload(role = "system", content = prompt1))
        messages.add(ChatMessagePayload(role = "user", content = prompt2))

        val chatRequest = ChatCompletionRequest(
            model = settingsRepository.getMainModel(),
            messages = messages,
            temperature = (settingsRepository.getApiTemperature() - 0.1f).coerceAtLeast(0.2f),
            responseFormat = ResponseFormat(type = "json_object")
        )

        val fullUrl = "${settingsRepository.getMainApiUrl()}/v1/chat/completions"
        // 【修复】在创建请求时，将 characterName 赋值给 contactName
        return AiPromptRequest(
            request = chatRequest,
            url = fullUrl,
            displayPromptJson = Gson().toJson(chatRequest),
            timestamp = System.currentTimeMillis(),
            contactName = characterName,
            activityType = "提取结构化事件和事实图谱" // 明确指定活动类型
        )
    }

    private suspend fun buildExistingGraphContext(contactId: String): String {
        val nodes: List<MemoryGraphNode> = memoryGraphDao.getNodesForContact(contactId, EXISTING_GRAPH_CONTEXT_LIMIT)
        val relations: List<MemoryGraphRelation> = memoryGraphDao.getRelationsForContact(contactId, ACTIVE_RECALL_STATUSES, EXISTING_GRAPH_CONTEXT_LIMIT)
        val events: List<MemoryEvent> = memoryEventDao.getEventsByStatuses(contactId, ACTIVE_RECALL_STATUSES, EXISTING_GRAPH_CONTEXT_LIMIT)
        if (nodes.isEmpty() && relations.isEmpty() && events.isEmpty()) return "无既有结构化图谱。"
        val nodeText: String = nodes.joinToString(separator = "\n") { node: MemoryGraphNode ->
            "- node name=${node.name}; normalizedName=${node.normalizedName}; type=${node.entityType}; aliases=${node.aliases.orEmpty()}"
        }.ifBlank { "无" }
        val nodeMap: Map<String, MemoryGraphNode> = nodes.associateBy { node: MemoryGraphNode -> node.id }
        val relationText: String = relations.joinToString(separator = "\n") { relation: MemoryGraphRelation ->
            val fromName: String = nodeMap[relation.fromNodeId]?.name ?: relation.fromNodeId
            val toName: String = nodeMap[relation.toNodeId]?.name ?: relation.toNodeId
            "- relation id=${relation.id}; from=$fromName; type=${relation.relationType}; to=$toName; status=${relation.status.name}; endpointKey=${relation.endpointKey}; relationKey=${relation.relationKey}; changeAction=${relation.changeAction.name}; effectiveFrom=${relation.effectiveFrom}; effectiveTo=${relation.effectiveTo}"
        }.ifBlank { "无" }
        val eventText: String = events.joinToString(separator = "\n") { event: MemoryEvent ->
            "- event id=${event.id}; type=${event.eventType.name}; status=${event.status.name}; dedupeKey=${event.dedupeKey.orEmpty()}; eventTime=${event.eventTime}; content=${event.content}"
        }.ifBlank { "无" }
        return """
既有节点：
$nodeText
既有关系：
$relationText
既有事件：
$eventText
        """.trimIndent()
    }

    private companion object {
        private const val EXISTING_GRAPH_CONTEXT_LIMIT: Int = 40
        // 自动提取的有效消息下限，少于该数量不触发，保证总结质量
        private const val MIN_MESSAGE_COUNT: Int = 5
        // 自动分批提取的单批窗口硬上限，窗口封顶斩断"失败-积压-更大窗口-再失败"的雪球死循环
        private const val DEFAULT_AUTO_BATCH_SIZE: Int = 50
        private val ACTIVE_RECALL_STATUSES: List<MemoryEventStatus> = listOf(MemoryEventStatus.ACTIVE, MemoryEventStatus.PENDING)
    }
}