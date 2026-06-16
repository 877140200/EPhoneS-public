package com.susking.ephone_s.aidata.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.DataMigrationRepository
import com.susking.ephone_s.aidata.util.ImageFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据迁移 Repository 实现类
 * 负责处理旧数据格式到新数据格式的迁移
 */
class DataMigrationRepositoryImpl(
    private val context: Context,
    private val database: AiDataDatabase
) : DataMigrationRepository {

    private val chatMessageDao = database.chatMessageDao()
    private val gson = Gson()

    private companion object {
        private const val TAG = "DataMigrationRepo"
        private const val PREFS_NAME = "qq_prefs"
        private const val KEY_MESSAGES = "messages_legacy"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun getOldMessagesForMigration(): Map<String, List<ChatMessage>> = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_MESSAGES, null)
        if (json.isNullOrBlank()) {
            return@withContext emptyMap()
        }
        return@withContext try {
            val type = object : TypeToken<Map<String, List<ChatMessage>>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading old messages for migration", e)
            emptyMap()
        }
    }

    override suspend fun deleteOldMessagesAfterMigration() {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(KEY_MESSAGES).apply()
            Log.i(TAG, "Successfully deleted old messages from SharedPreferences after migration.")
        }
    }

    override suspend fun migrateImageMessagesToFiles() {
        withContext(Dispatchers.IO) {
            val messageIdsToMigrate = chatMessageDao.getAllBase64ImageMessageIds()
            if (messageIdsToMigrate.isEmpty()) {
                Log.d(TAG, "No Base64 image messages found to migrate.")
                return@withContext
            }

            Log.d(TAG, "Found ${messageIdsToMigrate.size} messages with Base64 images to migrate.")

            // 定义块大小，例如1MB
            val chunkSize = 1024 * 1024

            messageIdsToMigrate.forEach { messageId ->
                try {
                    val totalLength = chatMessageDao.getImageUrlLength(messageId)
                    if (totalLength == null || totalLength == 0) {
                        Log.w(TAG, "Message ID $messageId has null or empty imageUrl. Skipping.")
                        return@forEach
                    }

                    val base64Builder = StringBuilder(totalLength)
                    var offset = 1 // SQLite SUBSTR 的 offset 从 1 开始

                    while (offset <= totalLength) {
                        val chunk = chatMessageDao.getImageUrlChunk(messageId, offset, chunkSize)
                        if (chunk != null) {
                            base64Builder.append(chunk)
                        } else {
                            Log.e(TAG, "Failed to read chunk for message ID $messageId at offset $offset. Aborting this message.")
                            break
                        }
                        offset += chunkSize
                    }

                    // 确保我们完整地读取了数据
                    if (base64Builder.toString().length == totalLength) {
                        val fullBase64String = base64Builder.toString()
                        val imagePath = ImageFileHelper.saveImageFromBase64(context, fullBase64String)

                        if (imagePath != null) {
                            // 更新数据库中的 imageUrl 为文件路径
                            chatMessageDao.updateMessageImageUrl(messageId, imagePath)
                            Log.d(TAG, "Successfully migrated image for message ID: $messageId to path: $imagePath")
                        } else {
                            Log.e(TAG, "Failed to save image from Base64 for message ID: $messageId")
                        }
                    } else {
                        Log.e(TAG, "Failed to fully read imageUrl for message ID $messageId. Expected length: $totalLength, actual length: ${base64Builder.toString().length}")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "An error occurred while migrating message ID $messageId.", e)
                }
            }
        }
    }
}