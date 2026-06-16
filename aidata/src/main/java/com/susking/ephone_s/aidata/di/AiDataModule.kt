package com.susking.ephone_s.aidata.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.gson.Gson
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.AlbumDatabase
import com.susking.ephone_s.aidata.data.local.AlipayDatabase
import com.susking.ephone_s.aidata.data.local.CPhoneDatabase
import com.susking.ephone_s.aidata.data.local.ShoppingDatabase
import com.susking.ephone_s.aidata.data.local.WorldBookDatabase
import com.susking.ephone_s.aidata.data.mapper.CPhoneDataMapper
import com.susking.ephone_s.aidata.data.repository.ActionRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.AlipayRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.BackpackRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.CPhoneRepositoryImpl
import com.susking.ephone_s.aidata.domain.manager.BackpackEventManager
import com.susking.ephone_s.aidata.data.repository.ChatRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ContactSemanticStateRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.DataImportExportRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.DataMigrationRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.FavoriteMessageRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.FeedRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.HeartbeatRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.JottingRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.LongTermMemoryRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.MemoriesRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.NotificationRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.PersonProfileRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.PromptContextRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ScheduleRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.SettingsRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.StickerRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.VideoCallHistoryRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.WeatherRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.WorldBookEntryRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.WorldBookRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.WorldSettingRepositoryImpl
import com.susking.ephone_s.aidata.data.service.MusicServiceImpl
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.provider.AlbumDataProvider
import com.susking.ephone_s.aidata.domain.provider.DesktopDataProvider
import com.susking.ephone_s.aidata.domain.repository.ActionRepository
import com.susking.ephone_s.aidata.domain.repository.BackpackRepository
import com.susking.ephone_s.aidata.domain.repository.CPhoneRepository
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.ContactSemanticStateRepository
import com.susking.ephone_s.aidata.domain.repository.DataImportExportRepository
import com.susking.ephone_s.aidata.domain.repository.DataMigrationRepository
import com.susking.ephone_s.aidata.domain.repository.FavoriteMessageRepository
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.domain.repository.LongTermMemoryRepository
import com.susking.ephone_s.aidata.domain.repository.MemoriesRepository
import com.susking.ephone_s.aidata.domain.repository.NotificationRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.PromptContextRepository
import com.susking.ephone_s.aidata.domain.repository.ScheduleRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.repository.StickerRepository
import com.susking.ephone_s.aidata.domain.repository.VideoCallHistoryRepository
import com.susking.ephone_s.aidata.domain.repository.WeatherRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookEntryRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import com.susking.ephone_s.aidata.domain.repository.WorldSettingRepository
import com.susking.ephone_s.aidata.domain.service.ImageGenerationService
import com.susking.ephone_s.aidata.domain.service.MemoryFactGraphExtractionService
import com.susking.ephone_s.aidata.domain.service.MemoryRecallService
import com.susking.ephone_s.aidata.domain.service.MusicService
import com.susking.ephone_s.aidata.domain.use_case.BuildSchedulePromptSummaryUseCase
import com.susking.ephone_s.aidata.domain.use_case.ExportDataUseCase
import com.susking.ephone_s.aidata.domain.use_case.GenerateCPhoneDataUseCase
import com.susking.ephone_s.aidata.domain.use_case.GenerateImageFromPromptUseCase
import com.susking.ephone_s.aidata.domain.use_case.ImportDataUseCase
import com.susking.ephone_s.aidata.domain.use_case.RewriteImagePromptUseCase
import com.susking.ephone_s.aidata.domain.use_case.SaveImageFromBase64UseCase
import com.susking.ephone_s.aidata.domain.use_case.SummarizeCallTranscriptUseCase
import com.susking.ephone_s.aidata.prompt.SummarizeChatHistoryPromptBuilder
import com.susking.ephone_s.aidata.domain.use_case.TriggerAutoSummarizeUseCase
import com.susking.ephone_s.aidata.prompt.AiPromptService
import com.susking.ephone_s.aidata.prompt.AiPromptServiceImpl
import com.susking.ephone_s.aidata.prompt.CPhonePromptBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * aidata 模块的依赖注入配置
 * 提供所有 Repository 和 Use Case 的实例
 */
@Module
@InstallIn(SingletonComponent::class)
object AiDataModule {

    // ==================== Database ====================
    
    @Provides
    @Singleton
    fun provideAiDataDatabase(
        @ApplicationContext context: Context
    ): AiDataDatabase {
        return AiDataDatabase.getDatabase(context)
    }
    
    /**
     * 提供ScheduledGreetingDao
     * 用于定时祝福功能
     */
    @Provides
    @Singleton
    fun provideScheduledGreetingDao(
        database: AiDataDatabase
    ): com.susking.ephone_s.aidata.data.local.dao.ScheduledGreetingDao {
        return database.scheduledGreetingDao()
    }
    
    /**
     * 提供课程表与校园动态 DAO
     */
    @Provides
    @Singleton
    fun provideScheduleDao(
        database: AiDataDatabase
    ): com.susking.ephone_s.aidata.data.local.dao.ScheduleDao {
        return database.scheduleDao()
    }
    
    /**
     * 提供ChatMessageDao
     * 用于聊天消息操作
     */
    @Provides
    @Singleton
    fun provideChatMessageDao(
        database: AiDataDatabase
    ): com.susking.ephone_s.aidata.data.local.dao.ChatMessageDao {
        return database.chatMessageDao()
    }
    
    /**
     * 提供WorldBookDatabase实例
     * 独立的世界书数据库,从AiDataDatabase拆分出来
     */
    @Provides
    @Singleton
    fun provideWorldBookDatabase(
        @ApplicationContext context: Context
    ): WorldBookDatabase {
        return WorldBookDatabase.getDatabase(context)
    }

    // ==================== 健康数据（AI 健康关怀）====================

    /**
     * 提供每日健康数据 DAO。
     */
    @Provides
    @Singleton
    fun provideHealthDailyRecordDao(
        database: AiDataDatabase
    ): com.susking.ephone_s.aidata.data.local.dao.HealthDailyRecordDao {
        return database.healthDailyRecordDao()
    }

    /**
     * 提供 Health Connect 读取器（本机 IPC，不经 brain）。
     */
    @Provides
    @Singleton
    fun provideHealthConnectReader(
        @ApplicationContext context: Context
    ): com.susking.ephone_s.aidata.data.health.HealthConnectReader {
        return com.susking.ephone_s.aidata.data.health.HealthConnectReader(context)
    }

    /**
     * 提供健康数据仓库（组合 Health Connect 读取与本地落库）。
     */
    @Provides
    @Singleton
    fun provideHealthRepository(
        reader: com.susking.ephone_s.aidata.data.health.HealthConnectReader,
        dao: com.susking.ephone_s.aidata.data.local.dao.HealthDailyRecordDao
    ): com.susking.ephone_s.aidata.domain.repository.HealthRepository {
        return com.susking.ephone_s.aidata.data.repository.HealthRepositoryImpl(reader, dao)
    }
    
    /**
     * 提供CPhoneDatabase实例
     * 独立的CPhone数据库,从AiDataDatabase拆分出来
     */
    @Provides
    @Singleton
    fun provideCPhoneDatabase(
        @ApplicationContext context: Context
    ): CPhoneDatabase {
        return CPhoneDatabase.getDatabase(context)
    }
    
    /**
     * 提供ShoppingDatabase实例
     * 独立的商城数据库,用于商城功能
     */
    @Provides
    @Singleton
    fun provideShoppingDatabase(
        @ApplicationContext context: Context
    ): ShoppingDatabase {
        return ShoppingDatabase.getDatabase(context)
    }
    
    /**
     * 提供AlbumDatabase实例
     * 独立的相册数据库
     */
    @Provides
    @Singleton
    fun provideAlbumDatabase(
        @ApplicationContext context: Context
    ): AlbumDatabase {
        return AlbumDatabase.getDatabase(context)
    }
    
    /**
     * 提供AlipayDatabase实例
     * 支付宝数据库,包含钱包、账单和工作状态
     */
    @Provides
    @Singleton
    fun provideAlipayDatabase(
        @ApplicationContext context: Context
    ): AlipayDatabase {
        return AlipayDatabase.getDatabase(context)
    }
    
    /**
     * 提供CPhoneDataMapper
     * 用于CPhone数据的JSON序列化和反序列化
     */
    @Provides
    @Singleton
    fun provideCPhoneDataMapper(
        gson: Gson
    ): CPhoneDataMapper {
        return CPhoneDataMapper(gson)
    }

    // ==================== Repositories ====================
    
    @Provides
    @Singleton
    fun provideChatRepository(
        database: AiDataDatabase,
        @ApplicationContext context: Context,
        favoriteMessageRepository: FavoriteMessageRepository,
        personProfileRepository: PersonProfileRepository,
        activeContactTracker: com.susking.ephone_s.aidata.domain.tracker.ActiveContactTracker?,
        scheduledGreetingService: com.susking.ephone_s.aidata.service.ScheduledGreetingService
    ): ChatRepository {
        return ChatRepositoryImpl(
            database,
            context,
            favoriteMessageRepository,
            personProfileRepository,
            activeContactTracker,
            scheduledGreetingService
        )
    }

    @Provides
    @Singleton
    fun providePersonProfileRepository(
        @ApplicationContext context: Context,
        database: AiDataDatabase
    ): PersonProfileRepository {
        return PersonProfileRepositoryImpl(context, database)
    }

    @Provides
    @Singleton
    fun provideDataImportExportRepository(
        @ApplicationContext context: Context,
        database: AiDataDatabase,
        alipayDatabase: AlipayDatabase,
        personProfileRepository: PersonProfileRepository
    ): DataImportExportRepository {
        return DataImportExportRepositoryImpl(context, database, alipayDatabase, personProfileRepository)
    }

    @Provides
    @Singleton
    fun provideLongTermMemoryRepository(
        database: AiDataDatabase
    ): LongTermMemoryRepository {
        // 旧原子事件仓库仅用于兼容仍然读取纪念记录的旧入口。
        // 新增、编辑、删除、向量化等功能入口已在调用侧禁用。
        return LongTermMemoryRepositoryImpl(database.longTermMemoryDao())
    }

    @Provides
    @Singleton
    fun provideDataMigrationRepository(
        @ApplicationContext context: Context,
        database: AiDataDatabase
    ): DataMigrationRepository {
        return DataMigrationRepositoryImpl(context, database)
    }

    @Provides
    @Singleton
    fun provideHeartbeatRepository(
        database: AiDataDatabase
    ): HeartbeatRepository {
        return HeartbeatRepositoryImpl(database.heartbeatDao())
    }

    @Provides
    @Singleton
    fun provideJottingRepository(
        database: AiDataDatabase
    ): JottingRepository {
        return JottingRepositoryImpl(database.jottingDao())
    }

    @Provides
    @Singleton
    fun provideContactSemanticStateRepository(
        database: AiDataDatabase
    ): ContactSemanticStateRepository {
        return ContactSemanticStateRepositoryImpl(database.contactSemanticStateDao())
    }

    @Provides
    @Singleton
    fun provideFeedRepository(
        database: AiDataDatabase,
        saveImageFromBase64UseCase: SaveImageFromBase64UseCase
    ): FeedRepository {
        return FeedRepositoryImpl(database.feedDao(), saveImageFromBase64UseCase)
    }

    /**
     * 提供AlipayRepository
     * 新的支付宝数据仓库,取代旧的WalletRepository
     */
    @Provides
    @Singleton
    fun provideAlipayRepository(
        @ApplicationContext context: Context
    ): AlipayRepository {
        return AlipayRepositoryImpl(context)
    }
    
    @Provides
    @Singleton
    fun provideMemoriesRepository(
        database: AiDataDatabase,
        favoriteMessageRepository: FavoriteMessageRepository
    ): MemoriesRepository {
        return MemoriesRepositoryImpl(
            database.appointmentDao(),
            database.generalMemoryDao(),
            favoriteMessageRepository
        )
    }

    @Provides
    @Singleton
    fun provideScheduleRepository(
        database: AiDataDatabase
    ): ScheduleRepository {
        return ScheduleRepositoryImpl(database.scheduleDao())
    }

    @Provides
    @Singleton
    fun provideBuildSchedulePromptSummaryUseCase(
        scheduleRepository: ScheduleRepository
    ): BuildSchedulePromptSummaryUseCase {
        return BuildSchedulePromptSummaryUseCase(scheduleRepository)
    }

    @Provides
    @Singleton
    fun provideNotificationRepository(
        database: AiDataDatabase
    ): NotificationRepository {
        return NotificationRepositoryImpl(database.notificationDao())
    }

    @Provides
    @Singleton
    fun provideStickerRepository(
        database: AiDataDatabase
    ): StickerRepository {
        return StickerRepositoryImpl(database.stickerDao())
    }

    @Provides
    @Singleton
    fun provideFavoriteMessageRepository(
        database: AiDataDatabase
    ): FavoriteMessageRepository {
        return FavoriteMessageRepositoryImpl(database)
    }

    /**
     * 提供WorldBookRepository
     * 注意：现在使用WorldBookDatabase而不是AiDataDatabase
     */
    @Provides
    @Singleton
    fun provideWorldBookRepository(
        worldBookDatabase: WorldBookDatabase
    ): WorldBookRepository {
        return WorldBookRepositoryImpl(worldBookDatabase.worldBookDao())
    }

    /**
     * 提供WorldBookEntryRepository
     * 注意：现在使用WorldBookDatabase而不是AiDataDatabase
     */
    @Provides
    @Singleton
    fun provideWorldBookEntryRepository(
        worldBookDatabase: WorldBookDatabase
    ): WorldBookEntryRepository {
        return WorldBookEntryRepositoryImpl(worldBookDatabase.worldBookEntryDao())
    }

    @Provides
    @Singleton
    fun provideWorldSettingRepository(
        database: AiDataDatabase
    ): WorldSettingRepository {
        return WorldSettingRepositoryImpl(database)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideActionRepository(
        personProfileRepository: PersonProfileRepository,
        chatRepository: ChatRepository,
        settingsRepository: SettingsRepository,
        shoppingAuthorizedAccountRepository: com.susking.ephone_s.aidata.domain.repository.ShoppingAuthorizedAccountRepository
    ): ActionRepository {
        return ActionRepositoryImpl(
            personProfileRepository,
            chatRepository,
            settingsRepository,
            shoppingAuthorizedAccountRepository
        )
    }

    @Provides
    @Singleton
    fun providePromptContextRepository(
        database: AiDataDatabase,
        personProfileRepository: PersonProfileRepository,
        settingsRepository: SettingsRepository,
        memoryRecallService: MemoryRecallService
    ): PromptContextRepository {
        return PromptContextRepositoryImpl(
            database,
            personProfileRepository,
            settingsRepository,
            memoryRecallService
        )
    }

    /**
     * 提供CPhoneRepository
     * 注意：现在使用CPhoneDatabase而不是AiDataDatabase
     */
    @Provides
    @Singleton
    fun provideCPhoneRepository(
        cphoneDatabase: CPhoneDatabase,
        cphoneDataMapper: CPhoneDataMapper
    ): CPhoneRepository {
        return CPhoneRepositoryImpl(cphoneDatabase.cphoneDao(), cphoneDataMapper)
    }

    @Provides
    @Singleton
    fun provideCPhonePromptBuilder(): CPhonePromptBuilder {
        return CPhonePromptBuilder()
    }
    
    /**
     * 提供BackpackRepository
     * 背包数据仓库
     */
    @Provides
    @Singleton
    fun provideBackpackRepository(
        database: AiDataDatabase
    ): BackpackRepository {
        return BackpackRepositoryImpl(database.backpackItemDao())
    }
    
    /**
     * 提供BackpackEventManager
     * 全局背包事件监听器
     */
    @Provides
    @Singleton
    fun provideBackpackEventManager(
        backpackRepository: BackpackRepository
    ): BackpackEventManager {
        return BackpackEventManager(backpackRepository)
    }
    
    /**
     * 提供VideoCallHistoryRepository
     * 视频通话历史记录数据仓库
     */
    @Provides
    @Singleton
    fun provideVideoCallHistoryRepository(
        database: AiDataDatabase
    ): VideoCallHistoryRepository {
        return VideoCallHistoryRepositoryImpl(database.videoCallHistoryDao())
    }

    /**
     * 提供WeatherRepository
     * 天气缓存仓库,使用 core 模块的 weatherDataStore 落盘,不纳入导入导出
     */
    @Provides
    @Singleton
    fun provideWeatherRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): WeatherRepository {
        return WeatherRepositoryImpl(context, gson)
    }

    // Shopping相关的Repository由ShoppingModule提供,避免重复绑定

    // ==================== Services ====================
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
    
    @Provides
    @Singleton
    fun provideMusicService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): MusicService {
        return MusicServiceImpl(okHttpClient, gson)
    }
    
    /**
     * 提供ImageLoader实例
     * 用于图片加载和缓存
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // 使用25%的可用内存
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // 使用2%的磁盘空间
                    .build()
            }
            .respectCacheHeaders(false) // 忽略HTTP缓存头,始终使用本地缓存
            .build()
    }
    
    @Provides
    @Singleton
    fun provideAiPromptService(
        personProfileRepository: PersonProfileRepository,
        chatRepository: ChatRepository,
        worldSettingRepository: WorldSettingRepository,
        stickerRepository: StickerRepository,
        feedRepository: FeedRepository,
        settingsRepository: SettingsRepository,
        worldBookRepository: WorldBookRepository,
        worldBookEntryRepository: WorldBookEntryRepository,
        cphonePromptBuilder: CPhonePromptBuilder,
        memoriesRepository: MemoriesRepository,
        memoryRecallService: MemoryRecallService,
        contactSemanticStateRepository: ContactSemanticStateRepository,
        buildSchedulePromptSummaryUseCase: BuildSchedulePromptSummaryUseCase,
        weatherRepository: WeatherRepository
    ): AiPromptService {
        return AiPromptServiceImpl(
            personProfileRepository,
            chatRepository,
            worldSettingRepository,
            stickerRepository,
            feedRepository,
            settingsRepository,
            worldBookRepository,
            worldBookEntryRepository,
            cphonePromptBuilder,
            memoriesRepository,
            memoryRecallService,
            contactSemanticStateRepository,
            buildSchedulePromptSummaryUseCase,
            weatherRepository
        )
    }
    
    // ==================== Providers ====================
    
    /**
     * 提供AlbumDataProvider
     * 注意：实现在app模块中，通过@Binds或手动提供
     */
    @Provides
    @Singleton
    fun provideAlbumDataProvider(
        @ApplicationContext context: Context,
        albumDatabase: AlbumDatabase,
        gson: Gson
    ): AlbumDataProvider? {
        return try {
            // 使用反射加载app模块中的实现
            val implClass = Class.forName("com.susking.ephone_s.album.AlbumDataProviderImpl")
            val constructor = implClass.getConstructor(
                Context::class.java,
                AlbumDatabase::class.java,
                Gson::class.java
            )
            constructor.newInstance(context, albumDatabase, gson) as AlbumDataProvider
        } catch (e: Exception) {
            // 如果app模块未提供实现，返回null
            null
        }
    }
    
    /**
     * 提供DesktopDataProvider
     * 注意：实现在app模块中，通过@Binds或手动提供
     */
    @Provides
    @Singleton
    fun provideDesktopDataProvider(
        @ApplicationContext context: Context,
        gson: Gson
    ): DesktopDataProvider? {
        return try {
            // 使用反射加载app模块中的实现和DesktopRepository
            val desktopRepoClass = Class.forName("com.susking.ephone_s.desktop.data.DesktopRepository")
            val desktopRepoConstructor = desktopRepoClass.getConstructor(
                Context::class.java,
                Gson::class.java
            )
            val desktopRepository = desktopRepoConstructor.newInstance(context, gson)
            
            val implClass = Class.forName("com.susking.ephone_s.desktop.DesktopDataProviderImpl")
            val constructor = implClass.getConstructor(
                Context::class.java,
                desktopRepoClass,
                Gson::class.java
            )
            constructor.newInstance(context, desktopRepository, gson) as DesktopDataProvider
        } catch (e: Exception) {
            // 如果app模块未提供实现，返回null
            null
        }
    }

    // ==================== Use Cases ====================
    
    /**
     * 提供ExportCompleteAppDataUseCase
     * 完整应用数据导出UseCase
     */
    @Provides
    @Singleton
    fun provideExportCompleteAppDataUseCase(
        @ApplicationContext context: Context,
        exportDataUseCase: ExportDataUseCase,
        personProfileRepository: PersonProfileRepository,
        dataImportExportRepository: DataImportExportRepository,
        worldBookRepository: WorldBookRepository,
        worldBookEntryRepository: WorldBookEntryRepository,
        shoppingDatabase: ShoppingDatabase,
        cphoneRepository: CPhoneRepository,
        settingsRepository: SettingsRepository,
        albumDataProvider: AlbumDataProvider?,
        desktopDataProvider: DesktopDataProvider?
    ): com.susking.ephone_s.aidata.domain.use_case.ExportCompleteAppDataUseCase {
        return com.susking.ephone_s.aidata.domain.use_case.ExportCompleteAppDataUseCase(
            context,
            exportDataUseCase,
            personProfileRepository,
            dataImportExportRepository,
            worldBookRepository,
            worldBookEntryRepository,
            shoppingDatabase,
            cphoneRepository,
            settingsRepository,
            albumDataProvider,
            desktopDataProvider
        )
    }
    
    /**
     * 提供ImportCompleteAppDataUseCase
     * 完整应用数据导入UseCase
     */
    @Provides
    @Singleton
    fun provideImportCompleteAppDataUseCase(
        @ApplicationContext context: Context,
        importDataUseCase: ImportDataUseCase,
        dataImportExportRepository: DataImportExportRepository,
        worldBookRepository: WorldBookRepository,
        worldBookEntryRepository: WorldBookEntryRepository,
        shoppingDatabase: ShoppingDatabase,
        cphoneRepository: CPhoneRepository,
        settingsRepository: SettingsRepository,
        albumDataProvider: AlbumDataProvider?,
        desktopDataProvider: DesktopDataProvider?
    ): com.susking.ephone_s.aidata.domain.use_case.ImportCompleteAppDataUseCase {
        return com.susking.ephone_s.aidata.domain.use_case.ImportCompleteAppDataUseCase(
            context,
            dataImportExportRepository,
            worldBookRepository,
            worldBookEntryRepository,
            shoppingDatabase,
            cphoneRepository,
            settingsRepository,
            albumDataProvider,
            desktopDataProvider
        )
    }
    
    @Provides
    @Singleton
    fun provideExportDataUseCase(
        @ApplicationContext context: Context,
        chatRepository: ChatRepository,
        personProfileRepository: PersonProfileRepository,
        database: AiDataDatabase,
        heartbeatRepository: HeartbeatRepository,
        jottingRepository: JottingRepository,
        feedRepository: FeedRepository,
        alipayRepository: AlipayRepository,
        memoriesRepository: MemoriesRepository,
        dataImportExportRepository: DataImportExportRepository,
        stickerRepository: StickerRepository,
        videoCallHistoryRepository: VideoCallHistoryRepository,
        contactSemanticStateRepository: ContactSemanticStateRepository,
        scheduleRepository: ScheduleRepository
    ): ExportDataUseCase {
        return ExportDataUseCase(
            context,
            chatRepository,
            personProfileRepository,
            database.longTermMemoryDao(),
            heartbeatRepository,
            jottingRepository,
            feedRepository,
            alipayRepository,
            memoriesRepository,
            dataImportExportRepository,
            stickerRepository,
            videoCallHistoryRepository,
            contactSemanticStateRepository,
            scheduleRepository,
            database
        )
    }

    @Provides
    @Singleton
    fun provideImportDataUseCase(
        @ApplicationContext context: Context,
        dataImportExportRepository: DataImportExportRepository,
        personProfileRepository: PersonProfileRepository
    ): ImportDataUseCase {
        return ImportDataUseCase(
            context,
            dataImportExportRepository,
            personProfileRepository
        )
    }

    @Provides
    @Singleton
    fun provideSummarizeChatHistoryUseCase(
        settingsRepository: SettingsRepository,
        chatRepository: ChatRepository,
        personProfileRepository: PersonProfileRepository,
        worldBookRepository: WorldBookRepository,
        worldBookEntryRepository: WorldBookEntryRepository,
        memoryGraphDao: com.susking.ephone_s.aidata.data.local.dao.memory.MemoryGraphDao,
        memoryEventDao: com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
    ): SummarizeChatHistoryPromptBuilder {
        return SummarizeChatHistoryPromptBuilder(
            settingsRepository,
            chatRepository,
            personProfileRepository,
            worldBookRepository,
            worldBookEntryRepository,
            memoryGraphDao,
            memoryEventDao
        )
    }

    @Provides
    @Singleton
    fun provideSummarizeCallTranscriptUseCase(
        aiPromptService: AiPromptService,
        aiRequestService: com.susking.ephone_s.aidata.api.AiRequestService,
        @ApplicationContext context: Context,
        memoryFactGraphExtractionService: MemoryFactGraphExtractionService
    ): SummarizeCallTranscriptUseCase {
        return SummarizeCallTranscriptUseCase(aiPromptService, aiRequestService, context, memoryFactGraphExtractionService)
    }

    @Provides
    @Singleton
    fun provideGenerateCPhoneDataUseCase(
        @ApplicationContext context: Context,
        aiPromptService: AiPromptService,
        cphoneRepository: CPhoneRepository
    ): GenerateCPhoneDataUseCase {
        // 直接从 AiDataApi 获取已初始化的 UseCase
        // 这个 UseCase 在 Application 中通过 registerBrainService() 初始化
        return com.susking.ephone_s.aidata.api.AiDataApi.getGenerateCPhoneDataUseCase()
    }

    @Provides
    @Singleton
    fun provideSaveImageFromBase64UseCase(
        @ApplicationContext context: Context
    ): SaveImageFromBase64UseCase {
        return com.susking.ephone_s.aidata.api.AiDataApi.getSaveImageUseCase()
    }

    @Provides
    @Singleton
    fun provideGenerateImageFromPromptUseCase(
        imageGenerationService: ImageGenerationService,
        saveImageUseCase: SaveImageFromBase64UseCase
    ): GenerateImageFromPromptUseCase {
        return GenerateImageFromPromptUseCase(
            imageGenerationService,
            saveImageUseCase
        )
    }

    @Provides
    @Singleton
    fun provideRewriteImagePromptUseCase(
        @ApplicationContext context: Context,
        chatRepository: ChatRepository
    ): RewriteImagePromptUseCase {
        return RewriteImagePromptUseCase(
            context,
            chatRepository
        )
    }
    
    @Provides
    @Singleton
    fun provideTriggerAutoSummarizeUseCase(
        personProfileRepository: PersonProfileRepository
    ): TriggerAutoSummarizeUseCase {
        return TriggerAutoSummarizeUseCase(
            personProfileRepository
        )
    }
    
}