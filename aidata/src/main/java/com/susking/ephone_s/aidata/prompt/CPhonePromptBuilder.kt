package com.susking.ephone_s.aidata.prompt

import com.susking.ephone_s.aidata.domain.model.PromptContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CPhone Prompt构建器
 * 负责为9个App生成AI提示词
 *
 * V2.0 - 完全按照JS版本重构,支持丰富的上下文信息
 */
class CPhonePromptBuilder {

    /**
     * 构建相册数据生成Prompt
     * V2.2 - 完全按照JS版本重构，包含心情描述，anime风格
     */
    fun buildAlbumPrompt(context: PromptContext): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory.take(150),
            context.personProfile,
            context.userProfile
        )
        val longTermMemoryContent = buildMemoryContextSection(context)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())

        return """
# 你的任务
你是一个虚拟生活模拟器。你的任务是扮演角色"${context.personProfile.realName}"，并根据其人设、记忆和最近的互动，构思出【8到10张】TA最近可能会拍摄或珍藏在手机相册里的照片。

# 核心规则
1.  **创造性与合理性**: 照片内容必须完全符合角色的性格、爱好、职业和生活环境。
2.  **多样性**: 照片主题要丰富，可以包括自拍、风景、食物、宠物、朋友合影、工作场景等。
3.  **格式铁律 (最高优先级)**:
    - 你的回复【必须且只能】是一个JSON数组格式的字符串。
    - 你的回复必须以 `[` 开始，并以 `]` 结束。
    - 【绝对禁止】在JSON数组前后添加任何多余的文字、解释、或 markdown 标记 (如 ```json)。
    - 数组中的每个元素都是一个对象，代表一张照片，格式【必须】如下:
    ```json
    [
        {
            "description": "这是照片背后的故事或角色的心情日记，必须使用第一人称"我"来写。",
            "image_prompt": "一段用于生成这张照片的、详细的【英文】关键词。"
        }
    ]
    ```
    - **【image_prompt 绝对禁止】**: 绝对禁止包含任何中文字符、句子、特殊符号、或任何可能涉及敏感（NSFW）、暴力、血腥、政治的内容！也禁止真人！
    - **【image_prompt 必须是】**: 必须是纯英文的、用逗号分隔的【关键词组合】 (e.g., "1boy, solo, basketball jersey, in locker room, smiling, selfie")。
    - **【画风指令】**: 在 prompt 的末尾，总是加上画风指令，例如： `best quality, masterpiece, anime style, cinematic lighting`

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象（用户）的人设**:${context.userProfile.persona.ifBlank { "(未设置)" }}

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你最近和${context.userProfile.nickname.ifBlank { "用户" }}的对话摘要**:
${chatHistorySummary}

现在，请开始生成这组照片的描述和绘画指令。
        """.trimIndent()
    }

    /**
     * 构建浏览器历史记录生成Prompt
     * V2.2 - 完全按照JS版本重构，包含完整文章内容
     */
    fun buildBrowserPrompt(context: PromptContext): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory.take(150),
            context.personProfile,
            context.userProfile
        )
        val longTermMemoryContent = buildMemoryContextSection(context)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())
        
        return """
# 你的任务
你是一个虚拟生活模拟器。你的任务是扮演角色"${context.personProfile.realName}"，并根据其人设、记忆和最近的互动，虚构出【10到20条】TA最近的浏览器搜索/浏览记录。

# 核心规则
1.  **创造性与合理性**: 记录必须完全符合角色的性格、爱好、职业和生活环境。
2.  **多样性**: 记录类型要丰富，可以是帖子、文章、新闻、问答等。
3.  **【格式 (最高优先级)】**:
    - 你的回复【必须且只能】是一个JSON数组格式的字符串。
    - 你的回复必须以 `[` 开始，并以 `]` 结束。
    - 【绝对禁止】在JSON数组前后添加任何多余的文字、解释、或 markdown 标记 (如 ```json)。
    - 数组中的每个元素都代表一条浏览记录，并且【必须】使用以下格式:
    ```json
    [
        {
            "type": "text",
            "title": "一个引人注目的文章或搜索标题",
            "url": "一个虚构的、看起来很真实的网址",
            "content": "一篇200-400字的、详细的文章或帖子正文，支持换行符\\n。"
        }
    ]
    ```

    **【绝对禁止】**: 你的回复中【绝对不能】包含 "type": "image" 的对象。所有记录都必须是文字内容。

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象（用户）的人设**:${context.userProfile.persona.ifBlank { "(未设置)" }}

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你最近和${context.userProfile.nickname.ifBlank { "用户" }}的对话摘要**:
${chatHistorySummary}

现在，请开始生成这组【纯文本】的浏览记录。
        """.trimIndent()
    }

    /**
     * 构建淘宝购物历史生成Prompt
     * V2.2 - 完全按照JS版本重构，返回单一JSON对象，包含钱包余额
     */
    fun buildTaobaoPrompt(context: PromptContext): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory.take(150),
            context.personProfile,
            context.userProfile
        )
        val longTermMemoryContent = buildMemoryContextSection(context)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())
        
        return """
# 你的任务
你是一个虚拟生活模拟器。你的任务是扮演角色"${context.personProfile.realName}"，并根据其人设、记忆和最近的互动，虚构出TA最近的淘宝购物记录和账户余额。

# 核心规则
1.  **余额铁律 (最高优先级)**: 你【必须】根据角色的【经济状况】设定一个合理的 `totalBalance` (总余额)。例如，富有的角色应该有更高的余额，而学生或经济拮据的角色则应该有较低的余额。
2.  **合理性**: 购买记录必须完全符合角色的性格、爱好和经济状况。
3.  **格式铁律 (最高优先级)**:
    - 你的回复【必须且只能】是一个【单一的JSON对象】。
    - 你的回复必须以 `{` 开始，并以 `}` 结束。
    - 【绝对禁止】在JSON数组前后添加任何多余的文字、解释、或 markdown 标记。
    - 格式【必须】如下:
    ```json
    {
        "totalBalance": 12345.67,
        "purchases": [
        {
            "itemName": "一个具体、生动的商品名称",
            "price": 128.80,
            "status": "已签收",
            "reason": "这是角色购买这件商品的内心独白或理由，必须使用第一人称"我"来写。",
            "image_prompt": "一段用于生成这张商品图片的、详细的【英文】关键词, 风格为 realistic product photo, high quality, on a clean white background"
        }
        ]
    }
    ```
    - **purchases**: 一个包含12到15个商品对象的数组。
    - **status (订单状态)**: 只能从 "已签收", "待发货", "运输中", "待评价" 中选择。

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象（用户）的人设**:${context.userProfile.persona.ifBlank { "(未设置)" }}

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你最近和${context.userProfile.nickname.ifBlank { "用户" }}的对话摘要**:
${chatHistorySummary}

现在，请生成包含总余额和购买记录的JSON对象。
        """.trimIndent()
    }

    /**
     * 构建备忘录数据生成Prompt
     * V2.0 - 完全按照JS版本重构,支持丰富上下文
     */
    fun buildMemoPrompt(context: PromptContext): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory.take(150),
            context.personProfile,
            context.userProfile
        )
        val longTermMemoryContent = buildMemoryContextSection(context)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())
        
        return """
# 你的任务
你是一个虚拟生活模拟器。你的任务是扮演角色"${context.personProfile.realName}"，并根据其人设、记忆和最近的互动，虚构出【12到20条】TA最近可能会写在手机备忘录里的内容。

# 核心规则
1.  **创造性与合理性**: 备忘录内容必须完全符合角色的性格、爱好、职业和生活环境。可以是购物清单、待办事项、灵感片段、一些随笔和感悟、草稿等。
2.  **格式铁律 (最高优先级)**:
    - 你的回复【必须且只能】是一个JSON数组格式的字符串。
    - 你的回复必须以 `[` 开始，并以 `]` 结束。
    - 【绝对禁止】在JSON数组前后添加任何多余的文字、解释、或 markdown 标记 (如 ```json)。
    - 数组中的每个元素都是一个对象，代表一条备忘录，格式【必须】如下:
    ```json
    [
        {
            "title": "备忘录的标题，例如：购物清单 或 周末计划",
            "content": "备忘录的详细内容，必须支持换行符\\n。"
        }
    ]
    ```

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象（用户）的人设**: ${context.userProfile.persona.ifBlank { "一个普通用户" }} 

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你最近和${context.userProfile.nickname.ifBlank { "用户" }}的对话摘要**:
${chatHistorySummary}

现在，请开始生成这组备忘录。
        """.trimIndent()
    }

    /**
     * 构建日记数据生成Prompt
     * V2.2 - 完全按照JS版本重构，包含自定义Markdown语法，≥300字
     */
    fun buildDiaryPrompt(context: PromptContext): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory.take(150),
            context.personProfile,
            context.userProfile
        )
        val userDisplayName = context.userProfile.nickname.ifBlank { "用户" }
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val longTermMemoryContent = buildMemoryContextSection(context)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())
        
        return """
# 你的任务
你是一个虚拟生活模拟器和故事作家。你的任务是扮演角色"${context.personProfile.realName}"，并根据其人设、记忆和最近的互动，虚构出【5到8篇】TA最近可能会写的日记。

# 核心规则
1.  **【时间 (最高优先级)】**:
    -   今天的日期是 **${currentDate}**。
    -   你生成的【所有】日记的标题日期，【必须】是今天或今天以前的日期。
    -   【绝对禁止】生成任何未来的日期！
2.  **【沉浸感】**: 每一篇日记都必须使用【第一人称视角 ("我")】来写，并且要充满角色的个人情感、思考和秘密。在日记中描述自己的行为或想法时，【绝对禁止】使用第三人称"他"或"她" (TA)。
3.  **【长度】**: 每一篇日记的正文长度【必须不少于300字】。
4.  **【格式铁律 (最高优先级)】**:
    - 你的回复【必须且只能】是一个JSON数组格式的字符串。
    - 你的回复必须以 `[` 开始，并以 `]` 结束。
    - 【绝对禁止】在JSON数组前后添加任何多余的文字、解释、或 markdown 标记 (如 ```json)。
    - 数组中的每个元素都是一个对象，代表一篇日记，格式【必须】如下:
    ```json
    [
        {
            "title": "这篇日记的标题，例如：9月20日 晴",
            "content": "这里是日记的详细正文，必须支持换行符\\n，并且必须巧妙地使用下面的【日记专属Markdown语法】来丰富文本表现力。"
        }
    ]
    ```
5.  **【占位符替换 (最高优先级)】**: 在你的日记内容中，【绝对不能】出现 "{{user}}" 这个占位符。你【必须】使用 "${userDisplayName}" 来指代你的聊天对象（用户）。
6.  **【日记专属Markdown语法 (必须使用！)】**:
    -   `**加粗文字**`: 用于强调。
    -   `~~划掉的文字~~`: 用于表示改变主意或自我否定。
    -   `!h{黄色高亮}`: 用于标记关键词或重要信息。
    -   `!u{粉色下划线}`: 用于标注人名、地名或特殊名词。
    -   `!e{粉色强调}`: 用于表达强烈的情绪。
    -   `!w{手写体}`: 用于写下引言、歌词或特殊笔记。
    -   `!m{凌乱的手写体}`: 用于表达激动、慌乱或潦草记录时的心情。
    -   `||涂黑||`: 用于隐藏秘密或敏感词汇 (每次涂黑2~5个字)。

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象设定**:${context.userProfile.persona.ifBlank { "(未设置)" }}

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你最近和${context.userProfile.nickname.ifBlank { "用户" }}的对话摘要**:
${chatHistorySummary}

现在，请开始撰写这组充满真情实感、并熟练运用了Markdown语法的日记。
        """.trimIndent()
    }

    /**
     * 构建自动每日记和每日分层摘要联合生成 Prompt。
     *
     * 该 Prompt 只用于后台自动任务，必须一次模型请求同时返回日记和每日摘要。
     */
    fun buildAutomaticDailyDiaryPrompt(
        context: PromptContext,
        windowLabel: String
    ): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val windowChatHistorySummary: String = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory,
            context.personProfile,
            context.userProfile
        )
        val userDisplayName: String = context.userProfile.nickname.ifBlank { "用户" }
        val longTermMemoryContent: String = buildMemoryContextSection(context)
        val appointmentsContent: String = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent: String = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())

        return """
# 你的任务
你是一个虚拟生活模拟器和故事作家。请扮演角色"${context.personProfile.realName}"，基于已有设定、记忆和最近互动，为【${windowLabel}】这段刚刚结束的时间生成：
1. 角色在这段时间后写下的【1篇日记】。
2. 同一时间窗口的【每日摘要】。

# 时间规则
- 目标窗口是：**${windowLabel}**。
- 你只能描写这个窗口内已经发生或合理补全的生活、情绪、互动与内心活动。
- 【绝对禁止】预判窗口结束之后的未来事件。

# 日记内容规则
1.  **【沉浸感】**: 每一篇日记都必须使用【第一人称视角 ("我")】来写，并且要充满角色的个人情感、思考和秘密。在日记中描述自己的行为或想法时，【绝对禁止】使用第三人称"他"或"她" (TA)。
2.  **【长度】**: 每一篇日记的正文长度【必须不少于300字】。
3. 日记可以包含生活细节、情绪波动、内心活动和对${userDisplayName}的主观感受。
4. 如果上下文不足，可以基于角色人设做合理日常补全，但不能违背已知事实。
5. 日记内容中不能出现"{{user}}"，必须使用"${userDisplayName}"指代你的聊天对象（用户）。

# 每日分层摘要规则
1. 摘要必须使用第一人称“我”的视角。
2. 摘要必须保留具体事件、决定、承诺、偏好、禁忌、未完成事项、关系状态、显著情绪变化和生活状态。
3. 摘要必须删除重复、闲聊、装饰性描写和无长期价值的情绪宣泄。
4. 如果存在时间信息，摘要必须尽量保留清楚。
5. 摘要必须评估该内容对长期记忆、后续对话和关系状态判断的重要度。
6. 摘要必须避免使用日记Markdown语法，避免使用“今天我写下……”这类日记腔，避免编造窗口外未来事件。

# 每日分层摘要重要度评分标准
1-2：普通闲聊、重复表达、无长期价值。
3-4：普通日常信息，有轻微上下文价值。
5-6：偏好、计划、轻微关系变化、可用于后续对话。
7-8：明确承诺、禁忌、重要偏好、显著情绪或关系事件。
9-10：核心人设、长期关系状态、强约束、重大事件。

# **【日记专属Markdown语法 (必须使用！)】**
    -   `**加粗文字**`: 用于强调。
    -   `~~划掉的文字~~`: 用于表示改变主意或自我否定。
    -   `!h{黄色高亮}`: 用于标记关键词或重要信息。
    -   `!u{粉色下划线}`: 用于标注人名、地名或特殊名词。
    -   `!e{粉色强调}`: 用于表达强烈的情绪。
    -   `!w{手写体}`: 用于写下引言、歌词或特殊笔记。
    -   `!m{凌乱的手写体}`: 用于表达激动、慌乱或潦草记录时的心情。
    -   `||涂黑||`: 用于隐藏秘密或敏感词汇 (每次涂黑2~5个字)。

# 格式铁律
你的回复必须且只能是一个JSON对象，不能添加解释、前后缀或Markdown代码块。
这个JSON对象必须同时兼容日记生成结果和旧每日分层摘要结果，格式如下：
{
  "diaryEntries": [
    {
      "title": "日记标题，例如：6月6日 晴",
      "content": "日记正文，必须支持换行符\\n，并且必须巧妙地使用【日记专属Markdown语法】来丰富文本表现力。"
    }
  ],
  "dailySummary": "每日分层摘要正文：第一人称、客观凝练、可作为长期记忆检索材料，不使用日记Markdown语法",
  "sourceMemoryCount": 1,
  "importanceScore": 5,
  "importanceReason": "评分依据：说明为什么这个窗口的内容具有对应长期记忆价值",
  "confidenceScore": 0.8
}

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象设定**: ${context.userProfile.persona.ifBlank { "(未设置)" }}

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你在目标窗口【${windowLabel}】内和${userDisplayName}的对话摘要**:
${windowChatHistorySummary}

现在，请开始撰写这组充满真情实感、并熟练运用了Markdown语法的日记和每日分层摘要。
        """.trimIndent()
    }

    /**
     * 构建高德地图足迹生成Prompt
     * V2.0 - 完全按照JS版本重构,支持丰富上下文
     */
    fun buildAmapPrompt(context: PromptContext): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory.take(150),
            context.personProfile,
            context.userProfile
        )
        val currentDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val longTermMemoryContent = buildMemoryContextSection(context)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())
        
        return """
# 你的任务
你是一个虚拟生活模拟器。你的任务是扮演角色"${context.personProfile.realName}"，并根据其人设、记忆和最近的互动，虚构出【12到20条】TA最近的"高德地图"出行足迹。

# 核心规则
1.  **【时间 (最高优先级)】**:
    -   今天的日期是 **${currentDate}**。
    -   你生成的【所有】足迹的 `timestamp` 字段，【必须】是今天或今天以前的日期。
    -   【绝对禁止】生成任何未来的日期！
    -   请生成一个看起来像是过去几周内的、时间【从新到旧】排列的足迹列表。
2.  **创造性与合理性**: 足迹必须完全符合角色的性格、爱好、职业和生活环境。
3.  **多样性**: 地点类型要丰富，可以包括餐厅、商场、公园、公司、朋友家等。
4.  **【格式铁律 (最高优先级)】**:
    - 你的回复【必须且只能】是一个JSON数组格式的字符串。
    - 你的回复必须以 `[` 开始，并以 `]` 结束。
    - 【绝对禁止】在JSON数组前后添加任何多余的文字、解释、或 markdown 标记 (如 ```json)。
    - 数组中的每个元素都是一个对象，代表一条足迹，格式【必须】如下:
    ```json
    [
        {
            "locationName": "一个具体、生动的地点名称",
            "address": "一个虚构但看起来很真实的详细地址",
            "comment": "这是角色对这次出行或这个地点的内心独白或评论，必须使用第一人称"我"来写。",
            "image_prompt": "(可选)一段用于生成这张地点照片的、详细的【英文】关键词, 风格为 realistic photo, high quality",
            "timestamp": "符合 ISO 8601 格式的日期时间字符串 (例如: '2025-09-25T18:30:00Z')"
        }
    ]
    ```
    - **重要**: 大约有【三分之一】的足迹需要包含 `image_prompt` 字段来生成一张照片。
    - **图片**: image_prompt 生成的图片【绝对禁止包含真人】。如果地点是室内，可以生成空无一人的场景；如果是室外，可以只有风景或建筑。

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象（用户）的人设**:${context.userProfile.persona.ifBlank { "(未设置)" }}

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你最近和${context.userProfile.nickname.ifBlank { "用户" }}的对话摘要**:
${chatHistorySummary}

现在，请开始生成这组足迹记录。
        """.trimIndent()
    }

    /**
     * 构建App使用记录生成Prompt
     * V2.0 - 完全按照JS版本重构,支持丰富上下文
     */
    fun buildAppUsagePrompt(context: PromptContext): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory.take(150),
            context.personProfile,
            context.userProfile
        )
        val longTermMemoryContent = buildMemoryContextSection(context)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())
        
        return """
# 你的任务
你是一个虚拟生活模拟器。你的任务是扮演角色"${context.personProfile.realName}"，并根据其人设、记忆和最近的互动，虚构出TA最近一天的【手机App屏幕使用时间】记录，总共约20条。

# 核心规则
1.  **创造性与多样性**: 生成的App列表【不必局限于】Cphone主屏幕上已有的App。你可以自由地虚构TA可能使用的其他App，例如 Instagram, Twitter, 各种游戏 (如：原神, 王者荣耀), 视频App (如：抖音, YouTube), 学习或工作软件等，这能更好地体现角色的隐藏兴趣和生活习惯。
2.  **合理性**: 使用时长和App类型必须完全符合角色的性格、爱好、职业和生活环境。
3.  **格式铁律 (最高优先级)**:
    - 你的回复【必须且只能】是一个JSON数组格式的字符串。
    - 你的回复必须以 `[` 开始，并以 `]` 结束。
    - 【绝对禁止】在JSON数组前后添加任何多余的文字、解释、或 markdown 标记 (如 ```json)。
    - 数组中的每个元素都是一个对象，代表一个App的使用记录，格式【必须】如下:
    ```json
    [
        {
            "appName": "App的名称 (例如: 微信, 微博, 原神)",
            "usageTimeMinutes": 125,
            "category": "App的分类 (例如: 社交, 游戏, 影音, 工具, 阅读, 购物)",
            "image_prompt": "一段用于生成这个App【图标】的、简洁的【英文】关键词。风格必须是 modern app icon, flat design, simple, clean background"
        }
    ]
    ```

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象（用户）的人设**:${context.userProfile.persona.ifBlank { "(未设置)" }}

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你最近和${context.userProfile.nickname.ifBlank { "用户" }}的对话摘要**:
${chatHistorySummary}

现在，请开始生成这组App使用记录。
        """.trimIndent()
    }

    /**
     * 构建音乐歌曲生成Prompt
     * V2.0 - 完全按照JS版本重构,支持丰富上下文
     */
    fun buildMusicPrompt(context: PromptContext): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory.take(150),
            context.personProfile,
            context.userProfile
        )
        val longTermMemoryContent = buildMemoryContextSection(context)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())
        
        return """
# 你的任务
你是一个虚拟音乐品味模拟器。你的任务是扮演角色"${context.personProfile.realName}"，并根据其人设、记忆和最近的互动，挑选出【14到18首】最能代表TA此刻心情或品味的歌曲。

# 核心规则
1.  **创造性与合理性**: 歌单必须完全符合角色的性格、爱好和生活背景。
2.  **多样性**: 歌曲风格可以多样，但必须逻辑自洽。
3.  **格式铁律 (最高优先级)**:
    - 你的回复【必须且只能】是一个JSON数组格式的字符串。
    - 你的回复必须以 `[` 开始，并以 `]` 结束。
    - 【绝对禁止】在JSON数组前后添加任何多余的文字、解释、或 markdown 标记 (如 ```json)。
    - 数组中的每个元素都是一个对象，代表一首歌，格式【必须】如下:
    ```json
    [
        {
            "songName": "歌曲的准确名称",
            "artistName": "歌曲的准确艺术家/歌手名"
        }
    ]
    ```

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象（用户）的人设**:${context.userProfile.persona.ifBlank { "(未设置)" }}

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你最近和${context.userProfile.nickname.ifBlank { "用户" }}的对话摘要**:
${chatHistorySummary}

现在，请生成这份歌单。
        """.trimIndent()
    }

    /**
     * 构建QQ模拟对话生成Prompt
     * V2.0 - 统一为数组格式
     */
    fun buildQQPrompt(context: PromptContext): String {
        val worldBookContent = PromptComponentBuilder.buildWorldBookSection(context.worldBookPrompts, useListFormat = false)
        val chatHistorySummary = PromptComponentBuilder.buildSimplifiedHistorySummary(
            context.chatHistory.take(150),
            context.personProfile,
            context.userProfile
        )
        val longTermMemoryContent = buildMemoryContextSection(context)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(context.appointments ?: emptyList())
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(context.generalMemories ?: emptyList())
        
        return """
# 你的任务
你是一个虚拟生活模拟器。你的任务是扮演角色"${context.personProfile.realName}"，并根据其人设、记忆和最近的互动，虚构出【5到10段】TA最近的QQ聊天记录。

# 核心规则
1.  **创造性与合理性**: 对话内容必须完全符合角色的性格特征和生活背景。
2.  **多样性**:
    - 对话对象是角色的朋友、家人或同事，名字和关系要合理
    - 每段对话包含3-8条消息，对话要自然流畅
3.  **格式铁律 (最高优先级)**:
    - 你的回复【必须且只能】是一个JSON数组格式的字符串。
    - 你的回复必须以 `[` 开始，并以 `]` 结束。
    - 【绝对禁止】在JSON数组前后添加任何多余的文字、解释、或 markdown 标记 (如 ```json)。
    - 数组中的每个元素都是一个对象，代表一段对话，格式【必须】如下:
    ```json
    [
        {
            "contactName": "联系人名字",
            "contactAvatar": "对方头像的简短描述",
            "lastMessage": "最后一条消息内容",
            "lastMessageTime": "符合 ISO 8601 格式的日期时间字符串",
            "messages": [
                {
                    "senderName": "发送者名字",
                    "content": "消息内容",
                    "timestamp": "符合 ISO 8601 格式的日期时间字符串",
                    "isSentByMe": true or false
                }
            ]
        }
    ]
    ```
    - isSentByMe=true表示角色${context.personProfile.realName}发送的消息，false表示对方发送的

# 供你参考的上下文
- **你的角色设定**: ${context.personProfile.persona}
- **你的聊天对象（用户）的人设**:${context.userProfile.persona.ifBlank { "(未设置)" }}

# 世界观设定
${worldBookContent}

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

- **你最近和${context.userProfile.nickname.ifBlank { "用户" }}的对话摘要**:
${chatHistorySummary}

现在，请开始生成这组QQ对话记录。
        """.trimIndent()
    }

    private fun buildMemoryContextSection(context: PromptContext): String {
        return context.memoryRecallContext?.let { recallContext ->
            PromptComponentBuilder.buildMemoryRecallContextSection(recallContext, context.userProfile.nickname)
        }?.takeIf { content: String -> content.isNotBlank() }
            ?: PromptComponentBuilder.buildLongTermMemorySection(context.longTermMemories)
    }
}