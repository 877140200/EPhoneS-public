package com.susking.ephone_s.aidata.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.susking.ephone_s.aidata.data.local.dao.*
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEvidenceDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryGraphDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryRecallDebugDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemorySummaryDao
import com.susking.ephone_s.aidata.data.local.converters.LongListConverter
import com.susking.ephone_s.aidata.data.local.converters.MemoryTypeConverters
import com.susking.ephone_s.aidata.data.local.converters.VideoCallMessageListConverter
import com.susking.ephone_s.aidata.data.local.entity.*
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.LongTermMemoryFts
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEmbedding
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventEvidence
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphNode
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphRelation
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRelationEvidence
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugEntry
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugRecord
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary

/**
 * aidata 模块的独立数据库
 * 管理所有与 AI 行为相关的数据
 */
@Database(
    entities = [
        ChatMessageEntity::class,
        AiResponseVersionEntity::class,
        WorldBookEntity::class,
        WorldBookEntryEntity::class,
        LongTermMemory::class,
        FeedEntity::class,
        StickerCategoryEntity::class,
        StickerEntity::class,
        FavoriteMessageEntity::class,
        HeartbeatEntity::class,
        JottingEntity::class,
        NotificationEntity::class,
        AppointmentEntity::class,
        PersonProfileEntity::class,
        BackpackItemEntity::class,
        GeneralMemoryEntity::class,
        VideoCallHistoryEntity::class,
        ScheduledGreetingEntity::class,
        ContactSemanticStateEntity::class,
        ScheduleCourseEntity::class,
        ScheduleCourseRuleEntity::class,
        ScheduleAdjustmentEntity::class,
        ScheduleAssignmentEntity::class,
        ScheduleExamEntity::class,
        CampusEventEntity::class,
        ScheduleAiPolicyEntity::class,
        ScheduleSemesterEntity::class,
        ScheduleSectionTemplateEntity::class,
        ScheduleImportDraftEntity::class,
        ScheduleReminderRuleEntity::class,
        ScheduleReminderRecordEntity::class,
        ScheduleCareCandidateEntity::class,
        ScheduleWidgetStateEntity::class,

        // --- 健康数据（AI 健康关怀）---
        HealthDailyRecordEntity::class,

        // --- 提示词储存器（酒馆「锦囊」）---
        PromptSentenceEntity::class,
        PromptWordEntity::class,

        // --- 新增记忆系统实体 ---
        MemoryEmbedding::class,
        MemorySummary::class,
        MemoryEvent::class,
        MemoryGraphNode::class,
        MemoryGraphRelation::class,
        MemoryEventEvidence::class,
        MemoryRelationEvidence::class,
        MemoryRecallDebugRecord::class,
        MemoryRecallDebugEntry::class,
        LongTermMemoryFts::class, // 新增全文搜索虚拟表
    ],
    version = 57, // 新增提示词储存器 prompt_sentences / prompt_words 两表（酒馆「锦囊」）
    exportSchema = true // 建议开启以便于追踪和迁移
)
@TypeConverters(
    ChatMessageConverters::class,
    FeedTypeConverters::class,
    BigDecimalConverter::class,
    DateConverter::class,
    VideoCallMessageListConverter::class,
    LongListConverter::class,
    MemoryTypeConverters::class // 新增记忆系统类型转换器
)
abstract class AiDataDatabase : RoomDatabase() {

    // DAO 抽象方法
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun aiResponseVersionDao(): AiResponseVersionDao
    abstract fun worldBookDao(): WorldBookDao
    abstract fun worldBookEntryDao(): WorldBookEntryDao
    abstract fun longTermMemoryDao(): LongTermMemoryDao
    abstract fun feedDao(): FeedDao
    abstract fun stickerDao(): StickerDao
    abstract fun favoriteMessageDao(): FavoriteMessageDao
    abstract fun heartbeatDao(): HeartbeatDao
    abstract fun jottingDao(): JottingDao
    abstract fun notificationDao(): NotificationDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun personProfileDao(): PersonProfileDao
    abstract fun backpackItemDao(): BackpackItemDao
    abstract fun generalMemoryDao(): GeneralMemoryDao
    abstract fun videoCallHistoryDao(): VideoCallHistoryDao
    abstract fun scheduledGreetingDao(): ScheduledGreetingDao
    abstract fun contactSemanticStateDao(): ContactSemanticStateDao
    abstract fun scheduleDao(): ScheduleDao

    // --- 健康数据 DAO（AI 健康关怀）---
    abstract fun healthDailyRecordDao(): com.susking.ephone_s.aidata.data.local.dao.HealthDailyRecordDao

    // --- 提示词储存器 DAO（酒馆「锦囊」）---
    abstract fun promptStorageDao(): com.susking.ephone_s.aidata.data.local.dao.PromptStorageDao

    // --- 新增记忆系统 DAO ---
    abstract fun memoryEmbeddingDao(): MemoryEmbeddingDao
    abstract fun memorySummaryDao(): MemorySummaryDao
    abstract fun memoryEventDao(): MemoryEventDao
    abstract fun memoryEvidenceDao(): MemoryEvidenceDao
    abstract fun memoryGraphDao(): MemoryGraphDao
    abstract fun memoryRecallDebugDao(): MemoryRecallDebugDao

    companion object {
        @Volatile
        private var INSTANCE: AiDataDatabase? = null

        private val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 校历事件功能已废弃，迁移时清理旧表，避免历史数据继续占用空间。
                database.execSQL("DROP TABLE IF EXISTS `schedule_calendar_events`")
            }
        }

        private val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 新增小米 MiMo TTS 缓存元数据。音频文件仍复用 voiceAudioPath 和 voiceDurationMillis。
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `ttsGenerationStatus` TEXT")
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `ttsModelId` TEXT")
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `ttsVoiceId` TEXT")
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `ttsGeneratedAt` INTEGER")
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `ttsErrorMessage` TEXT")
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `ttsIsStreaming` INTEGER")

                // 新增联系人专属 TTS 预置音色。为空时使用全局默认音色。
                database.execSQL("ALTER TABLE `person_profiles` ADD COLUMN `ttsVoiceId` TEXT")
            }
        }

        private val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 新增联系人音色文本描述，用于 voicedesign 模型
                database.execSQL("ALTER TABLE `person_profiles` ADD COLUMN `voiceDescription` TEXT")
            }
        }

        private val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 删除 video_call_history 表的 chronometerBase 列。
                // 该列原用于持久化计时器 base 值,但存的是 SystemClock.elapsedRealtime()(相对开机时间),
                // 进程重启后开机基准变化导致值失效,且代码中从未读回,是只写不读的死字段。
                // 计时器恢复已改为用 timestamp(墙钟开始时间)反推,不再需要此列。
                //
                // SQLite 不支持直接 DROP COLUMN(旧版本),采用标准的"重建表"方案,完整保留用户历史数据:
                // 1. 建不含 chronometerBase 的新表 2. 复制全部数据 3. 删旧表 4. 新表改回原名。
                // 整个迁移在 Room 的事务中执行,要么全部成功要么全部回滚,不会产生半成品或丢数据。
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_call_history_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `contactId` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `messages` TEXT NOT NULL,
                        `wasInitiatedByUser` INTEGER NOT NULL,
                        `terminationReason` TEXT,
                        `callStatus` TEXT NOT NULL,
                        `lastUpdateTime` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `video_call_history_new` (
                        `id`, `contactId`, `timestamp`, `duration`, `messages`,
                        `wasInitiatedByUser`, `terminationReason`, `callStatus`, `lastUpdateTime`
                    )
                    SELECT
                        `id`, `contactId`, `timestamp`, `duration`, `messages`,
                        `wasInitiatedByUser`, `terminationReason`, `callStatus`, `lastUpdateTime`
                    FROM `video_call_history`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `video_call_history`")
                database.execSQL("ALTER TABLE `video_call_history_new` RENAME TO `video_call_history`")
            }
        }

        /**
         * 补全课程表(schedule_*)全部 14 张表及其索引的建表迁移。
         *
         * 背景:这些 schedule 实体早先只通过 Room 的全新安装 schema 创建,迁移链里从未有过
         * 对应的 CREATE TABLE。任何从更早版本升级、且本地没有这些表的老用户,在 Room 校验
         * schema identity hash 时会因找不到表而抛 IllegalStateException 崩溃。
         *
         * 修复方式:统一用 CREATE TABLE IF NOT EXISTS 补建。已经有这些表的用户(在引入 schedule
         * 之后才全新安装)执行时是无害的空操作,缺表的老用户则被补齐,两种情况都安全。
         *
         * 注意:实体使用 Kotlin 构造函数默认值,而非 @ColumnInfo(defaultValue),因此 Room 期望的
         * schema 中这些列没有 SQL 层默认值。建表语句也必须不带 DEFAULT,否则 schema hash 校验失败。
         * 索引名严格采用 Room 默认命名规则 index_<表名>_<列名以下划线连接>。
         */
        private val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 课程本体
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_courses` (
                        `courseId` TEXT NOT NULL,
                        `courseName` TEXT NOT NULL,
                        `teacherName` TEXT NOT NULL,
                        `classroom` TEXT NOT NULL,
                        `courseColor` TEXT NOT NULL,
                        `semesterName` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`courseId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_courses_semesterName_isEnabled` ON `schedule_courses` (`semesterName`, `isEnabled`)")

                // 2. 课程重复时间规则
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_course_rules` (
                        `ruleId` TEXT NOT NULL,
                        `courseId` TEXT NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `startSection` INTEGER NOT NULL,
                        `endSection` INTEGER NOT NULL,
                        `startTimeText` TEXT NOT NULL,
                        `endTimeText` TEXT NOT NULL,
                        `startWeek` INTEGER NOT NULL,
                        `endWeek` INTEGER NOT NULL,
                        `weekMode` TEXT NOT NULL,
                        `classroomOverride` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ruleId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_course_rules_courseId` ON `schedule_course_rules` (`courseId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_course_rules_dayOfWeek_startSection_endSection` ON `schedule_course_rules` (`dayOfWeek`, `startSection`, `endSection`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_course_rules_startWeek_endWeek_weekMode` ON `schedule_course_rules` (`startWeek`, `endWeek`, `weekMode`)")

                // 3. 调课 / 停课 / 补课记录
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_adjustments` (
                        `adjustmentId` TEXT NOT NULL,
                        `courseId` TEXT,
                        `originalDate` INTEGER NOT NULL,
                        `originalStartSection` INTEGER NOT NULL,
                        `originalEndSection` INTEGER NOT NULL,
                        `adjustedDate` INTEGER,
                        `adjustedStartSection` INTEGER,
                        `adjustedEndSection` INTEGER,
                        `originalClassroom` TEXT NOT NULL,
                        `adjustedClassroom` TEXT NOT NULL,
                        `adjustmentType` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`adjustmentId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_adjustments_courseId` ON `schedule_adjustments` (`courseId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_adjustments_originalDate` ON `schedule_adjustments` (`originalDate`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_adjustments_adjustedDate` ON `schedule_adjustments` (`adjustedDate`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_adjustments_adjustmentType` ON `schedule_adjustments` (`adjustmentType`)")

                // 4. 作业任务
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_assignments` (
                        `assignmentId` TEXT NOT NULL,
                        `courseId` TEXT,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `dueAt` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `completedAt` INTEGER,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`assignmentId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_assignments_courseId` ON `schedule_assignments` (`courseId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_assignments_dueAt` ON `schedule_assignments` (`dueAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_assignments_status` ON `schedule_assignments` (`status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_assignments_priority` ON `schedule_assignments` (`priority`)")

                // 5. 考试安排
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_exams` (
                        `examId` TEXT NOT NULL,
                        `courseId` TEXT,
                        `examName` TEXT NOT NULL,
                        `examAt` INTEGER NOT NULL,
                        `classroom` TEXT NOT NULL,
                        `scopeText` TEXT NOT NULL,
                        `examType` TEXT NOT NULL,
                        `reviewStatus` TEXT NOT NULL,
                        `importance` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`examId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_exams_courseId` ON `schedule_exams` (`courseId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_exams_examAt` ON `schedule_exams` (`examAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_exams_reviewStatus` ON `schedule_exams` (`reviewStatus`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_exams_importance` ON `schedule_exams` (`importance`)")

                // 6. 校园动态
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `campus_events` (
                        `eventId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `startAt` INTEGER NOT NULL,
                        `endAt` INTEGER NOT NULL,
                        `location` TEXT NOT NULL,
                        `importance` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`eventId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_campus_events_eventType` ON `campus_events` (`eventType`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_campus_events_startAt` ON `campus_events` (`startAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_campus_events_endAt` ON `campus_events` (`endAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_campus_events_isCompleted` ON `campus_events` (`isCompleted`)")

                // 7. AI 关心策略(单行,主键 default)
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_ai_policies` (
                        `policyId` TEXT NOT NULL,
                        `isAiVisible` INTEGER NOT NULL,
                        `canAiProactivelyCare` INTEGER NOT NULL,
                        `careIntensity` TEXT NOT NULL,
                        `visibleRange` TEXT NOT NULL,
                        `includeTomorrowCourses` INTEGER NOT NULL,
                        `includeCompletedAssignments` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`policyId`)
                    )
                    """.trimIndent()
                )

                // 8. 学期配置
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_semesters` (
                        `semesterId` TEXT NOT NULL,
                        `semesterName` TEXT NOT NULL,
                        `startDateMillis` INTEGER NOT NULL,
                        `totalWeeks` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`semesterId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_semesters_isActive_startDateMillis` ON `schedule_semesters` (`isActive`, `startDateMillis`)")

                // 9. 节次模板
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_section_templates` (
                        `sectionTemplateId` TEXT NOT NULL,
                        `semesterId` TEXT NOT NULL,
                        `sectionIndex` INTEGER NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `startTimeText` TEXT NOT NULL,
                        `endTimeText` TEXT NOT NULL,
                        `dayPart` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sectionTemplateId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_section_templates_semesterId` ON `schedule_section_templates` (`semesterId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_section_templates_sectionIndex` ON `schedule_section_templates` (`sectionIndex`)")

                // 10. 导入草稿
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_import_drafts` (
                        `draftId` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `rawContent` TEXT NOT NULL,
                        `previewJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`draftId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_import_drafts_sourceType` ON `schedule_import_drafts` (`sourceType`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_import_drafts_status` ON `schedule_import_drafts` (`status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_import_drafts_createdAt` ON `schedule_import_drafts` (`createdAt`)")

                // 11. 本地提醒规则
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_reminder_rules` (
                        `reminderRuleId` TEXT NOT NULL,
                        `targetType` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `minutesBefore` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`reminderRuleId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_reminder_rules_targetType_targetId` ON `schedule_reminder_rules` (`targetType`, `targetId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_reminder_rules_isEnabled` ON `schedule_reminder_rules` (`isEnabled`)")

                // 12. 本地提醒记录
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_reminder_records` (
                        `reminderRecordId` TEXT NOT NULL,
                        `reminderRuleId` TEXT NOT NULL,
                        `targetType` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `triggerAt` INTEGER NOT NULL,
                        `deliveredAt` INTEGER,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`reminderRecordId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_reminder_records_reminderRuleId` ON `schedule_reminder_records` (`reminderRuleId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_reminder_records_targetType_targetId` ON `schedule_reminder_records` (`targetType`, `targetId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_reminder_records_triggerAt` ON `schedule_reminder_records` (`triggerAt`)")

                // 13. AI 主动关心候选
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_care_candidates` (
                        `careCandidateId` TEXT NOT NULL,
                        `targetType` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `nextEligibleAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`careCandidateId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_care_candidates_targetType_targetId` ON `schedule_care_candidates` (`targetType`, `targetId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_care_candidates_status` ON `schedule_care_candidates` (`status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_care_candidates_nextEligibleAt` ON `schedule_care_candidates` (`nextEligibleAt`)")

                // 14. 桌面小组件状态(单行,主键 default)
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_widget_states` (
                        `widgetId` TEXT NOT NULL,
                        `nextCourseText` TEXT NOT NULL,
                        `examCountdownText` TEXT NOT NULL,
                        `pendingAssignmentText` TEXT NOT NULL,
                        `accentColor` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`widgetId`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * 语义账本新增“写前快照”两列，用于支持重试/重说时回退被丢弃那一轮造成的语义更新。
         *
         * - previousStateJson：每次写语义前，把当前整条状态序列化存进去（一步撤销）。
         * - lastUpdateAiTurnId：记录产生当前态的 AI 回复轮次 ID，回退时据此校验，避免误退合法轮次。
         *
         * 两列均可空、无 SQL 默认值，与实体的 Kotlin 默认值(null)一致，符合 Room schema hash 校验要求。
         */
        private val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `contact_semantic_states` ADD COLUMN `previousStateJson` TEXT")
                database.execSQL("ALTER TABLE `contact_semantic_states` ADD COLUMN `lastUpdateAiTurnId` TEXT")
            }
        }

        /**
         * 为 person_profiles 新增 autoSummaryFailureCount 列。
         *
         * 该列记录自动结构化事件提取的连续失败次数，失败 +1、成功清零，供触发器做指数退避，
         * 避免提取持续失败时每条新消息都全量重试、刷爆悬浮窗、烧 token。
         *
         * 列为 NOT NULL，SQLite 要求加非空列时必须给默认值；这里用 DEFAULT 0，与实体上的
         * @ColumnInfo(defaultValue = "0") 一致，满足 Room schema identity hash 校验。
         */
        private val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `person_profiles` ADD COLUMN `autoSummaryFailureCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 新增 health_daily_records 表，支持「AI 健康关怀」的每日健康数据汇总。
         *
         * 数据来源为系统 Health Connect（经 Health Sync 灌入），本表只按天存汇总值。
         * 建表用 CREATE TABLE IF NOT EXISTS 保证幂等；各列默认值与实体 @ColumnInfo(defaultValue=...)
         * 严格一致（数值列 NOT NULL DEFAULT 0），可空心率列不带 SQL 默认值（与 Kotlin 默认 null 对应），
         * 满足 Room schema identity hash 校验。
         */
        private val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_daily_records` (
                        `date` TEXT NOT NULL,
                        `steps` INTEGER NOT NULL DEFAULT 0,
                        `distanceMeters` REAL NOT NULL DEFAULT 0,
                        `activeCaloriesKcal` REAL NOT NULL DEFAULT 0,
                        `sleepTotalMinutes` INTEGER NOT NULL DEFAULT 0,
                        `sleepDeepMinutes` INTEGER NOT NULL DEFAULT 0,
                        `sleepLightMinutes` INTEGER NOT NULL DEFAULT 0,
                        `sleepRemMinutes` INTEGER NOT NULL DEFAULT 0,
                        `sleepSessionCount` INTEGER NOT NULL DEFAULT 0,
                        `heartRateAvg` INTEGER,
                        `heartRateMax` INTEGER,
                        `heartRateMin` INTEGER,
                        `restingHeartRate` INTEGER,
                        `lastSyncTime` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * health_daily_records 新增睡眠时间段与同步数据类型列，支持睡眠详情页与同步信息展示。
         *
         * 新增列：
         * - sleepStartTime/sleepEndTime（可空，睡眠起止时间戳）用于卡片显示时间段。
         * - syncedDataTypes（TEXT NOT NULL DEFAULT ''，存"步数、睡眠、心率"这类文本）用于主页显示同步内容。
         *
         * 可空列无 SQL DEFAULT（与 Kotlin 默认 null 对应），NOT NULL 列带 DEFAULT ''，满足 Room schema hash 校验。
         */
        private val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `health_daily_records` ADD COLUMN `sleepStartTime` INTEGER")
                database.execSQL("ALTER TABLE `health_daily_records` ADD COLUMN `sleepEndTime` INTEGER")
                database.execSQL("ALTER TABLE `health_daily_records` ADD COLUMN `syncedDataTypes` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 新增提示词储存器两张表（酒馆「锦囊」提示词储存器）。
         *
         * 用 CREATE TABLE IF NOT EXISTS 保证幂等；id 为自增主键（INTEGER PRIMARY KEY AUTOINCREMENT），
         * content 与 createdAt 为 NOT NULL，但实体使用 Kotlin 构造函数默认值而非 @ColumnInfo(defaultValue)，
         * 故建表语句不带 DEFAULT，与 Room 期望的 schema identity hash 一致。
         */
        private val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prompt_sentences` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prompt_words` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AiDataDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AiDataDatabase::class.java,
                    "aidata_database"
                )
                    .addMigrations(MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53, MIGRATION_53_54, MIGRATION_54_55, MIGRATION_55_56, MIGRATION_56_57)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}