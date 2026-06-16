package com.susking.ephone_s.brain.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.local.dao.AiActivityDao
import com.susking.ephone_s.aidata.data.local.entity.toDomainModel
import com.susking.ephone_s.aidata.data.local.entity.toEntity
import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.brain.domain.repository.BrainRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * BrainRepository接口的实现类。
 */
class BrainRepositoryImpl(
    private val aiActivityDao: AiActivityDao,
    private val context: Context
) : BrainRepository {

    private val gson = Gson()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("brain_prefs", Context.MODE_PRIVATE)
    }

    private companion object {
        const val KEY_FAB_X = "fab_x"
        const val KEY_FAB_Y = "fab_y"
        const val TAG = "BrainRepositoryImpl"
    }

    override val allActivities: Flow<List<AiActivity>> =
        aiActivityDao.getAllActivities()
            .distinctUntilChanged() // 避免重复查询
            .map { entities ->
                entities.map { it.toDomainModel() }
            }
            .catch { e ->
                // 捕获游标异常，记录日志并发出空列表
                Log.e(TAG, "获取活动列表时发生错误", e)
                emit(emptyList())
            }

    /**
     * 对提示词进行"脱水"处理，将图片内容替换为占位符。
     */
    private fun getSanitizedPromptForLogging(promptJson: String): String {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val promptMap = gson.fromJson<Map<String, Any>>(promptJson, type)

            if (promptMap.containsKey("image_data")) {
                val sanitizedMap = promptMap.toMutableMap()
                sanitizedMap["image_data"] = "[图片数据已省略]"
                gson.toJson(sanitizedMap)
            } else if (promptMap.containsKey("messages")) {
                val messagesRaw = promptMap["messages"]
                if (messagesRaw !is List<*>) {
                    return promptJson
                }

                @Suppress("UNCHECKED_CAST")
                val messages = messagesRaw.mapNotNull { it as? Map<String, Any> }
                val sanitizedMessages = messages.map { message ->
                    val content = message["content"]
                    if (content is String && content.startsWith("data:image")) {
                        message.toMutableMap().apply { this["content"] = "[图片数据已省略]" }
                    } else if (content is List<*>) {
                        val sanitizedContent = content.map { item ->
                            if (item is Map<*, *> && item["type"] == "image_url") {
                                mapOf("type" to "image_url", "image_url" to mapOf("url" to "[图片数据已省略]"))
                            } else {
                                item
                            }
                        }
                        message.toMutableMap().apply { this["content"] = sanitizedContent }
                    } else {
                        message
                    }
                }
                val sanitizedMap = promptMap.toMutableMap()
                sanitizedMap["messages"] = sanitizedMessages
                gson.toJson(sanitizedMap)
            } else {
                promptJson
            }
        } catch (e: Exception) {
            promptJson
        }
    }

    override suspend fun logActivity(activity: AiActivity) {
        // 同时截断prompt和rawResponse中的base64数据
        val sanitizedActivity = activity.copy(
            prompt = getSanitizedPromptForLogging(activity.prompt),
            rawResponse = truncateBase64InResponse(activity.rawResponse)
        )
        val existingActivity = aiActivityDao.findByChainId(sanitizedActivity.activityChainId)

        if (existingActivity != null) {
            val updatedActivity = sanitizedActivity.toEntity().copy(id = existingActivity.id)
            aiActivityDao.updateActivity(updatedActivity)
        } else {
            aiActivityDao.insertActivity(sanitizedActivity.toEntity())
            aiActivityDao.trim(50)
        }
    }

    /**
     * 截断响应中的base64图片数据,只保留前50位
     * 防止数据库行过大导致SQLiteBlobTooBigException
     */
    private fun truncateBase64InResponse(response: String): String {
        // 匹配 data:image/...;base64, 后面的base64数据
        val base64Pattern = Regex("""(data:image/[^;]+;base64,)([A-Za-z0-9+/=]{50})[A-Za-z0-9+/=]+""")
        return base64Pattern.replace(response) { matchResult ->
            "${matchResult.groupValues[1]}${matchResult.groupValues[2]}...[已截断]"
        }
    }

    override suspend fun markAllAsRead() {
        aiActivityDao.markAllAsRead()
    }

    override suspend fun clearAll() {
        aiActivityDao.clearAll()
    }

    override suspend fun markAsVibrated(id: Long) {
        aiActivityDao.markAsVibrated(id)
    }

    override fun saveFabPosition(x: Float, y: Float) {
        prefs.edit()
            .putFloat(KEY_FAB_X, x)
            .putFloat(KEY_FAB_Y, y)
            .apply()
    }

    override fun getFabPosition(): Pair<Float, Float>? {
        return if (prefs.contains(KEY_FAB_X) && prefs.contains(KEY_FAB_Y)) {
            val x = prefs.getFloat(KEY_FAB_X, 0f)
            val y = prefs.getFloat(KEY_FAB_Y, 0f)
            Pair(x, y)
        } else {
            null
        }
    }

    override fun clearFabPosition() {
        prefs.edit()
            .remove(KEY_FAB_X)
            .remove(KEY_FAB_Y)
            .apply()
    }
    
    override suspend fun pauseWaitingBackgroundTasks() {
        android.util.Log.d("BrainRepositoryImpl", "开始暂停后台任务(更新数据库)")
        aiActivityDao.pauseWaitingBackgroundTasks()
        android.util.Log.d("BrainRepositoryImpl", "已暂停后台任务(数据库已更新)")
    }
    
    override suspend fun resumeStoppedBackgroundTasks() {
        android.util.Log.d("BrainRepositoryImpl", "开始恢复后台任务(更新数据库)")
        aiActivityDao.resumeStoppedBackgroundTasks()
        android.util.Log.d("BrainRepositoryImpl", "已恢复后台任务(数据库已更新)")
    }
    
    override suspend fun cancelTask(activityChainId: String) {
        android.util.Log.d("BrainRepositoryImpl", "开始取消任务: $activityChainId")
        aiActivityDao.cancelTask(activityChainId)
        android.util.Log.d("BrainRepositoryImpl", "已取消任务: $activityChainId")
    }
    
    override suspend fun cancelAllBackgroundTasks() {
        android.util.Log.d("BrainRepositoryImpl", "开始取消所有后台任务(更新数据库)")
        aiActivityDao.cancelAllBackgroundTasks()
        android.util.Log.d("BrainRepositoryImpl", "已取消所有后台任务(数据库已更新)")
    }
}