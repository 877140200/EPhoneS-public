package com.susking.ephone_s.aidata.domain.use_case

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.google.gson.GsonBuilder
import com.susking.ephone_s.aidata.data.ChatHistoryMapper
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.import_export.ExportData
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.DataImportExportRepository
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.data.local.dao.LongTermMemoryDao
import com.susking.ephone_s.aidata.domain.repository.ContactSemanticStateRepository
import com.susking.ephone_s.aidata.domain.repository.MemoriesRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.ScheduleRepository
import com.susking.ephone_s.aidata.domain.repository.StickerRepository
import com.susking.ephone_s.aidata.util.ImageFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportDataUseCase(
    private val context: Context,
    private val chatRepository: ChatRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val longTermMemoryDao: LongTermMemoryDao,
    private val heartbeatRepository: HeartbeatRepository,
    private val jottingRepository: JottingRepository,
    private val feedRepository: FeedRepository,
    private val alipayRepository: AlipayRepository,
    private val memoriesRepository: MemoriesRepository,
    private val dataImportExportRepository: DataImportExportRepository,
    private val stickerRepository: StickerRepository,
    private val videoCallHistoryRepository: com.susking.ephone_s.aidata.domain.repository.VideoCallHistoryRepository,
    private val contactSemanticStateRepository: ContactSemanticStateRepository,
    private val scheduleRepository: ScheduleRepository,
    private val database: com.susking.ephone_s.aidata.data.local.AiDataDatabase
) {

    suspend operator fun invoke(format: String, contacts: List<PersonProfile>, friendGroups: List<String>?): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (format == "zip") {
                exportToZip(contacts, friendGroups)
            } else {
                exportToJson(contacts, friendGroups)
            }
        } catch (e: Exception) {
            Log.e("ExportDataUseCase", "导出数据时出错", e)
            Result.failure(e)
        }
    }

    suspend fun exportSingleChat(contactId: String, format: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (format == "zip") {
                exportSingleChatToZip(contactId)
            } else {
                // Fallback or specific implementation for JSON single chat export if needed
                Result.failure(UnsupportedOperationException("JSON export for single chat not supported yet."))
            }
        } catch (e: Exception) {
            Log.e("ExportDataUseCase", "导出单个聊天记录时出错", e)
            Result.failure(e)
        }
    }

    private suspend fun exportToZip(initialContacts: List<PersonProfile>, friendGroups: List<String>?): Result<String> = coroutineScope {
        val tempDir = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}").apply { mkdirs() }
        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        val favoritesDir = File(tempDir, "favorites").apply { mkdirs() }
        try {
            val chatHistoryDeferred = async { chatRepository.getAllMessages() }
            val memoriesDeferred = async { longTermMemoryDao.getAllMemories().first() }
            val heartbeatsDeferred = async { heartbeatRepository.getAllHeartbeats().first() }
            val jottingsDeferred = async { jottingRepository.getAllJottings().first() }
            val favoritesDeferred = async { memoriesRepository.getAllFavoriteMessages() }
            val semanticStatesDeferred = async { contactSemanticStateRepository.getAllSemanticStates().first() }
            val userProfileDeferred = async { personProfileRepository.getUserProfile() }
            val feedsDeferred = async { feedRepository.getAllFeeds().first() }
            val walletDeferred = async { alipayRepository.getWalletInfo("user_main").first() }
            val transactionsDeferred = async { alipayRepository.getBillList().first() }
            val appointmentsDeferred = async { memoriesRepository.getAllAppointmentsSuspend() }
            val stickersDeferred = async { stickerRepository.getAllStickersSuspend() }
            val stickerCategoriesDeferred = async { stickerRepository.getAllCategories().first() }
            val videoCallHistoryDeferred = async { videoCallHistoryRepository.getAllVideoCallHistorySuspend() }

            // 背包/通用记忆/定时问候
            val backpackItemsDeferred = async { database.backpackItemDao().getAllItemsList() }
            val generalMemoriesDeferred = async { database.generalMemoryDao().getAllMemoriesSuspend() }
            val scheduledGreetingsDeferred = async { database.scheduledGreetingDao().getAllGreetingsList() }

            // 健康数据（每日汇总）
            val healthDailyRecordsDeferred = async { database.healthDailyRecordDao().getAllHealthDailyRecordsList() }

            // 提示词储存器（酒馆「锦囊」）
            val promptSentencesDeferred = async { database.promptStorageDao().getAllSentencesList() }
            val promptWordsDeferred = async { database.promptStorageDao().getAllWordsList() }

            // AI记忆系统
            val memoryEmbeddingsDeferred = async { database.memoryEmbeddingDao().getAllEmbeddingsList() }
            val memorySummariesDeferred = async { database.memorySummaryDao().getAllSummaryList() }
            val memoryEventsDeferred = async { database.memoryEventDao().getAllEventList() }
            val memoryGraphNodesDeferred = async { database.memoryGraphDao().getAllNodesList() }
            val memoryGraphRelationsDeferred = async { database.memoryGraphDao().getAllRelationsList() }
            val memoryEventEvidencesDeferred = async { database.memoryEvidenceDao().getAllEventEvidencesList() }
            val memoryRelationEvidencesDeferred = async { database.memoryEvidenceDao().getAllRelationEvidencesList() }
            val memoryRecallDebugRecordsDeferred = async { database.memoryRecallDebugDao().getAllRecordsList() }
            val memoryRecallDebugEntriesDeferred = async { database.memoryRecallDebugDao().getAllEntriesList() }

            val contacts = initialContacts.map { contact ->
                var tempContact = contact
                // 在导出前，动态生成并填充 info_line_text
                val infoItems = mutableListOf<String>()
                contact.gender?.let { infoItems.add("♀ $it") }
                contact.age?.let { infoItems.add("${it}岁") }
                contact.birthday?.let { infoItems.add(it) }
                contact.zodiacSign?.let { infoItems.add(it) }
                contact.location?.let { infoItems.add("现居$it") }
                contact.companyOrSchool?.let { infoItems.add(it) }
                contact.profession?.let { infoItems.add(it) }
                tempContact = tempContact.copy(info_line_text = if (infoItems.isEmpty()) null else infoItems.joinToString(" | "))

                contact.avatarUri?.let { uriString ->
                    val uri = parseUriSafely(uriString)
                    if (uri != null) {
                        val file = ImageFileHelper.copyImageWithCustomName(context, uri, imagesDir, "${contact.id}_avatar")
                        if (file != null) {
                            tempContact = tempContact.copy(avatarUri = "images/${file.name}")
                        }
                    }
                }
                contact.backgroundUri?.let { uriString ->
                    val uri = parseUriSafely(uriString)
                    if (uri != null) {
                        val file = ImageFileHelper.copyImageWithCustomName(context, uri, imagesDir, "${contact.id}_background")
                        if (file != null) {
                            tempContact = tempContact.copy(backgroundUri = "images/${file.name}")
                        }
                    }
                }
                contact.chatBackgroundUri?.let { uriString ->
                    val uri = parseUriSafely(uriString)
                    if (uri != null) {
                        val file = ImageFileHelper.copyImageWithCustomName(context, uri, imagesDir, "${contact.id}_chatBackground")
                        if (file != null) {
                            tempContact = tempContact.copy(chatBackgroundUri = "images/${file.name}")
                        }
                    }
                }
                val newSelectedPhotos = contact.selectedPhotos.mapIndexed { index, uriString ->
                    val uri = parseUriSafely(uriString)
                    if (uri != null) {
                        val file = ImageFileHelper.copyImageWithCustomName(context, uri, imagesDir, "${contact.id}_photo_$index")
                        if (file != null) {
                            "images/${file.name}"
                        } else null
                    } else null
                }.filterNotNull()
                tempContact.copy(selectedPhotos = newSelectedPhotos)
            }

            val chatMessages = chatHistoryDeferred.await().map { message ->
                var tempMessage = message
                if (message.type == "image" || message.type == "naiimag" || !message.imageUrl.isNullOrBlank()) {
                    message.imageUrl?.let { imagePath ->
                        val imageFile = File(imagePath)
                        if (imageFile.exists()) {
                            ImageFileHelper.copyImageToDirectory(context, Uri.fromFile(imageFile), imagesDir)?.let { file ->
                                tempMessage = tempMessage.copy(imageUrl = "images/${file.name}")
                            }
                        }
                    }
                }
                tempMessage
            }

            val feeds = feedsDeferred.await().map { feed ->
                val newImageUrls = feed.imageUrls.mapNotNull { uriString ->
                    val uri = parseUriSafely(uriString)
                    if (uri != null) {
                        val file = ImageFileHelper.copyImageToDirectory(context, uri, imagesDir)
                        if (file != null) {
                            "images/${file.name}"
                        } else null
                    } else null
                }
                feed.copy(imageUrls = newImageUrls)
            }

            val userProfile = userProfileDeferred.await()?.let { profile ->
                var tempProfile = profile
                profile.avatarUri?.let { uriString ->
                    val uri = parseUriSafely(uriString)
                    if (uri != null) {
                        val file = ImageFileHelper.copyImageWithCustomName(context, uri, imagesDir, "user_avatar")
                        if (file != null) {
                            tempProfile = tempProfile.copy(avatarUri = "images/${file.name}")
                        }
                    }
                }
                profile.backgroundUri?.let { uriString ->
                    val uri = parseUriSafely(uriString)
                    if (uri != null) {
                        val file = ImageFileHelper.copyImageWithCustomName(context, uri, imagesDir, "user_background")
                        if (file != null) {
                            tempProfile = tempProfile.copy(backgroundUri = "images/${file.name}")
                        }
                    }
                }
                profile.feedsHeaderBackgroundUri?.let { uriString ->
                    val uri = parseUriSafely(uriString)
                    if (uri != null) {
                        val file = ImageFileHelper.copyImageWithCustomName(context, uri, imagesDir, "user_feedsHeaderBackground")
                        if (file != null) {
                            tempProfile = tempProfile.copy(feedsHeaderBackgroundUri = "images/${file.name}")
                        }
                    }
                }
                tempProfile
            }

            val favoriteMessages = favoritesDeferred.await().map { message ->
                var tempMessage = message
                message.senderAvatar?.let { uriString ->
                    val uri = parseUriSafely(uriString)
                    if (uri != null) {
                        val file = ImageFileHelper.copyImageToDirectory(context, uri, imagesDir)
                        if (file != null) {
                            tempMessage = tempMessage.copy(senderAvatar = "images/${file.name}")
                        }
                    }
                }
                message.imageUrl?.let { imagePath ->
                    val imageFile = File(imagePath)
                    if (imageFile.exists() && imageFile.parentFile?.name == "favorites") {
                        val file = ImageFileHelper.copyImageToDirectory(context, Uri.fromFile(imageFile), favoritesDir)
                        if (file != null) {
                            tempMessage = tempMessage.copy(imageUrl = "favorites/${file.name}")
                        }
                    } else if (!imagePath.startsWith("http")) { // 假设非网络图片是本地图片
                        val uri = parseUriSafely(imagePath)
                        if (uri != null) {
                            val file = ImageFileHelper.copyImageToDirectory(context, uri, imagesDir)
                            if (file != null) {
                                tempMessage = tempMessage.copy(imageUrl = "images/${file.name}")
                            }
                        }
                    }
                }
                tempMessage
            }

            val exportData = ExportData(
                contacts = contacts,
                chatMessages = chatMessages,
                longTermMemories = memoriesDeferred.await(),
                heartbeats = heartbeatsDeferred.await(),
                jottings = jottingsDeferred.await(),
                semanticStates = semanticStatesDeferred.await(),
                favoriteMessages = favoriteMessages,
                userProfile = userProfile,
                feeds = feeds,
                wallet = walletDeferred.await(),
                transactions = transactionsDeferred.await(),
                friendGroups = friendGroups,
                appointments = appointmentsDeferred.await(),
                headerBackgroundImage = userProfile?.feedsHeaderBackgroundUri,
                stickers = stickersDeferred.await(),
                stickerCategories = stickerCategoriesDeferred.await(),
                videoCallHistory = videoCallHistoryDeferred.await(),
                scheduleCourses = scheduleRepository.getAllCourses(),
                scheduleCourseRules = scheduleRepository.getAllCourseRules(),
                scheduleAdjustments = scheduleRepository.getAllAdjustments(),
                scheduleAssignments = scheduleRepository.getAllAssignments(),
                scheduleExams = scheduleRepository.getAllExams(),
                campusEvents = scheduleRepository.getAllCampusEvents(),
                scheduleAiPolicy = scheduleRepository.getAiPolicy(),
                scheduleSemesters = scheduleRepository.getAllSemesters(),
                scheduleSectionTemplates = scheduleRepository.getAllSectionTemplates(),
                scheduleImportDrafts = scheduleRepository.getAllImportDrafts(),
                scheduleReminderRules = scheduleRepository.getAllReminderRules(),
                scheduleReminderRecords = scheduleRepository.getAllReminderRecords(),
                scheduleCareCandidates = scheduleRepository.getAllCareCandidates(),
                scheduleWidgetState = scheduleRepository.getWidgetState(),
                backpackItems = backpackItemsDeferred.await(),
                generalMemories = generalMemoriesDeferred.await(),
                scheduledGreetings = scheduledGreetingsDeferred.await(),
                healthDailyRecords = healthDailyRecordsDeferred.await(),
                promptSentences = promptSentencesDeferred.await(),
                promptWords = promptWordsDeferred.await(),
                memoryEmbeddings = memoryEmbeddingsDeferred.await(),
                memorySummaries = memorySummariesDeferred.await(),
                memoryEvents = memoryEventsDeferred.await(),
                memoryGraphNodes = memoryGraphNodesDeferred.await(),
                memoryGraphRelations = memoryGraphRelationsDeferred.await(),
                memoryEventEvidences = memoryEventEvidencesDeferred.await(),
                memoryRelationEvidences = memoryRelationEvidencesDeferred.await(),
                memoryRecallDebugRecords = memoryRecallDebugRecordsDeferred.await(),
                memoryRecallDebugEntries = memoryRecallDebugEntriesDeferred.await()
            )

            val gson = GsonBuilder()
                .setPrettyPrinting()
                .create()
            val json = gson.toJson(exportData)
            File(tempDir, "data.json").writeText(json)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFileName = "EPhoneS_All_Export_$timestamp.zip"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
            val zipFile = File(downloadsDir, zipFileName)

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                tempDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entryPath = file.relativeTo(tempDir).path
                        val zipEntry = ZipEntry(entryPath)
                        zos.putNextEntry(zipEntry)
                        FileInputStream(file).use { fis -> fis.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            Result.success("数据已成功导出到 '下载' 文件夹下的 $zipFileName 文件中。")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun exportToJson(contacts: List<PersonProfile>, friendGroups: List<String>?): Result<String> {
        val chatHistory = chatRepository.getAllMessages()
        val memories = longTermMemoryDao.getAllMemories().first()
        val heartbeats = heartbeatRepository.getAllHeartbeats().first()
        val jottings = jottingRepository.getAllJottings().first()
        val favorites = memoriesRepository.getAllFavoriteMessages()
        val semanticStates = contactSemanticStateRepository.getAllSemanticStates().first()
        val userProfile = personProfileRepository.getUserProfile()
        val feeds = feedRepository.getAllFeeds().first()
        val wallet = alipayRepository.getWalletInfo("user_main").first()
        val transactions = alipayRepository.getBillList().first()
        val appointments = memoriesRepository.getAllAppointmentsSuspend()
        val stickers = stickerRepository.getAllStickersSuspend()
        val stickerCategories = stickerRepository.getAllCategories().first()
        val videoCallHistory = videoCallHistoryRepository.getAllVideoCallHistorySuspend()

        // 背包/通用记忆/定时问候
        val backpackItems = database.backpackItemDao().getAllItemsList()
        val generalMemories = database.generalMemoryDao().getAllMemoriesSuspend()
        val scheduledGreetings = database.scheduledGreetingDao().getAllGreetingsList()
        val healthDailyRecords = database.healthDailyRecordDao().getAllHealthDailyRecordsList()

        // 提示词储存器（酒馆「锦囊」）
        val promptSentences = database.promptStorageDao().getAllSentencesList()
        val promptWords = database.promptStorageDao().getAllWordsList()

        // AI记忆系统
        val memoryEmbeddings = database.memoryEmbeddingDao().getAllEmbeddingsList()
        val memorySummaries = database.memorySummaryDao().getAllSummaryList()
        val memoryEvents = database.memoryEventDao().getAllEventList()
        val memoryGraphNodes = database.memoryGraphDao().getAllNodesList()
        val memoryGraphRelations = database.memoryGraphDao().getAllRelationsList()
        val memoryEventEvidences = database.memoryEvidenceDao().getAllEventEvidencesList()
        val memoryRelationEvidences = database.memoryEvidenceDao().getAllRelationEvidencesList()
        val memoryRecallDebugRecords = database.memoryRecallDebugDao().getAllRecordsList()
        val memoryRecallDebugEntries = database.memoryRecallDebugDao().getAllEntriesList()

        val exportData = ExportData(
            contacts = contacts,
            chatMessages = chatHistory,
            longTermMemories = memories,
            heartbeats = heartbeats,
            jottings = jottings,
            semanticStates = semanticStates,
            favoriteMessages = favorites,
            userProfile = userProfile,
            feeds = feeds,
            wallet = wallet,
            transactions = transactions,
            friendGroups = friendGroups,
            appointments = appointments,
            stickers = stickers,
            stickerCategories = stickerCategories,
            videoCallHistory = videoCallHistory,
            scheduleCourses = scheduleRepository.getAllCourses(),
            scheduleCourseRules = scheduleRepository.getAllCourseRules(),
            scheduleAdjustments = scheduleRepository.getAllAdjustments(),
            scheduleAssignments = scheduleRepository.getAllAssignments(),
            scheduleExams = scheduleRepository.getAllExams(),
            campusEvents = scheduleRepository.getAllCampusEvents(),
            scheduleAiPolicy = scheduleRepository.getAiPolicy(),
            scheduleSemesters = scheduleRepository.getAllSemesters(),
            scheduleSectionTemplates = scheduleRepository.getAllSectionTemplates(),
            scheduleImportDrafts = scheduleRepository.getAllImportDrafts(),
            scheduleReminderRules = scheduleRepository.getAllReminderRules(),
            scheduleReminderRecords = scheduleRepository.getAllReminderRecords(),
            scheduleCareCandidates = scheduleRepository.getAllCareCandidates(),
            scheduleWidgetState = scheduleRepository.getWidgetState(),
            backpackItems = backpackItems,
            generalMemories = generalMemories,
            scheduledGreetings = scheduledGreetings,
            healthDailyRecords = healthDailyRecords,
            promptSentences = promptSentences,
            promptWords = promptWords,
            memoryEmbeddings = memoryEmbeddings,
            memorySummaries = memorySummaries,
            memoryEvents = memoryEvents,
            memoryGraphNodes = memoryGraphNodes,
            memoryGraphRelations = memoryGraphRelations,
            memoryEventEvidences = memoryEventEvidences,
            memoryRelationEvidences = memoryRelationEvidences,
            memoryRecallDebugRecords = memoryRecallDebugRecords,
            memoryRecallDebugEntries = memoryRecallDebugEntries
        )
        val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()
        val json = gson.toJson(exportData)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "EPhoneS_All_Export_$timestamp.json"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { it.write(json.toByteArray()) }
        return Result.success("数据已成功导出到 '下载' 文件夹下的 $fileName 文件中。")
    }

    private suspend fun exportSingleChatToZip(contactId: String): Result<String> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}").apply { mkdirs() }
        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        val voiceMessagesDir = File(tempDir, "voice_messages").apply { mkdirs() }
        try {
            val contact = personProfileRepository.getPersonProfileById(contactId) ?: return@withContext Result.failure(Exception("找不到联系人"))
            val messages = chatRepository.getMessagesForContact(contactId).first()
            val longTermMemories = longTermMemoryDao.getMemoriesForContact(contactId).first()
            val heartbeats = heartbeatRepository.getAllHeartbeats().first().filter { it.contactId == contactId }
            val jottings = jottingRepository.getAllJottings().first().filter { it.contactId == contactId }

            // 在导出前，动态生成并填充 info_line_text
            val infoItems = mutableListOf<String>()
            contact.gender?.let { infoItems.add("♀ $it") }
            contact.age?.let { infoItems.add("${it}岁") }
            contact.birthday?.let { infoItems.add(it) }
            contact.zodiacSign?.let { infoItems.add(it) }
            contact.location?.let { infoItems.add("现居$it") }
            contact.companyOrSchool?.let { infoItems.add(it) }
            contact.profession?.let { infoItems.add(it) }
            var updatedContact = contact.copy(info_line_text = if (infoItems.isEmpty()) null else infoItems.joinToString(" | "))

            // 处理并复制图片
            updatedContact = updatedContact.copy(
                avatarUri = ImageFileHelper.copyUriToRelativePathWithCustomName(context, updatedContact.avatarUri, imagesDir, "${contact.id}_avatar"),
                backgroundUri = ImageFileHelper.copyUriToRelativePathWithCustomName(context, updatedContact.backgroundUri, imagesDir, "${contact.id}_background"),
                chatBackgroundUri = ImageFileHelper.copyUriToRelativePathWithCustomName(context, updatedContact.chatBackgroundUri, imagesDir, "${contact.id}_chatBackground"),
                selectedPhotos = updatedContact.selectedPhotos.mapIndexed { index, uriString ->
                    ImageFileHelper.copyUriToRelativePathWithCustomName(context, uriString, imagesDir, "${contact.id}_photo_$index")
                }.filterNotNull()
            )

            val updatedMessages = messages.map { message ->
                val relativeVoiceAudioPath: String? = copyVoiceAudioToRelativePath(message.voiceAudioPath, voiceMessagesDir)
                message.copy(
                    imageUrl = ImageFileHelper.copyUriToRelativePath(context, message.imageUrl, imagesDir),
                    voiceAudioPath = relativeVoiceAudioPath ?: message.voiceAudioPath
                )
            }

            val ephoneSChat = ChatHistoryMapper.toEPhoneSChat(
                contact = updatedContact,
                chatHistory = updatedMessages,
                longTermMemories = longTermMemories,
                heartbeats = heartbeats,
                jottings = jottings
            )

            val gson = GsonBuilder().setPrettyPrinting().create()
            val json = gson.toJson(ephoneSChat)
            File(tempDir, "data.json").writeText(json)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFileName = "EPhoneS_Chat_${contact.remarkName}_$timestamp.zip"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
            val zipFile = File(downloadsDir, zipFileName)

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                tempDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entryPath = file.relativeTo(tempDir).path
                        val zipEntry = ZipEntry(entryPath)
                        zos.putNextEntry(zipEntry)
                        FileInputStream(file).use { fis -> fis.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            Result.success("聊天记录已成功导出到 '下载' 文件夹下的 $zipFileName 文件中。")
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    private fun copyVoiceAudioToRelativePath(voiceAudioPath: String?, voiceMessagesDir: File): String? {
        if (voiceAudioPath.isNullOrBlank()) {
            return null
        }
        val sourceFile = File(voiceAudioPath)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return null
        }
        val targetFile = createUniqueTargetFile(voiceMessagesDir, sourceFile.name)
        sourceFile.copyTo(targetFile, overwrite = true)
        return "voice_messages/${targetFile.name}"
    }

    private fun createUniqueTargetFile(directory: File, originalFileName: String): File {
        val safeFileName: String = originalFileName.ifBlank { "voice_${System.currentTimeMillis()}.wav" }
        val baseName: String = safeFileName.substringBeforeLast('.', safeFileName)
        val extension: String = safeFileName.substringAfterLast('.', missingDelimiterValue = "")
        var candidateFile = File(directory, safeFileName)
        var index = 1
        while (candidateFile.exists()) {
            val indexedFileName: String = if (extension.isBlank()) {
                "${baseName}_$index"
            } else {
                "${baseName}_$index.$extension"
            }
            candidateFile = File(directory, indexedFileName)
            index++
        }
        return candidateFile
    }
    
    /**
     * 安全地解析URI字符串
     * - 如果是绝对路径，使用Uri.fromFile()
     * - 如果是URI格式（content://或file://），使用Uri.parse()
     */
    private fun parseUriSafely(uriString: String): Uri? {
        return try {
            when {
                // 如果是绝对路径
                uriString.startsWith("/") -> {
                    val file = File(uriString)
                    if (file.exists()) Uri.fromFile(file) else null
                }
                // 如果是content://或file://等URI格式
                uriString.contains("://") -> Uri.parse(uriString)
                // 其他情况，尝试作为文件路径处理
                else -> {
                    val file = File(uriString)
                    if (file.exists()) Uri.fromFile(file) else Uri.parse(uriString)
                }
            }
        } catch (e: Exception) {
            Log.e("ExportDataUseCase", "解析URI失败: $uriString", e)
            null
        }
    }
}