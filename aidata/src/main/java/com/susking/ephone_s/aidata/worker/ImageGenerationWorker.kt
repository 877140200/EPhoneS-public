package com.susking.ephone_s.aidata.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.AlbumPhoto
import com.susking.ephone_s.aidata.domain.model.TaobaoPurchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 通用图片生成后台任务
 *
 * 功能:
 * - 后台生成NovelAI图片(支持相册照片、淘宝商品图等)
 * - 支持长时间运行,不受界面生命周期影响
 * - 自动保存生成结果到数据库
 *
 * 使用方式:
 * - 由ImageGenerationManager统一调度
 * - 每次生成一张图片,间隔30秒
 */
class ImageGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ImageGenWorker"
        
        // 输入参数键
        const val KEY_CONTACT_ID = "contact_id"
        const val KEY_IMAGE_TYPE = "image_type" // "album" 或 "taobao"
        const val KEY_ITEM_JSON = "item_json" // AlbumPhoto或TaobaoPurchase的JSON
        const val KEY_ITEM_INDEX = "item_index"
        const val KEY_TOTAL_COUNT = "total_count"
        const val KEY_ACTIVITY_CHAIN_ID = "activity_chain_id"
        const val KEY_DELAY_SECONDS = "delay_seconds"
        
        // 输出参数键
        const val KEY_SUCCESS = "success"
        const val KEY_IMAGE_PATH = "image_path"
        const val KEY_ERROR_MESSAGE = "error_message"
        
        // 图片类型常量
        const val IMAGE_TYPE_ALBUM = "album"
        const val IMAGE_TYPE_TAOBAO = "taobao"
    }

    private val gson = Gson()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val activityChainId = inputData.getString(KEY_ACTIVITY_CHAIN_ID) ?: "unknown"
        val itemIndex = inputData.getInt(KEY_ITEM_INDEX, -1)
        // 使用Error级别确保日志显示
        Log.e(TAG, "【诊断-Worker启动嘻嘻嘻嘻嘻】activityChainId=$activityChainId, index=$itemIndex, workId=${id}")
        Log.e(TAG, "【诊断-Worker启动】当前线程: ${Thread.currentThread().name}")
        
        // 1. 检查是否可以执行(时间间隔)
        if (!ImageGenerationManager.canExecuteNextTask(applicationContext)) {
            val lastTime = ImageGenerationManager.getLastCompletionTime(applicationContext)
            val elapsed = (System.currentTimeMillis() - lastTime) / 1000
            val remaining = ImageGenerationManager.DELAY_SECONDS - elapsed
            val waitSeconds = remaining + 1 // 加1秒作为缓冲
            Log.d(TAG, "距离上次完成仅${elapsed}秒,还需等待${remaining}秒,将等待${waitSeconds}秒后继续")
            // 等待指定时间后继续执行
            delay(waitSeconds * 1000)
            Log.d(TAG, "等待完成,继续执行生图任务")
        }
        
        // 2. 获取输入参数
        val contactId = inputData.getString(KEY_CONTACT_ID)
        val imageType = inputData.getString(KEY_IMAGE_TYPE)
        val itemJson = inputData.getString(KEY_ITEM_JSON)
//        val itemIndex = inputData.getInt(KEY_ITEM_INDEX, -1)
        val totalCount = inputData.getInt(KEY_TOTAL_COUNT, 0)
//        val activityChainId = inputData.getString(KEY_ACTIVITY_CHAIN_ID)
        val delaySeconds = inputData.getLong(KEY_DELAY_SECONDS, 0L)
        
        // 参数校验 - 返回success以便任务链继续，但标记为失败
        if (contactId == null || imageType == null || itemJson == null || activityChainId == null) {
            Log.e(TAG, "缺少必要参数")
            return@withContext Result.success(createErrorData("缺少必要参数"))
        }
        
        val typeName = when (imageType) {
            IMAGE_TYPE_ALBUM -> "相册图片"
            IMAGE_TYPE_TAOBAO -> "淘宝图片"
            else -> "图片"
        }
        Log.d(TAG, "开始生成$typeName [$itemIndex/$totalCount] for contact: $contactId")
        
        try {
            // 2. 根据类型解析数据并提取图片提示词和ID
            val (imagePrompt, itemId) = when (imageType) {
                IMAGE_TYPE_ALBUM -> {
                    val photo = try {
                        gson.fromJson(itemJson, AlbumPhoto::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "解析相册照片数据失败", e)
                        return@withContext Result.success(createErrorData("照片数据解析失败: ${e.message}"))
                    }
                    Pair(photo.imagePrompt, photo.id)
                }
                IMAGE_TYPE_TAOBAO -> {
                    val purchase = try {
                        gson.fromJson(itemJson, TaobaoPurchase::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "解析淘宝商品数据失败", e)
                        return@withContext Result.success(createErrorData("商品数据解析失败: ${e.message}"))
                    }
                    Pair(purchase.imagePrompt, purchase.id)
                }
                else -> {
                    Log.e(TAG, "未知的图片类型: $imageType")
                    return@withContext Result.success(createErrorData("未知的图片类型"))
                }
            }
            
            // 3. 获取联系人资料
            val personProfileRepository = AiDataApi.getPersonProfileRepository()
            val profile = personProfileRepository.getPersonProfileById(contactId)
            
            if (profile == null) {
                Log.e(TAG, "联系人不存在: $contactId")
                return@withContext Result.success(createErrorData("联系人不存在"))
            }
            
            // 4. 生成图片(返回Base64)并记录日志，使用预注册的activityChainId
            // generateImageWithChainId会自动记录FAILED状态到brain日志
            val aiRequestService = AiDataApi.getAiRequestService()
            Log.d(TAG, "调用NovelAI生成图片: $imagePrompt, 延迟: ${delaySeconds}秒")
            val taskDescription = "${typeName}生成 [$itemIndex/$totalCount]"
            val imageBase64 = aiRequestService.generateImageWithChainId(
                activityChainId = activityChainId,
                prompt = imagePrompt,
                profile = profile,
                description = taskDescription,
                delaySeconds = delaySeconds
            )
            
            if (imageBase64 == null) {
                Log.w(TAG, "图片生成返回null")
                // brain日志已经记录FAILED，返回success让任务链继续
                return@withContext Result.success(createErrorData("图片生成失败"))
            }
            
            // 5. 保存图片到文件
            val saveImageUseCase = AiDataApi.getSaveImageUseCase()
            val filePath = saveImageUseCase.invoke(imageBase64)
            
            if (filePath == null) {
                Log.w(TAG, "图片保存失败")
                return@withContext Result.success(createErrorData("图片保存失败"))
            }
            
            Log.d(TAG, "图片生成并保存成功: $filePath")
            
            // 6. 根据类型更新数据库
            val cphoneRepository = AiDataApi.getCPhoneRepository()
            val existingData = cphoneRepository.getCPhoneDataSuspend(contactId)
            
            if (existingData != null) {
                when (imageType) {
                    IMAGE_TYPE_ALBUM -> {
                        val photo = gson.fromJson(itemJson, AlbumPhoto::class.java)
                        val updatedPhoto = photo.copy(imageUrl = filePath)
                        val updatedPhotos = existingData.albumPhotos.map {
                            if (it.id == itemId) updatedPhoto else it
                        }
                        cphoneRepository.updateAlbumPhotos(contactId, updatedPhotos)
                        Log.d(TAG, "相册数据库更新成功")
                    }
                    IMAGE_TYPE_TAOBAO -> {
                        val purchase = gson.fromJson(itemJson, TaobaoPurchase::class.java)
                        val updatedPurchase = purchase.copy(imageUrl = filePath)
                        if (existingData.taobaoData != null) {
                            val updatedPurchases = existingData.taobaoData.purchases.map {
                                if (it.id == itemId) updatedPurchase else it
                            }
                            cphoneRepository.updateTaobaoPurchases(contactId, updatedPurchases)
                            Log.d(TAG, "淘宝数据库更新成功")
                        } else {
                            Log.w(TAG, "找不到淘宝数据,跳过数据库更新")
                        }
                    }
                }
            } else {
                Log.w(TAG, "找不到CPhone数据,跳过数据库更新")
            }
            
            // 7. 记录任务完成时间
            ImageGenerationManager.recordTaskCompletion(applicationContext)
            
            // 8. 返回成功结果
            val outputData = Data.Builder()
                .putBoolean(KEY_SUCCESS, true)
                .putString(KEY_IMAGE_PATH, filePath)
                .build()
            
            Log.d(TAG, "${typeName}生成任务完成 [$itemIndex/$totalCount]")
            Result.success(outputData)
            
        } catch (e: Exception) {
            Log.e(TAG, "${typeName}生成任务失败", e)
            // 返回success让任务链继续，错误信息通过outputData传递
            Result.success(createErrorData("生成失败: ${e.message}"))
        }
    }

    /**
     * 创建错误输出数据
     */
    private fun createErrorData(errorMessage: String): Data {
        return Data.Builder()
            .putBoolean(KEY_SUCCESS, false)
            .putString(KEY_ERROR_MESSAGE, errorMessage)
            .build()
    }
}