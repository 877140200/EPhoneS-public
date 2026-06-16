package com.susking.ephone_s.aidata.domain.use_case

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.ChatHistoryMapper
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictItem
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictResolution
import com.susking.ephone_s.aidata.domain.model.import_export.EPhoneSChat
import com.susking.ephone_s.aidata.domain.model.import_export.ExportData
import com.susking.ephone_s.aidata.domain.model.import_export.ImportOutcome
import com.susking.ephone_s.aidata.domain.model.import_export.ImportPreview
import com.susking.ephone_s.aidata.domain.model.import_export.ImportResult
import com.susking.ephone_s.aidata.domain.repository.DataImportExportRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.util.BackupSnapshotHelper
import com.susking.ephone_s.aidata.util.ImportPathProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class ImportDataUseCase(
    private val context: Context,
    private val dataImportExportRepository: DataImportExportRepository,
    private val personProfileRepository: PersonProfileRepository
) {
    private val gson = GsonBuilder().create()
    private val pathProcessor = ImportPathProcessor(context)
    private val snapshotHelper = BackupSnapshotHelper(context)

    /**
     * 准备导入数据，解析文件并返回预览信息。
     */
    suspend fun prepareImport(uri: Uri): Result<ImportPreview> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getFileName(uri)
                if (fileName?.endsWith(".zip") == true) {
                    prepareImportFromZip(inputStream)
                } else {
                    prepareImportFromJson(inputStream)
                }
            } ?: Result.failure(Exception("无法打开文件输入流"))
        } catch (e: Exception) {
            Log.e("ImportDataUseCase", "准备导入数据时出错", e)
            Result.failure(e)
        }
    }

    /**
     * 执行导入操作。
     */
    suspend fun executeImport(preview: ImportPreview): ImportOutcome<String> = withContext(Dispatchers.IO) {
        // 在真正写库前创建回滚快照；创建失败说明尚未触碰用户数据，返回 Failed。
        val snapshotDir: File = try {
            snapshotHelper.createSnapshot()
        } catch (e: Exception) {
            Log.e("ImportDataUseCase", "创建回滚快照失败，已中止导入以保护现有数据", e)
            return@withContext ImportOutcome.Failed(
                Exception("无法创建数据备份，已中止导入以保护现有数据：${e.message}", e)
            )
        }

        try {
            val successMessage: String = when (preview) {
                is ImportPreview.SingleChat -> {
                    val contact = ChatHistoryMapper.toPersonProfile(preview.ephoneSChat)
                    val chatHistory = preview.ephoneSChat.chatData.history.map { historyMessage ->
                        ChatHistoryMapper.fromHistoryMessage(historyMessage, contact.id)
                    }
                    val longTermMemories = preview.ephoneSChat.chatData.longTermMemory.map {
                        LongTermMemory(
                            contactId = contact.id,
                            memoryText = it.content,
                            timestamp = it.timestamp
                        )
                    }
                    
                    // 从chatData.thoughtsHistory中提取jottings和heartbeats
                    val thoughtsHistory = preview.ephoneSChat.chatData.thoughtsHistory
                    
                    val jottings = thoughtsHistory.mapNotNull { thought ->
                        thought.randomJottings?.let { content ->
                            com.susking.ephone_s.aidata.data.local.entity.JottingEntity(
                                id = 0, // Room会自动生成
                                contactId = contact.id,
                                title = "", // thoughtsHistory中没有title
                                content = content,
                                timestamp = thought.timestamp,
                                isFavorited = false,
                                aiTurnId = null,
                                sourceMessageId = null
                            )
                        }
                    }
                    
                    val heartbeats = thoughtsHistory.mapNotNull { thought ->
                        thought.heartfeltVoice?.let { content ->
                            com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity(
                                id = 0, // Room会自动生成
                                contactId = contact.id,
                                content = content,
                                timestamp = thought.timestamp,
                                isFavorited = false,
                                aiTurnId = null,
                                sourceMessageId = null
                            )
                        }
                    }
                    
                    // 根据导入模式选择不同的导入方法
                    if (preview.mode.isIncremental()) {
                        dataImportExportRepository.importFullChatHistoryIncremental(
                            contact,
                            chatHistory,
                            longTermMemories,
                            jottings,
                            heartbeats,
                            preview.mode
                        )
                    } else {
                        dataImportExportRepository.importFullChatHistory(
                            contact,
                            chatHistory,
                            longTermMemories,
                            jottings,
                            heartbeats
                        )
                    }
                    
                    val modeDesc = when (preview.mode) {
                        com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.OVERWRITE -> "覆盖"
                        com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING -> "增量(保留现有)"
                        com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_PREFER_IMPORT -> "增量(优先导入)"
                    }
                    "角色 ${contact.remarkName} ${modeDesc}导入成功！"
                }
                is ImportPreview.AllData -> {
                    // 根据导入模式选择不同的导入方法
                    if (preview.mode.isIncremental()) {
                        dataImportExportRepository.importAllDataIncremental(preview.exportData, preview.mode)
                    } else {
                        dataImportExportRepository.importAllData(preview.exportData)
                    }

                    val modeDesc = when (preview.mode) {
                        com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.OVERWRITE -> "覆盖"
                        com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING -> "增量(保留现有)"
                        com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_PREFER_IMPORT -> "增量(优先导入)"
                    }
                    "全部数据${modeDesc}导入成功！"
                }
            }
            // 写库成功，清理快照
            snapshotHelper.discardSnapshot(snapshotDir)
            ImportOutcome.Success(successMessage)
        } catch (e: Exception) {
            Log.e("ImportDataUseCase", "执行导入时出错，开始回滚到导入前状态", e)
            val restoredCleanly: Boolean = snapshotHelper.restoreSnapshot(snapshotDir)
            snapshotHelper.discardSnapshot(snapshotDir)
            ImportOutcome.RolledBack(error = e, restoredCleanly = restoredCleanly)
        }
    }
    
    /**
     * 执行交互式导入操作(智能合并模式)
     * @param uri 导入文件的URI
     * @param onConflict 冲突解决回调函数,返回用户的选择
     * @return 导入结果,包含详细的统计信息
     */
    suspend fun executeInteractiveImport(
        uri: Uri,
        onConflict: suspend (ConflictItem) -> ConflictResolution
    ): ImportOutcome<ImportResult> = withContext(Dispatchers.IO) {
        // 先准备导入,获取预览信息（仅解析文件，不写库；失败时用户数据未被触碰，返回 Failed）
        val prepareResult = prepareImport(uri)
        if (prepareResult.isFailure) {
            return@withContext ImportOutcome.Failed(
                prepareResult.exceptionOrNull() ?: Exception("准备导入失败")
            )
        }
        val preview = prepareResult.getOrNull()
            ?: return@withContext ImportOutcome.Failed(Exception("无法获取预览信息"))

        // 在真正写库前创建回滚快照；创建失败说明尚未触碰用户数据，返回 Failed。
        val snapshotDir: File = try {
            snapshotHelper.createSnapshot()
        } catch (e: Exception) {
            Log.e("ImportDataUseCase", "创建回滚快照失败，已中止导入以保护现有数据", e)
            return@withContext ImportOutcome.Failed(
                Exception("无法创建数据备份，已中止导入以保护现有数据：${e.message}", e)
            )
        }

        try {
            val importResult: ImportResult = when (preview) {
                is ImportPreview.SingleChat -> {
                    val contact = ChatHistoryMapper.toPersonProfile(preview.ephoneSChat)
                    val chatHistory = preview.ephoneSChat.chatData.history.map { historyMessage ->
                        ChatHistoryMapper.fromHistoryMessage(historyMessage, contact.id)
                    }
                    val longTermMemories = preview.ephoneSChat.chatData.longTermMemory.map {
                        LongTermMemory(
                            contactId = contact.id,
                            memoryText = it.content,
                            timestamp = it.timestamp
                        )
                    }
                    
                    // 从chatData.thoughtsHistory中提取jottings和heartbeats
                    val thoughtsHistory = preview.ephoneSChat.chatData.thoughtsHistory
                    
                    val jottings = thoughtsHistory.mapNotNull { thought ->
                        thought.randomJottings?.let { content ->
                            com.susking.ephone_s.aidata.data.local.entity.JottingEntity(
                                id = 0, // Room会自动生成
                                contactId = contact.id,
                                title = "", // thoughtsHistory中没有title
                                content = content,
                                timestamp = thought.timestamp,
                                isFavorited = false,
                                aiTurnId = null,
                                sourceMessageId = null
                            )
                        }
                    }
                    
                    val heartbeats = thoughtsHistory.mapNotNull { thought ->
                        thought.heartfeltVoice?.let { content ->
                            com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity(
                                id = 0, // Room会自动生成
                                contactId = contact.id,
                                content = content,
                                timestamp = thought.timestamp,
                                isFavorited = false,
                                aiTurnId = null,
                                sourceMessageId = null
                            )
                        }
                    }
                    
                    // 使用交互式导入
                    val result = dataImportExportRepository.importFullChatHistoryInteractive(
                        contact = contact,
                        chatMessages = chatHistory,
                        longTermMemories = longTermMemories,
                        jottings = jottings,
                        heartbeats = heartbeats,
                        onConflict = onConflict
                    )

                    result
                }
                is ImportPreview.AllData -> {
                    // 全量数据使用交互式导入
                    val result = dataImportExportRepository.importAllDataInteractive(
                        data = preview.exportData,
                        onConflict = onConflict
                    )

                    result
                }
            }
            // 写库成功，清理快照
            snapshotHelper.discardSnapshot(snapshotDir)
            ImportOutcome.Success(importResult)
        } catch (e: Exception) {
            Log.e("ImportDataUseCase", "执行交互式导入时出错，开始回滚到导入前状态", e)
            val restoredCleanly: Boolean = snapshotHelper.restoreSnapshot(snapshotDir)
            snapshotHelper.discardSnapshot(snapshotDir)
            ImportOutcome.RolledBack(error = e, restoredCleanly = restoredCleanly)
        }
    }

    private suspend fun prepareImportFromJson(inputStream: InputStream): Result<ImportPreview> {
        // 将输入流复制到临时文件，以支持多次读取，避免直接在内存中缓冲大文件
        val tempFile = File.createTempFile("import_json", ".tmp", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            // 尝试以流式方式解析为 ExportData
            try {
                tempFile.inputStream().use { fileStream ->
                    val reader = fileStream.reader()
                    val exportData = gson.fromJson(reader, ExportData::class.java)
                    if (exportData != null && (!exportData.contacts.isNullOrEmpty() || exportData.userProfile != null)) {
                        return Result.success(
                            ImportPreview.AllData(
                                exportData = exportData,
                                contactCount = exportData.contacts?.size ?: 0,
                                messageCount = exportData.chatMessages?.size ?: 0
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // 解析为 ExportData 失败，继续尝试解析为 EPhoneSChat
                Log.w("ImportDataUseCase", "Failed to parse as ExportData, trying EPhoneSChat", e)
            }

            // 如果失败，则尝试以流式方式解析为单个聊天记录 EPhoneSChat
            try {
                tempFile.inputStream().use { fileStream ->
                    val (ephoneSChat, _) = ChatHistoryMapper.fromJson(fileStream)
                    return createSingleChatPreview(ephoneSChat)
                }
            } catch (e2: Exception) {
                return Result.failure(Exception("无法解析为任何已知的数据格式。", e2))
            }
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun prepareImportFromZip(inputStream: InputStream): Result<ImportPreview> {
        val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val dataJsonFile = unzipAndGetDataJson(inputStream, tempDir)
                ?: return Result.failure(Exception("ZIP文件中未找到 data.json"))

            // 尝试解析为 ExportData (全量备份)
            try {
                dataJsonFile.inputStream().use { jsonStream ->
                    val reader = jsonStream.reader()
                    val rawExportData: ExportData = gson.fromJson(reader, object : TypeToken<ExportData>() {}.type)
                    if (!rawExportData.contacts.isNullOrEmpty() || rawExportData.userProfile != null) {
                        val processedData = pathProcessor.processAllDataPaths(rawExportData, tempDir)
                        return Result.success(ImportPreview.AllData(
                            exportData = processedData,
                            contactCount = processedData.contacts?.size ?: 0,
                            messageCount = processedData.chatMessages?.size ?: 0
                        ))
                    }
                }
            } catch (e: Exception) {
                // 解析为 ExportData 失败，继续尝试解析为 EPhoneSChat
                Log.w("ImportDataUseCase", "Failed to parse as ExportData, trying EPhoneSChat", e)
            }

            // 如果失败，则尝试以流式方式解析为 EPhoneSChat (单个角色备份)
            try {
                dataJsonFile.inputStream().use { jsonStream ->
                    Log.d("ImportDataUseCase", "prepareImportFromZip: 尝试解析为 EPhoneSChat")
                    val (rawEphoneSChat, _) = ChatHistoryMapper.fromJson(jsonStream)
                    
                    val processedChat = pathProcessor.processSingleChatPaths(rawEphoneSChat, tempDir)
                    return createSingleChatPreview(processedChat)
                }
            } catch (e2: Exception) {
                Log.e("ImportDataUseCase", "prepareImportFromZip: 解析EPhoneSChat失败", e2)
                return Result.failure(Exception("无法将ZIP内容解析为任何已知的数据格式。原因: ${e2.message}", e2))
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun createSingleChatPreview(ephoneSChat: EPhoneSChat): Result<ImportPreview.SingleChat> {
        val importedContact = ChatHistoryMapper.toPersonProfile(ephoneSChat)
        val existingContact = personProfileRepository.getPersonProfileById(importedContact.id)
        
        // 计算冲突统计
        val messageCount = ephoneSChat.chatData.history.size
        val conflictStats = if (existingContact != null) {
            // 角色已存在，所有消息都可能冲突
            ImportPreview.ConflictStats(
                newContacts = 0,
                conflictingContacts = 1,
                newMessages = 0,
                conflictingMessages = messageCount
            )
        } else {
            // 新角色，所有消息都是新增
            ImportPreview.ConflictStats(
                newContacts = 1,
                conflictingContacts = 0,
                newMessages = messageCount,
                conflictingMessages = 0
            )
        }
        
        val preview = if (existingContact != null) {
            ImportPreview.SingleChat(
                ephoneSChat = ephoneSChat,
                isNewCharacter = false,
                characterNickname = importedContact.remarkName.ifEmpty { importedContact.realName },
                characterRealName = importedContact.realName,
                existingCharacterNickname = existingContact.remarkName.ifEmpty { existingContact.realName },
                existingCharacterRealName = existingContact.realName,
                conflictStats = conflictStats
            )
        } else {
            ImportPreview.SingleChat(
                ephoneSChat = ephoneSChat,
                isNewCharacter = true,
                characterNickname = importedContact.remarkName.ifEmpty { importedContact.realName },
                characterRealName = importedContact.realName,
                conflictStats = conflictStats
            )
        }
        return Result.success(preview)
    }

    private fun unzipAndGetDataJson(inputStream: InputStream, tempDir: File): File? {
        var dataJsonFile: File? = null
        ZipInputStream(inputStream.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(tempDir, entry.name)
                if (!file.canonicalPath.startsWith(tempDir.canonicalPath)) {
                    throw SecurityException("Zip Path is outside of the target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                    if (entry.name == "data.json") {
                        dataJsonFile = file
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return dataJsonFile
    }


    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        result = cursor.getString(columnIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                if (cut != null) {
                    result = result.substring(cut + 1)
                }
            }
        }
        return result
    }
}