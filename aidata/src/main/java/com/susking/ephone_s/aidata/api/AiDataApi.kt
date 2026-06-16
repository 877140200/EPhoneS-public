package com.susking.ephone_s.aidata.api

import android.content.Context
import com.google.gson.Gson
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.AlipayDatabase
import com.susking.ephone_s.aidata.data.local.CPhoneDatabase
import com.susking.ephone_s.aidata.data.local.ShoppingDatabase
import com.susking.ephone_s.aidata.data.mapper.CPhoneDataMapper
import com.susking.ephone_s.aidata.data.repository.ActionRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.AlipayRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.CPhoneRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.CallStateRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ChatRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ContactSemanticStateRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.FavoriteMessageRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.FeedRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.LongTermMemoryRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.PersonProfileRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.PromptContextRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ScheduleRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.SettingsRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.StickerRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.WorldBookEntryRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.WorldBookRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.WorldSettingRepositoryImpl
import com.susking.ephone_s.aidata.data.service.MemoryRecallDebugServiceImpl
import com.susking.ephone_s.aidata.data.service.MemoryRecallServiceImpl
import com.susking.ephone_s.aidata.data.service.OnlineEmbeddingServiceImpl
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.provider.AlbumDataProvider
import com.susking.ephone_s.aidata.domain.provider.DesktopDataProvider
import com.susking.ephone_s.aidata.domain.repository.ActionRepository
import com.susking.ephone_s.aidata.domain.repository.CPhoneRepository
import com.susking.ephone_s.aidata.domain.repository.CallStateRepository
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.FavoriteMessageRepository
import com.susking.ephone_s.aidata.domain.repository.ContactSemanticStateRepository
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.repository.LongTermMemoryRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.PromptContextRepository
import com.susking.ephone_s.aidata.domain.repository.ScheduleRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.repository.StickerRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookEntryRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import com.susking.ephone_s.aidata.domain.repository.WorldSettingRepository
import com.susking.ephone_s.aidata.domain.use_case.GenerateCPhoneDataUseCase
import com.susking.ephone_s.aidata.domain.service.MemoryRecallService
import com.susking.ephone_s.aidata.domain.use_case.BuildSchedulePromptSummaryUseCase
import com.susking.ephone_s.aidata.domain.use_case.SaveImageFromBase64UseCase
import com.susking.ephone_s.aidata.prompt.AiPromptService
import com.susking.ephone_s.aidata.prompt.AiPromptServiceImpl
import com.susking.ephone_s.aidata.prompt.CPhonePromptBuilder

/**
 * aidata 模块的对外 API
 * 其他模块（app, brain, QQ）通过此 API 访问 aidata
 */
object AiDataApi {
    
    private lateinit var appContext: Context
    private lateinit var database: AiDataDatabase
    private lateinit var alipayDatabase: AlipayDatabase
    private lateinit var cphoneDatabase: CPhoneDatabase
    private lateinit var shoppingDatabase: ShoppingDatabase
    private lateinit var cphoneDataMapper: CPhoneDataMapper
    private lateinit var personProfileRepository: PersonProfileRepository
    private lateinit var favoriteMessageRepository: FavoriteMessageRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var worldSettingRepository: WorldSettingRepository
    private lateinit var longTermMemoryRepository: LongTermMemoryRepository
    private lateinit var stickerRepository: StickerRepository
    private lateinit var feedRepository: FeedRepository
    private lateinit var promptContextRepository: PromptContextRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var worldBookRepository: WorldBookRepository
    private lateinit var worldBookEntryRepository: WorldBookEntryRepository
    private lateinit var actionRepository: ActionRepository
    private lateinit var callStateRepository: CallStateRepository
    private lateinit var cphoneRepository: CPhoneRepository
    private lateinit var aiPromptService: AiPromptService
    // 注意：不再作为静态字段持有，改为动态创建以避免内存泄漏
    private lateinit var saveImageUseCase: SaveImageFromBase64UseCase
    private lateinit var aiRequestService: AiRequestService
    private var memoryRecallService: MemoryRecallService? = null
    private lateinit var dataImportExportRepository: com.susking.ephone_s.aidata.domain.repository.DataImportExportRepository
    private lateinit var heartbeatRepository: com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
    private lateinit var jottingRepository: com.susking.ephone_s.aidata.domain.repository.JottingRepository
    private lateinit var contactSemanticStateRepository: ContactSemanticStateRepository
    private lateinit var alipayRepository: AlipayRepository
    private lateinit var memoriesRepository: com.susking.ephone_s.aidata.domain.repository.MemoriesRepository
    private lateinit var shoppingAuthorizedAccountRepository: com.susking.ephone_s.aidata.domain.repository.ShoppingAuthorizedAccountRepository
    private lateinit var shoppingProductRepository: com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
    private lateinit var backpackRepository: com.susking.ephone_s.aidata.domain.repository.BackpackRepository
    private lateinit var videoCallHistoryRepository: com.susking.ephone_s.aidata.domain.repository.VideoCallHistoryRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var buildSchedulePromptSummaryUseCase: BuildSchedulePromptSummaryUseCase
    // 天气仓库，注入在线提示词供 AI 感知用户当前天气
    private lateinit var weatherRepository: com.susking.ephone_s.aidata.domain.repository.WeatherRepository

    // 提示词储存器仓库（酒馆「锦囊」），供 tavern 模块读写提示词
    private lateinit var promptStorageRepository: com.susking.ephone_s.aidata.domain.repository.PromptStorageRepository

    // Provider - 由app模块注入
    private var albumDataProvider: AlbumDataProvider? = null
    private var desktopDataProvider: DesktopDataProvider? = null

    /**
     * 初始化 aidata 模块
     * 必须在 Application 的 onCreate 中调用
     *
     * 简化后的初始化方法：
     * - 不再需要外部传入 lambda 函数
     * - 所有数据都由内部 Repository 管理
     * - 模块完全独立,边界清晰
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        database = AiDataDatabase.getDatabase(context)
        alipayDatabase = AlipayDatabase.getDatabase(context)
        cphoneDatabase = CPhoneDatabase.getDatabase(context)
        shoppingDatabase = ShoppingDatabase.getDatabase(context)
        cphoneDataMapper = CPhoneDataMapper(Gson())
        
        // 初始化基础 Repository
        personProfileRepository = PersonProfileRepositoryImpl(context, database)
        settingsRepository = SettingsRepositoryImpl(context)
        favoriteMessageRepository = FavoriteMessageRepositoryImpl(database)
        // 注意：AiDataApi 中不使用 ActiveContactTracker，传入 null。
        // ActiveContactTracker 只在通过 Hilt 依赖注入的模块中使用。
        chatRepository = ChatRepositoryImpl(
            database,
            context,
            favoriteMessageRepository,
            personProfileRepository,
            null
        )
        worldSettingRepository = WorldSettingRepositoryImpl(database)
        longTermMemoryRepository = LongTermMemoryRepositoryImpl(database.longTermMemoryDao())
        stickerRepository = StickerRepositoryImpl(database.stickerDao())
        
        // 初始化SaveImageFromBase64UseCase（feedRepository依赖它）
        saveImageUseCase = SaveImageFromBase64UseCase(context)
        
        feedRepository = FeedRepositoryImpl(database.feedDao(), saveImageUseCase)
        worldBookRepository = WorldBookRepositoryImpl(database.worldBookDao())
        worldBookEntryRepository = WorldBookEntryRepositoryImpl(database.worldBookEntryDao())
        callStateRepository = CallStateRepositoryImpl()
        cphoneRepository = CPhoneRepositoryImpl(cphoneDatabase.cphoneDao(), cphoneDataMapper)
        heartbeatRepository = com.susking.ephone_s.aidata.data.repository.HeartbeatRepositoryImpl(database.heartbeatDao())
        jottingRepository = com.susking.ephone_s.aidata.data.repository.JottingRepositoryImpl(database.jottingDao())
        contactSemanticStateRepository = ContactSemanticStateRepositoryImpl(database.contactSemanticStateDao())
        videoCallHistoryRepository = com.susking.ephone_s.aidata.data.repository.VideoCallHistoryRepositoryImpl(database.videoCallHistoryDao())
        scheduleRepository = ScheduleRepositoryImpl(database.scheduleDao())
        buildSchedulePromptSummaryUseCase = BuildSchedulePromptSummaryUseCase(scheduleRepository)
        // 初始化天气仓库（DataStore 缓存，依赖 core 的 weatherDataStore）
        weatherRepository = com.susking.ephone_s.aidata.data.repository.WeatherRepositoryImpl(context, Gson())

        // 初始化提示词储存器仓库（酒馆「锦囊」）
        promptStorageRepository = com.susking.ephone_s.aidata.data.repository.PromptStorageRepositoryImpl(
            context,
            database.promptStorageDao()
        )
        
        // 初始化支付宝Repository（新系统）
        alipayRepository = AlipayRepositoryImpl(context)
        // 使用适配器将AlipayRepository适配到WalletRepository接口

        memoriesRepository = com.susking.ephone_s.aidata.data.repository.MemoriesRepositoryImpl(database.appointmentDao(), database.generalMemoryDao(), favoriteMessageRepository)
        dataImportExportRepository = com.susking.ephone_s.aidata.data.repository.DataImportExportRepositoryImpl(context, database, alipayDatabase, personProfileRepository)
        
        // 初始化购物相关Repository
        val shoppingDatabase = com.susking.ephone_s.aidata.data.local.ShoppingDatabase.getDatabase(context)
        shoppingProductRepository = com.susking.ephone_s.aidata.data.repository.ShoppingProductRepositoryImpl(shoppingDatabase.shoppingProductDao())
        shoppingAuthorizedAccountRepository = com.susking.ephone_s.aidata.data.repository.ShoppingAuthorizedAccountRepositoryImpl(
            shoppingDatabase.shoppingAuthorizedAccountDao(),
            shoppingProductRepository
        )
        
        // 初始化背包Repository
        backpackRepository = com.susking.ephone_s.aidata.data.repository.BackpackRepositoryImpl(database.backpackItemDao())
        
        // 初始化 ActionRepository（依赖其他 Repository）
        actionRepository = ActionRepositoryImpl(
            personProfileRepository = personProfileRepository,
            chatRepository = chatRepository,
            settingsRepository = settingsRepository,
            shoppingAuthorizedAccountRepository = shoppingAuthorizedAccountRepository
        )
        
        // 初始化 PromptContextRepository（依赖其他 Repository）
        promptContextRepository = PromptContextRepositoryImpl(
            database = database,
            personProfileRepository = personProfileRepository,
            settingsRepository = settingsRepository
        )
        
        // 初始化 AiPromptService（依赖多个 Repository）
        aiPromptService = AiPromptServiceImpl(
            personProfileRepository = personProfileRepository,
            chatRepository = chatRepository,
            worldSettingRepository = worldSettingRepository,
            stickerRepository = stickerRepository,
            feedRepository = feedRepository,
            settingsRepository = settingsRepository,
            worldBookRepository = worldBookRepository,
            worldBookEntryRepository = worldBookEntryRepository,
            cphonePromptBuilder = CPhonePromptBuilder(),
            memoriesRepository = memoriesRepository,
            contactSemanticStateRepository = contactSemanticStateRepository,
            buildSchedulePromptSummaryUseCase = buildSchedulePromptSummaryUseCase,
            weatherRepository = weatherRepository
        )

        // 注意：不再在初始化时创建包含Context的UseCase，改为动态创建以避免内存泄漏
    }
    
    /**
     * 注册相册和桌面Provider
     * 在app模块初始化Hilt依赖注入后调用
     */
    fun registerProviders(
        albumProvider: AlbumDataProvider?,
        desktopProvider: DesktopDataProvider?
    ) {
        this.albumDataProvider = albumProvider
        this.desktopDataProvider = desktopProvider
        
        // 注意：不再预创建UseCase，改为动态创建以避免内存泄漏
    }
    
    /**
     * 注册brain模块的AiRequestService
     * 必须在brain模块初始化后调用
     */
    fun registerBrainService(context: Context, aiRequestService: AiRequestService) {
        this.aiRequestService = aiRequestService
        memoryRecallService = createMemoryRecallService(aiRequestService)
        rebuildPromptServices(memoryRecallService)
        // 注意：不再在这里创建UseCase，改为动态创建以避免内存泄漏
    }

    private fun createMemoryRecallService(aiRequestService: AiRequestService): MemoryRecallService {
        val debugService = MemoryRecallDebugServiceImpl(
            database.memoryRecallDebugDao(),
            database.longTermMemoryDao(),
            database.memoryEventDao(),
            database.memorySummaryDao()
        )
        return MemoryRecallServiceImpl(
            OnlineEmbeddingServiceImpl(aiRequestService),
            database.memoryEmbeddingDao(),
            database.longTermMemoryDao(),
            database.memoryEventDao(),
            database.memorySummaryDao(),
            database.memoryGraphDao(),
            debugService
        )
    }

    private fun rebuildPromptServices(memoryRecallService: MemoryRecallService?): Unit {
        promptContextRepository = PromptContextRepositoryImpl(
            database = database,
            personProfileRepository = personProfileRepository,
            settingsRepository = settingsRepository,
            memoryRecallService = memoryRecallService
        )
        aiPromptService = AiPromptServiceImpl(
            personProfileRepository = personProfileRepository,
            chatRepository = chatRepository,
            worldSettingRepository = worldSettingRepository,
            stickerRepository = stickerRepository,
            feedRepository = feedRepository,
            settingsRepository = settingsRepository,
            worldBookRepository = worldBookRepository,
            worldBookEntryRepository = worldBookEntryRepository,
            cphonePromptBuilder = CPhonePromptBuilder(),
            memoriesRepository = memoriesRepository,
            memoryRecallService = memoryRecallService,
            contactSemanticStateRepository = contactSemanticStateRepository,
            buildSchedulePromptSummaryUseCase = buildSchedulePromptSummaryUseCase,
            weatherRepository = weatherRepository
        )
    }

    /**
     * 获取角色设定 Repository
     */
    fun getPersonProfileRepository(): PersonProfileRepository {
        if (!::personProfileRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return personProfileRepository
    }
    
    /**
     * 获取提示词上下文 Repository
     */
    fun getPromptContextRepository(): PromptContextRepository {
        if (!::promptContextRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return promptContextRepository
    }
    
    /**
     * 获取聊天 Repository
     */
    fun getChatRepository(): ChatRepository {
        if (!::chatRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return chatRepository
    }
    
    /**
     * 获取世界观 Repository
     */
    fun getWorldSettingRepository(): WorldSettingRepository {
        if (!::worldSettingRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return worldSettingRepository
    }
    
    /**
     * 获取长期记忆 Repository
     */
    fun getLongTermMemoryRepository(): LongTermMemoryRepository {
        if (!::longTermMemoryRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return longTermMemoryRepository
    }
    
    /**
     * 获取表情包 Repository
     */
    fun getStickerRepository(): StickerRepository {
        if (!::stickerRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return stickerRepository
    }
    
    /**
     * 获取动态 Repository
     */
    fun getFeedRepository(): FeedRepository {
        if (!::feedRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return feedRepository
    }
    
    /**
     * 获取设置 Repository
     */
    fun getSettingsRepository(): SettingsRepository {
        if (!::settingsRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return settingsRepository
    }

    /**
     * 获取提示词储存器 Repository（酒馆「锦囊」）
     * tavern 模块通过此方法读写句子/词语提示词与数量范围配置。
     */
    fun getPromptStorageRepository(): com.susking.ephone_s.aidata.domain.repository.PromptStorageRepository {
        if (!::promptStorageRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return promptStorageRepository
    }
    
    /**
     * 获取世界书 Repository
     */
    fun getWorldBookRepository(): WorldBookRepository {
        if (!::worldBookRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return worldBookRepository
    }
    
    /**
     * 获取世界书条目 Repository
     */
    fun getWorldBookEntryRepository(): WorldBookEntryRepository {
        if (!::worldBookEntryRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return worldBookEntryRepository
    }
    
    /**
     * 获取 Action Repository
     * brain 模块的 ActionExecutor 通过此方法获取操作数据
     */
    fun getActionRepository(): ActionRepository {
        if (!::actionRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return actionRepository
    }
    
    /**
     * 获取通话状态 Repository
     * 管理通话发起方信息
     */
    fun getCallStateRepository(): CallStateRepository {
        if (!::callStateRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return callStateRepository
    }

    /**
     * 获取视频通话历史仓库。
     * 后台 Worker 在 AI 独立行动产生来电请求、但前台没有通话界面接听时,
     * 通过此仓库写入一条"未接来电"记录,避免来电被静默丢弃。
     */
    fun getVideoCallHistoryRepository(): com.susking.ephone_s.aidata.domain.repository.VideoCallHistoryRepository {
        if (!::videoCallHistoryRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return videoCallHistoryRepository
    }
    
    /**
     * 获取记忆召回服务。
     * Brain 服务注册前可能为空，注册后用于调试面板读取最近召回记录。
     */
    fun getMemoryRecallServiceOrNull(): MemoryRecallService? {
        return memoryRecallService
    }

    /**
     * 获取 AI 提示词服务
     * brain 模块通过此方法获取提示词服务来构建 AI 请求
     */
    fun getAiPromptService(): AiPromptService {
        if (!::aiPromptService.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return aiPromptService
    }
    
    /**
     * 获取CPhone Repository
     * 用于管理查手机数据
     */
    fun getCPhoneRepository(): CPhoneRepository {
        if (!::cphoneRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return cphoneRepository
    }
    
    /**
     * 获取GenerateCPhoneDataUseCase
     * 用于生成查手机数据
     * 注意：每次调用都会创建新实例，避免内存泄漏
     */
    fun getGenerateCPhoneDataUseCase(): GenerateCPhoneDataUseCase {
        if (!::aiRequestService.isInitialized) {
            throw IllegalStateException("AiRequestService not initialized. Call registerBrainService() first.")
        }
        return GenerateCPhoneDataUseCase(
            context = appContext,
            aiPromptService = aiPromptService,
            cphoneRepository = cphoneRepository,
            aiRequestService = aiRequestService,
            personProfileRepository = personProfileRepository,
            saveImageUseCase = saveImageUseCase,
            memorySummaryDao = database.memorySummaryDao()
        )
    }
    
    /**
     * 获取SaveImageFromBase64UseCase
     * 用于将Base64图片保存为本地文件
     */
    fun getSaveImageUseCase(): SaveImageFromBase64UseCase {
        if (!::saveImageUseCase.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return saveImageUseCase
    }
    
    /**
     * 获取AiRequestService
     * 用于Worker中生成图片
     */
    fun getAiRequestService(): AiRequestService {
        if (!::aiRequestService.isInitialized) {
            throw IllegalStateException("AiRequestService not initialized. Call registerBrainService() first.")
        }
        return aiRequestService
    }
    
    /**
     * 获取DataImportExportRepository
     * 用于数据导入导出操作
     */
    fun getDataImportExportRepository(): com.susking.ephone_s.aidata.domain.repository.DataImportExportRepository {
        if (!::dataImportExportRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return dataImportExportRepository
    }
    
    /**
     * 获取联系人语义状态 Repository
     * 用于维护和展示每个联系人的当前语义状态。
     */
    fun getContactSemanticStateRepository(): ContactSemanticStateRepository {
        if (!::contactSemanticStateRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return contactSemanticStateRepository
    }
    
    /**
     * 获取ExportDataUseCase
     * 用于导出单个聊天记录
     * 注意：每次调用都会创建新实例，避免内存泄漏
     */
    fun getExportDataUseCase(): com.susking.ephone_s.aidata.domain.use_case.ExportDataUseCase {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return com.susking.ephone_s.aidata.domain.use_case.ExportDataUseCase(
            context = appContext,
            chatRepository = chatRepository,
            personProfileRepository = personProfileRepository,
            longTermMemoryDao = database.longTermMemoryDao(),
            heartbeatRepository = heartbeatRepository,
            jottingRepository = jottingRepository,
            feedRepository = feedRepository,
            alipayRepository = alipayRepository,
            memoriesRepository = memoriesRepository,
            dataImportExportRepository = dataImportExportRepository,
            stickerRepository = stickerRepository,
            videoCallHistoryRepository = videoCallHistoryRepository,
            contactSemanticStateRepository = contactSemanticStateRepository,
            scheduleRepository = scheduleRepository,
            database = database
        )
    }
    
    /**
     * 获取ImportDataUseCase
     * 用于导入单个聊天记录
     * 注意：每次调用都会创建新实例，避免内存泄漏
     */
    fun getImportDataUseCase(): com.susking.ephone_s.aidata.domain.use_case.ImportDataUseCase {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return com.susking.ephone_s.aidata.domain.use_case.ImportDataUseCase(
            context = appContext,
            dataImportExportRepository = dataImportExportRepository,
            personProfileRepository = personProfileRepository
        )
    }
    
    /**
     * 获取ExportCompleteAppDataUseCase
     * 用于导出完整应用数据
     * 注意：每次调用都会创建新实例，避免内存泄漏
     */
    fun getExportCompleteAppDataUseCase(): com.susking.ephone_s.aidata.domain.use_case.ExportCompleteAppDataUseCase {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        // 每次动态创建ExportDataUseCase
        val exportDataUseCase = com.susking.ephone_s.aidata.domain.use_case.ExportDataUseCase(
            context = appContext,
            chatRepository = chatRepository,
            personProfileRepository = personProfileRepository,
            longTermMemoryDao = database.longTermMemoryDao(),
            heartbeatRepository = heartbeatRepository,
            jottingRepository = jottingRepository,
            feedRepository = feedRepository,
            alipayRepository = alipayRepository,
            memoriesRepository = memoriesRepository,
            dataImportExportRepository = dataImportExportRepository,
            stickerRepository = stickerRepository,
            videoCallHistoryRepository = videoCallHistoryRepository,
            contactSemanticStateRepository = contactSemanticStateRepository,
            scheduleRepository = scheduleRepository,
            database = database
        )

        return com.susking.ephone_s.aidata.domain.use_case.ExportCompleteAppDataUseCase(
            context = appContext,
            exportDataUseCase = exportDataUseCase,
            personProfileRepository = personProfileRepository,
            dataImportExportRepository = dataImportExportRepository,
            worldBookRepository = worldBookRepository,
            worldBookEntryRepository = worldBookEntryRepository,
            shoppingDatabase = shoppingDatabase,
            cphoneRepository = cphoneRepository,
            settingsRepository = settingsRepository,
            albumDataProvider = albumDataProvider,
            desktopDataProvider = desktopDataProvider
        )
    }
    
    /**
     * 获取ImportCompleteAppDataUseCase
     * 用于导入完整应用数据
     * 注意：每次调用都会创建新实例，避免内存泄漏
     */
    fun getImportCompleteAppDataUseCase(): com.susking.ephone_s.aidata.domain.use_case.ImportCompleteAppDataUseCase {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return com.susking.ephone_s.aidata.domain.use_case.ImportCompleteAppDataUseCase(
            context = appContext,
            dataImportExportRepository = dataImportExportRepository,
            worldBookRepository = worldBookRepository,
            worldBookEntryRepository = worldBookEntryRepository,
            shoppingDatabase = shoppingDatabase,
            cphoneRepository = cphoneRepository,
            settingsRepository = settingsRepository,
            albumDataProvider = albumDataProvider,
            desktopDataProvider = desktopDataProvider
        )
    }
    
    /**
     * 获取AlipayRepository
     * 新的支付宝数据仓库
     */
    fun getAlipayRepository(): AlipayRepository {
        if (!::alipayRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return alipayRepository
    }
    
    /**
     * 获取WalletRepository
     * 用于钱包功能的数据访问
     * 注意：现在使用AlipayWalletAdapter,底层使用AlipayRepository
     */
    fun getWalletRepository(): AlipayRepository {
        if (!::alipayRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return alipayRepository
    }
    
    /**
     * 获取BackpackRepository
     * 用于背包物品管理
     */
    fun getBackpackRepository(): com.susking.ephone_s.aidata.domain.repository.BackpackRepository {
        if (!::backpackRepository.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return backpackRepository
    }
    
    /**
     * 获取Application Context
     * 用于需要Context的操作（如AI请求）
     */
    fun getContext(): Context {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("AiDataApi not initialized. Call initialize() first.")
        }
        return appContext
    }
    
    /**
     * 重置图片生成队列计数（调试用）
     * 用于清除队列中积累的"幽灵任务"
     */
    fun resetImageGenerationQueue(context: Context) {
        com.susking.ephone_s.aidata.worker.ImageGenerationManager.resetQueueSize(context)
    }
}