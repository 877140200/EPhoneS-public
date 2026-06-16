package com.susking.ephone_s.qq.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.ContactSemanticStateRepository
import com.susking.ephone_s.aidata.domain.repository.FavoriteMessageRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicyStore
import com.susking.ephone_s.qq.domain.manager.QqChatManager
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.domain.manager.QqContentManager
import com.susking.ephone_s.qq.domain.manager.QqTransactionManager
import com.susking.ephone_s.qq.domain.use_case.ai.ExecuteAiResponseUseCase
import com.susking.ephone_s.qq.domain.use_case.ai.RegenerateResponseUseCase
import com.susking.ephone_s.qq.domain.use_case.ai.RequestAiResponseUseCase
import com.susking.ephone_s.qq.domain.use_case.ai.SwitchResponseVersionUseCase
import com.susking.ephone_s.qq.domain.use_case.chat.ClearChatHistoryUseCase
import com.susking.ephone_s.qq.domain.use_case.chat.SendMessageUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.AcceptFriendRequestUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.DeclineFriendRequestUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.DeleteContactUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.LoadContactsUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.PerformPatActionUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.ToggleBlockStatusUseCase
import com.susking.ephone_s.qq.domain.use_case.contact.UpdateContactUseCase
import com.susking.ephone_s.qq.domain.use_case.message.DeleteMessageUseCase
import com.susking.ephone_s.qq.domain.use_case.message.EditMessageUseCase
import com.susking.ephone_s.qq.domain.use_case.transfer.AcceptTransferUseCase
import com.susking.ephone_s.qq.domain.use_case.transfer.DeclineTransferUseCase
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
 * QQ模块的Hilt依赖注入配置
 * 
 * 提供4个核心Manager的单例实例:
 * - QqChatManager: 聊天和AI对话
 * - QqContactManager: 联系人和分组管理
 * - QqContentManager: 收藏和内心活动
 * - QqTransactionManager: 转账和交易
 */
@Module
@InstallIn(SingletonComponent::class)
object QqDomainModule {

    /**
     * 提供QQ模块专用的CoroutineScope
     */
    @Provides
    @Singleton
    @QqScope
    fun provideQqCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    /**
     * 提供 QqChatManager
     * 负责聊天消息和AI对话管理
     */
    @Provides
    @Singleton
    fun provideQqChatManager(
        chatRepository: ChatRepository,
        sendMessageUseCase: SendMessageUseCase,
        deleteMessageUseCase: DeleteMessageUseCase,
        editMessageUseCase: EditMessageUseCase,
        clearChatHistoryUseCase: ClearChatHistoryUseCase,
        requestAiResponseUseCase: RequestAiResponseUseCase,
        regenerateResponseUseCase: RegenerateResponseUseCase,
        executeAiResponseUseCase: ExecuteAiResponseUseCase,
        switchResponseVersionUseCase: SwitchResponseVersionUseCase,
        followUpPolicyStore: FollowUpPolicyStore,
        settingsRepository: com.susking.ephone_s.aidata.domain.repository.SettingsRepository,
        performPatActionUseCase: PerformPatActionUseCase,
        @QqScope coroutineScope: CoroutineScope
    ): QqChatManager {
        return QqChatManager(
            chatRepository = chatRepository,
            sendMessageUseCase = sendMessageUseCase,
            deleteMessageUseCase = deleteMessageUseCase,
            editMessageUseCase = editMessageUseCase,
            clearChatHistoryUseCase = clearChatHistoryUseCase,
            requestAiResponseUseCase = requestAiResponseUseCase,
            regenerateResponseUseCase = regenerateResponseUseCase,
            executeAiResponseUseCase = executeAiResponseUseCase,
            switchResponseVersionUseCase = switchResponseVersionUseCase,
            followUpPolicyStore = followUpPolicyStore,
            settingsRepository = settingsRepository,
            performPatActionUseCase = performPatActionUseCase,
            coroutineScope = coroutineScope
        )
    }

    /**
     * 提供 QqContactManager
     * 负责联系人、分组和用户资料管理
     */
    @Provides
    @Singleton
    fun provideQqContactManager(
        loadContactsUseCase: LoadContactsUseCase,
        updateContactUseCase: UpdateContactUseCase,
        deleteContactUseCase: DeleteContactUseCase,
        toggleBlockStatusUseCase: ToggleBlockStatusUseCase,
        acceptFriendRequestUseCase: AcceptFriendRequestUseCase,
        declineFriendRequestUseCase: DeclineFriendRequestUseCase,
        personProfileRepository: PersonProfileRepository,
        chatRepository: ChatRepository,
        sharedPreferences: SharedPreferences,
        gson: Gson,
        @QqScope coroutineScope: CoroutineScope
    ): QqContactManager {
        return QqContactManager(
            loadContactsUseCase = loadContactsUseCase,
            updateContactUseCase = updateContactUseCase,
            deleteContactUseCase = deleteContactUseCase,
            toggleBlockStatusUseCase = toggleBlockStatusUseCase,
            acceptFriendRequestUseCase = acceptFriendRequestUseCase,
            declineFriendRequestUseCase = declineFriendRequestUseCase,
            personProfileRepository = personProfileRepository,
            chatRepository = chatRepository,
            sharedPreferences = sharedPreferences,
            gson = gson,
            coroutineScope = coroutineScope
        )
    }

    /**
     * 提供 QqContentManager
     * 负责收藏消息和内心活动管理
     */
    @Provides
    @Singleton
    fun provideQqContentManager(
        favoriteMessageRepository: FavoriteMessageRepository,
        heartbeatRepository: HeartbeatRepository,
        jottingRepository: JottingRepository,
        personProfileRepository: PersonProfileRepository,
        contactSemanticStateRepository: ContactSemanticStateRepository,
        @QqScope coroutineScope: CoroutineScope,
        @ApplicationContext context: Context
    ): QqContentManager {
        return QqContentManager(
            favoriteMessageRepository = favoriteMessageRepository,
            heartbeatRepository = heartbeatRepository,
            jottingRepository = jottingRepository,
            personProfileRepository = personProfileRepository,
            contactSemanticStateRepository = contactSemanticStateRepository,
            coroutineScope = coroutineScope,
            context = context
        )
    }

    /**
     * 提供 QqTransactionManager
     * 负责转账、外卖和好友申请管理
     */
    @Provides
    @Singleton
    fun provideQqTransactionManager(
        alipayRepository: AlipayRepository,
        chatRepository: ChatRepository,
        sendMessageUseCase: SendMessageUseCase,
        acceptTransferUseCase: AcceptTransferUseCase,
        declineTransferUseCase: DeclineTransferUseCase,
        @QqScope coroutineScope: CoroutineScope
    ): QqTransactionManager {
        return QqTransactionManager(
            alipayRepository = alipayRepository,
            chatRepository = chatRepository,
            sendMessageUseCase = sendMessageUseCase,
            acceptTransferUseCase = acceptTransferUseCase,
            declineTransferUseCase = declineTransferUseCase,
            coroutineScope = coroutineScope
        )
    }

    /**
     * 提供 ActiveContactTracker
     * 用于追踪当前活跃的联系人,避免在聊天界面时增加未读计数
     */
    @Provides
    @Singleton
    fun provideActiveContactTracker(): com.susking.ephone_s.aidata.domain.tracker.ActiveContactTracker {
        return com.susking.ephone_s.qq.domain.manager.ActiveContactTrackerImpl()
    }
}

/**
 * QQ模块专用的CoroutineScope限定符
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class QqScope