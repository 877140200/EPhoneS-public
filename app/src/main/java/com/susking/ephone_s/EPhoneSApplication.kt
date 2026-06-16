package com.susking.ephone_s

import android.app.Application
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import com.canhub.cropper.CropImageOptions
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.album.AlbumDatabaseProviderImpl
import com.susking.ephone_s.album.AlbumNavigatorImpl
import com.susking.ephone_s.album.api.AlbumDatabaseProvider
import com.susking.ephone_s.album.api.AlbumNavigator
import com.susking.ephone_s.album.api.ImageSelectionCallback
import com.susking.ephone_s.aidata.data.local.AlbumDatabase
import com.susking.ephone_s.aidata.data.local.dao.AlbumDao
import com.susking.ephone_s.aidata.data.local.dao.PhotoDao
import com.susking.ephone_s.album.domain.model.Photo
import com.susking.ephone_s.brain.BrainColorProvider
import com.susking.ephone_s.brain.FloatingWindowStyleProviderImpl
import com.susking.ephone_s.brain.NotificationProviderImpl
import com.susking.ephone_s.brain.api.ActivityLogger
import com.susking.ephone_s.brain.api.BrainApi
import com.susking.ephone_s.brain.service.BrainService
import com.susking.ephone_s.brain.ui.AiActivityAdapter
import com.susking.ephone_s.brain.ui.BrainFragment
import com.susking.ephone_s.brain.ui.BrainViewModel
import com.susking.ephone_s.brain.ui.BrainViewModelFactory
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.HapticFeedbackManager
import com.susking.ephone_s.aidata.data.repository.NotificationRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.SettingsRepositoryImpl
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.provider.AlbumDataProvider
import com.susking.ephone_s.aidata.domain.provider.DesktopDataProvider
import com.susking.ephone_s.aidata.domain.manager.BackpackEventManager
import com.susking.ephone_s.features.theme.domain.repository.ThemeRepository
import com.susking.ephone_s.qq.QqFragmentProviderImpl
import com.susking.ephone_s.core.api.QqApi
import com.susking.ephone_s.qq.domain.manager.QqEvent
import com.susking.ephone_s.worker.AiBackgroundWorker
import com.susking.ephone_s.notification.IncomingCallNotificationHelper
import com.susking.ephone_s.notification.NewMessageNotificationHelper
import com.susking.ephone_s.aidata.worker.CPhoneAutoDiaryWorker
import com.susking.ephone_s.aidata.worker.ScheduleReminderWorker
import com.susking.ephone_s.aidata.worker.ScheduledGreetingWorker
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
// EntryPoint用于从Application访问Provider
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProviderEntryPoint {
    @org.jetbrains.annotations.Nullable
    fun albumDataProvider(): AlbumDataProvider?
    
    @org.jetbrains.annotations.Nullable
    fun desktopDataProvider(): DesktopDataProvider?
    
    fun backpackEventManager(): BackpackEventManager

    /** 健康数据仓库：供应用前台时触发 Health Connect 同步。 */
    fun healthRepository(): com.susking.ephone_s.aidata.domain.repository.HealthRepository

    /** 活跃联系人追踪器：判断用户当前是否正在看某联系人聊天界面，决定是否发新消息通知。 */
    fun activeContactTracker(): com.susking.ephone_s.aidata.domain.tracker.ActiveContactTracker
}

@HiltAndroidApp
class EPhoneSApplication : Application(), Configuration.Provider, ViewModelStoreOwner,
    AlbumDatabaseProvider, AlbumNavigator, ImageSelectionCallback {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var themeRepository: ThemeRepository

    /**
     * Brain 悬浮窗样式提供者（单例复用）。
     *
     * 内部持有应用级协程作用域订阅主题，必须全进程复用同一实例；
     * 若每次 configureBrainFragment 都新建，会不断泄漏协程作用域。
     */
    private val floatingWindowStyleProvider: FloatingWindowStyleProviderImpl by lazy {
        FloatingWindowStyleProviderImpl(themeRepository)
    }

    companion object {
        lateinit var db: AiDataDatabase
            private set
        lateinit var albumdb: AlbumDatabase
            private set
        lateinit var settingsRepository: SettingsRepository
            private set
        lateinit var activityLogger: ActivityLogger
            private set
        lateinit var colorProvider: AiActivityAdapter.ColorProvider
            private set
    }

    lateinit var brainViewModel: BrainViewModel
        private set
    
    // Album模块接口实现的委托
    private val albumDatabaseProvider by lazy { AlbumDatabaseProviderImpl(this) }
    private val albumNavigator by lazy { AlbumNavigatorImpl() }

    private val appViewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
    
    // 实现Configuration.Provider接口，提供自定义WorkManager配置
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 数据库和基础仓库初始化
        db = AiDataDatabase.getDatabase(this)
        albumdb = AlbumDatabase.getDatabase(this)
        settingsRepository = SettingsRepositoryImpl(this)
        
        // 初始化 aidata 模块
        // 简化后的初始化：不再需要传入 lambda 函数
        AiDataApi.initialize(this)

        // 初始化 QQ 模块
        QqApi.init(QqFragmentProviderImpl())

        // 初始化Brain模块
        BrainApi.initialize(this)
        activityLogger = BrainApi.getActivityLogger()
        colorProvider = BrainColorProvider(this)

        // 提前创建来电通知渠道。
        // 后台来电时由 AiBackgroundWorker 通过 IncomingCallNotificationHelper 发全屏来电通知,
        // 这里提前建好渠道,使用户能在系统设置中提前看到并单独控制"来电通知"提醒。
        IncomingCallNotificationHelper.createNotificationChannel(this)
        
        // 注册brain模块的AiRequestService到aidata模块
        // 这样aidata模块的GenerateCPhoneDataUseCase就可以使用brain的AI请求服务
        AiDataApi.registerBrainService(this, BrainApi.getAiRequestService())
        
        // 通过EntryPoint获取Provider并注册到aidata模块
        // 这样完整应用数据导入导出功能就可以使用相册和桌面的Provider
        val providerEntryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ProviderEntryPoint::class.java
        )
        val albumDataProvider = providerEntryPoint.albumDataProvider()
        val desktopDataProvider = providerEntryPoint.desktopDataProvider()
        AiDataApi.registerProviders(albumDataProvider, desktopDataProvider)
        
        // 启动背包事件监听器
        val backpackEventManager = providerEntryPoint.backpackEventManager()
        backpackEventManager.startListening()
        Log.d("EPhoneSApplication", "背包事件监听器已启动")
        
        val notificationRepository = NotificationRepositoryImpl(db.notificationDao())
        val notificationProvider = NotificationProviderImpl(notificationRepository)

        // 初始化 BrainViewModel
        val brainViewModelFactory = BrainViewModelFactory(BrainApi.getRepository(), notificationProvider, this)
        brainViewModel = ViewModelProvider(this, brainViewModelFactory)[BrainViewModel::class.java]

        // 启动后台AI决策任务
        setupAiBackgroundWorker()

        // 注册应用级健康数据同步：进入前台（打开/切回/亮屏解锁）时从 Health Connect 同步一次。
        // Health Sync 最短 15 分钟自动同步，故此处不另设定时任务，仅在前台时机拉取即可。
        setupHealthSyncOnForeground(providerEntryPoint.healthRepository())
        
        // 启动定时祝福发送任务
        ScheduledGreetingWorker.schedule(this)
        Log.d("EPhoneSApplication", "定时祝福发送任务已启动")

        // 启动课程表本地提醒任务。
        // 该任务只处理本地提醒记录和系统通知，不会直接请求外部模型。
        ScheduleReminderWorker.schedule(this)
        Log.d("EPhoneSApplication", "课程表提醒任务已启动")

        // 根据联系人级日记设置决定是否启动CPhone自动日记任务，并执行启动补检查
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            CPhoneAutoDiaryWorker.scheduleEnabledContactsAndCatchUp(
                context = this@EPhoneSApplication,
                repository = AiDataApi.getPersonProfileRepository()
            )
            Log.d("EPhoneSApplication", "CPhone联系人级自动日记任务已按设置刷新并执行补检查")
        }
        
        // 根据设置决定是否启动BrainService后台服务
        val isBackgroundServiceEnabled = settingsRepository.isBackgroundServiceEnabled()
        if (isBackgroundServiceEnabled) {
            BrainService.startService(this)
            Log.d("EPhoneSApplication", "Brain后台服务已启动(用户已开启)")
        } else {
            Log.d("EPhoneSApplication", "Brain后台服务未启动(用户已关闭)")
        }

        // 注册新消息系统通知监听（进程级，应用内任意界面/后台均可收到）
        setupNewMessageNotificationListener()
    }
    
    /**
     * 配置BrainFragment的依赖项。
     */
    fun configureBrainFragment(fragment: BrainFragment) {
        fragment.setDependencies(
            viewModel = brainViewModel,
            styleProvider = floatingWindowStyleProvider,
            colorProvider = colorProvider
        )
    }

    /**
     * 注册新消息系统通知监听。
     *
     * 监听「一轮 AI 回复完整落库」事件 [QqEvent.AiResponseCompleted]（由 QqChatManager 在
     * typingCompletion 完成后 post），据此每轮只发一条通知，避免逐气泡密集打扰。
     * 仅当用户当前不在该联系人聊天界面（依据 [ActiveContactTracker]）时才发通知 + 震动，
     * 故用户在桌面/相册/其他联系人聊天页等任意界面均可收到，正在看该聊天时静默。
     *
     * 放在进程级 [CoroutineScope]：前台服务保活下进程长存，与 Activity 是否存活无关。
     */
    private fun setupNewMessageNotificationListener() {
        // 提前建好新消息渠道，便于用户在系统设置中提前看到并单独控制。
        NewMessageNotificationHelper.createNotificationChannel(this)

        // 取 @Singleton 的 ActiveContactTracker（QqChatFragment 进出聊天页时维护其值）。
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, ProviderEntryPoint::class.java)
        val activeContactTracker = entryPoint.activeContactTracker()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            EventBus.events.filterIsInstance<QqEvent.AiResponseCompleted>().collect { event ->
                try {
                    val contactId: String = event.contactId
                    // 用户正在看该联系人聊天 → 不打扰。
                    if (activeContactTracker.isActiveContact(contactId)) return@collect
                    val contact = AiDataApi.getPersonProfileRepository().getPersonProfileById(contactId)
                        ?: return@collect
                    val lastAiMessage = AiDataApi.getChatRepository()
                        .getMessagesForContact(contactId).first()
                        .lastOrNull { message -> message.role == "assistant" }
                    NewMessageNotificationHelper.send(
                        context = this@EPhoneSApplication,
                        contact = contact,
                        lastMessage = lastAiMessage,
                        unreadCount = contact.unreadMessageCount
                    )
                    // 新消息一律震动（震动开关已下线）。
                    HapticFeedbackManager.performVibration(
                        this@EPhoneSApplication,
                        HapticFeedbackManager.VibrationType.LONG
                    )
                } catch (e: Exception) {
                    Log.e("EPhoneSApplication", "发送新消息通知失败", e)
                }
            }
        }
        Log.d("EPhoneSApplication", "新消息通知监听已注册")
    }

    /**
     * 注册应用级健康数据同步。
     *
     * 通过 ProcessLifecycleOwner 监听整个进程的前台事件：ON_START 在「冷启动打开」「从后台切回」
     * 「息屏后亮屏解锁回到应用」时触发，正好覆盖用户要求的三种同步时机。
     * 同步是 IO 协程，无权限/SDK 不可用时仓库内部静默跳过，不阻塞主线程。
     */
    private fun setupHealthSyncOnForeground(
        healthRepository: com.susking.ephone_s.aidata.domain.repository.HealthRepository
    ) {
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        runCatching { healthRepository.syncRecentDays() }
                            .onFailure { error ->
                                Log.w("EPhoneSApplication", "健康数据前台同步失败: ${error.message}")
                            }
                    }
                }
            }
        )
        Log.d("EPhoneSApplication", "健康数据前台同步监听已注册")
    }

    /**
     * 配置AI后台活动工作器。
     *
     * 重要行为：
     * - 首次执行会等待一个完整的间隔时间后才触发
     * - 使用 ExistingPeriodicWorkPolicy.REPLACE 策略，每次调用都会重置计时器
     * - 这确保了修改间隔时间后，会从修改生效的那一刻重新计时
     */
    fun setupAiBackgroundWorker() {
        val workManager = WorkManager.getInstance(this)
        if (settingsRepository.isBackgroundActivityEnabled()) {
            val interval = settingsRepository.getBackgroundActivityInterval().toLong()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 使用 setInitialDelay 确保首次执行等待一个完整的间隔
            val periodicWorkRequest = PeriodicWorkRequestBuilder<AiBackgroundWorker>(interval, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .setInitialDelay(interval, TimeUnit.SECONDS) // 首次延迟执行
                .build()

            // REPLACE 策略会取消旧任务并创建新任务，重置计时器
            workManager.enqueueUniquePeriodicWork(
                AiBackgroundWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE, // 重置计时器
                periodicWorkRequest
            )
            Log.d("AiBackgroundWorker", "后台生图任务已配置，间隔: ${interval}秒，首次执行将在${interval}秒后")
        } else {
            workManager.cancelUniqueWork(AiBackgroundWorker.WORK_NAME)
            Log.d("AiBackgroundWorker", "后台生图任务已取消")
        }
    }
    // AlbumDatabaseProvider 接口实现
    override fun getAlbumDao(): AlbumDao = albumDatabaseProvider.getAlbumDao()
    override fun getPhotoDao(): PhotoDao = albumDatabaseProvider.getPhotoDao()
    
    // AlbumNavigator 接口实现
    override fun getContainerId(): Int = albumNavigator.getContainerId()
    override fun navigateToPhotoViewer(fragmentManager: FragmentManager, photos: ArrayList<Photo>, position: Int) {
        albumNavigator.navigateToPhotoViewer(fragmentManager, photos, position)
    }
    override fun navigateToPhotoGrid(fragmentManager: FragmentManager, albumId: Long, albumName: String, isFavorites: Boolean) {
        albumNavigator.navigateToPhotoGrid(fragmentManager, albumId, albumName, isFavorites)
    }
    override fun navigateBack() = albumNavigator.navigateBack()
    
    // ImageSelectionCallback 接口实现
    override fun onImageSelected(requestKey: String, imageUri: String, cropOptions: CropImageOptions?) {
        EventBus.postImageSelectedEvent(requestKey, imageUri, cropOptions)
    }
}