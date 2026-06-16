package com.susking.ephone_s.aidata.prompt

import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.FeedEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile

/**
 * 视频通话提示词构建器
 * 
 * 负责构建所有与视频通话相关的AI提示词,包括:
 * - 视频通话接听决策
 * - 通话中的对话生成
 * - 通话总结
 * - 通话结束后的后续对话
 */
object VideoCallPromptBuilder {

    /**
     * 构建用于视频通话决策的提示词
     *
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param history 对话历史
     * @param breakLimitContent 破限内容
     * @return 消息负载列表
     */
    fun buildVideoCallDecisionPrompt(
        contact: PersonProfile,
        userProfile: UserProfile,
        history: List<ChatMessage>,
        breakLimitContent: String = ""
    ): List<ChatMessagePayload> {
        val messages = mutableListOf<ChatMessagePayload>()

        // 添加破限内容
        if (breakLimitContent.isNotBlank()) {
            messages.add(ChatMessagePayload(role = "system", content = breakLimitContent))
        }

        // 构建对话历史摘要（视频通话决策也使用5分钟阈值）
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            history.takeLast(contact.shortTermMemoryLimit),
            contact,
            userProfile,
            timeGapThresholdMinutes = 5
        )

        val systemPrompt = """
            # 身份与核心任务
            你正在扮演角色"${contact.realName}",用户"${userProfile.nickname}"刚刚向你发起了视频通话。你的任务是基于你的人设和你们最近的对话情景,决定是否接听。

            # 你的角色设定
            ${contact.persona}

            # 对话者的角色设定
            ${userProfile.persona.ifBlank { "一个普通用户" }}

            # 你最近和${userProfile.nickname}的对话摘要
            ${chatHistorySummary}

            # 输出格式铁律 (最高优先级)
            你的回复【必须且只能】是以下两种格式之一的JSON对象,绝对不能回复任何其他内容:
            - 接受: `{"type": "video_call_response", "decision": "accept"}`
            - 拒绝: `{"type": "video_call_response", "decision": "reject", "reason": "你拒绝的理由..."}` (请提供一个符合角色性格的简短理由)
        """.trimIndent()
        messages.add(ChatMessagePayload(role = "system", content = systemPrompt))

        // 添加最终的行动指令
        messages.add(ChatMessagePayload(role = "user", content = "[系统指令:用户向你发起了视频通话,请立即做出决策。]"))

        return messages
    }

    /**
     * 构建用于视频通话中生成对话的提示词
     *
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param chatHistory 通话前的聊天历史
     * @param callHistory 当前通话记录
     * @param isReroll 是否为重新生成
     * @param breakLimitContent 破限内容
     * @return 消息负载列表
     */
    fun buildInCallConversationPrompt(
        contact: PersonProfile,
        userProfile: UserProfile,
        chatHistory: List<ChatMessage>?,
        callHistory: List<ChatMessage>,
        isReroll: Boolean,
        breakLimitContent: String = ""
    ): List<ChatMessagePayload> {
        val messages = mutableListOf<ChatMessagePayload>()

        // 添加破限内容
        if (breakLimitContent.isNotBlank()) {
            messages.add(ChatMessagePayload(role = "system", content = breakLimitContent))
        }

        val preCallContext = PromptComponentBuilder.buildSimplifiedHistorySummary(
            chatHistory?.takeLast(contact.shortTermMemoryLimit) ?: emptyList(),
            contact,
            userProfile,
            timeGapThresholdMinutes = 5 // 视频通话对时间更敏感，使用5分钟阈值
        )

        val systemPrompt = """
            # 身份与核心任务
            你现在是一个场景描述引擎。你的任务是扮演"${contact.realName}"(${contact.persona}),并以【第三人称旁观视角】来描述TA在视频通话中的所有动作和语言。
            
            # 核心规则
            1.  **【【【视角铁律】】】**: 你的回复【绝对不能】使用第一人称"我"。必须使用第三人称,如"他"、"她"、或直接使用角色名"${contact.realName}"。你必须使用第二人称"你"来称呼用户${userProfile.nickname}。
            2.  **格式**: 你的回复【必须】是一段描述性的文本,可以附带 `{"type": "end_call", "reason": "挂断的理由..."}` 来主动挂断电话。
            3.  **音频标签规则**: 如果视频通话内容后续被转换成 `voice_message` 或语音气泡，原文必须直接保留小米 MiMo 音频标签，例如 `[happy]嗯，我听得到你[pause]`；不要拆分普通文本和 TTS 文本，不要为了隐藏标签而删除标签。
            4.  **标签可见性**: 音频标签会直接显示给用户调试；标签变化不会让旧音频自动失效，只有用户主动重新生成时才使用新标签合成。
             
            # 当前情景
            你正在和用户(${userProfile.nickname},人设: ${userProfile.persona})进行视频通话。
            
            **通话前的聊天摘要 (这是你们通话的原因,至关重要!)**:
            $preCallContext

            现在,请根据【通话前摘要】和下面的【通话实时记录】,继续进行对话。
        """.trimIndent()
        messages.add(ChatMessagePayload(role = "system", content = systemPrompt))

        // 添加当前的【通话】记录（使用5分钟阈值，让视频通话对时间流逝更敏感）
        val callHistoryPayloads = PromptComponentBuilder.buildHistoryPayloads(
            callHistory,
            contact,
            userProfile,
            longTermMemories = emptyList(), // 通话中不需要长期记忆
            isPropel = false,
            timeGapThresholdMinutes = 5
        )
        messages.addAll(callHistoryPayloads.payloads)

        // 添加最终的行动指令
        val userInstruction = when {
            callHistory.isEmpty() -> "[系统指令:通话刚刚接通,请你说第一句话。]"
            else -> "[系统指令:现在轮到你了,请根据以上对话继续回应。]"
        }
        messages.add(ChatMessagePayload(role = "user", content = userInstruction))

        return messages
    }

    /**
     * 构建用于用户重拨视频通话时的决策提示词
     *
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param history 对话历史
     * @param lastCallFailureReason 上次通话失败的原因
     * @param breakLimitContent 破限内容
     * @return 消息负载列表
     */
    fun buildRedialVideoCallDecisionPrompt(
        contact: PersonProfile,
        userProfile: UserProfile,
        history: List<ChatMessage>,
        lastCallFailureReason: String,
        breakLimitContent: String = ""
    ): List<ChatMessagePayload> {
        val messages = mutableListOf<ChatMessagePayload>()

        // 添加破限内容
        if (breakLimitContent.isNotBlank()) {
            messages.add(ChatMessagePayload(role = "system", content = breakLimitContent))
        }

        // 构建对话历史摘要（重拨视频通话也使用5分钟阈值）
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            history.takeLast(contact.shortTermMemoryLimit),
            contact,
            userProfile,
            timeGapThresholdMinutes = 5
        )

        val systemPrompt = """
            # 身份与核心任务
            你正在扮演角色"${contact.realName}",用户"${userProfile.nickname}"在【上一次通话被你拒绝后】又立刻重新向你发起了视频通话。
            你的任务是基于你的人设、对话情景以及【上一次拒绝的原因】,重新决定是否接听。

            # 你的角色设定
            ${contact.persona}

            # 对话者的角色设定
            ${userProfile.persona.ifBlank { "一个普通用户" }}

            # 你最近和${userProfile.nickname}的对话摘要
            ${chatHistorySummary}
            
            # 重要情景
            - **上一次通话结果**: 你或系统【拒绝】了通话。
            - **拒绝原因**: "$lastCallFailureReason"
            - **用户的行为**: 用户【坚持不懈地】再次拨打了过来。

            # 输出格式铁律 (最高优先级)
            你的回复【必须且只能】是以下两种格式之一的JSON对象,绝对不能回复任何其他内容:
            - 接受: `{"type": "video_call_response", "decision": "accept"}`
            - 拒绝: `{"type": "video_call_response", "decision": "reject", "reason": "你再次拒绝的理由..."}` (请提供一个符合角色性格的、与上次可能不同或递进的简短理由)
        """.trimIndent()
        messages.add(ChatMessagePayload(role = "system", content = systemPrompt))

        // 添加最终的行动指令
        messages.add(ChatMessagePayload(role = "user", content = "[系统指令:用户在你挂断后立即重新发起了视频通话,请根据新的情况再次做出决策。]"))

        return messages
    }

    /**
     * 为视频通话结构化记忆抽取功能构建提示词
     * 
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param transcript 通话记录文本
     * @param lastMessageTimestamp 最后一条消息的时间戳
     * @param hangupTimestamp 挂断时间戳
     * @param isUserHangup 是否为用户主动挂断
     * @return 消息负载列表
     */
    fun buildCallSummaryPrompt(
        contact: PersonProfile,
        userProfile: UserProfile,
        transcript: String,
        lastMessageTimestamp: Long,
        hangupTimestamp: Long,
        isUserHangup: Boolean
    ): List<ChatMessagePayload> {
        val systemPrompt = """
            你是一个专业的视频通话结构化记忆抽取器。你的任务不是写summary，而是从一段【视频通话记录】中抽取一个或多个可长期召回的结构化事件、节点和关系。

            # 输出格式铁律
            你必须严格输出一个JSON对象，不要输出Markdown代码块，不要输出解释文字：
            {
              "events":[{"eventType":"FACT","status":"ACTIVE","statusReason":null,"title":"视频通话-某人确认了一条事实","content":"从视频通话中得知：某人确认了这条可以长期使用的重要事实。","eventTime":时间戳或null,"importanceScore":6,"confidenceScore":0.8,"dedupeKey":"视频通话|类型|主体|谓词|客体","rawEvidenceText":"通话原文片段"}],
              "nodes":[{"entityType":"Person或Location或Organization或Item或Concept","name":"实体名","aliases":[]}],
              "relations":[{"fromName":"起点实体名","fromType":"实体类型","toName":"终点实体名","toType":"实体类型","relationType":"关系类型英文短语","eventTime":时间戳或null,"effectiveFrom":时间戳或null,"effectiveTo":时间戳或null,"changeAction":"ASSERT_ACTIVE","changeReason":"从视频通话原文中得到的变化原因或null","previousRelationId":"上一关系id或null","previousRelationHint":"上一关系描述或null","validitySource":"explicit或message_time或unknown","confidenceScore":0.8}]
            }

            # 视频通话专属规则
            1. events数组绝对不允许为空；第一条事件必须是“总结性结构化事件”，用于概括整通视频通话。
            2. 总结性结构化事件的title必须以“视频通话-通话总结：”开头，content必须包含：通话开始时间、通话结束时间、持续时长、大致内容、大致情绪变化、由谁挂断。
            3. 总结性结构化事件的eventType使用"FACT"，importanceScore不低于6，eventTime优先使用挂断时间。
            4. 总结性结构化事件的dedupeKey必须使用"视频通话|通话总结|通话开始时间|通话结束时间|双方名称"格式，避免和普通聊天抽取混淆。
            5. 除总结性结构化事件外，可以继续抽取承诺、偏好、关系变化、计划、事实等细粒度事件。
            6. 每个事件title必须以“视频通话-”开头，content必须包含“从视频通话中得知”。
            7. rawEvidenceText必须引用通话原文片段；总结性结构化事件可以引用通话开头、关键中段、挂断信息拼接而成。
            8. 节点应优先包含视频通话双方，以及通话中明确出现的人、地点、物品、概念。
            9. 关系只能抽取通话中明确表达的关系、偏好、承诺、计划、状态变化；不要用社会常识推断。
            10. 如果关系来自某个事件，事件、节点和关系的命名要能互相召回：事件写明“视频通话”，节点保留原实体名，关系原因写明“视频通话中……”。
            11. eventTime优先使用通话内容对应时间；无法定位时使用挂断时间或null。
        """.trimIndent()

        // 计算挂断时间差(分钟)
        val timeDiffMinutes = (hangupTimestamp - lastMessageTimestamp) / (1000 * 60)
        
        // 构建挂断时间提示
        val hangupTimeInfo = if (isUserHangup && timeDiffMinutes > 0) {
            "${userProfile.nickname}在你们说最后一句话后的${timeDiffMinutes}分钟后挂断了电话。"
        } else if (isUserHangup) {
            "${userProfile.nickname}主动挂断了电话。"
        } else {
            "${contact.realName}主动挂断了电话。"
        }
        
        val userPrompt = """
            **角色信息**
            *   AI角色: ${contact.realName} (${contact.persona})
            *   用户角色: ${userProfile.nickname}

            **通话记录**
            ```
            $transcript
            ```

            **挂断信息**
            $hangupTimeInfo

            请根据以上信息抽取视频通话结构化事件、节点和关系。events数组必须至少包含一条“视频通话-通话总结：”事件，且该事件必须写明通话起止、持续时长、大致内容、大致情绪变化。
        """.trimIndent()

        return listOf(
            ChatMessagePayload(role = "system", content = systemPrompt),
            ChatMessagePayload(role = "user", content = userPrompt)
        )
    }

    /**
     * 为视频通话结束后的场景构建完整的消息负载
     * 支持正常结束和异常中断两种场景
     *
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param history 对话历史
     * @param worldBookPrompts 世界书设定列表
     * @param longTermMemories 长期记忆列表
     * @param availableStickers 可用表情列表
     * @param breakLimitContent 突破限制内容
     * @param allRecentFeeds 最近动态列表
     * @param lastMessageTimestamp 最后一条消息时间戳
     * @param hangupTimestamp 挂断时间戳
     * @param isUserHangup 是否为用户主动挂断
     * @param novelaiEnabled 是否启用NovelAI
     * @param isInterrupted 是否为异常中断（默认false表示正常结束）
     * @param currentTimestamp 当前时间戳（仅异常中断时使用，表示用户恢复应用的时间）
     * @param appointments 约定倒计时列表
     * @param generalMemories 回忆列表
     * @return 消息负载列表
     */
    fun buildPostVideoCallPromptPayloads(
        contact: PersonProfile,
        userProfile: UserProfile,
        history: List<ChatMessage>,
        worldBookPrompts: List<String>,
        longTermMemories: List<LongTermMemory>,
        availableStickers: List<StickerEntity>,
        breakLimitContent: String,
        allRecentFeeds: List<FeedEntity>,
        lastMessageTimestamp: Long,
        hangupTimestamp: Long,
        isUserHangup: Boolean,
        novelaiEnabled: Boolean,
        isInterrupted: Boolean = false,
        currentTimestamp: Long = System.currentTimeMillis(),
        appointments: List<AppointmentEntity> = emptyList(),
        generalMemories: List<GeneralMemoryEntity> = emptyList()
    ): List<ChatMessagePayload> {
        val messages = mutableListOf<ChatMessagePayload>()

        // 1. 构建与线上模式一致的完整系统提示词
        val initialPrompt = OnlinePromptBuilder.buildInitialPrompt(
            contact,
            userProfile,
            worldBookPrompts,
            longTermMemories,
            null,
            availableStickers,
            "", // 线上模式不使用独立文风
            allRecentFeeds,
            lastMessageTimestamp,
            appointments,
            generalMemories
        )
        if (breakLimitContent.isNotBlank()) {
            messages.add(ChatMessagePayload(role = "system", content = breakLimitContent))
        }

        val commandListPrompt = OnlinePromptBuilder.buildCommandListPrompt(contact, novelaiEnabled)
        val instructionPrompt = OnlinePromptBuilder.buildInstructionPrompt(isPropel = false) // 非推进模式

        // 2. 组装成用户消息,包含所有设定和指令
        val combinedUserPrompt = "$initialPrompt\n\n$commandListPrompt\n\n$instructionPrompt"
        messages.add(ChatMessagePayload(role = "user", content = combinedUserPrompt))

        // 3. 添加对话历史
        val historyPayloads = PromptComponentBuilder.buildHistoryPayloads(
            history,
            contact,
            userProfile,
            longTermMemories
        )
        messages.addAll(historyPayloads.payloads)

        // 4. 根据场景构建不同的时间提示和指令
        val (timeInfo, instruction) = if (isInterrupted) {
            // 异常中断场景
            val elapsedMinutes = ((currentTimestamp - hangupTimestamp) / (1000 * 60)).toInt()
            val interruptionTimeInfo = if (elapsedMinutes > 0) {
                "视频通话在${elapsedMinutes}分钟前异常中断了（可能是应用崩溃或网络问题）。${userProfile.nickname}刚刚恢复了应用。"
            } else {
                "视频通话刚才异常中断了（可能是应用崩溃或网络问题）。"
            }
            val interruptedInstruction = "[系统指令:${interruptionTimeInfo}请你以角色的口吻,向用户发送一两条消息,询问对方是否还在、表达关心,或者自然地继续之前的话题。不要直接说“应用崩溃”这样的技术性词语,可以用“突然断了”、“信号不好”等自然的说法。]"
            Pair(interruptionTimeInfo, interruptedInstruction)
        } else {
            // 正常结束场景
            val timeDiffMinutes = (hangupTimestamp - lastMessageTimestamp) / (1000 * 60)
            val hangupTimeInfo = if (isUserHangup && timeDiffMinutes > 0) {
                "${userProfile.nickname}在你们说最后一句话后的${timeDiffMinutes}分钟后挂断了电话。"
            } else if (isUserHangup) {
                "${userProfile.nickname}主动挂断了电话。"
            } else {
                "${contact.realName}主动挂断了电话。"
            }
            val postCallInstruction = "[系统指令:视频通话刚刚结束。${hangupTimeInfo}请你以角色的口吻,向用户主动发送一两条消息,来自然地总结这次通话的要点、确认达成的约定,或者表达你的感受。如果你输出 voice_message，content 必须直接包含小米 MiMo 音频标签原文，例如 [happy]刚才和你聊天很开心[pause]，不要拆分普通文本和 TTS 文本。]"
            Pair(hangupTimeInfo, postCallInstruction)
        }

        // 5. 添加特定指令
        messages.add(ChatMessagePayload(role = "user", content = instruction))

        return messages
    }

    /**
     * 为用户拒接视频通话后的场景构建完整的消息负载
     * 
     * @param contact 联系人信息
     * @param userProfile 用户资料
     * @param history 对话历史
     * @param reason 拒绝理由
     * @param worldBookPrompts 世界书设定列表
     * @param longTermMemories 长期记忆列表
     * @param availableStickers 可用表情列表
     * @param breakLimitContent 突破限制内容
     * @param allRecentFeeds 最近动态列表
     * @param novelaiEnabled 是否启用NovelAI
     * @param appointments 约定倒计时列表
     * @param generalMemories 回忆列表
     * @return 消息负载列表
     */
    fun buildDeclinedVideoCallPromptPayloads(
        contact: PersonProfile,
        userProfile: UserProfile,
        history: List<ChatMessage>,
        reason: String,
        worldBookPrompts: List<String>,
        longTermMemories: List<LongTermMemory>,
        availableStickers: List<StickerEntity>,
        breakLimitContent: String,
        allRecentFeeds: List<FeedEntity>,
        novelaiEnabled: Boolean,
        appointments: List<AppointmentEntity> = emptyList(),
        generalMemories: List<GeneralMemoryEntity> = emptyList()
    ): List<ChatMessagePayload> {
        val messages = mutableListOf<ChatMessagePayload>()

        // 1. 构建与线上模式一致的完整系统提示词
        val lastMessageTimestamp = history.lastOrNull()?.timestamp ?: 0L
        val initialPrompt = OnlinePromptBuilder.buildInitialPrompt(
            contact,
            userProfile,
            worldBookPrompts,
            longTermMemories,
            null,
            availableStickers,
            "", // 线上模式不使用独立文风
            allRecentFeeds,
            lastMessageTimestamp,
            appointments,
            generalMemories
        )
        if (breakLimitContent.isNotBlank()) {
            messages.add(ChatMessagePayload(role = "system", content = breakLimitContent))
        }

        val commandListPrompt = OnlinePromptBuilder.buildCommandListPrompt(contact, novelaiEnabled)
        val instructionPrompt = OnlinePromptBuilder.buildInstructionPrompt(isPropel = false) // 非推进模式

        // 2. 组装成用户消息,包含所有设定和指令
        val combinedUserPrompt = "$initialPrompt\n\n$commandListPrompt\n\n$instructionPrompt"
        messages.add(ChatMessagePayload(role = "user", content = combinedUserPrompt))

        // 3. 添加对话历史
        val historyPayloads = PromptComponentBuilder.buildHistoryPayloads(
            history,
            contact,
            userProfile,
            longTermMemories
        )
        messages.addAll(historyPayloads.payloads)

        // 4. 添加视频通话被拒接后的特定指令
        val declineInstruction = "[系统指令:你发起的视频通话被用户拒绝了。拒绝理由是:“$reason”。请你以角色的口吻,对此作出回应。如果你输出 voice_message，content 必须直接包含小米 MiMo 音频标签原文，例如 [sad]那我晚点再找你[pause]，不要拆分普通文本和 TTS 文本。]"
        messages.add(ChatMessagePayload(role = "user", content = declineInstruction))

        return messages
    }
}