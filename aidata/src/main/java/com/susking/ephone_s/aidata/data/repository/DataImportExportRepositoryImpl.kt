package com.susking.ephone_s.aidata.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.AlipayDatabase
import com.susking.ephone_s.aidata.data.local.entity.AiResponseVersionEntity
import com.susking.ephone_s.aidata.data.local.entity.AlipayBillEntity
import com.susking.ephone_s.aidata.data.local.entity.AlipayWalletEntity
import com.susking.ephone_s.aidata.data.local.entity.ContactSemanticStateEntity
import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import com.susking.ephone_s.aidata.data.local.entity.toEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictItem
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictResolution
import com.susking.ephone_s.aidata.domain.model.import_export.ExportData
import com.susking.ephone_s.aidata.domain.model.import_export.ImportResult
import com.susking.ephone_s.aidata.domain.repository.DataImportExportRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.util.ImageFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 数据导入导出 Repository 实现类
 * 负责处理所有数据的导入和导出操作
 */
class DataImportExportRepositoryImpl(
    private val context: Context,
    private val database: AiDataDatabase,
    private val alipayDatabase: AlipayDatabase,
    private val personProfileRepository: PersonProfileRepository
) : DataImportExportRepository {

    private val chatMessageDao = database.chatMessageDao()
    private val longTermMemoryDao = database.longTermMemoryDao()
    private val heartbeatDao = database.heartbeatDao()
    private val jottingDao = database.jottingDao()
    private val favoriteMessageDao = database.favoriteMessageDao()
    private val contactSemanticStateDao = database.contactSemanticStateDao()
    private val feedDao = database.feedDao()
    private val alipayDao = alipayDatabase.alipayDao()
    private val appointmentDao = database.appointmentDao()
    private val stickerDao = database.stickerDao()
    private val videoCallHistoryDao = database.videoCallHistoryDao()
    private val scheduleDao = database.scheduleDao()
    
    private val gson = Gson()
    
    private companion object {
        private const val TAG = "DataImportExportRepo"
        private const val FRIEND_GROUPS_PREFS_NAME = "qq_groups"
        private const val KEY_GROUP_ORDER = "group_order"
        private const val IMPORTED_ACTIVE_CONTEXT_MAX_LINES: Int = 6
        private const val IMPORTED_HISTORICAL_ANCHOR_MAX_LINES: Int = 18
        private const val IMPORTED_RESOLVED_EVENT_MAX_LINES: Int = 12
        private const val IMPORTED_LIFECYCLE_NOTE_MAX_LINES: Int = 8
        private const val IMPORTED_KEYWORD_MAX_COUNT: Int = 20
        private const val IMPORTED_SEMANTIC_LINE_MIN_LENGTH: Int = 6
        private const val IMPORTED_SEMANTIC_LINE_MAX_LENGTH: Int = 96
        private const val IMPORTED_KEYWORD_MIN_LENGTH: Int = 2
        private const val IMPORTED_KEYWORD_MAX_LENGTH: Int = 12
        private val IMPORTED_SEMANTIC_FIELD_PREFIX_REGEX: Regex = Regex("^(当前互动语义|历史召回锚点|已结束事件线索|语义关键词|生命周期说明|activeSemanticContext|historicalRecallAnchors|resolvedEventAnchors|semanticKeywords|lifecycleNotes)[：:]")
        private val IMPORTED_SEMANTIC_WHITESPACE_REGEX: Regex = Regex("\\s+")
        private val IMPORTED_COMPARABLE_REMOVE_REGEX: Regex = Regex("[\\s。！？!?，,；;：:\\-—_（）()【】\\[\\]《》<>\"'“”‘’]")
        private val IMPORTED_KEYWORD_SENTENCE_MARKERS: List<String> = listOf("。", "！", "？", "需要", "正在", "已经", "用户", "角色")
        private val IMPORTED_NOISY_KEYWORD_PARTS: List<String> = listOf("情绪", "状态", "氛围", "安抚", "委屈", "难过", "开心", "生气", "正在", "当前", "临时", "本轮")
    }

    override suspend fun importFullChatHistory(
        contact: PersonProfile,
        messages: List<ChatMessage>,
        memories: List<LongTermMemory>,
        jottings: List<JottingEntity>,
        heartbeats: List<com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity>
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            // 1. 清理与该联系人相关的旧数据库数据；旧原子事件只读保留，不随导入覆盖清除。
            chatMessageDao.deleteMessagesForContact(contact.id)
            jottingDao.deleteJottingsForContact(contact.id)
            heartbeatDao.deleteHeartbeatsForContact(contact.id)

            // 2. 预处理消息中的图片并插入新的数据库数据
            val processedMessages = messages.map { message ->
                message.imageUrl?.let { imageUrl ->
                    if (imageUrl.startsWith("data:image")) {
                        val imagePath = ImageFileHelper.saveImageFromBase64(context, imageUrl)
                        if (imagePath != null) {
                            Log.d(TAG, "Import: Converted Base64 image to file at $imagePath for message ${message.id}")
                            return@map message.copy(imageUrl = imagePath)
                        } else {
                            Log.e(TAG, "Import: Failed to save Base64 image for message ${message.id}")
                            return@map message.copy(imageUrl = null)
                        }
                    }
                }
                message
            }

            processedMessages.forEach { message ->
                val versions = message.aiResponseVersions.map {
                    AiResponseVersionEntity(chatMessageId = message.id, versionContent = it)
                }
                chatMessageDao.insertMessageWithVersions(message.toEntity(), versions)
            }
            
            if (memories.isNotEmpty()) {
                longTermMemoryDao.insertAll(memories)
            }
            
            if (jottings.isNotEmpty()) {
                jottingDao.insertAll(jottings)
            }
            
            if (heartbeats.isNotEmpty()) {
                heartbeatDao.insertAll(heartbeats)
            }
        }

        // 3. 更新 SharedPreferences 中的联系人列表
        val existingContacts = personProfileRepository.getPersonProfiles()
        val updatedContacts = existingContacts.filterNot { it.id == contact.id } + contact
        personProfileRepository.savePersonProfiles(updatedContacts)
    }

    override suspend fun importAllData(data: ExportData) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // 1. 清空所有相关表
                chatMessageDao.clearAll()
                // 旧原子事件只读保留，全量导入不再清空长期记忆表；同ID导入记录会通过替换插入覆盖。
                heartbeatDao.clearAll()
                jottingDao.clearAll()
                favoriteMessageDao.clearAll()
                contactSemanticStateDao.clearAll()
                feedDao.clearAll()
                alipayDao.clearAllTransactions()
                alipayDao.clearWallet()
                appointmentDao.clearAll()
                videoCallHistoryDao.deleteAllVideoCallHistory()
                scheduleDao.clearCourseRules()
                scheduleDao.clearAdjustments()
                scheduleDao.clearAssignments()
                scheduleDao.clearExams()
                scheduleDao.clearCampusEvents()
                scheduleDao.clearAiPolicies()
                scheduleDao.clearSectionTemplates()
                scheduleDao.clearImportDrafts()
                scheduleDao.clearReminderRecords()
                scheduleDao.clearReminderRules()
                scheduleDao.clearCareCandidates()
                scheduleDao.clearWidgetStates()
                scheduleDao.clearSemesters()
                scheduleDao.clearCourses()

                // 清空背包/通用记忆/定时问候
                database.backpackItemDao().deleteItem(0) // 删除占位,实际需要 clearAll 但该 DAO 没提供,用批量删除替代
                database.generalMemoryDao().getAllMemoriesSuspend().forEach { database.generalMemoryDao().delete(it) }
                database.scheduledGreetingDao().getAllGreetingsList().forEach { database.scheduledGreetingDao().deleteGreeting(it) }

                // 清空提示词储存器（酒馆「锦囊」），覆盖导入
                database.promptStorageDao().clearAllSentences()
                database.promptStorageDao().clearAllWords()

                // 清空AI记忆系统(全量导入覆盖)
                database.memoryEmbeddingDao().deleteAllEmbeddings()
                // 其他记忆表没有 clearAll,用 REPLACE 策略自然覆盖

                // 只有当导入数据包含表情时才清空表情表
                if (!data.stickers.isNullOrEmpty() || !data.stickerCategories.isNullOrEmpty()) {
                    stickerDao.clearAllStickers()
                    stickerDao.clearAllCategories()
                }

                // 2. 插入新数据
                for (message in data.chatMessages ?: emptyList()) {
                    val versions = message.aiResponseVersions.map {
                        AiResponseVersionEntity(chatMessageId = message.id, versionContent = it)
                    }
                    chatMessageDao.insertMessageWithVersions(message.toEntity(), versions)
                }
                
                longTermMemoryDao.insertAll(data.longTermMemories ?: emptyList())
                heartbeatDao.insertAll(data.heartbeats ?: emptyList())
                jottingDao.insertAll(data.jottings ?: emptyList())
                contactSemanticStateDao.insertAll(normalizeImportedSemanticStates(data.semanticStates ?: emptyList()))
                favoriteMessageDao.insertFavorites(data.favoriteMessages ?: emptyList())
                feedDao.insertAll(data.feeds ?: emptyList())
                data.videoCallHistory?.let { videoCallHistoryDao.insertAll(it) }
                data.wallet?.let { walletInfo ->
                    alipayDao.insertWallet(
                        AlipayWalletEntity(
                            userId = walletInfo.userId,
                            balance = walletInfo.balance
                        )
                    )
                }
                data.transactions?.let { transactions ->
                    val entities = transactions.map { record ->
                        AlipayBillEntity(
                            id = record.id,
                            timestamp = record.timestamp,
                            amount = record.amount,
                            type = record.type,
                            description = record.description,
                            relatedContactId = record.relatedContactId
                        )
                    }
                    alipayDao.insertAllTransactions(entities)
                }
                data.appointments?.let { appointmentDao.insertAll(it) }
                data.scheduleCourses?.let { scheduleDao.upsertCourses(it) }
                data.scheduleCourseRules?.let { scheduleDao.upsertCourseRules(it) }
                data.scheduleAdjustments?.let { scheduleDao.upsertAdjustments(it) }
                data.scheduleAssignments?.let { scheduleDao.upsertAssignments(it) }
                data.scheduleExams?.let { scheduleDao.upsertExams(it) }
                data.campusEvents?.let { scheduleDao.upsertCampusEvents(it) }
                data.scheduleAiPolicy?.let { scheduleDao.upsertAiPolicy(it) }
                data.scheduleSemesters?.let { scheduleDao.upsertSemesters(it) }
                data.scheduleSectionTemplates?.let { scheduleDao.upsertSectionTemplates(it) }
                data.scheduleImportDrafts?.let { scheduleDao.upsertImportDrafts(it) }
                data.scheduleReminderRules?.let { scheduleDao.upsertReminderRules(it) }
                data.scheduleReminderRecords?.let { scheduleDao.upsertReminderRecords(it) }
                data.scheduleCareCandidates?.let { scheduleDao.upsertCareCandidates(it) }
                data.scheduleWidgetState?.let { scheduleDao.upsertWidgetState(it) }

                // 插入背包/通用记忆/定时问候
                data.backpackItems?.let { if (it.isNotEmpty()) database.backpackItemDao().insertItems(it) }
                data.generalMemories?.let { if (it.isNotEmpty()) database.generalMemoryDao().insertAll(it) }
                data.scheduledGreetings?.let { if (it.isNotEmpty()) database.scheduledGreetingDao().insertGreetings(it) }

                // 插入健康数据（每日汇总，主键 date 冲突即覆盖）
                data.healthDailyRecords?.let { if (it.isNotEmpty()) database.healthDailyRecordDao().upsertAll(it) }

                // 插入提示词储存器（酒馆「锦囊」）
                data.promptSentences?.let { if (it.isNotEmpty()) database.promptStorageDao().insertSentences(it) }
                data.promptWords?.let { if (it.isNotEmpty()) database.promptStorageDao().insertWords(it) }

                // 插入AI记忆系统
                data.memoryEmbeddings?.let { if (it.isNotEmpty()) database.memoryEmbeddingDao().insertAll(it) }
                data.memorySummaries?.let { if (it.isNotEmpty()) database.memorySummaryDao().insertAll(it) }
                data.memoryEvents?.let { if (it.isNotEmpty()) database.memoryEventDao().insertAll(it) }
                data.memoryGraphNodes?.let { if (it.isNotEmpty()) database.memoryGraphDao().insertNodes(it) }
                data.memoryGraphRelations?.let { if (it.isNotEmpty()) database.memoryGraphDao().insertRelations(it) }
                data.memoryEventEvidences?.let { if (it.isNotEmpty()) database.memoryEvidenceDao().insertEventEvidences(it) }
                data.memoryRelationEvidences?.let { if (it.isNotEmpty()) database.memoryEvidenceDao().insertRelationEvidences(it) }
                data.memoryRecallDebugRecords?.let { if (it.isNotEmpty()) database.memoryRecallDebugDao().insertRecords(it) }
                data.memoryRecallDebugEntries?.let { if (it.isNotEmpty()) database.memoryRecallDebugDao().insertEntries(it) }

                // 插入表情分类和表情(如果存在)
                if (!data.stickerCategories.isNullOrEmpty()) {
                    stickerDao.insertCategories(data.stickerCategories)
                }
                if (!data.stickers.isNullOrEmpty()) {
                    stickerDao.insertStickers(data.stickers)
                }
            }

            // 3. 更新 SharedPreferences 中的数据
            personProfileRepository.savePersonProfiles(data.contacts ?: emptyList())
            data.userProfile?.let { personProfileRepository.saveUserProfile(it) }

            // 4. 导入好友分组信息
            data.friendGroups?.let { groups ->
                saveFriendGroups(groups)
            }
        }
    }

    override suspend fun getFriendGroups(): List<String>? = withContext(Dispatchers.IO) {
        val groupPrefs = context.getSharedPreferences(FRIEND_GROUPS_PREFS_NAME, Context.MODE_PRIVATE)
        val json = groupPrefs.getString(KEY_GROUP_ORDER, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(json, type)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing friend groups", e)
                null
            }
        } else {
            null
        }
    }

    override suspend fun saveFriendGroups(groups: List<String>) {
        withContext(Dispatchers.IO) {
            val groupPrefs = context.getSharedPreferences(FRIEND_GROUPS_PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(groups)
            groupPrefs.edit().putString(KEY_GROUP_ORDER, json).apply()
        }
    }
    
    override suspend fun getAllContactIds(): List<String> {
        return personProfileRepository.getPersonProfiles().map { it.id }
    }
    
    override suspend fun importFullChatHistoryIncremental(
        contact: PersonProfile,
        messages: List<ChatMessage>,
        memories: List<LongTermMemory>,
        jottings: List<JottingEntity>,
        heartbeats: List<com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity>,
        mode: com.susking.ephone_s.aidata.domain.model.import_export.ImportMode
    ) = withContext(Dispatchers.IO) {
        // 1. 处理联系人级别数据(在事务外部)
        val existingContacts = personProfileRepository.getPersonProfiles()
        val existingContact = existingContacts.find { it.id == contact.id }
        
        val contactToSave = when {
            existingContact == null -> {
                // 新联系人,直接使用导入数据
                contact
            }
            mode == com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING -> {
                // 保留现有联系人,不更新
                existingContact
            }
            mode == com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_PREFER_IMPORT -> {
                // 使用导入的联系人数据
                contact
            }
            else -> contact // OVERWRITE模式不应该调用这个方法,但以防万一
        }
        
        database.withTransaction {
            
            // 2. 合并关联数据
            // 获取现有数据(使用first()收集Flow)
            val existingMessagesWithVersions = chatMessageDao.getMessagesForContactNonFlow(contact.id)
            val existingMemoriesList = longTermMemoryDao.getMemoriesForContact(contact.id).first()
            val existingJottingsList = jottingDao.getJottingsForContact(contact.id).first()
            val existingHeartbeatsList = heartbeatDao.getHeartbeatsForContact(contact.id).first()
            
            // 合并消息
            val mergedMessages = mergeDataList(
                existing = existingMessagesWithVersions.map { withVersions ->
                    val entity = withVersions.chatMessage
                    ChatMessage(
                        id = entity.id,
                        type = entity.type,
                        contactId = entity.contactId,
                        content = entity.content,
                        timestamp = entity.timestamp,
                        role = entity.role,
                        imageUrl = entity.imageUrl,
                        stickerUrl = entity.stickerUrl,
                        stickerName = entity.stickerName,
                        productInfo = entity.productInfo,
                        amount = entity.amount,
                        status = entity.status,
                        greeting = entity.greeting,
                        senderName = entity.senderName,
                        recipientName = entity.recipientName,
                        notes = entity.notes,
                        quotedMessage = entity.quotedMessage,
                        aiResponseVersions = withVersions.versions.map { it.versionContent },
                        displayedResponseIndex = entity.displayedResponseIndex,
                        aiTurnId = entity.aiTurnId,
                        isHidden = entity.isHidden,
                        actionId = entity.actionId
                    )
                },
                imported = messages,
                mode = mode,
                getId = { it.id }
            )
            
            // 合并长期记忆
            val mergedMemories = mergeDataList(
                existing = existingMemoriesList,
                imported = memories,
                mode = mode,
                getId = { it.timestamp }
            )
            
            // 合并随笔
            val mergedJottings = mergeDataList(
                existing = existingJottingsList,
                imported = jottings,
                mode = mode,
                getId = { it.timestamp }
            )
            
            // 合并心声
            val mergedHeartbeats = mergeDataList(
                existing = existingHeartbeatsList,
                imported = heartbeats,
                mode = mode,
                getId = { it.timestamp }
            )
            
            // 3. 清除该联系人的旧数据；旧原子事件只读保留，合并后的记忆使用替换插入覆盖同ID记录。
            chatMessageDao.deleteMessagesForContact(contact.id)
            jottingDao.deleteJottingsForContact(contact.id)
            heartbeatDao.deleteHeartbeatsForContact(contact.id)
            
            // 4. 插入合并后的数据
            // 预处理消息中的图片
            val processedMessages = mergedMessages.map { message ->
                message.imageUrl?.let { imageUrl ->
                    if (imageUrl.startsWith("data:image")) {
                        val imagePath = ImageFileHelper.saveImageFromBase64(context, imageUrl)
                        if (imagePath != null) {
                            Log.d(TAG, "Import: Converted Base64 image to file at $imagePath for message ${message.id}")
                            return@map message.copy(imageUrl = imagePath)
                        } else {
                            Log.e(TAG, "Import: Failed to save Base64 image for message ${message.id}")
                            return@map message.copy(imageUrl = null)
                        }
                    }
                }
                message
            }
            
            processedMessages.forEach { message ->
                val versions = message.aiResponseVersions.map {
                    AiResponseVersionEntity(chatMessageId = message.id, versionContent = it)
                }
                chatMessageDao.insertMessageWithVersions(message.toEntity(), versions)
            }
            
            if (mergedMemories.isNotEmpty()) {
                longTermMemoryDao.insertAll(mergedMemories)
            }
            
            if (mergedJottings.isNotEmpty()) {
                jottingDao.insertAll(mergedJottings)
            }
            
            if (mergedHeartbeats.isNotEmpty()) {
                heartbeatDao.insertAll(mergedHeartbeats)
            }
        }
        
        // 5. 更新联系人列表(在事务外部)
        val updatedContacts = existingContacts.filterNot { it.id == contact.id } + contactToSave
        personProfileRepository.savePersonProfiles(updatedContacts)
    }
    
    override suspend fun importAllDataIncremental(
        data: com.susking.ephone_s.aidata.domain.model.import_export.ExportData,
        mode: com.susking.ephone_s.aidata.domain.model.import_export.ImportMode
    ) {
        withContext(Dispatchers.IO) {
            // 1. 处理联系人级别数据(在事务外部)
            val existingContacts = personProfileRepository.getPersonProfiles()
            val importedContacts = data.contacts ?: emptyList()
            
            val mergedContacts = mergeDataList(
                existing = existingContacts,
                imported = importedContacts,
                mode = mode,
                getId = { it.id }
            )
            
            database.withTransaction {
                
                // 2. 合并所有关联数据
                // 获取现有数据(使用first()收集Flow)
                val existingMessagesWithVersions = chatMessageDao.getAllMessagesWithVersionsNonFlow()
                val existingMemoriesList = longTermMemoryDao.getAllMemories().first()
                val existingHeartbeatsList = heartbeatDao.getAllHeartbeats().first()
                val existingJottingsList = jottingDao.getAllJottings().first()
                val existingFavoritesList = favoriteMessageDao.getAllFavoritesNonFlow()
                val existingSemanticStatesList = contactSemanticStateDao.getAllSemanticStates().first()
                val existingFeedsList = feedDao.getAllFeeds().first()
                val existingVideoCallHistoryList = videoCallHistoryDao.getAllVideoCallHistorySuspend()
                val existingTransactionsEntities = alipayDao.getAllTransactionsSync()
                val existingTransactionsList = existingTransactionsEntities.map { entity ->
                    com.susking.ephone_s.aidata.domain.alipay.BillRecord(
                        id = entity.id,
                        timestamp = entity.timestamp,
                        amount = entity.amount,
                        type = entity.type,
                        description = entity.description,
                        relatedContactId = entity.relatedContactId
                    )
                }
                val existingAppointmentsList = appointmentDao.getAllAppointmentsSuspend()
                val existingScheduleCourses = scheduleDao.getAllCourses()
                val existingScheduleCourseRules = scheduleDao.getAllCourseRules()
                val existingScheduleAdjustments = scheduleDao.getAllAdjustments()
                val existingScheduleAssignments = scheduleDao.getAllAssignments()
                val existingScheduleExams = scheduleDao.getAllExams()
                val existingCampusEvents = scheduleDao.getAllCampusEvents()
                val existingScheduleSemesters = scheduleDao.getAllSemesters()
                val existingScheduleSectionTemplates = scheduleDao.getAllSectionTemplates()
                val existingScheduleImportDrafts = scheduleDao.getAllImportDrafts()
                val existingScheduleReminderRules = scheduleDao.getAllReminderRules()
                val existingScheduleReminderRecords = scheduleDao.getAllReminderRecords()
                val existingScheduleCareCandidates = scheduleDao.getAllCareCandidates()
                
                // 合并各类数据
                val mergedMessages = mergeDataList(
                    existing = existingMessagesWithVersions.map { withVersions ->
                        val entity = withVersions.chatMessage
                        ChatMessage(
                            id = entity.id,
                            type = entity.type,
                            contactId = entity.contactId,
                            content = entity.content,
                            timestamp = entity.timestamp,
                            role = entity.role,
                            imageUrl = entity.imageUrl,
                            stickerUrl = entity.stickerUrl,
                            stickerName = entity.stickerName,
                            productInfo = entity.productInfo,
                            amount = entity.amount,
                            status = entity.status,
                            greeting = entity.greeting,
                            senderName = entity.senderName,
                            recipientName = entity.recipientName,
                            notes = entity.notes,
                            quotedMessage = entity.quotedMessage,
                            aiResponseVersions = withVersions.versions.map { it.versionContent },
                            displayedResponseIndex = entity.displayedResponseIndex,
                            aiTurnId = entity.aiTurnId,
                            isHidden = entity.isHidden,
                            actionId = entity.actionId
                        )
                    },
                    imported = data.chatMessages ?: emptyList(),
                    mode = mode,
                    getId = { it.id }
                )
                
                val mergedMemories = mergeDataList(
                    existing = existingMemoriesList,
                    imported = data.longTermMemories ?: emptyList(),
                    mode = mode,
                    getId = { it.timestamp }
                )
                
                val mergedHeartbeats = mergeDataList(
                    existing = existingHeartbeatsList,
                    imported = data.heartbeats ?: emptyList(),
                    mode = mode,
                    getId = { it.timestamp }
                )
                
                val mergedJottings = mergeDataList(
                    existing = existingJottingsList,
                    imported = data.jottings ?: emptyList(),
                    mode = mode,
                    getId = { it.timestamp }
                )
                
                val mergedFavorites = mergeDataList(
                    existing = existingFavoritesList,
                    imported = data.favoriteMessages ?: emptyList(),
                    mode = mode,
                    getId = { it.messageId }
                )
                
                val importedSemanticStatesList: List<ContactSemanticStateEntity> = normalizeImportedSemanticStates(data.semanticStates ?: emptyList())
                val mergedSemanticStates = mergeDataList(
                    existing = existingSemanticStatesList,
                    imported = importedSemanticStatesList,
                    mode = mode,
                    getId = { semanticState: ContactSemanticStateEntity -> semanticState.contactId }
                )
                
                val mergedFeeds = mergeDataList(
                    existing = existingFeedsList,
                    imported = data.feeds ?: emptyList(),
                    mode = mode,
                    getId = { it.id }
                )
                
                val mergedVideoCallHistory = mergeDataList(
                    existing = existingVideoCallHistoryList,
                    imported = data.videoCallHistory ?: emptyList(),
                    mode = mode,
                    getId = { it.id.toString() }
                )
                
                val mergedTransactions = mergeDataList(
                    existing = existingTransactionsList,
                    imported = data.transactions ?: emptyList(),
                    mode = mode,
                    getId = { it.id }
                )
                
                data.appointments?.let { importedAppointments ->
                    val mergedAppointments = mergeDataList(
                        existing = existingAppointmentsList,
                        imported = importedAppointments,
                        mode = mode,
                        getId = { it.id }
                    )
                    
                    // 清空并插入合并后的预约数据
                    appointmentDao.clearAll()
                    appointmentDao.insertAll(mergedAppointments)
                }
                
                // 3. 清空数据库准备插入合并后的数据；旧原子事件只读保留，不清空长期记忆表。
                chatMessageDao.clearAll()
                heartbeatDao.clearAll()
                jottingDao.clearAll()
                favoriteMessageDao.clearAll()
                contactSemanticStateDao.clearAll()
                feedDao.clearAll()
                videoCallHistoryDao.deleteAllVideoCallHistory()
                alipayDao.clearAllTransactions()
                scheduleDao.clearCourseRules()
                scheduleDao.clearAdjustments()
                scheduleDao.clearAssignments()
                scheduleDao.clearExams()
                scheduleDao.clearCampusEvents()
                scheduleDao.clearAiPolicies()
                scheduleDao.clearSectionTemplates()
                scheduleDao.clearImportDrafts()
                scheduleDao.clearReminderRecords()
                scheduleDao.clearReminderRules()
                scheduleDao.clearCareCandidates()
                scheduleDao.clearWidgetStates()
                scheduleDao.clearSemesters()
                scheduleDao.clearCourses()
                
                // 4. 插入合并后的数据
                for (message in mergedMessages) {
                    val versions = message.aiResponseVersions.map {
                        AiResponseVersionEntity(chatMessageId = message.id, versionContent = it)
                    }
                    chatMessageDao.insertMessageWithVersions(message.toEntity(), versions)
                }
                
                longTermMemoryDao.insertAll(mergedMemories)
                heartbeatDao.insertAll(mergedHeartbeats)
                jottingDao.insertAll(mergedJottings)
                contactSemanticStateDao.insertAll(mergedSemanticStates)
                favoriteMessageDao.insertFavorites(mergedFavorites)
                feedDao.insertAll(mergedFeeds)
                
                // 插入视频通话历史记录
                if (mergedVideoCallHistory.isNotEmpty()) {
                    videoCallHistoryDao.insertAll(mergedVideoCallHistory)
                }
                
                val transactionEntities = mergedTransactions.map { record ->
                    AlipayBillEntity(
                        id = record.id,
                        timestamp = record.timestamp,
                        amount = record.amount,
                        type = record.type,
                        description = record.description,
                        relatedContactId = record.relatedContactId
                    )
                }
                alipayDao.insertAllTransactions(transactionEntities)
                insertMergedScheduleData(
                    data = data,
                    mode = mode,
                    existingCourses = existingScheduleCourses,
                    existingCourseRules = existingScheduleCourseRules,
                    existingAdjustments = existingScheduleAdjustments,
                    existingAssignments = existingScheduleAssignments,
                    existingExams = existingScheduleExams,
                    existingCampusEvents = existingCampusEvents,
                    existingSemesters = existingScheduleSemesters,
                    existingSectionTemplates = existingScheduleSectionTemplates,
                    existingImportDrafts = existingScheduleImportDrafts,
                    existingReminderRules = existingScheduleReminderRules,
                    existingReminderRecords = existingScheduleReminderRecords,
                    existingCareCandidates = existingScheduleCareCandidates
                )
                
                // 5. 处理钱包数据(特殊处理,因为只有一个钱包)
                data.wallet?.let { importedWallet ->
                    val existingWallets = alipayDao.getAllWalletsSync()
                    val existingWallet = existingWallets.firstOrNull()
                    if (existingWallet != null && mode == com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING) {
                        // 保留现有钱包
                        // 不需要操作,现有数据已在数据库中
                    } else {
                        // 使用导入的钱包数据
                        alipayDao.clearWallet()
                        alipayDao.insertWallet(
                            AlipayWalletEntity(
                                userId = importedWallet.userId,
                                balance = importedWallet.balance
                            )
                        )
                    }
                } ?: run {
                    // 如果导入数据没有钱包,根据模式决定是否保留现有钱包
                    if (mode != com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING) {
                        alipayDao.clearWallet()
                    }
                }
                
                // 6. 处理表情数据
                if (!data.stickers.isNullOrEmpty() || !data.stickerCategories.isNullOrEmpty()) {
                    val existingCategoriesList = stickerDao.getAllCategories().first()
                    val existingStickersList = stickerDao.getAllStickers().first()
                    
                    val mergedCategories = mergeDataList(
                        existing = existingCategoriesList,
                        imported = data.stickerCategories ?: emptyList(),
                        mode = mode,
                        getId = { it.id }
                    )
                    
                    val mergedStickers = mergeDataList(
                        existing = existingStickersList,
                        imported = data.stickers ?: emptyList(),
                        mode = mode,
                        getId = { it.id }
                    )
                    
                    stickerDao.clearAllStickers()
                    stickerDao.clearAllCategories()
                    
                    if (mergedCategories.isNotEmpty()) {
                        stickerDao.insertCategories(mergedCategories)
                    }
                    if (mergedStickers.isNotEmpty()) {
                        stickerDao.insertStickers(mergedStickers)
                    }
                }

                // 7. 处理背包/通用记忆/定时问候的增量导入
                data.backpackItems?.let { importedItems ->
                    val existingItems = database.backpackItemDao().getAllItemsList()
                    val mergedItems = mergeDataList(existingItems, importedItems, mode) { it.id }
                    if (mergedItems.isNotEmpty()) database.backpackItemDao().insertItems(mergedItems)
                }

                data.generalMemories?.let { importedMemories ->
                    val existingMemories = database.generalMemoryDao().getAllMemoriesSuspend()
                    val mergedMemories = mergeDataList(existingMemories, importedMemories, mode) { it.id }
                    if (mergedMemories.isNotEmpty()) database.generalMemoryDao().insertAll(mergedMemories)
                }

                data.scheduledGreetings?.let { importedGreetings ->
                    val existingGreetings = database.scheduledGreetingDao().getAllGreetingsList()
                    val mergedGreetings = mergeDataList(existingGreetings, importedGreetings, mode) { it.id }
                    if (mergedGreetings.isNotEmpty()) database.scheduledGreetingDao().insertGreetings(mergedGreetings)
                }

                // 健康数据增量导入：以 date 为键合并，按模式取舍冲突天
                data.healthDailyRecords?.let { importedHealth ->
                    val existingHealth = database.healthDailyRecordDao().getAllHealthDailyRecordsList()
                    val mergedHealth = mergeDataList(existingHealth, importedHealth, mode) { it.date }
                    if (mergedHealth.isNotEmpty()) database.healthDailyRecordDao().upsertAll(mergedHealth)
                }

                // 提示词储存器增量导入（酒馆「锦囊」）：以 id 为键合并
                data.promptSentences?.let { importedSentences ->
                    val existingSentences = database.promptStorageDao().getAllSentencesList()
                    val mergedSentences = mergeDataList(existingSentences, importedSentences, mode) { it.id }
                    if (mergedSentences.isNotEmpty()) database.promptStorageDao().insertSentences(mergedSentences)
                }
                data.promptWords?.let { importedWords ->
                    val existingWords = database.promptStorageDao().getAllWordsList()
                    val mergedWords = mergeDataList(existingWords, importedWords, mode) { it.id }
                    if (mergedWords.isNotEmpty()) database.promptStorageDao().insertWords(mergedWords)
                }

                // 8. 处理AI记忆系统的增量导入
                data.memoryEmbeddings?.let { importedEmbeddings ->
                    val existingEmbeddings = database.memoryEmbeddingDao().getAllEmbeddingsList()
                    val mergedEmbeddings = mergeDataList(existingEmbeddings, importedEmbeddings, mode) { it.memoryId }
                    if (mergedEmbeddings.isNotEmpty()) database.memoryEmbeddingDao().insertAll(mergedEmbeddings)
                }

                data.memorySummaries?.let { importedSummaries ->
                    val existingSummaries = database.memorySummaryDao().getAllSummaryList()
                    val mergedSummaries = mergeDataList(existingSummaries, importedSummaries, mode) { it.id }
                    if (mergedSummaries.isNotEmpty()) database.memorySummaryDao().insertAll(mergedSummaries)
                }

                data.memoryEvents?.let { importedEvents ->
                    val existingEvents = database.memoryEventDao().getAllEventList()
                    val mergedEvents = mergeDataList(existingEvents, importedEvents, mode) { it.id }
                    if (mergedEvents.isNotEmpty()) database.memoryEventDao().insertAll(mergedEvents)
                }

                data.memoryGraphNodes?.let { importedNodes ->
                    val existingNodes = database.memoryGraphDao().getAllNodesList()
                    val mergedNodes = mergeDataList(existingNodes, importedNodes, mode) { it.id }
                    if (mergedNodes.isNotEmpty()) database.memoryGraphDao().insertNodes(mergedNodes)
                }

                data.memoryGraphRelations?.let { importedRelations ->
                    val existingRelations = database.memoryGraphDao().getAllRelationsList()
                    val mergedRelations = mergeDataList(existingRelations, importedRelations, mode) { it.id }
                    if (mergedRelations.isNotEmpty()) database.memoryGraphDao().insertRelations(mergedRelations)
                }

                data.memoryEventEvidences?.let { importedEvidences ->
                    val existingEvidences = database.memoryEvidenceDao().getAllEventEvidencesList()
                    val mergedEvidences = mergeDataList(existingEvidences, importedEvidences, mode) { it.id }
                    if (mergedEvidences.isNotEmpty()) database.memoryEvidenceDao().insertEventEvidences(mergedEvidences)
                }

                data.memoryRelationEvidences?.let { importedEvidences ->
                    val existingEvidences = database.memoryEvidenceDao().getAllRelationEvidencesList()
                    val mergedEvidences = mergeDataList(existingEvidences, importedEvidences, mode) { it.id }
                    if (mergedEvidences.isNotEmpty()) database.memoryEvidenceDao().insertRelationEvidences(mergedEvidences)
                }

                data.memoryRecallDebugRecords?.let { importedRecords ->
                    val existingRecords = database.memoryRecallDebugDao().getAllRecordsList()
                    val mergedRecords = mergeDataList(existingRecords, importedRecords, mode) { it.id }
                    if (mergedRecords.isNotEmpty()) database.memoryRecallDebugDao().insertRecords(mergedRecords)
                }

                data.memoryRecallDebugEntries?.let { importedEntries ->
                    val existingEntries = database.memoryRecallDebugDao().getAllEntriesList()
                    val mergedEntries = mergeDataList(existingEntries, importedEntries, mode) { it.id }
                    if (mergedEntries.isNotEmpty()) database.memoryRecallDebugDao().insertEntries(mergedEntries)
                }
            }

            // 7. 更新SharedPreferences中的数据
            personProfileRepository.savePersonProfiles(mergedContacts)
            
            // 8. 处理用户资料
            data.userProfile?.let { importedUserProfile ->
                val existingUserProfile = personProfileRepository.getUserProfile()
                if (existingUserProfile != null && mode == com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING) {
                    // 保留现有用户资料
                    // 不需要操作
                } else {
                    // 使用导入的用户资料
                    personProfileRepository.saveUserProfile(importedUserProfile)
                }
            }
            
            // 9. 处理好友分组
            data.friendGroups?.let { importedGroups ->
                val existingGroups = getFriendGroups()
                if (existingGroups != null && mode == com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING) {
                    // 保留现有分组
                    // 不需要操作
                } else {
                    // 使用导入的分组
                    saveFriendGroups(importedGroups)
                }
            }
        }
    }
    
    private suspend fun insertMergedScheduleData(
        data: ExportData,
        mode: com.susking.ephone_s.aidata.domain.model.import_export.ImportMode,
        existingCourses: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseEntity>,
        existingCourseRules: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseRuleEntity>,
        existingAdjustments: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleAdjustmentEntity>,
        existingAssignments: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleAssignmentEntity>,
        existingExams: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleExamEntity>,
        existingCampusEvents: List<com.susking.ephone_s.aidata.data.local.entity.CampusEventEntity>,
        existingSemesters: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleSemesterEntity>,
        existingSectionTemplates: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleSectionTemplateEntity>,
        existingImportDrafts: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleImportDraftEntity>,
        existingReminderRules: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleReminderRuleEntity>,
        existingReminderRecords: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleReminderRecordEntity>,
        existingCareCandidates: List<com.susking.ephone_s.aidata.data.local.entity.ScheduleCareCandidateEntity>
    ): Unit {
        val mergedCourses = mergeDataList(existingCourses, data.scheduleCourses ?: emptyList(), mode) { course -> course.courseId }
        val mergedCourseRules = mergeDataList(existingCourseRules, data.scheduleCourseRules ?: emptyList(), mode) { rule -> rule.ruleId }
        val mergedAdjustments = mergeDataList(existingAdjustments, data.scheduleAdjustments ?: emptyList(), mode) { adjustment -> adjustment.adjustmentId }
        val mergedAssignments = mergeDataList(existingAssignments, data.scheduleAssignments ?: emptyList(), mode) { assignment -> assignment.assignmentId }
        val mergedExams = mergeDataList(existingExams, data.scheduleExams ?: emptyList(), mode) { exam -> exam.examId }
        val mergedCampusEvents = mergeDataList(existingCampusEvents, data.campusEvents ?: emptyList(), mode) { event -> event.eventId }
        val mergedSemesters = mergeDataList(existingSemesters, data.scheduleSemesters ?: emptyList(), mode) { semester -> semester.semesterId }
        val mergedSectionTemplates = mergeDataList(existingSectionTemplates, data.scheduleSectionTemplates ?: emptyList(), mode) { template -> template.sectionTemplateId }
        val mergedImportDrafts = mergeDataList(existingImportDrafts, data.scheduleImportDrafts ?: emptyList(), mode) { draft -> draft.draftId }
        val mergedReminderRules = mergeDataList(existingReminderRules, data.scheduleReminderRules ?: emptyList(), mode) { rule -> rule.reminderRuleId }
        val mergedReminderRecords = mergeDataList(existingReminderRecords, data.scheduleReminderRecords ?: emptyList(), mode) { record -> record.reminderRecordId }
        val mergedCareCandidates = mergeDataList(existingCareCandidates, data.scheduleCareCandidates ?: emptyList(), mode) { candidate -> candidate.careCandidateId }
        scheduleDao.upsertCourses(mergedCourses)
        scheduleDao.upsertCourseRules(mergedCourseRules)
        scheduleDao.upsertAdjustments(mergedAdjustments)
        scheduleDao.upsertAssignments(mergedAssignments)
        scheduleDao.upsertExams(mergedExams)
        scheduleDao.upsertCampusEvents(mergedCampusEvents)
        scheduleDao.upsertSemesters(mergedSemesters)
        scheduleDao.upsertSectionTemplates(mergedSectionTemplates)
        scheduleDao.upsertImportDrafts(mergedImportDrafts)
        scheduleDao.upsertReminderRules(mergedReminderRules)
        scheduleDao.upsertReminderRecords(mergedReminderRecords)
        scheduleDao.upsertCareCandidates(mergedCareCandidates)
        data.scheduleAiPolicy?.let { policy ->
            if (mode != com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING || scheduleDao.getAiPolicy() == null) {
                scheduleDao.upsertAiPolicy(policy)
            }
        }
        data.scheduleWidgetState?.let { state ->
            if (mode != com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING || scheduleDao.getWidgetState() == null) {
                scheduleDao.upsertWidgetState(state)
            }
        }
    }

    /**
     * 智能合并数据列表
     * @param existing 现有数据列表
     * @param imported 导入数据列表
     * @param mode 导入模式
     * @param getId 获取数据ID的函数
     * @return 合并后的数据列表
     */
    private fun <T, ID> mergeDataList(
        existing: List<T>,
        imported: List<T>,
        mode: com.susking.ephone_s.aidata.domain.model.import_export.ImportMode,
        getId: (T) -> ID
    ): List<T> {
        val existingMap = existing.associateBy { getId(it) }
        val importedMap = imported.associateBy { getId(it) }
        val result = mutableListOf<T>()
        
        // 处理现有数据
        for ((id, existingItem) in existingMap) {
            if (importedMap.containsKey(id)) {
                // ID冲突,根据mode决定保留哪个
                when (mode) {
                    com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING -> {
                        result.add(existingItem) // 保留现有
                    }
                    com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_PREFER_IMPORT -> {
                        result.add(importedMap[id]!!) // 使用导入
                    }
                    else -> {
                        result.add(importedMap[id]!!) // OVERWRITE模式(理论上不会到这里)
                    }
                }
            } else {
                // 无冲突,保留现有项
                result.add(existingItem)
            }
        }
        
        // 添加新数据(导入中有但现有中没有的)
        for ((id, importedItem) in importedMap) {
            if (!existingMap.containsKey(id)) {
                result.add(importedItem)
            }
        }
        
        return result
    }
    
    override suspend fun importAllDataInteractive(
        data: ExportData,
        onConflict: suspend (ConflictItem) -> ConflictResolution
    ): ImportResult = withContext(Dispatchers.IO) {
        val result = ImportResult()
        
        // 1. 处理联系人资料(在事务外部)
        val existingContacts = personProfileRepository.getPersonProfiles()
        val importedContacts = data.contacts ?: emptyList()
        val contactNameMap = mutableMapOf<String, String>() // contactId -> realName映射
        
        val finalContacts = mutableListOf<PersonProfile>()
        
        for (importedContact in importedContacts) {
            val existingContact = existingContacts.find { it.id == importedContact.id }
            
            if (existingContact == null) {
                // 新联系人
                finalContacts.add(importedContact)
                contactNameMap[importedContact.id] = importedContact.realName
                result.personProfileStats.recordNew()
            } else {
                // 检查内容是否完全相同
                if (existingContact == importedContact) {
                    // 完全相同,跳过
                    finalContacts.add(existingContact)
                    contactNameMap[existingContact.id] = existingContact.realName
                    result.personProfileStats.recordIdenticalSkipped()
                } else {
                    // 有冲突,逐字段对比
                    val fieldStats = ImportResult.PersonProfileFieldConflictStats(
                        contactId = existingContact.id,
                        contactRealName = existingContact.realName
                    )
                    
                    val resolvedContact = PersonProfileFieldComparator.compareAndResolve(
                        existing = existingContact,
                        imported = importedContact,
                        onConflict = { conflictItem ->
                            val resolution = withContext(Dispatchers.Main) {
                                onConflict(conflictItem)
                            }
                            fieldStats.recordFieldConflict(resolution)
                            resolution
                        }
                    )
                    
                    finalContacts.add(resolvedContact)
                    contactNameMap[resolvedContact.id] = resolvedContact.realName
                    result.personProfileFieldConflicts[resolvedContact.id] = fieldStats
                }
            }
        }
        
        // 添加现有中有但导入中没有的联系人
        for (existingContact in existingContacts) {
            if (!importedContacts.any { it.id == existingContact.id }) {
                finalContacts.add(existingContact)
                contactNameMap[existingContact.id] = existingContact.realName
            }
        }
        
        database.withTransaction {
            // 2. 处理聊天消息
            val existingMessagesWithVersions = chatMessageDao.getAllMessagesWithVersionsNonFlow()
            val importedMessages = data.chatMessages ?: emptyList()
            val finalMessages = mutableListOf<ChatMessage>()
            
            for (importedMsg in importedMessages) {
                val existingMsgWithVersions = existingMessagesWithVersions.find {
                    it.chatMessage.id == importedMsg.id
                }
                
                if (existingMsgWithVersions == null) {
                    // 新消息
                    finalMessages.add(importedMsg)
                    result.chatMessageStats.recordNew()
                } else {
                    val existingMsg = existingMsgWithVersions.chatMessage
                    val existingChatMessage = ChatMessage(
                        id = existingMsg.id,
                        type = existingMsg.type,
                        contactId = existingMsg.contactId,
                        content = existingMsg.content,
                        timestamp = existingMsg.timestamp,
                        role = existingMsg.role,
                        imageUrl = existingMsg.imageUrl,
                        stickerUrl = existingMsg.stickerUrl,
                        stickerName = existingMsg.stickerName,
                        productInfo = existingMsg.productInfo,
                        amount = existingMsg.amount,
                        status = existingMsg.status,
                        greeting = existingMsg.greeting,
                        senderName = existingMsg.senderName,
                        recipientName = existingMsg.recipientName,
                        notes = existingMsg.notes,
                        quotedMessage = existingMsg.quotedMessage,
                        aiResponseVersions = existingMsgWithVersions.versions.map { it.versionContent },
                        displayedResponseIndex = existingMsg.displayedResponseIndex,
                        aiTurnId = existingMsg.aiTurnId,
                        isHidden = existingMsg.isHidden,
                        actionId = existingMsg.actionId
                    )
                    
                    // 检查是否完全相同
                    if (existingChatMessage == importedMsg) {
                        finalMessages.add(existingChatMessage)
                        result.chatMessageStats.recordIdenticalSkipped()
                    } else {
                        // 有冲突,询问用户
                        val conflictItem = ConflictItem.ChatMessageConflict(
                            messageId = importedMsg.id,
                            contactId = importedMsg.contactId,
                            contactRealName = contactNameMap[importedMsg.contactId] ?: "未知",
                            timestamp = importedMsg.timestamp,
                            existingMessage = chatMessageToMap(existingChatMessage),
                            importMessage = chatMessageToMap(importedMsg)
                        )
                        
                        val resolution = withContext(Dispatchers.Main) {
                            onConflict(conflictItem)
                        }
                        when (resolution) {
                            ConflictResolution.KEEP_EXISTING -> {
                                finalMessages.add(existingChatMessage)
                                result.chatMessageStats.recordConflictKeepExisting()
                            }
                            ConflictResolution.USE_IMPORT -> {
                                finalMessages.add(importedMsg)
                                result.chatMessageStats.recordConflictUseImport()
                            }
                        }
                    }
                }
            }
            
            // 添加现有中有但导入中没有的消息
            for (existingMsgWithVersions in existingMessagesWithVersions) {
                val existingMsg = existingMsgWithVersions.chatMessage
                if (!importedMessages.any { it.id == existingMsg.id }) {
                    val existingChatMessage = ChatMessage(
                        id = existingMsg.id,
                        type = existingMsg.type,
                        contactId = existingMsg.contactId,
                        content = existingMsg.content,
                        timestamp = existingMsg.timestamp,
                        role = existingMsg.role,
                        imageUrl = existingMsg.imageUrl,
                        stickerUrl = existingMsg.stickerUrl,
                        stickerName = existingMsg.stickerName,
                        productInfo = existingMsg.productInfo,
                        amount = existingMsg.amount,
                        status = existingMsg.status,
                        greeting = existingMsg.greeting,
                        senderName = existingMsg.senderName,
                        recipientName = existingMsg.recipientName,
                        notes = existingMsg.notes,
                        quotedMessage = existingMsg.quotedMessage,
                        aiResponseVersions = existingMsgWithVersions.versions.map { it.versionContent },
                        displayedResponseIndex = existingMsg.displayedResponseIndex,
                        aiTurnId = existingMsg.aiTurnId,
                        isHidden = existingMsg.isHidden,
                        actionId = existingMsg.actionId
                    )
                    finalMessages.add(existingChatMessage)
                }
            }
            
            // 3. 处理长期记忆
            val existingMemories = longTermMemoryDao.getAllMemories().first()
            val importedMemories = data.longTermMemories ?: emptyList()
            val finalMemories = processDataWithConflicts(
                existing = existingMemories,
                imported = importedMemories,
                getId = { it.id },
                stats = result.longTermMemoryStats,
                createConflict = { existing, imported ->
                    ConflictItem.LongTermMemoryConflict(
                        memoryId = imported.id,
                        contactId = imported.contactId,
                        contactRealName = contactNameMap[imported.contactId] ?: "未知",
                        timestamp = imported.timestamp,
                        existingMemory = longTermMemoryToMap(existing),
                        importMemory = longTermMemoryToMap(imported)
                    )
                },
                onConflict = onConflict
            )
            
            // 4. 处理随笔
            val existingJottings = jottingDao.getAllJottings().first()
            val importedJottings = data.jottings ?: emptyList()
            val finalJottings = processDataWithConflicts(
                existing = existingJottings,
                imported = importedJottings,
                getId = { it.id.toString() },
                stats = result.jottingStats,
                createConflict = { existing, imported ->
                    ConflictItem.JottingConflict(
                        jottingId = imported.id.toString(),
                        contactId = imported.contactId,
                        contactRealName = contactNameMap[imported.contactId] ?: "未知",
                        timestamp = imported.timestamp,
                        existingJotting = jottingToMap(existing),
                        importJotting = jottingToMap(imported)
                    )
                },
                onConflict = onConflict
            )
            
            // 5. 处理心声
            val existingHeartbeats = heartbeatDao.getAllHeartbeats().first()
            val importedHeartbeats = data.heartbeats ?: emptyList()
            val finalHeartbeats = processDataWithConflicts(
                existing = existingHeartbeats,
                imported = importedHeartbeats,
                getId = { it.id.toString() },
                stats = result.heartbeatStats,
                createConflict = { existing, imported ->
                    ConflictItem.HeartbeatConflict(
                        heartbeatId = imported.id.toString(),
                        contactId = imported.contactId,
                        contactRealName = contactNameMap[imported.contactId] ?: "未知",
                        timestamp = imported.timestamp,
                        existingHeartbeat = heartbeatToMap(existing),
                        importHeartbeat = heartbeatToMap(imported)
                    )
                },
                onConflict = onConflict
            )
            
            // 6. 处理语义状态。
            // 交互式导入暂不弹出新增冲突类型：已有联系人状态保留现有值，新联系人状态直接导入。
            val existingSemanticStates = contactSemanticStateDao.getAllSemanticStates().first()
            val importedSemanticStates = normalizeImportedSemanticStates(data.semanticStates ?: emptyList())
            val finalSemanticStates = existingSemanticStates + importedSemanticStates.filter { importedSemanticState: ContactSemanticStateEntity ->
                existingSemanticStates.none { existingSemanticState: ContactSemanticStateEntity ->
                    existingSemanticState.contactId == importedSemanticState.contactId
                }
            }
            
            // 清空数据库并插入最终数据；旧原子事件只读保留，不清空长期记忆表。
            chatMessageDao.clearAll()
            heartbeatDao.clearAll()
            jottingDao.clearAll()
            contactSemanticStateDao.clearAll()
            scheduleDao.clearCourseRules()
            scheduleDao.clearAdjustments()
            scheduleDao.clearAssignments()
            scheduleDao.clearExams()
            scheduleDao.clearCampusEvents()
            scheduleDao.clearAiPolicies()
            scheduleDao.clearSectionTemplates()
            scheduleDao.clearImportDrafts()
            scheduleDao.clearReminderRecords()
            scheduleDao.clearReminderRules()
            scheduleDao.clearCareCandidates()
            scheduleDao.clearWidgetStates()
            scheduleDao.clearSemesters()
            scheduleDao.clearCourses()
            
            // 插入最终数据
            for (message in finalMessages) {
                val versions = message.aiResponseVersions.map {
                    AiResponseVersionEntity(chatMessageId = message.id, versionContent = it)
                }
                chatMessageDao.insertMessageWithVersions(message.toEntity(), versions)
            }
            
            if (finalMemories.isNotEmpty()) {
                longTermMemoryDao.insertAll(finalMemories)
            }
            
            if (finalHeartbeats.isNotEmpty()) {
                heartbeatDao.insertAll(finalHeartbeats)
            }
            
            if (finalJottings.isNotEmpty()) {
                jottingDao.insertAll(finalJottings)
            }
            
            if (finalSemanticStates.isNotEmpty()) {
                contactSemanticStateDao.insertAll(finalSemanticStates)
            }
            scheduleDao.upsertCourses(data.scheduleCourses ?: emptyList())
            scheduleDao.upsertCourseRules(data.scheduleCourseRules ?: emptyList())
            scheduleDao.upsertAdjustments(data.scheduleAdjustments ?: emptyList())
            scheduleDao.upsertAssignments(data.scheduleAssignments ?: emptyList())
            scheduleDao.upsertExams(data.scheduleExams ?: emptyList())
            scheduleDao.upsertCampusEvents(data.campusEvents ?: emptyList())
            scheduleDao.upsertSemesters(data.scheduleSemesters ?: emptyList())
            scheduleDao.upsertSectionTemplates(data.scheduleSectionTemplates ?: emptyList())
            scheduleDao.upsertImportDrafts(data.scheduleImportDrafts ?: emptyList())
            scheduleDao.upsertReminderRules(data.scheduleReminderRules ?: emptyList())
            scheduleDao.upsertReminderRecords(data.scheduleReminderRecords ?: emptyList())
            scheduleDao.upsertCareCandidates(data.scheduleCareCandidates ?: emptyList())
            data.scheduleAiPolicy?.let { policy -> scheduleDao.upsertAiPolicy(policy) }
            data.scheduleWidgetState?.let { state -> scheduleDao.upsertWidgetState(state) }

            // 背包/定时问候/健康等简单列表无需逐条冲突询问，直接按「导入优先」合并。
            // （健康数据按日期去重，定时问候按 ID 去重，导入优先即后来居上。）
            val mode = com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_PREFER_IMPORT
            data.backpackItems?.let { importedItems ->
                val existingItems = database.backpackItemDao().getAllItemsList()
                val mergedItems = mergeDataList(existingItems, importedItems, mode) { it.id }
                if (mergedItems.isNotEmpty()) database.backpackItemDao().insertItems(mergedItems)
            }
            data.generalMemories?.let { importedMems ->
                val existingMems = database.generalMemoryDao().getAllMemoriesSuspend()
                val mergedMems = mergeDataList(existingMems, importedMems, mode) { it.id }
                if (mergedMems.isNotEmpty()) database.generalMemoryDao().insertAll(mergedMems)
            }
            data.scheduledGreetings?.let { importedGreetings ->
                val existingGreetings = database.scheduledGreetingDao().getAllGreetingsList()
                val mergedGreetings = mergeDataList(existingGreetings, importedGreetings, mode) { it.id }
                if (mergedGreetings.isNotEmpty()) database.scheduledGreetingDao().insertGreetings(mergedGreetings)
            }
            data.healthDailyRecords?.let { importedHealth ->
                val existingHealth = database.healthDailyRecordDao().getAllHealthDailyRecordsList()
                val mergedHealth = mergeDataList(existingHealth, importedHealth, mode) { it.date }
                if (mergedHealth.isNotEmpty()) database.healthDailyRecordDao().upsertAll(mergedHealth)
            }
        }
        
        // 更新联系人列表(在事务外部)
        personProfileRepository.savePersonProfiles(finalContacts)
        
        return@withContext result
    }
    
    /**
     * 兼容旧备份中的四字段语义状态，并统一写入新分层语义账本字段。
     */
    private fun normalizeImportedSemanticStates(semanticStates: List<ContactSemanticStateEntity>): List<ContactSemanticStateEntity> {
        return semanticStates.map { semanticState: ContactSemanticStateEntity ->
            val legacyUserCurrentState: String = semanticState.userCurrentState.orEmpty()
            val legacyCharCurrentState: String = semanticState.charCurrentState.orEmpty()
            val migratedActiveContext: String = mergeSemanticLines(
                semanticState.activeSemanticContext,
                legacyUserCurrentState.takeIf { content: String -> content.isNotBlank() }?.let { content: String -> "用户当前状态：$content" },
                legacyCharCurrentState.takeIf { content: String -> content.isNotBlank() }?.let { content: String -> "角色当前状态：$content" }
            )

            semanticState.copy(
                activeSemanticContext = normalizeImportedSemanticLines(migratedActiveContext, IMPORTED_ACTIVE_CONTEXT_MAX_LINES),
                historicalRecallAnchors = normalizeImportedSemanticLines(
                    mergeSemanticLines(semanticState.historicalRecallAnchors, semanticState.recentSemanticSummary),
                    IMPORTED_HISTORICAL_ANCHOR_MAX_LINES
                ),
                resolvedEventAnchors = normalizeImportedSemanticLines(semanticState.resolvedEventAnchors.orEmpty(), IMPORTED_RESOLVED_EVENT_MAX_LINES),
                semanticKeywords = normalizeImportedSemanticKeywords(semanticState.semanticKeywords.orEmpty()),
                lifecycleNotes = normalizeImportedSemanticLines(
                    mergeSemanticLines(semanticState.lifecycleNotes, semanticState.stateValidityNote),
                    IMPORTED_LIFECYCLE_NOTE_MAX_LINES
                ),
                // 撤销态是设备本地、本会话内的临时快照，跨备份导入毫无意义且可能误退，导入时一律清空。
                previousStateJson = null,
                lastUpdateAiTurnId = null,
                schemaVersion = ContactSemanticStateEntity.CURRENT_SCHEMA_VERSION
            )
        }
    }

    private fun mergeSemanticLines(vararg contents: String?): String {
        return contents.flatMap { content: String? -> content.orEmpty().lines() }
            .map { line: String -> line.trim() }
            .filter { line: String -> line.isNotBlank() }
            .distinct()
            .joinToString(separator = "\n")
    }

    private fun normalizeImportedSemanticLines(content: String, maxLines: Int): String {
        return content.lines()
            .flatMap { line: String -> line.split('；', ';') }
            .map { line: String -> normalizeImportedSemanticLine(line) }
            .filter { line: String -> line.length in IMPORTED_SEMANTIC_LINE_MIN_LENGTH..IMPORTED_SEMANTIC_LINE_MAX_LENGTH }
            .distinctBy { line: String -> buildImportedComparableText(line) }
            .takeLast(maxLines)
            .joinToString(separator = "\n")
    }

    private fun normalizeImportedSemanticLine(line: String): String {
        return line
            .trim()
            .trimStart('-', '*', '•', '·', ' ', '\t')
            .replace(IMPORTED_SEMANTIC_FIELD_PREFIX_REGEX, "")
            .replace(IMPORTED_SEMANTIC_WHITESPACE_REGEX, " ")
            .trim(' ', '。', '，', ',', '；', ';')
    }

    private fun normalizeImportedSemanticKeywords(content: String): String {
        return content.split('、', ',', '，', ';', '；', '\n', '/', '／')
            .map { keyword: String -> normalizeImportedSemanticKeyword(keyword) }
            .filter { keyword: String -> isValidImportedSemanticKeyword(keyword) }
            .distinctBy { keyword: String -> buildImportedComparableText(keyword) }
            .takeLast(IMPORTED_KEYWORD_MAX_COUNT)
            .joinToString(separator = "、")
    }

    private fun normalizeImportedSemanticKeyword(keyword: String): String {
        return keyword
            .trim()
            .trimStart('-', '*', '•', '·', ' ', '\t')
            .replace(IMPORTED_SEMANTIC_FIELD_PREFIX_REGEX, "")
            .replace(IMPORTED_SEMANTIC_WHITESPACE_REGEX, "")
            .trim(' ', '。', '，', ',', '；', ';', '：', ':')
    }

    private fun isValidImportedSemanticKeyword(keyword: String): Boolean {
        if (keyword.length !in IMPORTED_KEYWORD_MIN_LENGTH..IMPORTED_KEYWORD_MAX_LENGTH) return false
        if (IMPORTED_KEYWORD_SENTENCE_MARKERS.any { marker: String -> keyword.contains(marker) }) return false
        if (IMPORTED_NOISY_KEYWORD_PARTS.any { noisyPart: String -> keyword.contains(noisyPart, ignoreCase = true) }) return false
        return true
    }

    private fun buildImportedComparableText(content: String): String {
        return content.lowercase()
            .replace(IMPORTED_COMPARABLE_REMOVE_REGEX, "")
            .replace(IMPORTED_SEMANTIC_WHITESPACE_REGEX, "")
            .trim()
    }

    /**
     * 通用的数据冲突处理方法
     */
    private suspend fun <T> processDataWithConflicts(
        existing: List<T>,
        imported: List<T>,
        getId: (T) -> String,
        stats: ImportResult.DataTypeStats,
        createConflict: (T, T) -> ConflictItem,
        onConflict: suspend (ConflictItem) -> ConflictResolution
    ): List<T> {
        val result = mutableListOf<T>()
        val existingMap = existing.associateBy { getId(it) }
        
        for (importedItem in imported) {
            val id = getId(importedItem)
            val existingItem = existingMap[id]
            
            if (existingItem == null) {
                // 新数据
                result.add(importedItem)
                stats.recordNew()
            } else {
                // 检查是否完全相同
                if (existingItem == importedItem) {
                    result.add(existingItem)
                    stats.recordIdenticalSkipped()
                } else {
                    // 有冲突
                    val conflictItem = createConflict(existingItem, importedItem)
                    val resolution = withContext(Dispatchers.Main) {
                        onConflict(conflictItem)
                    }
                    
                    when (resolution) {
                        ConflictResolution.KEEP_EXISTING -> {
                            result.add(existingItem)
                            stats.recordConflictKeepExisting()
                        }
                        ConflictResolution.USE_IMPORT -> {
                            result.add(importedItem)
                            stats.recordConflictUseImport()
                        }
                    }
                }
            }
        }
        
        // 添加现有中有但导入中没有的数据
        for (existingItem in existing) {
            val id = getId(existingItem)
            if (!imported.any { getId(it) == id }) {
                result.add(existingItem)
            }
        }
        
        return result
    }
    
    /**
     * 将ChatMessage转换为Map用于显示
     */
    private fun chatMessageToMap(message: ChatMessage): Map<String, Any?> {
        return mapOf(
            "id" to message.id,
            "type" to message.type,
            "contactId" to message.contactId,
            "content" to message.content,
            "timestamp" to message.timestamp,
            "role" to message.role,
            "imageUrl" to message.imageUrl,
            "stickerUrl" to message.stickerUrl,
            "stickerName" to message.stickerName,
            "voiceAudioPath" to message.voiceAudioPath,
            "voiceDurationMillis" to message.voiceDurationMillis,
            "ttsGenerationStatus" to message.ttsGenerationStatus,
            "ttsModelId" to message.ttsModelId,
            "ttsVoiceId" to message.ttsVoiceId,
            "ttsGeneratedAt" to message.ttsGeneratedAt,
            "ttsErrorMessage" to message.ttsErrorMessage,
            "ttsIsStreaming" to message.ttsIsStreaming,
            "productInfo" to message.productInfo,
            "amount" to message.amount,
            "status" to message.status,
            "greeting" to message.greeting,
            "senderName" to message.senderName,
            "recipientName" to message.recipientName,
            "notes" to message.notes,
            "quotedMessage" to message.quotedMessage,
            "aiResponseVersions" to message.aiResponseVersions,
            "displayedResponseIndex" to message.displayedResponseIndex,
            "aiTurnId" to message.aiTurnId,
            "isHidden" to message.isHidden,
            "actionId" to message.actionId,
            "hasBeenSeenByAi" to message.hasBeenSeenByAi,
            "isRecalled" to message.isRecalled,
            "recalledContent" to message.recalledContent,
            "recallTimestamp" to message.recallTimestamp
        )
    }
    
    /**
     * 将LongTermMemory转换为Map用于显示
     */
    private fun longTermMemoryToMap(memory: LongTermMemory): Map<String, Any?> {
        return mapOf(
            "id" to memory.id,
            "contactId" to memory.contactId,
            "memoryText" to memory.memoryText,
            "timestamp" to memory.timestamp
        )
    }
    
    /**
     * 将JottingEntity转换为Map用于显示
     */
    private fun jottingToMap(jotting: JottingEntity): Map<String, Any?> {
        return mapOf(
            "id" to jotting.id,
            "contactId" to jotting.contactId,
            "title" to jotting.title,
            "content" to jotting.content,
            "timestamp" to jotting.timestamp,
            "isFavorited" to jotting.isFavorited,
            "aiTurnId" to jotting.aiTurnId,
            "sourceMessageId" to jotting.sourceMessageId
        )
    }
    
    /**
     * 将HeartbeatEntity转换为Map用于显示
     */
    private fun heartbeatToMap(heartbeat: com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity): Map<String, Any?> {
        return mapOf(
            "id" to heartbeat.id,
            "contactId" to heartbeat.contactId,
            "content" to heartbeat.content,
            "timestamp" to heartbeat.timestamp,
            "isFavorited" to heartbeat.isFavorited,
            "aiTurnId" to heartbeat.aiTurnId,
            "sourceMessageId" to heartbeat.sourceMessageId
        )
    }
    
    /**
     * 交互式导入单个角色的完整聊天历史
     */
    override suspend fun importFullChatHistoryInteractive(
        contact: PersonProfile,
        chatMessages: List<ChatMessage>,
        longTermMemories: List<LongTermMemory>,
        jottings: List<com.susking.ephone_s.aidata.data.local.entity.JottingEntity>,
        heartbeats: List<com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity>,
        onConflict: suspend (ConflictItem) -> ConflictResolution
    ): ImportResult = withContext(Dispatchers.IO) {
        val result = ImportResult()
        val contactNameMap = mapOf(contact.id to contact.realName)
        
        // 1. 处理联系人(在事务外部)
        val existingContacts = personProfileRepository.getPersonProfiles()
        val existingContact = existingContacts.find { it.id == contact.id }
        
        val finalContact = if (existingContact == null) {
            // 新联系人
            result.personProfileStats.recordNew()
            contact
        } else {
            // 检查是否完全相同
            if (existingContact == contact) {
                result.personProfileStats.recordIdenticalSkipped()
                existingContact
            } else {
                // 有冲突,逐字段对比
                val fieldStats = ImportResult.PersonProfileFieldConflictStats(
                    contactId = existingContact.id,
                    contactRealName = existingContact.realName
                )
                
                val resolvedContact = PersonProfileFieldComparator.compareAndResolve(
                    existing = existingContact,
                    imported = contact,
                    onConflict = { conflictItem ->
                        val resolution = withContext(Dispatchers.Main) {
                            onConflict(conflictItem)
                        }
                        fieldStats.recordFieldConflict(resolution)
                        resolution
                    }
                )
                
                result.personProfileFieldConflicts[resolvedContact.id] = fieldStats
                resolvedContact
            }
        }
        
        database.withTransaction {
            // 2. 处理聊天消息
            val existingMessagesWithVersions = chatMessageDao.getMessagesForContactNonFlow(contact.id)
            val finalMessages = mutableListOf<ChatMessage>()
            
            for (importedMsg in chatMessages) {
                val existingMsgWithVersions = existingMessagesWithVersions.find {
                    it.chatMessage.id == importedMsg.id
                }
                
                if (existingMsgWithVersions == null) {
                    // 新消息
                    finalMessages.add(importedMsg)
                    result.chatMessageStats.recordNew()
                } else {
                    val existingMsg = existingMsgWithVersions.chatMessage
                    val existingChatMessage = ChatMessage(
                        id = existingMsg.id,
                        type = existingMsg.type,
                        contactId = existingMsg.contactId,
                        content = existingMsg.content,
                        timestamp = existingMsg.timestamp,
                        role = existingMsg.role,
                        imageUrl = existingMsg.imageUrl,
                        stickerUrl = existingMsg.stickerUrl,
                        stickerName = existingMsg.stickerName,
                        productInfo = existingMsg.productInfo,
                        amount = existingMsg.amount,
                        status = existingMsg.status,
                        greeting = existingMsg.greeting,
                        senderName = existingMsg.senderName,
                        recipientName = existingMsg.recipientName,
                        notes = existingMsg.notes,
                        quotedMessage = existingMsg.quotedMessage,
                        aiResponseVersions = existingMsgWithVersions.versions.map { it.versionContent },
                        displayedResponseIndex = existingMsg.displayedResponseIndex,
                        aiTurnId = existingMsg.aiTurnId,
                        isHidden = existingMsg.isHidden,
                        actionId = existingMsg.actionId
                    )
                    
                    // 检查是否完全相同
                    if (existingChatMessage == importedMsg) {
                        finalMessages.add(existingChatMessage)
                        result.chatMessageStats.recordIdenticalSkipped()
                    } else {
                        // 有冲突
                        val conflictItem = ConflictItem.ChatMessageConflict(
                            messageId = importedMsg.id,
                            contactId = importedMsg.contactId,
                            contactRealName = contactNameMap[importedMsg.contactId] ?: "未知",
                            timestamp = importedMsg.timestamp,
                            existingMessage = chatMessageToMap(existingChatMessage),
                            importMessage = chatMessageToMap(importedMsg)
                        )
                        
                        when (withContext(Dispatchers.Main) {
                            onConflict(conflictItem)
                        }) {
                            ConflictResolution.KEEP_EXISTING -> {
                                finalMessages.add(existingChatMessage)
                                result.chatMessageStats.recordConflictKeepExisting()
                            }
                            ConflictResolution.USE_IMPORT -> {
                                finalMessages.add(importedMsg)
                                result.chatMessageStats.recordConflictUseImport()
                            }
                        }
                    }
                }
            }
            
            // 添加现有中有但导入中没有的消息
            for (existingMsgWithVersions in existingMessagesWithVersions) {
                val existingMsg = existingMsgWithVersions.chatMessage
                if (!chatMessages.any { it.id == existingMsg.id }) {
                    val existingChatMessage = ChatMessage(
                        id = existingMsg.id,
                        type = existingMsg.type,
                        contactId = existingMsg.contactId,
                        content = existingMsg.content,
                        timestamp = existingMsg.timestamp,
                        role = existingMsg.role,
                        imageUrl = existingMsg.imageUrl,
                        stickerUrl = existingMsg.stickerUrl,
                        stickerName = existingMsg.stickerName,
                        productInfo = existingMsg.productInfo,
                        amount = existingMsg.amount,
                        status = existingMsg.status,
                        greeting = existingMsg.greeting,
                        senderName = existingMsg.senderName,
                        recipientName = existingMsg.recipientName,
                        notes = existingMsg.notes,
                        quotedMessage = existingMsg.quotedMessage,
                        aiResponseVersions = existingMsgWithVersions.versions.map { it.versionContent },
                        displayedResponseIndex = existingMsg.displayedResponseIndex,
                        aiTurnId = existingMsg.aiTurnId,
                        isHidden = existingMsg.isHidden,
                        actionId = existingMsg.actionId
                    )
                    finalMessages.add(existingChatMessage)
                }
            }
            
            // 3. 处理长期记忆
            val existingMemories = longTermMemoryDao.getMemoriesForContact(contact.id).first()
            val finalMemories = processDataWithConflicts(
                existing = existingMemories,
                imported = longTermMemories,
                getId = { it.id },
                stats = result.longTermMemoryStats,
                createConflict = { existing, imported ->
                    ConflictItem.LongTermMemoryConflict(
                        memoryId = imported.id,
                        contactId = imported.contactId,
                        contactRealName = contactNameMap[imported.contactId] ?: "未知",
                        timestamp = imported.timestamp,
                        existingMemory = longTermMemoryToMap(existing),
                        importMemory = longTermMemoryToMap(imported)
                    )
                },
                onConflict = onConflict
            )
            
            // 4. 处理随笔
            val existingJottings = jottingDao.getJottingsForContact(contact.id).first()
            val finalJottings = processDataWithConflicts(
                existing = existingJottings,
                imported = jottings,
                getId = { it.id.toString() },
                stats = result.jottingStats,
                createConflict = { existing, imported ->
                    ConflictItem.JottingConflict(
                        jottingId = imported.id.toString(),
                        contactId = imported.contactId,
                        contactRealName = contactNameMap[imported.contactId] ?: "未知",
                        timestamp = imported.timestamp,
                        existingJotting = jottingToMap(existing),
                        importJotting = jottingToMap(imported)
                    )
                },
                onConflict = onConflict
            )
            
            // 5. 处理心声
            val existingHeartbeats = heartbeatDao.getHeartbeatsForContact(contact.id).first()
            val finalHeartbeats = processDataWithConflicts(
                existing = existingHeartbeats,
                imported = heartbeats,
                getId = { it.id.toString() },
                stats = result.heartbeatStats,
                createConflict = { existing, imported ->
                    ConflictItem.HeartbeatConflict(
                        heartbeatId = imported.id.toString(),
                        contactId = imported.contactId,
                        contactRealName = contactNameMap[imported.contactId] ?: "未知",
                        timestamp = imported.timestamp,
                        existingHeartbeat = heartbeatToMap(existing),
                        importHeartbeat = heartbeatToMap(imported)
                    )
                },
                onConflict = onConflict
            )
            
            // 清空该联系人的旧数据；旧原子事件只读保留，最终记忆使用替换插入覆盖同ID记录。
            chatMessageDao.deleteMessagesForContact(contact.id)
            jottingDao.deleteJottingsForContact(contact.id)
            heartbeatDao.deleteHeartbeatsForContact(contact.id)
            
            // 插入最终数据
            for (message in finalMessages) {
                val versions = message.aiResponseVersions.map {
                    AiResponseVersionEntity(chatMessageId = message.id, versionContent = it)
                }
                chatMessageDao.insertMessageWithVersions(message.toEntity(), versions)
            }
            
            if (finalMemories.isNotEmpty()) {
                longTermMemoryDao.insertAll(finalMemories)
            }
            
            if (finalJottings.isNotEmpty()) {
                jottingDao.insertAll(finalJottings)
            }
            
            if (finalHeartbeats.isNotEmpty()) {
                heartbeatDao.insertAll(finalHeartbeats)
            }
        }
        
        // 更新联系人列表(在事务外部)
        val updatedContacts = existingContacts.filterNot { it.id == contact.id } + finalContact
        personProfileRepository.savePersonProfiles(updatedContacts)
        
        return@withContext result
    }
}