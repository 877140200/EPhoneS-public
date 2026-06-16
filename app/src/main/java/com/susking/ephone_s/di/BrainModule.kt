package com.susking.ephone_s.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.susking.ephone_s.EPhoneSApplication
import com.susking.ephone_s.aidata.di.ApplicationScope
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.repository.ActionRepository
import com.susking.ephone_s.aidata.domain.repository.BackpackRepository
import com.susking.ephone_s.aidata.domain.repository.ContactSemanticStateRepository
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.domain.repository.MemoriesRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import com.susking.ephone_s.aidata.domain.repository.StickerRepository
import com.susking.ephone_s.aidata.domain.use_case.GenerateShoppingProductUseCase
import com.susking.ephone_s.album.data.repository.AlbumRepositoryImpl
import com.susking.ephone_s.album.data.service.AlbumServiceImpl
import com.susking.ephone_s.album.domain.service.AlbumService
import com.susking.ephone_s.brain.api.ActivityLogger
import com.susking.ephone_s.brain.api.BrainApi
import com.susking.ephone_s.brain.service.ActionExecutor
import com.susking.ephone_s.brain.service.AlbumServiceCallback
import com.susking.ephone_s.brain.service.AiRequestService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Brain 和 Album 模块的依赖注入配置
 * 提供 BrainApi 和 AlbumService 相关的服务
 */
@Module
@InstallIn(SingletonComponent::class)
object BrainModule {

    /**
     * 提供应用级别的 CoroutineScope
     * 使用 SupervisorJob 确保子协程失败不会影响其他协程
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    /**
     * 提供默认的 CoroutineScope (用于 Manager 类)
     * 使用 SupervisorJob 确保子协程失败不会影响其他协程
     */
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    /**
     * 提供 SharedPreferences
     * 注意：使用 "qq_groups" 以匹配导入导出功能中的分组数据
     */
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("qq_groups", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideAiRequestService(): AiRequestService {
        return BrainApi.getAiRequestService()
    }

    @Provides
    @Singleton
    fun provideActivityLogger(): ActivityLogger {
        return BrainApi.getActivityLogger()
    }

    @Provides
    @Singleton
    fun provideAlbumService(): AlbumService {
        val albumDb = EPhoneSApplication.albumdb
        val albumRepository = AlbumRepositoryImpl(albumDb.albumDao(), albumDb.photoDao())
        return AlbumServiceImpl(albumRepository)
    }

    @Provides
    @Singleton
    fun provideAlbumServiceCallback(albumService: AlbumService): AlbumServiceCallback {
        return object : AlbumServiceCallback {
            override suspend fun addPhotoToAlbum(albumName: String, photoPath: String) {
                albumService.addPhotoToAlbum(albumName, photoPath)
            }
        }
    }

    @Provides
    @Singleton
    fun provideActionExecutor(
        actionRepository: ActionRepository,
        heartbeatRepository: HeartbeatRepository,
        jottingRepository: JottingRepository,
        feedRepository: FeedRepository,
        albumServiceCallback: AlbumServiceCallback,
        stickerRepository: StickerRepository,
        alipayRepository: AlipayRepository,
        activityLogger: ActivityLogger,
        gson: Gson,
        shoppingProductRepository: ShoppingProductRepository,
        generateShoppingProductUseCase: GenerateShoppingProductUseCase,
        backpackRepository: BackpackRepository,
        memoriesRepository: MemoriesRepository,
        chatRepository: com.susking.ephone_s.aidata.domain.repository.ChatRepository,
        contactSemanticStateRepository: ContactSemanticStateRepository
    ): ActionExecutor {
        return ActionExecutor(
            actionRepository = actionRepository,
            heartbeatRepository = heartbeatRepository,
            jottingRepository = jottingRepository,
            feedRepository = feedRepository,
            albumServiceCallback = albumServiceCallback,
            stickerRepository = stickerRepository,
            alipayRepository = alipayRepository,
            activityLogger = activityLogger,
            gson = gson,
            shoppingProductRepository = shoppingProductRepository,
            generateShoppingProductUseCase = generateShoppingProductUseCase,
            backpackRepository = backpackRepository,
            memoriesRepository = memoriesRepository,
            chatRepository = chatRepository,
            contactSemanticStateRepository = contactSemanticStateRepository
        )
    }
}