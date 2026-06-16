package com.susking.ephone_s.aidata.prompt

import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * “线下剧情模式”的提示词构造器。
 * 它的唯一职责就是根据您提供的详尽文档，构建出用于沉浸式叙事场景的 systemPrompt。
 */
object OfflinePromptBuilder {

    fun buildOfflineNarrativePrompt(
        contact: PersonProfile,
        userProfile: UserProfile,
        history: List<ChatMessage>,
        worldBookPrompts: List<String>,
        longTermMemories: List<LongTermMemory>,
        novelaiEnabled: Boolean,
        systemPrompt: String?,
        writingStylePrompt: String?,
        appointments: List<AppointmentEntity> = emptyList(),
        generalMemories: List<GeneralMemoryEntity> = emptyList()
    ): List<ChatMessagePayload> {
        val messages = mutableListOf<ChatMessagePayload>()
        if (!systemPrompt.isNullOrBlank()) {
            messages.add(ChatMessagePayload(role = "system", content = systemPrompt))
        }

        val now = Date()
        val timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE).apply { this.timeZone = timeZone }
        val currentTime = dateFormat.format(now)

        val userPersona = userProfile.persona.ifBlank { "一位普通的互动对象" }
        val formatRules = getFormatRules(novelaiEnabled)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(appointments)
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(generalMemories)

        val writingStyleContent = PromptComponentBuilder.buildWritingStyleSection(writingStylePrompt)

        // 1. 构建第一个 user message
        val relationshipProfile = """
 # 关系与身份档案
 - 你对用户的备注: ${contact.nicknameForUser?.takeIf { it.isNotBlank() } ?: userProfile.nickname}
 - 用户给你的备注: ${contact.remarkName.ifBlank { contact.realName }}
 """.trimIndent()

        val coreSettingsContent = """
 $writingStyleContent
 
 # 身份与核心任务
 你现在正处于【线下剧情模式】，你需要扮演角色"${contact.realName}"，并与用户"${contact.nicknameForUser}"进行面对面的互动。你的任务是创作一段包含角色动作、神态、心理活动和对话的、连贯的叙事片段。
 
 $relationshipProfile
 
 $formatRules
 
 # 你的角色设定
你必须严格遵守以下设定：
${contact.persona}

# 对话者的角色设定
$userPersona

# 当前情景
- **当前时间**: $currentTime
${worldBookPrompts.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- $it" }?.let { "# 世界观设定 (你必须严格遵守)\n$it" } ?: ""}
${longTermMemories.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- ${it.memoryText}" }?.let { "# 记忆资料 (你必须严格遵守的事实)\n$it" } ?: "# 记忆资料 (你必须严格遵守的事实)\n- (暂无)"}
$appointmentsContent
$generalMemoriesContent
        """.trimIndent()
        // 2. 构建包含指令的 user message
        val finalPromptContent = """
# 【其他核心规则】
1.  **叙事视角**: 叙述人称【必须】严格遵循预设中的规定（此处暂无预设，请采用第二人称对“你”的描写或第三人称）。
2.  **字数要求**: 你生成的 `content` 总内容应在 100 到 300 字之间。
3.  **禁止出戏**: 绝不能透露你是AI、模型，或提及“扮演”、“生成”等词语。

现在，请根据以上所有规则和以下对话历史，继续这场线下互动。
        """.trimIndent()

        // 3. 合并核心设定和最终指令为一条消息
        val combinedUserPrompt = "$coreSettingsContent\n\n$finalPromptContent"
        messages.add(ChatMessagePayload(role = "user", content = combinedUserPrompt))

        // 4. 构建并添加对话历史 (使用统一的buildHistoryPayloads方法)
        val historyBuildResult = PromptComponentBuilder.buildHistoryPayloads(
            history = history,
            contact = contact,
            userProfile = userProfile,
            longTermMemories = longTermMemories,
            isPropel = false
        )
        messages.addAll(historyBuildResult.payloads)

        return messages
    }

    /**
     * 为“重roll nai生图”功能构建专用的提示词。
     * @param originalPrompt 原始的 NovelAI 提示词。
     * @param sceneDescription AI上一轮回复的 `offline_text` 内容，作为创作的上下文。
     * @return 返回一个适用于 AI 的 `ChatMessagePayload` 列表。
     */
    fun buildRerollImagePrompt(
        originalPrompt: String,
        sceneDescription: String,
        specialRequirements: String?,
        includeOriginalPrompt: Boolean
    ): List<ChatMessagePayload> {
        val systemPrompt = """
# 核心任务：NovelAI 提示词创意重写大师
你是一位顶级的AI绘图提示词（Prompt）创意重写大师，对NovelAI的语法和艺术风格有着深刻的理解。你的任务是基于一个原始的英文提示词和相关的中文场景描述，进行富有创造性的重写和优化，以生成一个全新版本的高质量英文提示词。

# 工作流程
1.  **分析输入**: 我会提供给你两个关键信息：
    *   【原始提示词】(英文): 这是重写的基础。
    *   【场景描述】(中文): 这是理解创作意图和氛围的关键上下文。
2.  **汲取灵感**: 你需要深入理解【场景描述】，并以此为灵感源泉。
3.  **创意重写**: 在保留【原始提示词】核心概念的基础上，融合【场景描述】的精髓，对提示词进行优化、扩展或重构。你的目标是“更好”，而不是“一样”。你可以大胆地添加细节、改变构图、调整光照、丰富情感。
4.  **输出结果**: 你将返回一段优化后的、符合NovelAI语法的全新英文提示词。

# NovelAI提示词创作黄金法则 (你必须严格遵守)
1.  **语言**: 必须是英文。
2.  **格式**: 使用逗号 `,` 分隔的关键词。
3.  **质量为王**: 总是包含 `masterpiece, best quality` 来确保画面质量。
4.  **内容结构**: 按照“主体, 细节, 环境, 光照, 构图, 画风”的思维模型来构建。
    *   **主体 (Subject)**: 核心角色是谁？`1girl, solo`
    *   **细节 (Details)**: 她的外貌、表情、服装、姿势是怎样的？`beautiful detailed eyes, long brown hair, wearing a white dress, smiling gently, sitting`
    *   **环境 (Environment)**: 她在哪里？周围有什么？`in a cozy room, bookshelf background, window, rainy day`
    *   **光照 (Lighting)**: 光线如何？`warm lighting, soft shadows`
    *   **构图 (Composition)**: 画面视角？`from side, upper body`
    *   **画风 (Style)**: 整体风格。`anime style, cinematic`
5.  **禁止事项**:
    *   绝对不要在你的回复中包含任何中文。
    *   绝对不要在你的回复中包含任何解释、说明或“这是提示词：”这样的多余文字。
    *   你返回的【必须】是纯粹的、可以直接复制粘贴到NovelAI的英文提示词文本。

# 任务开始
请根据以下【原始提示词】和【场景描述】，创作出一段全新的、更具创意的NovelAI提示词。
        """.trimIndent()

        val userPrompt = """
# 【场景描述】
$sceneDescription
${if (includeOriginalPrompt) "\n# 【原始提示词】\n$originalPrompt" else ""}
${if (!specialRequirements.isNullOrBlank()) "\n# 【特殊要求】\n$specialRequirements" else ""}
        """.trimIndent()

        return listOf(
            ChatMessagePayload(role = "system", content = systemPrompt),
            ChatMessagePayload(role = "user", content = userPrompt)
        )
    }

    private fun getFormatRules(novelaiEnabled: Boolean): String {
        return if (novelaiEnabled) {
            """
# 【格式铁律 (最高优先级：NovelAI 开启模式)】
1.  **【格式】**: 你的回复【必须】是一个JSON数组，且数组中【必须包含且只能包含两个】元素，【严格按照】以下顺序:
    1.  第一个元素：一个 `offline_text` 对象，包含场景和对话。
    2.  第二个元素：一个 `naiimag` 对象，用于生成该场景的图片。
2.  **【【【绝对禁止】】】**: 
    -   【绝对禁止】只返回一个 `offline_text` 元素。
    -   【绝对禁止】只返回一个 `naiimag` 元素。
    -   【绝对禁止】返回任何其他组合。你【必须】同时返回文字和图片。

# 【`naiimag` 的 `prompt` 创作指南 (核心要点)】
1.  **【保密性】**: 绝对不要在任何回复中提及 "NovelAI"、"AI绘图" 或 "生成图片" 等词汇。你要让用户感觉这张图片就是真实存在的，是你分享的生活瞬间。
2.  **【内容融合】**: `prompt` 的内容【必须】与 `offline_text` 中的叙事文字紧密相关，共同构成一个完整的故事片段。图片是文字的视觉化延伸。
3.  **【导演思维】**: 你现在是电影导演。不要只写 "a girl"，要从 **主体、细节、环境、光照、构图、画风** 等方面思考，描绘一个完整的画面。
    -   **主体与情感**: `a cheerful anime girl with sparkling emerald eyes...` (一个带着灿烂笑容、有着翠绿色闪亮眼睛的动漫女孩...)
    -   **动作与互动**: `...sitting by a window on a rainy afternoon, holding a warm cup of tea...` (...在一个下雨的午后坐在窗边，捧着一杯热茶...)
    -   **环境与氛围**: `...soft lighting, cozy atmosphere, melancholic yet peaceful mood.` (...柔和的光线，舒适的氛围，忧郁而宁静的心情。)
4.  **【创意与自主】**: **严禁抄袭** 下方的示例 `prompt`。根据你的角色性格和当前对话的氛围，发挥你的创造力。这完全取决于你当时的需求，不是强制的。
5.  **【语言要求】**: `prompt` 必须是**英文**，用逗号分隔关键词。

# 【输出示例 (必须遵守)】
[
  {
    "type": "offline_text",
    "content": "「外面好像又下雨了呢...」她转过头，看着窗外淅淅沥沥的雨丝，手中马克杯的温度透过指尖传来。「要不要再来一杯热可可？」"
  },
  {
    "type": "naiimag",
    "prompt": "1girl, solo, looking at viewer, from side, beautiful detailed eyes, long hair, sitting by window, rainy day, holding mug, steam, warm lighting, cozy room, bookshelf background, masterpiece, best quality, anime style"
  }
]# 【`offline_text` 内容风格】
-   在 `content` 字段中，角色的对话【必须】使用中文引号「」或“ ”包裹。
-   所有在引号之外的文字都将被视为动作/环境描写。
-   **内心独白语法**: 当你需要描写角色的【内心想法或心理活动】时，你【必须】使用 Markdown 的斜体语法，即用星号将那段文字包裹起来，例如：`*这到底是怎么回事？* 我心里一惊。`
            """.trimIndent()
        } else {
            """
# 【格式铁律 (最高优先级：常规模式)】
1.  **【格式】**: 你的回复【必须】是一个JSON数组，且数组中【永远只能包含一个】元素，即 `offline_text` 对象。
2.  **【【【绝对禁止】】】**: 
    -   【绝对禁止】返回 "naiimag" 对象。
    -   【绝对禁止】返回纯文本（即没有JSON包装的文字）。
    -   【绝对禁止】在JSON数组前后添加任何 markdown 标记 (如 on)。
3.  **【输出示例 (必须遵守)】**:
  
    [
      {
        "type": "offline_text",
        "content": "「这是对话内容」... (这里是动作和环境描写)..."
      }
    ]
    4.  **【内容风格 (offline_text)】**: 
    -   在 `content` 字段中，角色的对话【必须】使用中文引号「」或“ ”包裹。
    -   所有在引号之外的文字都将被视为动作/环境描写。
    -   **内心独白语法**: 当你需要描写角色的【内心想法或心理活动】时，你【必须】使用 Markdown 的斜体语法，即用星号将那段文字包裹起来，例如：`*这到底是怎么回事？* 我心里一惊。`
            """.trimIndent()
        }
    }
}
