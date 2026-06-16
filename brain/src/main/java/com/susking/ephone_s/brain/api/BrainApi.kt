package com.susking.ephone_s.brain.api

import android.content.Context
import com.susking.ephone_s.aidata.data.local.BrainDatabase
import com.susking.ephone_s.brain.data.repository.BrainRepositoryImpl
import com.susking.ephone_s.brain.domain.repository.BrainRepository
import com.susking.ephone_s.brain.service.AiRequestService

/**
 * Brain模块的统一API入口，提供模块初始化和获取各种服务的方法。
 */
object BrainApi {
    
    @Volatile
    private var repository: BrainRepository? = null
    
    @Volatile
    private var activityLogger: ActivityLogger? = null
    
    @Volatile
    private var aiRequestService: AiRequestService? = null
    
    /**
     * 初始化Brain模块。
     * 应在Application.onCreate()中调用。
     */
    fun initialize(context: Context) {
        if (repository == null) {
            synchronized(this) {
                if (repository == null) {
                    val database = BrainDatabase.getInstance(context)
                    repository = BrainRepositoryImpl(database.aiActivityDao(), context)
                    activityLogger = ActivityLoggerImpl(repository!!)
                    
                    // 初始化 AiRequestService（只负责 HTTP 请求）
                    aiRequestService = AiRequestService(
                        activityLogger = activityLogger!!
                    )
                }
            }
        }
    }
    
    /**
     * 获取BrainRepository实例。
     */
    fun getRepository(): BrainRepository {
        return repository ?: throw IllegalStateException("BrainApi未初始化，请先调用initialize()")
    }
    
    /**
     * 获取ActivityLogger实例。
     */
    fun getActivityLogger(): ActivityLogger {
        return activityLogger ?: throw IllegalStateException("BrainApi未初始化，请先调用initialize()")
    }
    
    /**
     * 获取AiRequestService实例。
     * 用于发送 AI 请求，完全无状态，每次请求都从 aidata 获取最新数据。
     */
    fun getAiRequestService(): AiRequestService {
        return aiRequestService ?: throw IllegalStateException("BrainApi未初始化，请先调用initialize()")
    }
}