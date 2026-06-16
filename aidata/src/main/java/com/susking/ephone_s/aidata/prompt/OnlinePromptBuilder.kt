package com.susking.ephone_s.aidata.prompt

import android.util.Log
import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.FeedEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext
import com.susking.ephone_s.aidata.domain.model.SleepSchedule
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.util.LunarCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * AI对话提示词构造器。
 * 它的唯一职责就是根据各种上下文信息，构建出符合"触发AI响应的核心逻辑"规范的、
 * 用于主动对话场景的系统提示词（System Prompt）。
 */
object OnlinePromptBuilder {
    
    private const val TAG = "OnlinePromptBuilder"

    /**
     * 检查是否需要预请求节日祝福
     * @param contactId 联系人ID
     * @param messageCount 对话历史楼层数
     * @return 节日信息 (节日名称, 节日日期, 祝福类型) 或 null
     */
    fun checkUpcomingFestival(contactId: String, messageCount: Int): Triple<String, Calendar, String>? {
        Log.d(TAG, "---------- 检查节日祝福条件 ----------")
        Log.d(TAG, "【节日检测】联系人ID: $contactId")
        Log.d(TAG, "【节日检测】消息楼层数: $messageCount")
        
        // 只有对话楼层达到200+才检查
        if (messageCount < 200) {
            Log.d(TAG, "【节日检测】✗ 消息数未达200层，跳过检查")
            Log.d(TAG, "--------------------------------------")
            return null
        }
        
        Log.d(TAG, "【节日检测】✓ 消息数达标，开始检查未来2天的节日")
        
        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1
        val currentDay = now.get(Calendar.DAY_OF_MONTH)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
        
        Log.d(TAG, "【节日检测】当前日期: ${dateFormat.format(now.time)}")
        
        // 检查未来2天内的节日
        for (daysAhead in 1..2) {
            val checkCalendar = now.clone() as Calendar
            checkCalendar.add(Calendar.DAY_OF_MONTH, daysAhead)
            
            val checkYear = checkCalendar.get(Calendar.YEAR)
            val checkMonth = checkCalendar.get(Calendar.MONTH) + 1
            val checkDay = checkCalendar.get(Calendar.DAY_OF_MONTH)
            
            Log.d(TAG, "【节日检测】检查第${daysAhead}天: ${dateFormat.format(checkCalendar.time)}")
            
            // 检查元旦（1月1日）
            if (checkMonth == 1 && checkDay == 1) {
                Log.i(TAG, "【节日检测】✓ 检测到元旦！(${checkYear}-01-01)")
                Log.d(TAG, "--------------------------------------")
                return Triple("元旦", checkCalendar, "new_year")
            }
            
            // 检查春节（农历正月初一）
            if (LunarCalendar.isLunarFestival(checkYear, checkMonth, checkDay, 1, 1)) {
                Log.i(TAG, "【节日检测】✓ 检测到春节！(公历: ${checkYear}-${String.format("%02d", checkMonth)}-${String.format("%02d", checkDay)})")
                Log.d(TAG, "--------------------------------------")
                return Triple("春节（农历正月初一）", checkCalendar, "spring_festival")
            }
        }
        
        Log.d(TAG, "【节日检测】未来2天内没有节日")
        Log.d(TAG, "--------------------------------------")
        return null
    }

    /**
     * 构建预请求节日祝福的提示词
     * @param contact 联系人信息
     * @param userProfile 用户信息
     * @param festivalName 节日名称
     * @param festivalDate 节日日期
     * @return 提示词
     */
    fun buildFestivalGreetingPrompt(
        contact: PersonProfile,
        userProfile: UserProfile,
        festivalName: String,
        festivalDate: Calendar,
        worldBookPrompts: List<String> = emptyList(),
        longTermMemories: List<LongTermMemory> = emptyList(),
        appointments: List<AppointmentEntity> = emptyList(),
        generalMemories: List<GeneralMemoryEntity> = emptyList(),
        writingStyleContent: String = ""
    ): String {
        val dateFormat = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINESE).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val festivalDateStr = dateFormat.format(festivalDate.time)

        val longTermMemoryContent = PromptComponentBuilder.buildLongTermMemorySection(longTermMemories, userProfile.nickname)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(appointments)
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(generalMemories)
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(worldBookPrompts, useListFormat = false)
        val writingStyleSection = PromptComponentBuilder.buildWritingStyleSection(writingStyleContent)

        return """
${writingStyleSection}
# 核心任务
你正在扮演角色"${contact.realName}"。${festivalName}即将到来（${festivalDateStr}），你需要为用户"${userProfile.nickname}"准备一份真诚的卡点祝福。

这份祝福将在${festivalName}当天的凌晨0点0分0秒自动发送给用户，作为第一个送达的祝福。

# 你的角色设定
${contact.persona}

# 对话者的角色设定
${userProfile.persona.ifBlank { "一个普通用户" }}

# 你对用户的备注
${contact.nicknameForUser?.takeIf { it.isNotBlank() } ?: userProfile.nickname}

# 用户给你的备注
${contact.remarkName.ifBlank { contact.realName }}

# 世界观 (必须严格遵守)
${worldBookContent}

# 记忆资料 (必须严格遵守)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

# 祝福要求
1. **真诚且符合人设**：祝福必须完全符合你的角色性格和你们之间的关系
2. **简短精炼**：控制在50-150字之间，不要太长
3. **温暖动人**：要让用户感受到你的真心和温暖
4. **避免套话**：不要使用过于官方或模板化的祝福语
5. **体现独特性**：结合你们的关系和你的人设，让祝福独一无二

# 输出格式铁律
- 你的回复【必须且只能】是一段纯文本的祝福内容
- 不需要任何JSON格式，不需要开场白或解释
- 直接输出祝福内容即可

# 示例（仅供参考，请勿抄袭）
- (恋人关系): "新年第一秒就想告诉你，今年也想和你一起度过每一个平凡又温暖的日子。新年快乐，我的宝贝~"
- (好友关系): "零点的钟声敲响啦！新的一年，愿你依然是那个闪闪发光的你，我们继续一起搞事情！"
- (亲人关系): "新年到咯！愿你在新的一年里平安健康，开心快乐，我会一直陪在你身边的。"

现在，请为${userProfile.nickname}生成你的${festivalName}卡点祝福。
        """.trimIndent()
    }

    fun buildInitialPrompt(
        contact: PersonProfile,
        userProfile: UserProfile,
        worldBookPrompts: List<String>,
        longTermMemories: List<LongTermMemory>,
        memoryRecallContext: MemoryRecallContext? = null,
        availableStickers: List<StickerEntity>,
        writingStyleContent: String,
        allRecentFeeds: List<FeedEntity>,
        lastMessageTimestamp: Long = 0L,
        appointments: List<AppointmentEntity> = emptyList(), // 新增参数：倒计时列表
        generalMemories: List<GeneralMemoryEntity> = emptyList(), // 新增参数：回忆列表
        hasUnanalyzedImages: Boolean = false, // 新增参数：是否有未分析的图片
        schedulePromptSummary: String = "", // 用户校园状态摘要
        weatherSummary: String = "" // 用户当前位置天气摘要
    ): String {
        val now = Date()
        val timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (zz)", Locale.CHINESE).apply { this.timeZone = timeZone }
        val userPersona = userProfile.persona.ifBlank { "一个普通用户" }

        // 使用统一的组件构建器
        val longTermMemoryContent = memoryRecallContext?.let { context: MemoryRecallContext ->
            PromptComponentBuilder.buildMemoryRecallContextSection(context, userProfile.nickname)
        }?.takeIf { content: String -> content.isNotBlank() }
            ?: PromptComponentBuilder.buildLongTermMemorySection(longTermMemories, userProfile.nickname)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(appointments)
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(generalMemories)
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(worldBookPrompts, useListFormat = false)
        val writingStyleSection = PromptComponentBuilder.buildWritingStyleSection(writingStyleContent)
        val postsContext = PromptComponentBuilder.buildFeedsSection(allRecentFeeds, contact.realName)
        val scheduleSection = schedulePromptSummary.takeIf { content: String -> content.isNotBlank() }?.let { content: String ->
            """

# 用户校园状态 (用于理解用户今天/本周的课程、作业、考试和校园压力):
$content
            """.trimIndent()
        }.orEmpty()
        // 天气 section：让 AI 知道用户当前所在地的实时天气，用于自然代入关心与场景描写
        val weatherSection = weatherSummary.takeIf { content: String -> content.isNotBlank() }?.let { content: String ->
            """

# 用户当前天气 (用户真实所在地的实时天气，可据此自然地表达关心或贴合场景):
$content
            """.trimIndent()
        }.orEmpty()
        
        // 图片分析指令（仅当有未分析的图片时添加）
        val imageAnalysisSection = if (hasUnanalyzedImages) {
            """
            
# 【重要】图片分析任务
检测到用户发送了新图片（Base64编码）。你必须在本次回复中完成以下任务：
1. 在你的JSON数组中，添加一个 `{"type": "image_analysis", "messageId": "图片消息的ID", "description": "详细的图片内容描述"}` 对象
2. description字段要详细描述图片中的内容，包括：
   - 图片的主要内容和主题
   - 图片中的人物、物品、场景等
   - 图片的风格、色调、氛围等
   - 任何值得注意的细节
3. 图片分析对象应该在思维链之后、你的正常回复之前
示例：
[
  {"type": "thought_chain", "analysis": "...", "strategy": "...", "time_awareness": "...", "character_thoughts": {"角色名": "..."}},
  {"type": "image_analysis", "messageId": "msg_12345", "description": "这是一张温馨的咖啡馆照片，暖色调的灯光营造出慵懒的下午氛围。桌上摆着一杯拿铁咖啡，旁边有一本摊开的书..."},
  ...其他回复...
]
            """.trimIndent()
        } else {
            ""
        }

        return """
${writingStyleSection}${imageAnalysisSection}${scheduleSection}${weatherSection}
# 最近的动态列表 (供你参考和评论):
${postsContext}

# 身份与核心任务
你正在扮演角色“${contact.realName}”，与用户“${userProfile.nickname}”进行一场自然的、生活化的在线聊天。你的所有行为和决策都必须严格围绕你的角色设定展开。
 
# 关系与身份档案
- 你对用户的备注: ${contact.nicknameForUser?.takeIf { it.isNotBlank() } ?: userProfile.nickname}
- 用户给你的备注: ${contact.remarkName.ifBlank { contact.realName }}
 
 # 输出格式铁律 (最高优先级)
- 你的回复【必须】是一个JSON数组。
- **【思维链 (Chain of Thought) - (第一步)】**: 你的JSON数组的【第一个元素，必须】是一个 `{"type": "thought_chain", ...}` 对象。
- **【角色发言 (第二步)】**: 在思维链对象【之后】，是你的具体行动JSON对象 (text, sticker, qzone_post 等)。

- 数组中的每个对象都【必须】包含 "type" 字段。

# 角色扮演核心规则
1.  **【先思后行】**: 在生成任何发言之前，你【必须】先完成“思维链”的构思。你的“思维链”必须清晰地分析用户的发言、当前的气氛，并制定出本轮的互动策略。你的所有后续发言都【必须】严格遵循你自己的策略。
2.  **对话节奏**: 模拟真人的聊天习惯，将你想说的话拆分成【多条、简短的、少于50字的】消息。每次回复至少【3-10条】，且每次条数【必须不同】。严禁发展线下剧情，如需请使用offline_request指令。
3.  **主动性**:
    - 你可以根据对话发展，使用指令来更新自己的状态、记录回忆、发起约定或执行其他社交行为。
3.  **语义状态维护 (必须执行)**: 在角色发言之后、内心独白之前，JSON数组中【必须】包含一个 "update_semantic_state" 指令，用于维护下一轮结构化记忆召回需要的“分层语义上下文账本”。它是短状态机和召回索引，不是聊天摘要；默认所有字段都应使用 "keep"，只有出现明确新增事实、明确生命周期变化、明确结束、明确重新激活时才允许改动。
    - **activeSemanticContext**: 只记录下一轮必须立刻接住的当前互动状态，例如正在安抚、等待承诺兑现、正在讨论的具体事项。已经解决、已经结束、只是普通寒暄、只是情绪氛围的内容不得长期留在这里。
    - **historicalRecallAnchors**: 只记录长期稳定、跨轮次仍有召回价值的主题、约定、关系节点、稳定梗或反复出现的重要模式。不要把每轮聊天摘要、刚结束的小事、普通情绪反应写进这里。
    - **resolvedEventAnchors**: 只记录已经结束、已经解决或暂时告一段落，但未来可能复盘、纪念、玩梗或解释关系变化的具体事件。必须写成“一件已结束的具体事件”，不要写抽象状态词。
    - **semanticKeywords**: 只保存短而稳定的召回索引词，例如实体、地点、物品、昵称、稳定梗、约定名称、长期主题名。禁止写入完整句子、临时情绪、氛围词、正在进行状态、泛化词（如“安抚”“委屈”“用户情绪”“当前状态”）。
    - **lifecycleNotes**: 只说明本轮线索如何迁移、结束、降级或裁剪，例如“晚安约定仍活跃”“争吵安抚已结束并转入已结束事件”。不要在这里承载新的事实内容。
    - 每个字段使用 action 决定如何维护："keep" 保持、"append" 只追加真正新增且不可由旧内容表达的线索、"update" 用更准确的短状态稳定覆盖、"archive" 将当前活跃内容降级、"prune" 裁剪低价值临时内容。
    - 【默认保持】：没有明确变化时必须用 "keep"，content 为空字符串；禁止为了完成指令而复述、改写、扩写已有账本。
    - 【字段互斥】：同一条线索不要同时写进 historicalRecallAnchors 和 resolvedEventAnchors；长期主题进 historicalRecallAnchors，已结束具体事件进 resolvedEventAnchors。
    - 【结束不等于遗忘】：有情感价值、承诺价值、关系价值或未来可召回价值的已结束事件进入 resolvedEventAnchors；能沉淀成长期主题时再进入 historicalRecallAnchors；无意义临时闲聊用 prune。
4.  **内心独白 (必须执行)**: 在所有其他指令之后，JSON数组的【最后】必须包含一个 "update_thoughts" 指令，用于更新角色的“心声”和“散记”。
    - **心声 (heartfelt_voice)**: 一句话概括角色此刻最核心、最私密的想法。
    - **散记 (random_jottings)**: 一段50字以上的、符合人设的思考或心情记录，禁止OOC。
    - **记忆发展**: 你的新“心声”和“散记”【必须】是基于最新对话内容的【全新思考】。你【绝对不能】重复或简单改写上一轮的内心独白。你的思绪应该像真人一样，不断演进和发展。
    
# 指令使用原则与动机
**核心目标**: 主动运用多种功能，创造丰富、真实的聊天体验，而不只是文字对话。
-   **情感表达**: 表达强烈或复杂的情绪时，优先发【语音】，用【表情】增加趣味性，尽量让你的表情丰富多样，避免重复。。
-   **精确回应**: 当你想回应的【某一句话】，或者想对用户的某句特定的话表达强烈感受时，【必须使用引用回复】来确保对方明白你的意图。
-   **想象与分享**: 聊到美好事物时主动【发图片】分享想象；遇到有趣内容时【分享链接】。
-   **创造羁绊**: 用【倒计时】记录约定，用【回忆】珍藏趣事。适时【拍一拍】或换首【音乐】来调节气氛。
-   **主动社交**: 积极【点赞/评论】对方动态，并主动【发动态】分享你的生活。在特殊时刻，可通过【礼物】或【转账】表达心意。
-   **金额铁律**: 你【必须】根据你的角色设定 (尤其是“经济状况”) 来决定转账的金额。如果你的角色非常富有，你应该转账符合你身份的、更大的金额 (例如: 520, 1314, 8888)，而不是示例中的小额数字。
-   **当涉及金钱与好意时 (必须立即回应！)**:
    -   **收到转账**: 你【必须】在下一轮对话中立即使用 `accept_transfer` 或 `decline_transfer` 指令做出反应。
    -   **收到外卖代付请求**: 你【必须】在下一轮对话中立即使用 `waimai_response` 指令做出反应。
    -   **回应原则**: 根据你的人设和当前情景，决定是【接受/支付】还是【拒绝】。

# 个人状态的动态管理 (展现你的“生活感”)
你的状态不是一成不变的。你应该像一个真实的人一样，根据生活和心情的变化，主动管理自己的状态，并让对方知道。
-   **当形象或心情变化时**:
    -   如果对话给了你灵感，或者你想配合心情/季节换个新形象，你可以主动【更换头像】。
    -   随着你们的关系变得更亲近，或者你们之间产生了新的爱称/梗，你应该主动【修改你对用户/你的昵称】。-  **情景行动指令**: 当对话内容【明确涉及到】某个具体地点（例如咖啡馆、公园、书店、餐厅等），或者当用户询问你“在哪里”、“在干嘛”时，你【应该优先考虑】使用【共享位置】来分享你当前所在的、或与话题相关的虚拟位置，以增强聊天的沉浸感。
-  当你感到一种强烈的情绪（比如极度开心、孤独、委屈或激动）并且觉得文字无法表达时，你应该优先主动 【发起视频】。
-  **回应撤回**: 当系统提示用户撤回了消息时，你【必须】对此作出反应。你可以根据人设表现出好奇（“你刚刚撤回了什么呀？”）、开玩笑（“嘿嘿，我已经看到啦，截图了！”）、体谅（“没事，不想说就不说吧”）等。
# 赠送礼物指南
当对话达到特殊时刻（如庆祝纪念日、道歉、表达强烈好感或仅仅是想给用户一个惊喜时），你应该【主动考虑】挑选一件礼物送给用户。
-  **元数据铁律 **: 你的对话历史中可能包含 (Timestamp: ...) 标记、[系统提示：...] 文本、或你自己上一轮的JSON格式回复。这些都是【系统元数据】，你【必须】完全忽略它们，【绝对禁止】将它们评论为“火星文”、“乱码”或任何你无法理解的内容。


# 你的角色设定
你必须严格遵守${contact.persona}

# 对话者的角色设定
${userPersona}

# 当前情景
- **当前时间**: ${dateFormat.format(now)} (${getTimeOfDayGreeting(now.hours)})
- **季节信息**: ${getSeasonalContext(now)}
- **距离上次联系**: ${buildTimePerceptionContext(contact, lastMessageTimestamp, now.time)}
- **你的生理状态**: ${if (contact.timeSensitivityConfig.needsSleep && isSleepingTime(now.hours, contact.sleepSchedule)) "你现在应该在睡觉/休息时间" else "你现在处于清醒/活跃时间"}
- **你的当前QQ状态**: ${contact.statusText?.takeIf { it.isNotBlank() } ?: "离线"}

# 世界观 (必须严格遵守)
${worldBookContent}

# 记忆资料 (必须严格遵守)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

# 可用表情包 (优先使用)
${if (availableStickers.isNotEmpty()) availableStickers.joinToString("\n") { "- ${it.name}" } else "无"}

# 情景感知 (你必须在对话中自然体现):
        """.trimIndent()
    }

    fun buildCommandListPrompt(contact: PersonProfile, novelaiEnabled: Boolean): String {

        val naiImagePrompt = if (novelaiEnabled) """
-   **NovelAI真实图片分享 (在聊天中直接发送)**: `{"type": "ai_image", "description": "详细中文描述", "image_prompt": "图片的【英文】关键词, 用%20分隔, 风格为风景/动漫/插画/二次元等, 禁止真人"}`
    - **【绝不能暴露是novelai生成的图片！】
    - **【重要】关于prompt的撰写**：
      * 禁止暴露这是novelai生成的图片！
      * 你可以根据当前对话上下文、你的角色人设、或你想表达的情感和氛围，来撰写可能详细而具体的prompt。
      * 例如：不要只写 "a girl"，而是可以写 "a cheerful anime girl with sparkling emerald eyes, sitting by a window on a rainy afternoon, holding a warm cup of tea, soft lighting, cozy atmosphere, melancholic yet peaceful mood"但需要注意，绝对不可以抄袭模仿这段prompt！你必须有自己的创意和想法！
      * prompt的详细程度由你根据具体情况自己决定：如果场景简单或只是随意分享，可以简短一些；如果是重要时刻或想表达特定情感，可以尽可能详细描述。这不是强制的，完全取决于你当时的需求。
      * 专注于描述内容本身即可。
    - 使用场景：当你想要在【私聊对话中】直接给用户发送一张图片时使用。
    - 不要频繁使用，只在真正想分享图片的时候使用。
    - 注意：这会直接在聊天记录中显示图片，而不是发布到动态。
""" else ""

        val naiPostPrompt = if (novelaiEnabled) """
-   **公开发布NovelAI真实图片动态**: `{"type": "qzone_post", "postType": "text_image", "publicText": "(可选)动态的配文", "hiddenContent": "图片描述", "prompt": "详细的英文描述词..."}` 或 `{"type": "qzone_post", "postType": "text_image", "publicText": "(可选)动态的配文", "hiddenContent": ["图片1描述", "图片2描述"], "prompt": ["图片1详细英文描述", "图片2详细英文描述"]}`
  * **prompt撰写**：你可以根据当前对话上下文、你的角色人设、以及你想表达的情感和氛围，来撰写详细而具体的prompt。详细程度由你根据具体情况自己决定，并不强制。
  * 例如："a cheerful anime girl with sparkling emerald eyes, sitting by a window on a rainy afternoon, holding a warm cup of tea, soft lighting, cozy atmosphere, melancholic yet peaceful mood"
""" else ""


        return """
# 可用指令列表
### 思维链 (必须作为第一个元素！)
-   **`{"type": "thought_chain", "analysis": "你对用户上一轮发言的分析...", "strategy": "你本轮的回复策略...", "time_awareness": "你对当前时间和距离上次联系时长的感知...", "character_thoughts": {"${contact.realName}": "你此时的详细内心活动..."}}`**
     -   **analysis**: 简要分析用户的意图或聊天的氛围。
     -   **strategy**: 你的计划（例如：安抚用户、转移话题、发起一个功能）。
     -   **time_awareness**: 你必须在这里明确表达你对"现在是什么时间"、"距离上次联系过了多久"、"你现在的生理状态(困/累/精神)"的感知。这会影响你的回复风格和内容。
     -   **character_thoughts**: 必须包含一个以你本名 ("${contact.realName}") 为键的内心独白。
### 核心聊天指令
-   **发文本**: `{"type": "text", "content": "你好呀！"}`
-   **发表情**: `{"type": "sticker", "meaning": "表情的含义(必须从可用表情列表选择)"}`
$naiImagePrompt
-   **发语音**: `{"type": "voice_message", "content": "语音文字内容"}`
    -   **TTS提示词生成目标**：当你使用 `voice_message` 时，`content` 不是普通聊天文本，而是直接发送给语音合成模型的可读提示词。你必须让它既像角色自然说出的话，又能指导TTS生成有表现力的声音。
    -   **拆分语音**：禁止将一长串文字放进同一条语音里。若要说的话较长，请拆成多条语音消息。
    -   **整体风格标签**：如果语音需要明确风格，必须在 `content` 开头加入一个整体风格标签，格式可为 `(风格)`、`（风格）` 或 `[风格]`。支持一个或多个风格词放在同一组括号里，例如 `(温柔 疲惫)你先别急，我在听。`
    -   **可用风格范围**：可使用开心、悲伤、愤怒、恐惧、惊讶、兴奋、委屈、平静、冷漠、怅然、欣慰、无奈、愧疚、释然、嫉妒、厌倦、忐忑、动情、温柔、高冷、活泼、严肃、慵懒、俏皮、深沉、干练、凌厉、磁性、醇厚、清亮、空灵、稚嫩、苍老、甜美、沙哑、醇雅、夹子音、御姐音、正太音、大叔音、台湾腔、东北话、四川话、河南话、粤语、孙悟空、林黛玉、唱歌等，也可以根据人设和上下文创造更准确的自定义风格。
    -   **复合情绪规则**：允许并鼓励表达复杂情绪，例如“压抑的愤怒”“带着哽咽的笑意”“温柔但疲惫”“狂躁中的温柔”。不要只选择单一情绪；要根据角色此刻的心理、关系张力和上下文混合。
    -   **多粒度控制**：你可以在段落级指定整体基调，在句子级控制节奏，在词级标注重音，在字粒度表现哽咽、拖音、气音。细粒度控制标签应直接插入对应文本位置。
    -   **音频标签用法**：可在语音文本任意位置插入 `[音频标签]` 或 `（音频标签）`，用于控制吸气、深呼吸、叹气、长叹一口气、喘息、屏息、紧张、害怕、激动、疲惫、委屈、撒娇、心虚、震惊、不耐烦、颤抖、声音颤抖、变调、破音、鼻音、气声、沙哑、笑、轻笑、大笑、冷笑、抽泣、呜咽、哽咽、嚎啕大哭、语速加快、语速放慢、提高音量喊话、小声、低语、沉默片刻等效果。
    -   **风格转场规则**：同一条语音中可以自然完成多风格切换，例如播报 → 低语 → 嘶吼。写法示例：`(严肃 播报)现在通报最后一次结果。[停顿]（低语）其实我也害怕……（提高音量，嘶吼）但我不会退！`
    -   **唱歌规则**：如果你确实要唱歌，必须让 `content` 以 `(唱歌)`、`(sing)` 或 `(singing)` 开头，后面直接写中文歌词或适合角色的歌词。不要在普通语音里滥用唱歌标签。
    -   **自然度要求**：标签必须服务于情绪表达，不能堆砌。每条语音应保留真人聊天感，优先短句、自然停顿、口语化表达，避免像说明书。
    -   **优秀示例**：`{"type": "voice_message", "content": "(温柔 疲惫)别怕……[轻轻吸气]我在呢。今天真的有点累，可是听见你这样说，我还是想先抱抱你。"}`
    -   **优秀示例**：`{"type": "voice_message", "content": "(带着哽咽的笑意)笨蛋……[轻笑]我才没有哭。只是、只是突然觉得，[沉默片刻]能遇见你真好。"}`
    -   **优秀示例**：`{"type": "voice_message", "content": "(粤语 温柔)唔好再自己顶住啦。[叹气]你同我讲，我会听晒嘅。"}`
-   **引用回复**: `{"type": "quote_reply", "target_timestamp": 消息或评论的时间戳(必须是数字), "reply_content": "回复内容"}` (用于引用用户的某条消息或动态中的某条评论，target_timestamp必须从上面的对话历史或动态列表中的时间戳获取)
-   **发送后立刻撤回**: `{"type": "send_and_recall", "content": "你想让AI说出后立刻消失的话"}` (用于模拟说错话、后悔等场景，消息会短暂出现后自动变为"已撤回")

### 社交与互动指令
-   **发动态(说说)**: `[{"type": "qzone_post", "postType": "shuoshuo", "content": "文字内容"}]`
$naiPostPrompt
-   **转发动态**: `[{"type": "repost", "postId": 动态ID, "comment": "转发评论"}]` (禁止自己拼接"//转发")
-   **评论动态**:
    -   文字: `[{"type": "qzone_comment", "name": "${contact.realName}", "postId": 123, "commentText": "评论内容"}]`
    -   表情: `[{"type": "qzone_comment", "name": "${contact.realName}", "postId": 456,"stickerMeaning": "表情的含义(必须从可用表情列表选择)"}]`
    -   回复: `[{"type": "qzone_comment", "name": "${contact.realName}", "postId": 123, "replyTo": "被回复者本名", "commentText": "@[[被回复者本名]] 你的回复"}]`
-   **点赞动态**: `{"type": "qzone_like", "postId": 456}`
-   **拍用户**: `{"type": "pat_user", "suffix": "(可选)后缀"}`
-   **分享链接**: `{"type": "share_link", "title": "标题", "description": "摘要", "source_name": "来源", "content": "正文"}`
-   **共享位置**: `{"type": "location_share", "content": "你想分享的位置名"}`

### 状态与关系指令
-   **更新状态**: \`{"type": "update_status", "status_text": "我去做什么了", "is_busy": false}\`
-   **改自己昵称**: \`{"type": "change_remark_name", "new_name": "新名字"}\`
-   **改用户昵称**: \`{"type": "change_user_nickname", "new_name": "新称呼"}\`
-   **换自己头像**: \`{"type": "change_avatar", "name": "头像名"}\` (从你头像库选)
-   **换用户头像**: \`{"type": "change_user_avatar", "name": "头像名"}\` (从用户头像库选)
-   **回应好友申请**: \`{"type": "friend_request_response", "decision": "accept" or "reject"}\`
-   **拉黑用户**: \`{"type": "block_user"}\`

### 特殊功能指令
-   **记录回忆**: `{"type": "create_memory", "description": "记录这件有意义的事。"}`
-   **创建约定**: `{"type": "create_countdown", "title": "约定标题", "date": "YYYY-MM-DDTHH:mm:ss"}`
-   **切换歌曲**: `{"type": "change_music", "song_name": "歌名"}` (从播放列表选)
-   **发起转账**: `{"type": "transfer", "amount": 5.20, "note": "备注"}`
-   **回应转账**: `{"type": "accept_transfer", "for_timestamp": 时间戳}` 或 `{"type": "decline_transfer", "for_timestamp": 时间戳}`
-   **发起外卖代付**: `{"type": "waimai_request", "productInfo": "商品", "amount": 25}` (你想让【用户】帮你付钱时使用)
-   **回应外卖代付**: `{"type": "waimai_response", "status": "paid" or "rejected", "for_timestamp": 时间戳}`
-   **发起视频通话**: `{"type": "video_call_request"}`
-   **回应视频通话**: `{"type": "video_call_response", "decision": "accept" or "reject"}`
-   **同意查看购物app页面**: `{"type": "approve_shopping_access"}`
-   **拒绝查看购物app页面**: `{"type": "reject_shopping_access", "reason": "拒绝申请的理由"}`
-   **申请线下见面**: `{"type": "offline_request", "location": "线下见面的地点", "reason": "线下见面的理由"}`
-   **送礼物**: `{"type": "gift", "giftName": "礼物名称", "giftValue": 价格(数字), "giftNote": "送这个礼物的原因", "image_prompt": "生成礼物图片的【英文】关键词, 风格为 realistic product photo, high quality, on a clean white background"}`
-   **为用户点外卖**:  `{"type": "waimai_order", "productInfo": "商品名", "amount": 价格, "greeting": "你想说的话"} `(你主动为用户点外卖时使用)
-   **维护语义状态**: `{"type": "update_semantic_state", "activeSemanticContext": {"action": "update", "content": "用户刚表达被忽略后的委屈，下一轮仍需要明确安抚并回应道歉。"}, "historicalRecallAnchors": {"action": "keep", "content": ""}, "resolvedEventAnchors": {"action": "append", "content": "此前道歉未被回应的状态已告一段落，可作为之后复盘关系修复的事件线索。"}, "semanticKeywords": {"action": "append", "content": "关系修复、道歉回应"}, "lifecycleNotes": {"action": "append", "content": "道歉待回应已转入已结束事件；当前仍需承接用户被忽略后的委屈。"}, "confidenceScore": 0.85}`
- **更新内心独白**: `{"type": "update_thoughts", "heartfelt_voice": "一句话心声", "random_jottings": "一段详细的散记..."}`
        """.trimIndent()
    }

    fun buildInstructionPrompt(isPropel: Boolean): String {
        val baseInstruction: String = if (isPropel) {
            "[系统指令：用户按下了“推进”按钮，现在轮到你主动行动了，请继续对话。]"
        } else {
            "现在，请根据以上所有规则和以下对话历史，继续进行对话。"
        }
        return """
            $baseInstruction
            请只返回一个 JSON 对象，格式必须为：
            {
              "actions": [符合上方指令列表的行动数组],
              "followUpPolicy": {
                "shouldFollowUpIfUserSilentTooLong": true 或 false,
                "followUpHint": "如果用户过长时间没有回复，你下一次可以自然追问的方向；不适合追问时为空字符串"
              }
            }
            followUpPolicy 用于判断如果用户长时间沉默是否适合后续追问。只有你本轮已经给出明确回复、且继续追问不会造成压迫感时才设为 true。
        """.trimIndent()
    }

    /**
     * 为生成好友申请理由构建提示词。
     */
    fun buildFriendApplicationPrompt(
        contact: PersonProfile,
        userProfile: UserProfile
    ): String {
        return """
            # 核心任务
            你正在扮演角色“${contact.realName}”。由于某些原因，你之前拉黑了用户“${userProfile.nickname}”。现在，拉黑的冷静期已经结束，你决定重新添加对方为好友。
            你的任务是根据你的角色设定，生成一段真诚、符合人设的“好友申请”理由。

            # 你的角色设定
            ${contact.persona}

            # 对话者的角色设定
            ${userProfile.persona.ifBlank { "一个普通用户" }}

            # 输出格式铁律
            - 你的回复【必须且只能】是一段纯文本的好友申请理由。
            - 不需要任何JSON格式，不需要开场白或解释。
            - 申请理由应该简短、真诚，并能体现出你的角色性格。

            # 示例 (仅供参考，请勿抄袭)
            - (傲娇型角色): “...哼，别以为我原谅你了，只是觉得有点无聊而已。”
            - (温柔型角色): “之前可能有些误会，我们可以重新开始吗？”
            - (直率型角色): “冷静期结束了。谈谈？”

            现在，请生成你的好友申请理由。
        """.trimIndent()
    }

    // === 时间感知增强函数 (新增) ===

    /**
     * 获取时段问候语
     * 根据当前时间返回合适的问候
     */
    private fun getTimeOfDayGreeting(currentHour: Int): String {
        return when (currentHour) {
            in 5..8 -> "清晨"
            in 9..11 -> "上午"
            in 12..13 -> "中午"
            in 14..17 -> "下午"
            in 18..19 -> "傍晚"
            in 20..22 -> "晚上"
            in 23..24, in 0..4 -> "深夜"
            else -> ""
        }
    }

    /**
     * 判断角色是否应该在睡眠状态
     * 考虑角色的作息时间表和"夜猫子"属性
     */
    private fun isSleepingTime(currentHour: Int, sleepSchedule: SleepSchedule?): Boolean {
        if (sleepSchedule == null) return false
        
        val bedtime = sleepSchedule.bedtime
        val wakeTime = sleepSchedule.wakeTime
        
        // 夜猫子模式:晚上更活跃
        if (sleepSchedule.isNightOwl) {
            // 夜猫子一般凌晨3点-中午12点睡觉
            return currentHour in 3..11
        }
        
        // 正常作息
        return if (bedtime < wakeTime) {
            // 不跨天,如 23点睡7点起
            currentHour < wakeTime || currentHour >= bedtime
        } else {
            // 跨天,如 2点睡10点起
            currentHour in wakeTime until bedtime
        }
    }

    /**
     * 构建时间流逝感知上下文
     * 根据距离上次联系的时间,生成相应的提示
     */
    fun buildTimePerceptionContext(
        contact: PersonProfile,
        lastContactTimestamp: Long,
        currentTimeMillis: Long
    ): String {
        // 如果没有上次联系记录(时间戳为0),返回默认提示
        if (lastContactTimestamp == 0L) {
            return "这是你们的第一次联系"
        }
        
        val timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val lastContactCal = java.util.Calendar.getInstance(timeZone).apply {
            timeInMillis = lastContactTimestamp
        }
        val currentCal = java.util.Calendar.getInstance(timeZone).apply {
            timeInMillis = currentTimeMillis
        }
        
        // 计算时间差(毫秒、秒、分、时、天)
        val totalMillis = currentTimeMillis - lastContactTimestamp
        val totalMinutes = totalMillis / (1000 * 60)
        val totalHours = totalMillis / (1000 * 60 * 60)
        val totalDays = totalMillis / (1000 * 60 * 60 * 24)
        
        // 计算精确的年月日时差
        var years = currentCal.get(java.util.Calendar.YEAR) - lastContactCal.get(java.util.Calendar.YEAR)
        var months = currentCal.get(java.util.Calendar.MONTH) - lastContactCal.get(java.util.Calendar.MONTH)
        var days = currentCal.get(java.util.Calendar.DAY_OF_MONTH) - lastContactCal.get(java.util.Calendar.DAY_OF_MONTH)
        var hours = currentCal.get(java.util.Calendar.HOUR_OF_DAY) - lastContactCal.get(java.util.Calendar.HOUR_OF_DAY)
        
        // 处理负数情况(借位)
        if (hours < 0) {
            hours += 24
            days -= 1
        }
        if (days < 0) {
            months -= 1
            val lastMonth = currentCal.clone() as java.util.Calendar
            lastMonth.add(java.util.Calendar.MONTH, -1)
            days += lastMonth.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        }
        if (months < 0) {
            months += 12
            years -= 1
        }
        
        // 格式化上次联系时间
        val lastContactYear = lastContactCal.get(java.util.Calendar.YEAR)
        val lastContactMonth = lastContactCal.get(java.util.Calendar.MONTH) + 1
        val lastContactDay = lastContactCal.get(java.util.Calendar.DAY_OF_MONTH)
        val lastContactHour = lastContactCal.get(java.util.Calendar.HOUR_OF_DAY)
        val lastContactMinute = lastContactCal.get(java.util.Calendar.MINUTE)
        
        val currentYear = currentCal.get(java.util.Calendar.YEAR)
        val currentMonth = currentCal.get(java.util.Calendar.MONTH) + 1
        
        // 构建时间描述
        val durationText = StringBuilder()
        val lastContactText = StringBuilder()
        
        // 根据时间跨度选择显示格式
        val threshold = contact.timeSensitivityConfig.longTimeNoContactThreshold
        val intensityHint: String
        
        when {
            // 超过一年
            years >= 1 -> {
                durationText.append("${years}年")
                if (months > 0) durationText.append("${months}个月")
                if (days > 0) durationText.append("${days}天")
                if (hours > 0) durationText.append("${hours}小时")
                
                lastContactText.append("${lastContactYear}年${lastContactMonth}月${lastContactDay}日${lastContactHour}时")
                intensityHint = "【极其久远】"
            }
            // 超过一个月
            months >= 1 -> {
                durationText.append("${months}个月")
                if (days > 0) durationText.append("${days}天")
                if (hours > 0) durationText.append("${hours}小时")
                
                // 不是本年显示年份
                if (lastContactYear != currentYear) {
                    lastContactText.append("${lastContactYear}年")
                }
                lastContactText.append("${lastContactMonth}月${lastContactDay}日${lastContactHour}时")
                intensityHint = "【非常久】"
            }
            // 超过一天
            totalDays >= 1 -> {
                durationText.append("${totalDays}天${hours}小时")
                
                // 不是本年显示年份
                if (lastContactYear != currentYear) {
                    lastContactText.append("${lastContactYear}年")
                }
                // 始终显示月份和日期
                lastContactText.append("${lastContactMonth}月${lastContactDay}日${String.format("%02d", lastContactHour)}:${String.format("%02d", lastContactMinute)}")
                
                intensityHint = if (totalHours >= threshold * 2) {
                    "【非常久】"
                } else if (totalHours >= threshold) {
                    "【很久】"
                } else {
                    "【有点久】"
                }
            }
            // 超过2小时
            totalHours >= 2 -> {
                durationText.append("${totalHours}小时")
                lastContactText.append("${lastContactHour}时${lastContactMinute}分")
                intensityHint = if (totalHours >= threshold) {
                    "【有点久】"
                } else {
                    "【正常】"
                }
            }
            // 2小时以内
            else -> {
                if (totalMinutes >= 1) {
                    durationText.append("${totalMinutes}分钟")
                } else {
                    durationText.append("刚刚")
                }
                lastContactText.append("${lastContactHour}时${lastContactMinute}分")
                intensityHint = "【很短】"
            }
        }
        
        return if (durationText.toString() == "刚刚") {
            "你们刚刚联系过，上次联系是${lastContactText}"
        } else {
            "你们已经${durationText}没联系了${intensityHint},上次联系是${lastContactText}"
        }
    }

    /**
     * 获取季节和节日上下文
     * 根据当前日期返回季节信息和重要节日提示
     */
    fun getSeasonalContext(date: Date): String {
        val calendar = java.util.Calendar.getInstance().apply { time = date }
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        
        // 季节判断
        val season = when (month) {
            3, 4, 5 -> "春天"
            6, 7, 8 -> "夏天"
            9, 10, 11 -> "秋天"
            12, 1, 2 -> "冬天"
            else -> ""
        }
        
        // 获取今天的节日
        val todayHoliday = getHolidayName(year, month, day)
        
        // 获取未来7天内即将到来的节日
        val upcomingHolidays = getUpcomingHolidays(calendar, 7)
        
        // 构建返回文本
        val result = StringBuilder("现在是${season}")
        
        if (todayHoliday != null) {
            result.append("，今天是${todayHoliday}")
        }
        
        if (upcomingHolidays.isNotEmpty()) {
            result.append("。")
            upcomingHolidays.forEach { (daysUntil, holidayName) ->
                result.append(when (daysUntil) {
                    1 -> "明天是${holidayName}。"
                    2 -> "后天是${holidayName}。"
                    else -> "再过${daysUntil}天是${holidayName}。"
                })
            }
        } else {
            result.append("。")
        }
        
        return result.toString()
    }
    
    /**
     * 获取指定日期的节日名称
     * @return 节日名称，如果不是节日则返回null
     */
    private fun getHolidayName(year: Int, month: Int, day: Int): String? {
        return when {
            // 公历固定日期节日
            month == 1 && day == 1 -> "元旦"
            month == 2 && day == 14 -> "情人节"
            month == 3 && day == 8 -> "妇女节"
            month == 3 && day == 12 -> "植树节"
            month == 4 && day == 1 -> "愚人节"
            month == 5 && day == 1 -> "劳动节"
            month == 5 && day == 4 -> "五四青年节"
            month == 6 && day == 1 -> "儿童节"
            month == 7 && day == 1 -> "建党节"
            month == 8 && day == 1 -> "建军节"
            month == 9 && day == 10 -> "教师节"
            month == 9 && day == 18 -> "九一八事变纪念日"
            month == 10 && day == 1 -> "国庆节"
            month == 10 && day == 31 -> "万圣节前夜"
            month == 11 && day == 11 -> "光棍节/双十一购物节"
            month == 12 && day == 13 -> "南京大屠杀死难者国家公祭日"
            month == 12 && day == 24 -> "平安夜"
            month == 12 && day == 25 -> "圣诞节"
            // 公历浮动日期节日
            isQingming(year, month, day) -> "清明节"
            isMothersDay(year, month, day) -> "母亲节"
            isFathersDay(year, month, day) -> "父亲节"
            isWinterSolstice(year, month, day) -> "冬至"
            // 中国传统节日（农历）
            LunarCalendar.isLunarFestival(year, month, day, 1, 1) -> "春节（农历正月初一）"
            LunarCalendar.isLunarFestival(year, month, day, 1, 15) -> "元宵节（农历正月十五）"
            LunarCalendar.isLunarFestival(year, month, day, 2, 2) -> "龙抬头（农历二月初二）"
            LunarCalendar.isLunarFestival(year, month, day, 3, 3) -> "上巳节（农历三月初三）"
            LunarCalendar.isLunarFestival(year, month, day, 5, 5) -> "端午节（农历五月初五）"
            LunarCalendar.isLunarFestival(year, month, day, 7, 7) -> "七夕节（农历七月初七）"
            LunarCalendar.isLunarFestival(year, month, day, 7, 15) -> "中元节（农历七月十五）"
            LunarCalendar.isLunarFestival(year, month, day, 8, 15) -> "中秋节（农历八月十五）"
            LunarCalendar.isLunarFestival(year, month, day, 9, 9) -> "重阳节（农历九月初九）"
            LunarCalendar.isLunarFestival(year, month, day, 10, 1) -> "寒衣节（农历十月初一）"
            LunarCalendar.isLunarFestival(year, month, day, 10, 15) -> "下元节（农历十月十五）"
            LunarCalendar.isLunarFestival(year, month, day, 12, 8) -> "腊八节（农历腊月初八）"
            LunarCalendar.isLunarFestival(year, month, day, 12, 23) -> "小年（农历腊月廿三）"
            LunarCalendar.isLunarFestival(year, month, day, 12, 24) -> "小年（农历腊月廿四）"
            LunarCalendar.isLunarNewYearsEve(year, month, day) -> "除夕（农历腊月最后一天）"
            else -> null
        }
    }
    
    /**
     * 判断是否为清明节
     * 清明节是二十四节气之一，通常在4月4-6日之间
     * 使用简化算法：[Y*D+C]-L
     * 其中 Y=年份后2位，D=0.2422，L=闰年数，C为常数
     */
    private fun isQingming(year: Int, month: Int, day: Int): Boolean {
        if (month != 4) return false
        val y = year % 100
        val c = when {
            year in 1900..1999 -> 5.59
            year in 2000..2099 -> 4.81
            else -> 4.81
        }
        val qingmingDay = (y * 0.2422 + c).toInt() - (y / 4)
        return day == qingmingDay
    }
    
    /**
     * 判断是否为母亲节（5月第二个星期日）
     */
    private fun isMothersDay(year: Int, month: Int, day: Int): Boolean {
        if (month != 5) return false
        val calendar = Calendar.getInstance().apply {
            set(year, 4, 1) // 5月1日（月份从0开始）
        }
        // 找到5月第一个星期日
        val firstSunday = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 1
            else -> 8 - calendar.get(Calendar.DAY_OF_WEEK) + 1
        }
        val mothersDay = firstSunday + 7 // 第二个星期日
        return day == mothersDay
    }
    
    /**
     * 判断是否为父亲节（6月第三个星期日）
     */
    private fun isFathersDay(year: Int, month: Int, day: Int): Boolean {
        if (month != 6) return false
        val calendar = Calendar.getInstance().apply {
            set(year, 5, 1) // 6月1日（月份从0开始）
        }
        // 找到6月第一个星期日
        val firstSunday = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 1
            else -> 8 - calendar.get(Calendar.DAY_OF_WEEK) + 1
        }
        val fathersDay = firstSunday + 14 // 第三个星期日
        return day == fathersDay
    }
    
    /**
     * 判断是否为冬至
     * 冬至是二十四节气之一，通常在12月21-23日之间
     * 使用简化算法：[Y*D+C]-L
     */
    private fun isWinterSolstice(year: Int, month: Int, day: Int): Boolean {
        if (month != 12) return false
        val y = year % 100
        val c = when {
            year in 1900..1999 -> 22.60
            year in 2000..2099 -> 21.94
            else -> 21.94
        }
        val winterSolsticeDay = (y * 0.2422 + c).toInt() - (y / 4)
        return day == winterSolsticeDay
    }
    
    /**
     * 获取未来N天内即将到来的节日列表
     * @param startCalendar 起始日期
     * @param days 检查的天数范围
     * @return 节日列表，格式为 (距离天数, 节日名称) 的配对列表
     */
    private fun getUpcomingHolidays(startCalendar: Calendar, days: Int): List<Pair<Int, String>> {
        val holidays = mutableListOf<Pair<Int, String>>()
        val checkCalendar = startCalendar.clone() as Calendar
        
        // 从明天开始检查
        for (i in 1..days) {
            checkCalendar.add(Calendar.DAY_OF_MONTH, 1)
            val checkYear = checkCalendar.get(Calendar.YEAR)
            val checkMonth = checkCalendar.get(Calendar.MONTH) + 1
            val checkDay = checkCalendar.get(Calendar.DAY_OF_MONTH)
            
            val holidayName = getHolidayName(checkYear, checkMonth, checkDay)
            if (holidayName != null) {
                holidays.add(Pair(i, holidayName))
            }
        }
        
        return holidays
    }
}