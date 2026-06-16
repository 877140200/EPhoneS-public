package com.susking.ephone_s.settings.ui.features

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.susking.ephone_s.core.R as CoreR
import com.susking.ephone_s.core.ui.BaseActivity

/**
 * 功能一览页面。
 *
 * 这里集中展示小手机已经提供的桌面应用、AI能力、数据工具和系统能力，
 * 便于用户在设置页快速了解当前版本可以做什么。
 */
class FeatureOverviewActivity : BaseActivity() {

    private val featureSections: List<FeatureSection> = listOf(
        FeatureSection(
            title = "桌面",
            summary = "像真实手机一样承载所有小手机应用，是用户进入各应用的中心。",
            highlights = listOf("桌面应用图标", "Dock 常驻栏", "壁纸与主题", "旧布局补齐"),
            details = listOf(
                createSimpleDetail("提供 QQ、世界书集、主题、相册、商城、支付宝、关系图、课程表、预设、CPhone、酒馆记录、设置等入口。"),
                createSimpleDetail("默认桌面会放置 QQ、世界书集、主题、相册、商城、支付宝、关系图、课程表等常用应用，第二页保留预设和 X。"),
                createSimpleDetail("Dock 常驻设置、CPhone、酒馆记录等高频入口，方便用户随时进入系统工具或查看联系人手机。"),
                createSimpleDetail("支持桌面分页、Dock 常驻应用、应用图标点击跳转，以及旧用户布局自动补齐新增应用。"),
                createSimpleDetail("支持桌面布局持久化，用户调整后的页面图标和 Dock 图标会被保存。"),
                createSimpleDetail("支持检查并修复桌面与 Dock 重复应用入口，避免升级或导入后出现重复图标。"),
                createSimpleDetail("支持主题模块提供图标、壁纸、Dock 背景、应用名称文字颜色等视觉配置。"),
                createSimpleDetail("支持桌面应用名称颜色、文字阴影、Dock 透明度和圆角半径随主题变化。"),
                createSimpleDetail("桌面可接入课程表摘要卡，让用户在小手机首页看到下一节课、考试倒计时和作业信息。")
            )
        ),
        FeatureSection(
            title = "设置",
            summary = "管理 API、功能开关、应用数据、存储空间和法律信息。",
            highlights = listOf("API 预设", "功能开关", "数据管理", "关于"),
            details = listOf(
                createSimpleDetail("支持主模型、副模型、Embedding、ASR 等 API 配置和预设管理。"),
                createSimpleDetail("支持 API 地址、模型名、密钥、代理、预设保存与加载等模型连接信息维护。"),
                createSimpleDetail("支持 NovelAI 全局生成设置、角色专属 NovelAI 设置、测试生成与提示词确认。"),
                createSimpleDetail("支持 CORS 代理选择和自定义代理输入，便于图片生成服务适配不同环境。"),
                createSimpleDetail("支持 Brain 悬浮窗、后台活动、后台服务、振动、语音转文字自动展开、聊天加载数量等功能开关。"),
                createSimpleDetail("支持后台活动间隔、主动活动开关等 ai 后台行为配置。"),
                createSimpleDetail("支持完整数据导出、备份文件导入、导入预览、冲突解决、数据检查与修复。"),
                createSimpleDetail("支持智能合并导入和覆盖导入，并为冲突项提供说明和确认流程。"),
                createSimpleDetail("支持查看头像、聊天图片、相册、聊天记录、向量数据、其他数据和总计存储占用。"),
                createSimpleDetail("支持存储占用刷新，统计过程以只读方式访问数据库和文件，避免影响业务写入。"),
                createSimpleDetail("支持语言、振动测试、图片压缩提示、冗余数据清理、高级数据清理等维护入口。"),
                createSimpleDetail("支持隐私政策、开源许可证、Apache 2.0 许可证和关于小手机信息。")
            )
        ),
        FeatureSection(
            title = "QQ",
            summary = "小手机最核心的社交入口，用来和联系人 ai 对话、维护关系与记录生活。",
            highlights = listOf("联系人", "聊天", "动态", "收藏", "回忆"),
            details = listOf(
                FeatureDetailItem(
                    title = "自动提取聊天历史事件（请求显示在brain悬浮窗）",
                    subtitle = "单个角色的独立设置。每有 x 条新消息，就触发自动提取流程。",
                    sections = listOf(
                        FeatureDetailSection(
                            title = "作用",
                            content = "每隔一段时间自动提取聊天中的关键事件与事实图谱，向量化并存入数据库。未来用户若提到相关信息，会索引后加入上下文模型，提高模型的记忆力。"
                        ),
                        FeatureDetailSection(
                            title = "相关设置",
                            content = """
                                1.“ai资料设置”里的“启用自动提取结构化事件”：启用了才会自动提取
                                2.支持调整自动提取间隔
                                2.“记忆管理中心”的“提取事件”的距离上次提取已有 xxx 条新消息
                            """.trimIndent()
                        )
                    )
                ),
                FeatureDetailItem(
                    title = "手动提取聊天历史事件（请求显示在brain悬浮窗）",
                    subtitle = "",
                    sections = listOf(
                        FeatureDetailSection(
                            title = "作用",
                            content = "手动提取聊天中的关键事件与事实图谱，向量化并存入数据库。未来用户若提到相关信息，会索引后加入上下文模型，提高模型的记忆力。"
                        ),
                        FeatureDetailSection(
                            title = "相关设置",
                            content = """
                                1.“记忆管理中心”里的“提取事件”：让ai帮你提取特定时间范围内的事件
                                2.“记忆管理中心”里的“添加事件”：手动输入事件
                                3.事件与事件之间的“在此后插入事件”：手动输入事件，事件时间只能设置在两个事件的时间之间
                            """.trimIndent()
                        )
                    )
                ),
                createSimpleDetail("支持心声与散记详情弹窗，能查看 ai 的内心活动、随笔内容、时间戳并收藏。"),
                FeatureDetailItem(
                    title = "收藏页",
                    subtitle = "",
                    sections = listOf(
                        FeatureDetailSection(
                            title = "作用",
                            content = "收藏心声散记、单条聊天消息、应用内外任意文字信息"
                        ),
                        FeatureDetailSection(
                            title = "功能解释",
                            content = """
                                1.收藏应用内外任意文字信息：在支持的界面选中想要收藏的文字后，点击悬浮操作栏中的“更多”-“收藏到小手机”。可自行编辑来源
                                2.支持根据来源筛选收藏内容
                                3.支持设置收藏内容默认全部折叠/全部展开
                                4.支持编辑/删除收藏内容
                                5.支持显示css+html美化
                            """.trimIndent()
                        )
                    )
                ),



                FeatureDetailItem(
                    title = "TTS功能（请求显示在brain悬浮窗）",
                    subtitle = "语音转文字",
                    sections = listOf(
                        FeatureDetailSection(
                            title = "作用",
                            content = "让角色活起来，能开口说话"
                        ),
                        FeatureDetailSection(
                            title = "相关设置",
                            content = """
                                1.桌面“设置”里可选配置TTS api（目前仅支持小米mimo-v2.5-tts/mimo-v2.5-tts-voicedesign模型）。支持设置流式播放、试听音色、启用“AI 回复自动生成语音”
                                2.支持长按后选择重新生成语音，支持缓存语音、无需重复生成
                            """.trimIndent()
                        )
                    )
                ),





                createSimpleDetail("支持联系人列表、联系人资料、备注、头像、聊天背景、角色人设、分组、屏蔽、好友申请同意或拒绝。"),
                createSimpleDetail("支持用户资料维护，包括昵称、头像、人设、空间顶部背景等个人信息。"),
                createSimpleDetail("支持文本聊天、AI 回复、继续回复、重新生成、回复版本切换、引用回复、跳转引用、编辑、删除、撤回和清空聊天记录。"),
                createSimpleDetail("支持消息状态展示、未读计数、活跃联系人追踪，进入聊天后可自动清除对应未读。"),
                createSimpleDetail("支持多选消息收藏，把重要聊天、心声、散记集中放入收藏页查看。"),
                createSimpleDetail("支持 QQ 空间/动态信息流、评论、点赞、浏览量、转发内容、图片视频动态和访问统计展示。"),
                createSimpleDetail("支持回忆页与长期记忆，能查看联系人相关记忆摘要，并与语义状态、向量化、事实图谱能力联动。"),
                createSimpleDetail("支持后续关心策略，同一联系人可记录是否允许主动跟进、跟进次数和冷却。"),
                createSimpleDetail("支持视频通话记录、拍一拍/互动动作、语音转写展开、表情、图片、动图、语音等聊天体验。"),
                createSimpleDetail("支持红包、转账、外卖、礼物赠送、背包物品、赠礼历史等生活化互动，并与支付宝钱包数据衔接。"),
                createSimpleDetail("支持聊天内选择背包物品赠送联系人，可填写赠礼备注并自动写入聊天消息。"),
                createSimpleDetail("支持导入单个角色聊天记录、导入 QQ 所有数据、导出 QQ 全量数据，便于迁移角色和会话。")
            )
        ),
        FeatureSection(
            title = "Brain 全局悬浮窗与后台活动",
            summary = "所有外部 AI 请求的统一出口，负责把请求透明地展示给用户。",
            highlights = listOf("请求转发", "悬浮窗", "后台消息", "通知"),
            details = listOf(
                createSimpleDetail("所有向外部模型 API 发送的请求都必须经由 Brain 转发，并在全局悬浮窗中可见。"),
                createSimpleDetail("负责从 aidata 拉取联系人资料、聊天历史、世界书、长期记忆、课程摘要等上下文。"),
                createSimpleDetail("负责发送请求、接收 AI 回复，再把结果交回 aidata 解析为聊天消息、通知或后台事件。"),
                createSimpleDetail("支持后台消息接收服务，让小手机在后台也可以接收 ai 消息并显示通知。"),
                createSimpleDetail("支持 Brain 悬浮窗开关和后台服务开关，用户可在设置中控制耗电与可见性。"),
                createSimpleDetail("支持 AI 后台活动、主动关心、课程表提醒等异步事件的统一展示与转发。"),
                createSimpleDetail("Brain 本身保持无状态，只负责转发和展示请求，真实数据仍由 aidata 统一管理。")
            )
        ),

        FeatureSection(
            title = "相册",
            summary = "保存和浏览小手机里的照片，是聊天图片、角色生活记录的重要补充。",
            highlights = listOf("照片网格", "相册列表", "大图查看", "选择管理"),
            details = listOf(
                createSimpleDetail("支持全部照片、相册列表、单个相册照片网格和照片浏览器。"),
                createSimpleDetail("支持照片大图查看、左右滑动浏览、PhotoView 缩放预览和沉浸式查看体验。"),
                createSimpleDetail("支持照片选择模式、多选菜单、照片操作底部弹窗和批量管理。"),
                createSimpleDetail("支持相册封面、照片缩略图、空状态和图片加载失败占位。"),
                createSimpleDetail("相册图片纳入存储统计和完整数据导入导出。"),
                createSimpleDetail("相册可作为聊天、CPhone 生活痕迹和角色记录的图片素材来源。")
            )
        ),
        FeatureSection(
            title = "CPhone 查手机",
            summary = "查看联系人 ai 的小手机内容，了解对方的生活痕迹。",
            highlights = listOf("联系人手机", "相册", "备忘录", "日记", "QQ"),
            details = listOf(
                createSimpleDetail("支持选择联系人并进入对方的模拟手机桌面。"),
                createSimpleDetail("内置相册、浏览器、淘宝、备忘录、日记、高德地图足迹、App 使用记录、音乐播放器、模拟 QQ。"),
                createSimpleDetail("相册可展示联系人手机中的照片墙和图片详情。"),
                createSimpleDetail("浏览器可展示浏览历史、文章列表和文章详情。"),
                createSimpleDetail("淘宝可展示联系人消费偏好、订单记录和商品痕迹。"),
                createSimpleDetail("备忘录支持列表与详情，展示联系人记下的事项、想法和提醒。"),
                createSimpleDetail("日记支持列表与详情，展示联系人私人生活记录和情绪变化。"),
                createSimpleDetail("高德地图足迹可展示地点、时间和移动痕迹，帮助还原 ai 的日常。"),
                createSimpleDetail("App 使用记录可显示联系人使用各应用的频次与习惯。"),
                createSimpleDetail("音乐播放器支持歌曲列表、播放/暂停、上一首下一首和底部迷你播放器。"),
                createSimpleDetail("模拟 QQ 可查看联系人手机里的会话、聊天内容和社交线索。"),
                createSimpleDetail("支持备忘录详情、日记详情、文章详情、音乐迷你播放器和空状态展示。"),
                createSimpleDetail("支持联系人记忆摘要，让用户从对方视角查看被沉淀下来的重要信息。")
            )
        ),
        FeatureSection(
            title = "酒馆记录查看器",
            summary = "独立查看 SillyTavern/酒馆记录，便于迁移、检查和阅读旧聊天。",
            highlights = listOf("文件列表", "聊天查看", "Markdown", "正则"),
            details = listOf(
                createSimpleDetail("支持启动酒馆记录主界面，浏览聊天记录文件列表。"),
                createSimpleDetail("支持直接打开指定聊天记录文件并阅读内容。"),
                createSimpleDetail("支持按消息容器展示用户文本、模型回复和角色头像。"),
                createSimpleDetail("支持 Markdown 渲染，让酒馆记录中的标题、列表、引用等内容更易读。"),
                createSimpleDetail("支持自动阅读、暂停/继续、向上/向下跳转和内容输出控制。"),
                createSimpleDetail("支持 DeepSeek、Gemini、OpenAI 等模型标识和空模型占位展示。"),
                createSimpleDetail("支持正则规则列表、规则下拉选择、规则编辑器、启用/暂停和调试输出。"),
                createSimpleDetail("支持导入旧酒馆记录后作为独立查看器使用，不影响小手机 QQ 的主聊天数据。")
            )
        ),
        FeatureSection(
            title = "商城与背包联动",
            summary = "AI 驱动的虚拟购物系统，让礼物和消费成为聊天互动的一部分。",
            highlights = listOf("商品", "分类", "购物车", "订单", "礼物"),
            details = listOf(
                createSimpleDetail("支持商品分类、商品列表、商品搜索、商品详情、款式/规格选择和图片展示。"),
                createSimpleDetail("支持搜索框、图片占位、图片加载失败提示和商品卡片式展示。"),
                createSimpleDetail("支持添加商品款式，维护不同规格、价格和图片。"),
                createSimpleDetail("支持购物车、下单、订单记录、账户选择和商城消费数据保存。"),
                createSimpleDetail("支持 AI 生成商品和分类，围绕聊天对象扩展可购买物品。"),
                createSimpleDetail("支持从聊天语境生成更贴合角色喜好的虚拟商品。"),
                createSimpleDetail("购买物品可进入背包，并在 QQ 聊天中作为礼物赠送给联系人。"),
                createSimpleDetail("背包支持查看拥有物品、赠送联系人、记录赠礼历史和自动移除已赠物品。"),
                createSimpleDetail("商城消费会与支付宝账单联动，形成完整资金流记录。")
            )
        ),
        FeatureSection(
            title = "支付宝",
            summary = "小手机里的钱包系统，承载余额、账单、支付和工作打卡。",
            highlights = listOf("余额", "账单", "支付", "打卡"),
            details = listOf(
                createSimpleDetail("支持查看当前余额，默认初始化钱包资金。"),
                createSimpleDetail("支持余额卡片、支付宝蓝色主题、下拉刷新和空账单提示。"),
                createSimpleDetail("支持最近账单列表，区分转账收入、转账支出、购物消费、红包收入、红包支出、退款、充值等类型。"),
                createSimpleDetail("账单按时间倒序展示，收入与支出使用不同颜色便于识别。"),
                createSimpleDetail("与 QQ 红包、转账、商城消费等资金流联动，统一沉淀到账单中。"),
                createSimpleDetail("通过支付宝钱包适配器，QQ 旧钱包行为可同步切换到支付宝账户。"),
                createSimpleDetail("支持上班打卡、下班、强制下班、工作状态查询等模拟工作功能。"),
                createSimpleDetail("工作系统可扩展工资、加班费、请假、考勤统计和工作成就等生活模拟玩法。")
            )
        ),
        FeatureSection(
            title = "课程表与校园动态",
            summary = "记录用户校园生活，让 ai 知道课程、作业、考试和调课信息。",
            highlights = listOf("课程", "作业", "考试", "导入", "提醒"),
            details = listOf(
                createSimpleDetail("支持课程表首页、周视图、日程流、考试倒计时、作业列表、导入页和设置页。"),
                createSimpleDetail("支持维护课程名、教师、教室、课程颜色、上课周次、节次和时间规则。"),
                createSimpleDetail("支持新增、编辑、完成、停课、调课等课程与校园事件交互。"),
                createSimpleDetail("支持作业列表、作业完成状态、优先级、截止时间和未完成事项提醒。"),
                createSimpleDetail("支持考试倒计时，帮助用户快速看到临近考试与复习压力。"),
                createSimpleDetail("支持调课、停课、校园动态等特殊事件，避免只显示固定课表。"),
                createSimpleDetail("支持课程、课程时间规则、作业、考试、调课和校园动态数据进入 aidata。"),
                createSimpleDetail("支持生成 AI 可见的校园状态摘要，让对话场景能感知用户近期课程、作业、考试和压力。"),
                createSimpleDetail("支持从文本解析或导入课程数据的路线，降低用户手动录入成本。"),
                createSimpleDetail("支持课程提醒 Worker 和校园主动关心候选事件的规划，让 ai 能在合适时机关心用户。"),
                createSimpleDetail("支持课程表导入，并预留系统通知、主动关心、桌面摘要卡和主题接入能力。")
            )
        ),
        FeatureSection(
            title = "关系图",
            summary = "把联系人之间的重要关系与事件以图谱方式整理出来。",
            highlights = listOf("事件", "关系", "详情", "可视化"),
            details = listOf(
                createSimpleDetail("支持关系图入口和事件图列表展示。"),
                createSimpleDetail("支持以条目形式查看联系人之间发生过的重要事件。"),
                createSimpleDetail("支持底部详情面板，展示事件内容、关联角色和上下文信息。"),
                createSimpleDetail("支持查看事件图条目详情，帮助用户理解角色之间的关系变化。"),
                createSimpleDetail("可与长期记忆、事实抽取和聊天事件沉淀能力配合使用。"),
                createSimpleDetail("适合用来回顾 ai 之间或用户与 ai 之间的关键节点、矛盾、亲密变化和共同经历。")
            )
        ),
        FeatureSection(
            title = "世界书、主题与预设",
            summary = "负责角色设定、视觉风格和模型配置的扩展能力。",
            highlights = listOf("世界书", "主题", "预设", "模型配置"),
            details = listOf(
                createSimpleDetail("世界书集用于管理世界观、角色设定、条目和提示词背景。"),
                createSimpleDetail("世界书条目可作为 AI 上下文的一部分，让回复保持世界观和设定一致。"),
                createSimpleDetail("支持世界书列表、条目管理、工具栏操作和上下文菜单。"),
                createSimpleDetail("主题应用用于定制桌面图标、壁纸、Dock 透明度、圆角、文字颜色和阴影。"),
                createSimpleDetail("主题配置会影响桌面图标 URI、壁纸 URI、应用名称颜色、Dock 背景颜色和透明度。"),
                createSimpleDetail("预设应用用于管理模型或提示词预设，让不同场景可以快速切换配置。"),
                createSimpleDetail("预设能力可配合设置中心的主模型、副模型、Embedding、ASR 等 API 配置。"),
                createSimpleDetail("相关数据纳入导入导出，保证迁移时设定、主题和配置不会丢失。")
            )
        ),
        FeatureSection(
            title = "安全、隐私与维护工具",
            summary = "帮助用户知道数据在哪里、如何备份，以及如何安全维护小手机。",
            highlights = listOf("备份", "恢复", "迁移", "隐私", "修复"),
            details = listOf(
                createSimpleDetail("完整应用数据导出会尽量覆盖联系人、消息、世界书、相册、CPhone、桌面布局等核心数据。"),
                createSimpleDetail("导出文件会记录导出版本和导出时间，便于用户判断备份来源。"),
                createSimpleDetail("导入时提供智能合并和覆盖导入，并在冲突时显示旧数据与导入数据差异。"),
                createSimpleDetail("冲突解决界面会按联系人资料、消息、随笔、世界书条目等类型展示差异。"),
                createSimpleDetail("数据检查与修复目前聚焦桌面和 Dock 重复入口，不会修改聊天记录、联系人、相册、世界书和设置。"),
                createSimpleDetail("存储统计会分类展示头像、聊天图片、相册、聊天记录、向量数据、其他数据和总计。"),
                createSimpleDetail("图片压缩等不可逆维护操作会提示用户先备份，避免误伤重要数据。"),
                createSimpleDetail("隐私政策和许可证信息集中在关于页面，用户可随时查看。"),
                createSimpleDetail("应用声明自身是娱乐性质的模拟手机，不是真实手机系统，提醒用户理性使用。")
            )
        )
    )

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): View {
        val rootLayout: LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar: MaterialToolbar = createToolbar()
        rootLayout.addView(createAppBar(toolbar))
        rootLayout.addView(createScrollContent())
        return rootLayout
    }

    private fun createToolbar(): MaterialToolbar {
        val toolbar: MaterialToolbar = MaterialToolbar(this).apply {
            title = "功能一览"
            setNavigationIcon(CoreR.drawable.ic_arrow_back_24)
            setNavigationOnClickListener { finish() }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getActionBarHeight()
            )
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        return toolbar
    }

    private fun createAppBar(toolbar: MaterialToolbar): AppBarLayout {
        return AppBarLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(toolbar)
        }
    }

    private fun createScrollContent(): ScrollView {
        val scrollView: ScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(resolveColor(android.R.attr.colorBackground))
        }

        val container: LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp())
        }

        container.addView(createHeroCard())
        featureSections.forEachIndexed { index: Int, section: FeatureSection ->
            container.addView(createFeatureCard(index = index + 1, section = section))
        }
        container.addView(createFooterText())
        scrollView.addView(container)
        return scrollView
    }

    private fun createHeroCard(): MaterialCardView {
        val card: MaterialCardView = createBaseCard().apply {
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorPrimaryContainer))
        }
        val contentLayout: LinearLayout = createVerticalContentLayout()
        contentLayout.addView(createTextView("小手机能做什么？", 24f, true, resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer)))
        contentLayout.addView(createTextView("目前为ai总结出来的，不保证能用。后续逐步更换为人话", 15f, false, resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer)))
        card.addView(contentLayout)
        return card
    }

    private fun createFeatureCard(index: Int, section: FeatureSection): MaterialCardView {
        val card: MaterialCardView = createBaseCard()
        val contentLayout: LinearLayout = createVerticalContentLayout()
        contentLayout.addView(createSectionTitle(index = index, title = section.title))
        contentLayout.addView(createTextView(section.summary, 14f, false, resolveColor(android.R.attr.textColorSecondary)))
        contentLayout.addView(createChipGroup(section.highlights))
        section.details.forEach { detail: FeatureDetailItem ->
            contentLayout.addView(createDetailCard(detail))
        }
        card.addView(contentLayout)
        return card
    }

    private fun createSectionTitle(index: Int, title: String): TextView {
        return createTextView("$index. $title", 18f, true, resolveColor(android.R.attr.textColorPrimary)).apply {
            setPadding(0, 0, 0, 6.dp())
        }
    }

    private fun createChipGroup(highlights: List<String>): ChipGroup {
        return ChipGroup(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp()
                bottomMargin = 6.dp()
            }
            isSingleLine = false
            highlights.forEach { highlight: String -> addView(createChip(highlight)) }
        }
    }

    private fun createChip(text: String): Chip {
        return Chip(this).apply {
            this.text = text
            isClickable = false
            isCheckable = false
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(resolveColor(com.google.android.material.R.attr.colorSecondaryContainer))
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
        }
    }

    private fun createDetailCard(detailItem: FeatureDetailItem): MaterialCardView {
        val card: MaterialCardView = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
            radius = 16.dp().toFloat()
            cardElevation = 0.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = resolveColor(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurface))
        }
        val itemLayout: LinearLayout = createDetailItemLayout()
        val headerLayout: LinearLayout = createDetailHeaderLayout()
        val titleLayout: LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleText: TextView = createTextView(detailItem.title, 15f, true, resolveColor(android.R.attr.textColorPrimary))
        val subtitleText: TextView = createTextView(detailItem.subtitle ?: "点击查看详细说明", 12f, false, resolveColor(android.R.attr.textColorSecondary)).apply {
            setPadding(0, 3.dp(), 0, 0)
        }
        val actionText: TextView = createTextView("展开", 12f, true, resolveColor(com.google.android.material.R.attr.colorPrimary)).apply {
            gravity = Gravity.CENTER
            setPadding(10.dp(), 4.dp(), 0, 4.dp())
        }
        val detailContentLayout: LinearLayout = createDetailContentLayout(detailItem).apply {
            visibility = View.GONE
        }
        titleLayout.addView(titleText)
        titleLayout.addView(subtitleText)
        headerLayout.addView(titleLayout)
        headerLayout.addView(actionText)
        itemLayout.addView(headerLayout)
        itemLayout.addView(detailContentLayout)
        card.addView(itemLayout)
        val toggleAction: (View) -> Unit = {
            val isExpanded: Boolean = detailContentLayout.visibility == View.VISIBLE
            detailContentLayout.visibility = if (isExpanded) View.GONE else View.VISIBLE
            actionText.text = if (isExpanded) "展开" else "收回"
        }
        headerLayout.setOnClickListener(toggleAction)
        titleText.setOnClickListener(toggleAction)
        return card
    }

    private fun createDetailItemLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp())
        }
    }

    private fun createDetailHeaderLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
        }
    }

    private fun createDetailContentLayout(detailItem: FeatureDetailItem): LinearLayout {
        val contentLayout: LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10.dp(), 0, 0)
        }
        detailItem.sections.forEach { section: FeatureDetailSection ->
            contentLayout.addView(createDetailParagraph(label = section.title, content = section.content))
        }
        if (detailItem.sections.isEmpty()) {
            contentLayout.addView(createTextView(detailItem.title, 13f, false, resolveColor(android.R.attr.textColorSecondary)).apply {
                setLineSpacing(3.dp().toFloat(), 1.0f)
            })
        }
        return contentLayout
    }

    private fun createDetailParagraph(label: String, content: String): LinearLayout {
        val paragraphLayout: LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 8.dp())
        }
        paragraphLayout.addView(createTextView(label, 12f, true, resolveColor(com.google.android.material.R.attr.colorPrimary)))
        paragraphLayout.addView(createTextView(content, 13f, false, resolveColor(android.R.attr.textColorPrimary)).apply {
            setPadding(0, 3.dp(), 0, 0)
            setLineSpacing(3.dp().toFloat(), 1.0f)
        })
        return paragraphLayout
    }

    private fun createFooterText(): TextView {
        return createTextView("功能会随着小手机继续成长而更新。新增数据能力时，会同步考虑导入导出与迁移。", 13f, false, resolveColor(android.R.attr.textColorSecondary)).apply {
            gravity = Gravity.CENTER
            setPadding(8.dp(), 18.dp(), 8.dp(), 28.dp())
        }
    }

    private fun createBaseCard(): MaterialCardView {
        return MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14.dp()
            }
            radius = 22.dp().toFloat()
            cardElevation = 3.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = resolveColor(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainer))
        }
    }

    private fun createVerticalContentLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp())
        }
    }

    private fun createTextView(text: String, textSizeSp: Float, isBold: Boolean, textColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = textSizeSp
            setTextColor(textColor)
            setTypeface(if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
            setLineSpacing(2.dp().toFloat(), 1.0f)
        }
    }

    private fun getActionBarHeight(): Int {
        val typedValue: android.util.TypedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
        return android.util.TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
    }

    private fun resolveColor(attributeId: Int): Int {
        val typedValue: android.util.TypedValue = android.util.TypedValue()
        val hasValue: Boolean = theme.resolveAttribute(attributeId, typedValue, true)
        if (!hasValue) return Color.TRANSPARENT
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private data class FeatureSection(
        val title: String,
        val summary: String,
        val highlights: List<String>,
        val details: List<FeatureDetailItem>
    )

    private fun createSimpleDetail(title: String): FeatureDetailItem {
        return FeatureDetailItem(title = title)
    }

    private data class FeatureDetailItem(
        val title: String,
        val subtitle: String? = null,
        val sections: List<FeatureDetailSection> = emptyList()
    )

    private data class FeatureDetailSection(
        val title: String,
        val content: String
    )
}
