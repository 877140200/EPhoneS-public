package com.susking.ephone_s.brain.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.worker.CPhoneAutoDiaryWorker
import com.susking.ephone_s.brain.api.BrainApi
import com.susking.ephone_s.brain.api.NotificationProvider
import com.susking.ephone_s.brain.domain.repository.BrainRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 用于监控 AI 活动的共享 ViewModel。
 */
class BrainViewModel(
    private val repository: BrainRepository,
    private val notificationProvider: NotificationProvider,
    private val context: Context
) : ViewModel() {
    
    // 移除暂停状态相关代码

    /**
     * 共享的活动列表Flow，避免多次订阅导致的数据库查询竞争
     */
    private val sharedActivities = repository.allActivities
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 1
        )

    /**
     * 所有AI活动（包括后台任务和普通日志）
     */
    val aiActivities: StateFlow<List<AiActivity>> = sharedActivities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 过滤后的普通日志（不包括后台任务）
     */
    val normalActivities: StateFlow<List<AiActivity>> = sharedActivities
        .map { activities ->
            activities.filter { !it.isBackgroundTask }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 后台任务列表
     */
    val backgroundTasks: StateFlow<List<AiActivity>> = sharedActivities
        .map { activities ->
            activities.filter { it.isBackgroundTask }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unreadNotificationCount: StateFlow<Int> = notificationProvider.getUnreadNotificationCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /**
     * 后台任务统计数据
     */
    data class TaskStats(
        val total: Int = 0,
        val success: Int = 0,
        val failed: Int = 0
    )

    /**
     * 后台任务统计
     */
    val backgroundTaskStats: StateFlow<TaskStats> = sharedActivities
        .map { activities ->
            val backgroundTasks = activities.filter { it.isBackgroundTask }
            TaskStats(
                total = backgroundTasks.size,
                success = backgroundTasks.count { it.status == AiActivityStatus.SUCCESS },
                failed = backgroundTasks.count { it.status == AiActivityStatus.FAILED }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TaskStats()
        )

    /**
     * 自动计划列表。
     *
     * 计划只读取已有联系人字段与自动日记 SharedPreferences，不写入任何新数据。
     */
    val autoPlans: StateFlow<List<AutoPlanItem>> = combine(
        AiDataApi.getPersonProfileRepository().getPersonProfilesFlow(),
        CPhoneAutoDiaryWorker.autoDiarySettingVersion
    ) { profiles: List<PersonProfile>, _: Int ->
        val isGlobalBackgroundActivityEnabled: Boolean = AiDataApi.getSettingsRepository().isBackgroundActivityEnabled()
        val backgroundActivityIntervalSeconds: Int = AiDataApi.getSettingsRepository().getBackgroundActivityInterval()
        buildAutoPlans(
            profiles = profiles,
            isGlobalBackgroundActivityEnabled = isGlobalBackgroundActivityEnabled,
            backgroundActivityIntervalSeconds = backgroundActivityIntervalSeconds
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun buildAutoPlans(
        profiles: List<PersonProfile>,
        isGlobalBackgroundActivityEnabled: Boolean,
        backgroundActivityIntervalSeconds: Int
    ): List<AutoPlanItem> {
        val autoDiaryPlans: List<AutoPlanItem> = profiles
            .filter { profile: PersonProfile -> CPhoneAutoDiaryWorker.isAutoDiaryEnabled(context, profile.id) }
            .map { profile: PersonProfile ->
                AutoPlanItem(
                    id = "auto_diary_${profile.id}",
                    type = AutoPlanType.AUTO_DIARY,
                    contactName = profile.realName,
                    title = "${profile.realName}的自动日记",
                    description = "每天 05:00 自动整理联系人小手机日记，并按周期生成分层摘要。",
                    isEnabled = true
                )
            }

        val backgroundActivityPlans: List<AutoPlanItem> = profiles
            .filter { profile: PersonProfile ->
                isGlobalBackgroundActivityEnabled &&
                    profile.backgroundActivityEnabled &&
                    !profile.isBlocked &&
                    !profile.isGroupChat
            }
            .map { profile: PersonProfile ->
                AutoPlanItem(
                    id = "background_activity_${profile.id}",
                    type = AutoPlanType.BACKGROUND_ACTIVITY,
                    contactName = profile.realName,
                    title = "${profile.realName}的独立后台活动",
                    description = "总开关已开启，约每 ${formatIntervalMinutes(backgroundActivityIntervalSeconds)} 检查一次，联系人冷却 ${profile.actionCooldownMinutes} 分钟。",
                    isEnabled = true
                )
            }

        val plans: List<AutoPlanItem> = autoDiaryPlans + backgroundActivityPlans
        if (plans.isNotEmpty()) return plans

        return listOf(
            AutoPlanItem(
                id = EMPTY_AUTO_PLAN_ID,
                type = AutoPlanType.EMPTY,
                contactName = "",
                title = "暂无自动计划",
                description = buildEmptyAutoPlanDescription(isGlobalBackgroundActivityEnabled),
                isEnabled = false
            )
        )
    }

    private fun formatIntervalMinutes(intervalSeconds: Int): Int {
        return (intervalSeconds / SECONDS_PER_MINUTE).coerceAtLeast(MINIMUM_DISPLAY_MINUTES)
    }

    private fun buildEmptyAutoPlanDescription(isGlobalBackgroundActivityEnabled: Boolean): String {
        if (!isGlobalBackgroundActivityEnabled) {
            return "独立后台活动总开关已关闭，且当前没有开启自动日记的联系人。"
        }
        return "当前没有开启自动日记，也没有符合独立后台活动条件的联系人。"
    }

    fun logActivity(activity: AiActivity) {
        viewModelScope.launch {
            repository.logActivity(activity)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
        }
    }

    fun clearAllActivities() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            notificationProvider.markAllAsRead()
        }
    }

    fun saveFabPosition(x: Float, y: Float) {
        repository.saveFabPosition(x, y)
    }

    fun getFabPosition(): Pair<Float, Float>? {
        return repository.getFabPosition()
    }

    fun clearFabPosition() {
        repository.clearFabPosition()
    }

    fun markAsVibrated(activityId: Long) {
        viewModelScope.launch {
            repository.markAsVibrated(activityId)
        }
    }
    
    /**
     * 一键取消所有后台任务
     */
    fun cancelAllBackgroundTasks() {
        viewModelScope.launch {
            try {
                // 1. 获取所有后台任务（使用共享Flow避免重复查询）
                val backgroundTasks = sharedActivities.first()
                    .filter { it.isBackgroundTask && it.status != AiActivityStatus.CANCELLED }
                
                Log.e("BrainViewModel", "【诊断-取消全部】准备取消${backgroundTasks.size}个后台任务喵喵")
                backgroundTasks.forEach { task ->
                    Log.e("BrainViewModel", "【诊断-取消全部】任务详情: chainId=${task.activityChainId}, status=${task.status}, description=${task.description}")
                }
                
                // 2. 取消每个任务的WorkManager工作
                backgroundTasks.forEach { task ->
                    try {
                        Log.e("BrainViewModel", "【诊断-取消全部】尝试取消任务: ${task.activityChainId}")
                        val imageGenerationManagerClass = Class.forName("com.susking.ephone_s.aidata.worker.ImageGenerationManager")
                        val companionField = imageGenerationManagerClass.getField("INSTANCE")
                        val managerInstance = companionField.get(null)
                        val cancelMethod = imageGenerationManagerClass.getMethod("cancelTask", Context::class.java, String::class.java)
                        cancelMethod.invoke(managerInstance, context, task.activityChainId)
                        Log.e("BrainViewModel", "【诊断-取消全部】反射调用成功: ${task.activityChainId}")
                    } catch (e: Exception) {
                        Log.e("BrainViewModel", "【诊断-取消全部】反射调用失败,尝试直接调用: ${task.activityChainId}", e)
                        try {
                            val clazz = context.classLoader.loadClass("com.susking.ephone_s.aidata.worker.ImageGenerationManager")
                            val method = clazz.getMethod("cancelTask", Context::class.java, String::class.java)
                            method.invoke(null, context, task.activityChainId)
                            Log.e("BrainViewModel", "【诊断-取消全部】直接调用成功: ${task.activityChainId}")
                        } catch (e2: Exception) {
                            Log.e("BrainViewModel", "【诊断-取消全部】直接调用也失败: ${task.activityChainId}", e2)
                        }
                    }
                }
                
                // 3. 重置队列计数为0（清除所有"幽灵任务"）
                try {
                    val imageGenerationManagerClass = Class.forName("com.susking.ephone_s.aidata.worker.ImageGenerationManager")
                    val companionField = imageGenerationManagerClass.getField("INSTANCE")
                    val managerInstance = companionField.get(null)
                    val resetMethod = imageGenerationManagerClass.getMethod("resetQueueSize", Context::class.java)
                    resetMethod.invoke(managerInstance, context)
                    Log.e("BrainViewModel", "【诊断-取消全部】已重置队列计数为0,清除所有幽灵任务")
                } catch (e: Exception) {
                    Log.e("BrainViewModel", "【诊断-取消全部】重置队列计数失败", e)
                }
                
                // 4. 更新数据库状态为CANCELLED
                repository.cancelAllBackgroundTasks()
                Log.e("BrainViewModel", "【诊断-取消全部】数据库状态已更新为CANCELLED")
                
                Log.e("BrainViewModel", "【诊断-取消全部】已成功取消所有后台任务(共${backgroundTasks.size}个)")
            } catch (e: Exception) {
                Log.e("BrainViewModel", "取消所有后台任务失败", e)
            }
        }
    }
    
    /**
     * 取消单个后台任务
     * @param activityChainId 任务的活动链ID
     */
    fun cancelTask(activityChainId: String) {
        viewModelScope.launch {
            try {
                Log.e("BrainViewModel", "【诊断-取消单个】开始取消任务: $activityChainId")
                
                // 1. 先更新数据库状态为CANCELLED，避免界面继续显示为处理中。
                repository.cancelTask(activityChainId)
                Log.e("BrainViewModel", "【诊断-取消单个】数据库状态已更新: $activityChainId")
                
                // 2. 取消仍在执行的普通AI、Embedding或ASR网络请求。
                BrainApi.getAiRequestService().cancelRequest(activityChainId)
                Log.e("BrainViewModel", "【诊断-取消单个】已请求取消网络调用: $activityChainId")
                
                // 3. 再取消WorkManager后台图片任务。
                cancelImageGenerationTask(activityChainId)
                
                Log.e("BrainViewModel", "【诊断-取消单个】已成功取消任务: $activityChainId")
            } catch (e: Exception) {
                Log.e("BrainViewModel", "【诊断-取消单个】取消任务失败: $activityChainId", e)
                e.printStackTrace()
            }
        }
    }

    private fun cancelImageGenerationTask(activityChainId: String) {
        try {
            val imageGenerationManagerClass = Class.forName("com.susking.ephone_s.aidata.worker.ImageGenerationManager")
            val companionField = imageGenerationManagerClass.getField("INSTANCE")
            val managerInstance = companionField.get(null)
            val cancelMethod = imageGenerationManagerClass.getMethod("cancelTask", Context::class.java, String::class.java)
            cancelMethod.invoke(managerInstance, context, activityChainId)
            Log.e("BrainViewModel", "【诊断-取消单个】反射调用成功: $activityChainId")
        } catch (e: Exception) {
            Log.e("BrainViewModel", "【诊断-取消单个】反射调用失败,尝试直接调用: $activityChainId", e)
            val clazz = context.classLoader.loadClass("com.susking.ephone_s.aidata.worker.ImageGenerationManager")
            val method = clazz.getMethod("cancelTask", Context::class.java, String::class.java)
            method.invoke(null, context, activityChainId)
            Log.e("BrainViewModel", "【诊断-取消单个】直接调用成功: $activityChainId")
        }
    }

    private companion object {
        private const val EMPTY_AUTO_PLAN_ID: String = "empty_auto_plan"
        private const val SECONDS_PER_MINUTE: Int = 60
        private const val MINIMUM_DISPLAY_MINUTES: Int = 1
    }
}

/**
 * BrainViewModel 的工厂类。
 */
@Suppress("UNCHECKED_CAST")
class BrainViewModelFactory(
    private val repository: BrainRepository,
    private val notificationProvider: NotificationProvider,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrainViewModel::class.java)) {
            return BrainViewModel(repository, notificationProvider, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}