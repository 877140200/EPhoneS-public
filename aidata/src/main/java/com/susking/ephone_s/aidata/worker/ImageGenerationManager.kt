package com.susking.ephone_s.aidata.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.AlbumPhoto
import com.susking.ephone_s.aidata.domain.model.TaobaoPurchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 通用图片生成管理器 - 独立Worker架构
 *
 * 新架构特点:
 * - 每个任务独立调度,不使用任务链
 * - 可以单独取消某个任务
 * - 通过SharedPreferences管理任务队列状态
 * - Worker执行时检查:全局暂停、上一个任务完成、时间间隔
 * - 支持暂停/恢复所有任务
 *
 * 使用方式:
 * ```
 * // 生成相册图片
 * ImageGenerationManager.scheduleAlbumImageGeneration(
 *     context = context,
 *     contactId = "contact_123",
 *     photos = listOf(photo1, photo2, photo3)
 * )
 *
 * // 取消单个任务
 * ImageGenerationManager.cancelTask(context, activityChainId)
 *
 * // 暂停/恢复所有任务
 * ImageGenerationManager.pauseAllTasks(context)
 * ImageGenerationManager.resumeAllTasks()
 * ```
 */
object ImageGenerationManager {

    private const val TAG = "ImageGenManager"
    private const val PREFS_NAME = "image_gen_manager"
    private const val KEY_LAST_COMPLETION_TIME = "last_completion_time"
    private const val KEY_QUEUE_SIZE = "queue_size" // 队列中任务数量
    
    const val DELAY_SECONDS = 60L // 每次生图间隔

    private val gson = Gson()
    
    /**
     * 获取SharedPreferences实例
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 获取队列中的任务数量
     */
    private fun getQueueSize(context: Context): Int {
        return getPrefs(context).getInt(KEY_QUEUE_SIZE, 0)
    }
    
    /**
     * 增加队列任务数量
     */
    private fun incrementQueueSize(context: Context, count: Int) {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_QUEUE_SIZE, 0)
        prefs.edit().putInt(KEY_QUEUE_SIZE, current + count).apply()
        Log.e(TAG, "【诊断-队列计数】incrementQueueSize: $current -> ${current + count} (增加$count)")
    }
    
    /**
     * 减少队列任务数量
     */
    private fun decrementQueueSize(context: Context) {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_QUEUE_SIZE, 0)
        if (current > 0) {
            prefs.edit().putInt(KEY_QUEUE_SIZE, current - 1).apply()
            Log.e(TAG, "【诊断-队列计数】decrementQueueSize: $current -> ${current - 1}")
        } else {
            Log.e(TAG, "【诊断-队列计数】decrementQueueSize: 当前已经是0,无法继续减少")
        }
    }
    
    /**
     * 记录最后一次任务完成时间并减少队列计数
     */
    fun recordTaskCompletion(context: Context) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_COMPLETION_TIME, System.currentTimeMillis())
            .apply()
        decrementQueueSize(context)
    }
    
    /**
     * 获取最后一次任务完成时间
     */
    fun getLastCompletionTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_COMPLETION_TIME, 0L)
    }
    
    /**
     * 检查是否可以执行下一个任务(距离上次完成>=60秒)
     */
    fun canExecuteNextTask(context: Context): Boolean {
        val lastTime = getLastCompletionTime(context)
        if (lastTime == 0L) return true // 第一个任务
        val elapsed = (System.currentTimeMillis() - lastTime) / 1000
        return elapsed >= DELAY_SECONDS
    }

    /**
     * 调度相册图片生成任务
     *
     * @param context 上下文
     * @param contactId 联系人ID
     * @param photos 需要生成图片的照片列表
     */
    fun scheduleAlbumImageGeneration(
        context: Context,
        contactId: String,
        photos: List<AlbumPhoto>
    ) {
        if (photos.isEmpty()) {
            Log.d(TAG, "没有需要生成的相册照片")
            return
        }

        Log.d(TAG, "开始调度${photos.size}张相册照片的生成任务 for contact: $contactId")

        scheduleImageGeneration(
            context = context,
            contactId = contactId,
            imageType = ImageGenerationWorker.IMAGE_TYPE_ALBUM,
            items = photos,
            itemToJson = { gson.toJson(it) },
            getImagePrompt = { it.imagePrompt },
            typeName = "相册图片"
        )
    }

    /**
     * 调度淘宝商品图生成任务
     *
     * @param context 上下文
     * @param contactId 联系人ID
     * @param purchases 需要生成图片的商品列表
     */
    fun scheduleTaobaoImageGeneration(
        context: Context,
        contactId: String,
        purchases: List<TaobaoPurchase>
    ) {
        if (purchases.isEmpty()) {
            Log.d(TAG, "没有需要生成的淘宝商品图")
            return
        }

        Log.d(TAG, "开始调度${purchases.size}张淘宝商品图的生成任务 for contact: $contactId")

        scheduleImageGeneration(
            context = context,
            contactId = contactId,
            imageType = ImageGenerationWorker.IMAGE_TYPE_TAOBAO,
            items = purchases,
            itemToJson = { gson.toJson(it) },
            getImagePrompt = { it.imagePrompt },
            typeName = "淘宝图片"
        )
    }

    /**
     * 通用的图片生成调度逻辑 - 队列模式
     * 新任务追加到队尾,基于已有WAITING任务数量计算延迟
     */
    private fun <T> scheduleImageGeneration(
        context: Context,
        contactId: String,
        imageType: String,
        items: List<T>,
        itemToJson: (T) -> String,
        getImagePrompt: (T) -> String,
        typeName: String
    ) {
        val workManager = WorkManager.getInstance(context)

        // 获取当前队列中的任务数量(从SharedPreferences读取)
        val queuedTaskCount = getQueueSize(context)
        Log.d(TAG, "当前队列中有${queuedTaskCount}个任务")
        
        // 将新任务加入队列计数
        incrementQueueSize(context, items.size)

        // 为每个item生成唯一的activityChainId
        val activityChainIds = items.map { UUID.randomUUID().toString() }

        // 预注册所有任务（状态为WAITING）
        val aiRequestService = AiDataApi.getAiRequestService()
        CoroutineScope(Dispatchers.IO).launch {
            items.forEachIndexed { index, item ->
                val description = "${typeName}生成 [${index + 1}/${items.size}]"
                aiRequestService.registerWaitingTask(
                    activityChainId = activityChainIds[index],
                    description = description,
                    prompt = getImagePrompt(item)
                )
            }
        }

        items.forEachIndexed { index, item ->
            val activityChainId = activityChainIds[index]
            
            // 为每个item创建一个Work请求
            val inputData = Data.Builder()
                .putString(ImageGenerationWorker.KEY_CONTACT_ID, contactId)
                .putString(ImageGenerationWorker.KEY_IMAGE_TYPE, imageType)
                .putString(ImageGenerationWorker.KEY_ITEM_JSON, itemToJson(item))
                .putInt(ImageGenerationWorker.KEY_ITEM_INDEX, index + 1)
                .putInt(ImageGenerationWorker.KEY_TOTAL_COUNT, items.size)
                .putString(ImageGenerationWorker.KEY_ACTIVITY_CHAIN_ID, activityChainId)
                .putLong(
                    ImageGenerationWorker.KEY_DELAY_SECONDS,
                    0L // 延迟由WorkManager的initialDelay处理
                )
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 计算初始延时: 基于队列中已有任务数量 + 当前任务在本批次中的位置
            // 队列中每个任务间隔DELAY_SECONDS
            val initialDelay = (queuedTaskCount + index) * DELAY_SECONDS
            val workRequest = OneTimeWorkRequestBuilder<ImageGenerationWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.SECONDS)
                .addTag("image_gen")
                .addTag("${imageType}_gen")
                .addTag("contact_$contactId")
                .addTag("task_$activityChainId") // 单独任务标签,用于取消
                .build()

            // 独立入队,追加到队尾
            workManager.enqueue(workRequest)

            Log.d(TAG, "【诊断】已调度第${index + 1}/${items.size}个${typeName}: activityChainId=$activityChainId, 队列位置=${queuedTaskCount + index}, 延时${initialDelay}秒, workId=${workRequest.id}")
        }

        Log.d(TAG, "任务调度完成,共${items.size}个${typeName}追加到队尾,起始位置=${queuedTaskCount}")
    }

    /**
     * 取消单个任务
     * @param context 上下文
     * @param activityChainId 任务的活动链ID
     */
    fun cancelTask(context: Context, activityChainId: String) {
        val workManager = WorkManager.getInstance(context)
        val taskTag = "task_$activityChainId"
        
        Log.e(TAG, "【诊断-cancelTask】开始取消单个任务: $activityChainId")
        Log.e(TAG, "【诊断-cancelTask】taskTag=$taskTag")
        
        // 先查询任务状态
        val workInfos = workManager.getWorkInfosByTag(taskTag).get()
        Log.e(TAG, "【诊断-cancelTask】找到${workInfos.size}个任务")
        workInfos.forEach { workInfo ->
            Log.e(TAG, "【诊断-cancelTask】workId=${workInfo.id}, state=${workInfo.state}")
        }
        
        workManager.cancelAllWorkByTag(taskTag)
        Log.e(TAG, "【诊断-cancelTask】已调用cancelAllWorkByTag")
        
        // 取消任务时也要减少队列计数
        decrementQueueSize(context)
        Log.e(TAG, "【诊断-cancelTask】已取消任务: $activityChainId")
    }

    /**
     * 取消指定联系人的所有图片生成任务
     */
    fun cancelAllImageGeneration(context: Context, contactId: String) {
        val workManager = WorkManager.getInstance(context)
        val contactTag = "contact_$contactId"
        
        Log.e(TAG, "【诊断-cancelAll】开始取消联系人所有任务: contactId=$contactId")
        Log.e(TAG, "【诊断-cancelAll】contactTag=$contactTag")
        
        // 先获取要取消的任务数量
        val workInfos = workManager.getWorkInfosByTag(contactTag).get()
        Log.e(TAG, "【诊断-cancelAll】找到${workInfos.size}个任务")
        workInfos.forEach { workInfo ->
            Log.e(TAG, "【诊断-cancelAll】workId=${workInfo.id}, state=${workInfo.state}, tags=${workInfo.tags}")
        }
        
        val cancelCount = workInfos.filter { !it.state.isFinished }.size
        Log.e(TAG, "【诊断-cancelAll】其中未完成的任务: $cancelCount 个")
        
        workManager.cancelAllWorkByTag(contactTag)
        Log.e(TAG, "【诊断-cancelAll】已调用cancelAllWorkByTag")
        
        // 批量取消时,根据实际取消的任务数量减少队列计数
        if (cancelCount > 0) {
            val prefs = getPrefs(context)
            val current = prefs.getInt(KEY_QUEUE_SIZE, 0)
            val newCount = maxOf(0, current - cancelCount)
            prefs.edit().putInt(KEY_QUEUE_SIZE, newCount).apply()
            Log.e(TAG, "【诊断-cancelAll】已取消联系人 $contactId 的所有图片生成任务(${cancelCount}个), 队列计数: $current -> $newCount")
        } else {
            Log.e(TAG, "【诊断-cancelAll】没有找到需要取消的任务")
        }
    }

    /**
     * 取消指定联系人指定类型的图片生成任务
     */
    fun cancelImageGeneration(context: Context, contactId: String, imageType: String) {
        val workManager = WorkManager.getInstance(context)
        
        // 【修复】需要同时匹配imageType和contactId两个tag
        val contactTag = "contact_$contactId"
        val typeTag = "${imageType}_gen"
        
        Log.e(TAG, "【诊断-取消】开始取消任务: contactId=$contactId, imageType=$imageType")
        Log.e(TAG, "【诊断-取消】contactTag=$contactTag, typeTag=$typeTag")
        
        // 先获取要取消的任务数量（必须同时拥有两个tag）
        val allTypeWorkInfos = workManager.getWorkInfosByTag(typeTag).get()
        Log.e(TAG, "【诊断-取消】${typeTag}的所有任务数: ${allTypeWorkInfos.size}")
        
        val contactWorkInfos = workManager.getWorkInfosByTag(contactTag).get()
        Log.e(TAG, "【诊断-取消】${contactTag}的所有任务数: ${contactWorkInfos.size}")
        
        // 找出同时拥有两个tag的任务
        val targetWorkIds = allTypeWorkInfos.map { it.id }.intersect(contactWorkInfos.map { it.id }.toSet())
        val cancelCount = allTypeWorkInfos.filter { it.id in targetWorkIds && !it.state.isFinished }.size
        
        Log.e(TAG, "【诊断-取消】实际需要取消的任务数: $cancelCount")
        
        // 【修复】只取消该联系人的该类型任务（通过workId精确取消）
        targetWorkIds.forEach { workId ->
            workManager.cancelWorkById(workId)
            Log.e(TAG, "【诊断-取消】已取消workId: $workId")
        }
        
        // 批量取消时,根据实际取消的任务数量减少队列计数
        if (cancelCount > 0) {
            val prefs = getPrefs(context)
            val current = prefs.getInt(KEY_QUEUE_SIZE, 0)
            val newCount = maxOf(0, current - cancelCount)
            prefs.edit().putInt(KEY_QUEUE_SIZE, newCount).apply()
            Log.e(TAG, "【诊断-取消】已取消联系人 $contactId 的${imageType}图片生成任务(${cancelCount}个), 队列计数: $current -> $newCount")
        } else {
            Log.e(TAG, "【诊断-取消】没有找到需要取消的任务")
        }
    }
    
    /**
     * 重置队列计数（调试用）
     * 当队列计数出现异常时可以手动重置
     */
    fun resetQueueSize(context: Context) {
        getPrefs(context).edit().putInt(KEY_QUEUE_SIZE, 0).apply()
        Log.d(TAG, "已重置队列计数为0")
    }
    
}