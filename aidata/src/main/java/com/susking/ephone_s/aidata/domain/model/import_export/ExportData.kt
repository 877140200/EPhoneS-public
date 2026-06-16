package com.susking.ephone_s.aidata.domain.model.import_export

import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.BackpackItemEntity
import com.susking.ephone_s.aidata.data.local.entity.CampusEventEntity
import com.susking.ephone_s.aidata.data.local.entity.ContactSemanticStateEntity
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.aidata.data.local.entity.FeedEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity
import com.susking.ephone_s.aidata.data.local.entity.HealthDailyRecordEntity
import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import com.susking.ephone_s.aidata.data.local.entity.NotificationEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAdjustmentEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAiPolicyEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAssignmentEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCareCandidateEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseRuleEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleExamEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleImportDraftEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleReminderRecordEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleReminderRuleEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSectionTemplateEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSemesterEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduledGreetingEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleWidgetStateEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingAuthorizedAccountEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCartItemEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCategoryEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingOrderEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingProductEntity
import com.susking.ephone_s.aidata.data.local.entity.StickerCategoryEntity
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity
import com.susking.ephone_s.aidata.domain.alipay.BillRecord
import com.susking.ephone_s.aidata.domain.alipay.WalletInfo
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile

/**
 * 导出数据模型 - 用于导出和导入应用数据
 *
 * 支持三种导出类型:
 * 1. 单个联系人导出 (isCompleteBackup = false, 只包含contacts和chatMessages)
 * 2. QQ全部数据导出 (isCompleteBackup = false, 包含所有QQ相关数据)
 * 3. 完整应用数据导出 (isCompleteBackup = true, 包含所有应用数据)
 */
data class ExportData(
    // ========== QQ应用数据 ==========
    val contacts: List<PersonProfile>? = emptyList(),
    val chatMessages: List<ChatMessage>? = emptyList(),
    val longTermMemories: List<LongTermMemory>? = emptyList(),
    val heartbeats: List<HeartbeatEntity>? = emptyList(),
    val jottings: List<JottingEntity>? = emptyList(),
    val semanticStates: List<ContactSemanticStateEntity>? = emptyList(),
    val favoriteMessages: List<FavoriteMessageEntity>? = emptyList(),
    val userProfile: UserProfile? = null,
    val feeds: List<FeedEntity>? = emptyList(),
    val wallet: WalletInfo? = null,
    val transactions: List<BillRecord>? = emptyList(),
    val friendGroups: List<String>? = null,
    val appointments: List<AppointmentEntity>? = emptyList(),
    val headerBackgroundImage: String? = null,
    val stickers: List<StickerEntity>? = emptyList(),
    val stickerCategories: List<StickerCategoryEntity>? = emptyList(),
    val notifications: List<NotificationEntity>? = emptyList(),
    val videoCallHistory: List<VideoCallHistoryEntity>? = emptyList(),

    // ========== 课程表与校园动态数据 ==========
    val scheduleCourses: List<ScheduleCourseEntity>? = emptyList(),
    val scheduleCourseRules: List<ScheduleCourseRuleEntity>? = emptyList(),
    val scheduleAdjustments: List<ScheduleAdjustmentEntity>? = emptyList(),
    val scheduleAssignments: List<ScheduleAssignmentEntity>? = emptyList(),
    val scheduleExams: List<ScheduleExamEntity>? = emptyList(),
    val campusEvents: List<CampusEventEntity>? = emptyList(),
    val scheduleAiPolicy: ScheduleAiPolicyEntity? = null,
    val scheduleSemesters: List<ScheduleSemesterEntity>? = emptyList(),
    val scheduleSectionTemplates: List<ScheduleSectionTemplateEntity>? = emptyList(),
    val scheduleImportDrafts: List<ScheduleImportDraftEntity>? = emptyList(),
    val scheduleReminderRules: List<ScheduleReminderRuleEntity>? = emptyList(),
    val scheduleReminderRecords: List<ScheduleReminderRecordEntity>? = emptyList(),
    val scheduleCareCandidates: List<ScheduleCareCandidateEntity>? = emptyList(),
    val scheduleWidgetState: ScheduleWidgetStateEntity? = null,

    // ========== 其他应用数据 (完整备份时使用) ==========
    val worldBooks: List<WorldBookEntity>? = emptyList(),
    val worldBookEntries: List<WorldBookEntryEntity>? = emptyList(),

    // 购物应用数据
    val shoppingCategories: List<ShoppingCategoryEntity>? = emptyList(),
    val shoppingProducts: List<ShoppingProductEntity>? = emptyList(),
    val shoppingCartItems: List<ShoppingCartItemEntity>? = emptyList(),
    val shoppingOrders: List<ShoppingOrderEntity>? = emptyList(),
    val shoppingAuthorizedAccounts: List<ShoppingAuthorizedAccountEntity>? = emptyList(),

    // 背包/通用记忆/定时问候
    val backpackItems: List<BackpackItemEntity>? = emptyList(),
    val generalMemories: List<GeneralMemoryEntity>? = emptyList(),
    val scheduledGreetings: List<ScheduledGreetingEntity>? = emptyList(),

    // 健康数据（AI 健康关怀，每日汇总）
    val healthDailyRecords: List<HealthDailyRecordEntity>? = emptyList(),

    // 提示词储存器数据（酒馆「锦囊」；词语数量范围随 allSharedPreferences 备份）
    val promptSentences: List<com.susking.ephone_s.aidata.data.local.entity.PromptSentenceEntity>? = emptyList(),
    val promptWords: List<com.susking.ephone_s.aidata.data.local.entity.PromptWordEntity>? = emptyList(),

    // AI记忆系统数据
    val memoryEmbeddings: List<com.susking.ephone_s.aidata.domain.model.memory.MemoryEmbedding>? = emptyList(),
    val memorySummaries: List<com.susking.ephone_s.aidata.domain.model.memory.MemorySummary>? = emptyList(),
    val memoryEvents: List<com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent>? = emptyList(),
    val memoryGraphNodes: List<com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphNode>? = emptyList(),
    val memoryGraphRelations: List<com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphRelation>? = emptyList(),
    val memoryEventEvidences: List<com.susking.ephone_s.aidata.domain.model.memory.MemoryEventEvidence>? = emptyList(),
    val memoryRelationEvidences: List<com.susking.ephone_s.aidata.domain.model.memory.MemoryRelationEvidence>? = emptyList(),
    val memoryRecallDebugRecords: List<com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugRecord>? = emptyList(),
    val memoryRecallDebugEntries: List<com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugEntry>? = emptyList(),

    // 注意: 相册、CPhone、Brain数据由于依赖其他模块的数据库,
    // 将在后续步骤中通过JSON字符串形式存储
    val albumDataJson: String? = null,        // 相册数据的JSON字符串
    val cphoneDataJson: String? = null,       // CPhone数据的JSON字符串
    val brainDataJson: String? = null,        // Brain数据的JSON字符串

    // ========== 配置数据 ==========
    val allSharedPreferences: Map<String, Map<String, Any?>>? = null,  // 所有SharedPreferences数据
    val desktopLayoutPages: String? = null,   // 桌面页面布局(JSON)
    val desktopLayoutDock: String? = null,    // 桌面Dock布局(JSON)

    // ========== 元数据 ==========
    val exportVersion: String = "1.0.0",          // 导出格式版本
    val exportTimestamp: Long = System.currentTimeMillis(),  // 导出时间戳
    val appVersion: String? = null,               // 应用版本号
    val isCompleteBackup: Boolean = false         // 是否为完整备份
)