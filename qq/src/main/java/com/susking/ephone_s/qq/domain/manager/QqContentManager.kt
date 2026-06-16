package com.susking.ephone_s.qq.domain.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.susking.ephone_s.aidata.data.local.entity.ContactSemanticStateEntity
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity
import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ContactSemanticStateRepository
import com.susking.ephone_s.aidata.domain.repository.FavoriteMessageRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.core.util.Event
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QQ 内容管理器
 * 
 * 合并了以下Manager的功能:
 * - FavoriteManager: 收藏消息管理
 * - InnerActivityManager: 心声/散记管理
 * 
 * 职责:
 * 1. 收藏消息的增删查
 * 2. 心声(Heartbeat)的加载和更新
 * 3. 散记(Jotting)的加载和更新
 * 4. 内心活动历史管理
 * 
 * 注意: 当前直接依赖Repository,阶段四将重构为依赖接口
 */
@Singleton
class QqContentManager @Inject constructor(
    private val favoriteMessageRepository: FavoriteMessageRepository,
    private val heartbeatRepository: HeartbeatRepository,
    private val jottingRepository: JottingRepository,
    private val contactSemanticStateRepository: ContactSemanticStateRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val coroutineScope: CoroutineScope,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "QqContentManager"
        private const val PREFS_NAME = "qq_content_prefs"
        private const val KEY_FAVORITES_DISPLAY_MODE = "favorites_display_mode"
    }

    // ==================== 收藏管理状态 ====================
    
    // 收藏消息列表（原始数据）
    private val allFavoriteMessages: LiveData<List<FavoriteMessageEntity>> =
        favoriteMessageRepository.getAllFavorites().asLiveData()
    
    // 筛选后的收藏消息列表
    private val _favoriteMessages = MutableLiveData<List<FavoriteMessageEntity>>()
    val favoriteMessages: LiveData<List<FavoriteMessageEntity>> = _favoriteMessages
    
    // 当前筛选类型
    private val _currentFilterType = MutableLiveData<String>("all")
    val currentFilterType: LiveData<String> = _currentFilterType
    
    // 当前筛选的联系人ID
    private val _currentFilterContactId = MutableLiveData<String?>(null)
    val currentFilterContactId: LiveData<String?> = _currentFilterContactId

    // 收藏显示模式
    private val _favoritesDisplayMode = MutableLiveData<String>("list")
    val favoritesDisplayMode: LiveData<String> = _favoritesDisplayMode

    // ==================== 内心活动状态 ====================
    
    // 最新心声
    private val _latestHeartbeat = MutableLiveData<Any?>()
    val latestHeartbeat: LiveData<Any?> = _latestHeartbeat

    // 最新散记
    private val _latestJotting = MutableLiveData<Any?>()
    val latestJotting: LiveData<Any?> = _latestJotting

    // 当前语义状态
    private val _latestSemanticState = MutableLiveData<ContactSemanticStateEntity?>()
    val latestSemanticState: LiveData<ContactSemanticStateEntity?> = _latestSemanticState

    // 所有心声
    private val _allHeartbeats = MutableLiveData<List<Any>>(emptyList())
    val allHeartbeats: LiveData<List<Any>> = _allHeartbeats

    // 所有散记
    private val _allJottings = MutableLiveData<List<Any>>(emptyList())
    val allJottings: LiveData<List<Any>> = _allJottings

    // ==================== 通用状态 ====================
    
    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent
    
    // 初始化：监听原始数据变化并应用筛选
    init {
        allFavoriteMessages.observeForever { messages ->
            applyFilter(messages)
        }
    }

    // ==================== 收藏管理功能 ====================

    /**
     * 将 ChatMessage 转换为 FavoriteMessageEntity
     */
    private fun convertToFavoriteEntity(message: ChatMessage, senderName: String, senderAvatar: String?): FavoriteMessageEntity {
        return FavoriteMessageEntity(
            messageId = message.id,
            contactId = message.contactId,
            text = message.content,
            content = message.content,
            senderName = senderName,
            senderAvatar = senderAvatar,
            source = "与${senderName}的聊天",
            timestamp = message.timestamp,
            imageUrl = message.imageUrl,
            type = message.type,
            stickerUrl = message.stickerUrl,
            stickerName = message.stickerName,
            amount = message.amount,
            productInfo = message.productInfo,
            notes = message.notes,
            status = message.status,
            greeting = message.greeting,
            recipientName = message.recipientName
        )
    }

    /**
     * 添加单条消息到收藏
     * 会检查是否已收藏,如果已收藏则取消收藏
     */
    fun addFavorite(message: ChatMessage, senderName: String = "未知", senderAvatar: String? = null) {
        coroutineScope.launch {
            try {
                // 检查消息是否已被收藏
                val existingFavorite = favoriteMessageRepository.getFavoriteByMessageId(message.id).first()
                if (existingFavorite != null) {
                    // 如果已收藏,则取消收藏
                    favoriteMessageRepository.removeFavorite(existingFavorite)
                    _toastEvent.postValue(Event("已取消收藏"))
                } else {
                    // 如果未收藏,则添加收藏
                    val favoriteEntity = convertToFavoriteEntity(message, senderName, senderAvatar)

                    // 如果是图片消息,则复制图片到安全目录
                    if (!favoriteEntity.imageUrl.isNullOrBlank()) {
                        val newImagePath = copyImageToFavoritesDir(favoriteEntity.imageUrl!!)
                        if (newImagePath != null) {
                            val updatedFavoriteEntity = favoriteEntity.copy(imageUrl = newImagePath)
                            favoriteMessageRepository.addFavorite(updatedFavoriteEntity)
                        } else {
                            // 如果复制失败,仍然收藏,但图片可能无法显示
                            favoriteMessageRepository.addFavorite(favoriteEntity)
                        }
                    } else {
                        favoriteMessageRepository.addFavorite(favoriteEntity)
                    }
                    _toastEvent.postValue(Event("已收藏"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "添加收藏失败", e)
                _toastEvent.postValue(Event("操作失败: ${e.message}"))
            }
        }
    }

    /**
     * 批量添加消息到收藏
     */
    fun addFavorites(messages: List<ChatMessage>, senderName: String = "未知", senderAvatar: String? = null) {
        coroutineScope.launch {
            try {
                val favoriteEntities = messages.map { message ->
                    convertToFavoriteEntity(message, senderName, senderAvatar)
                }
                favoriteMessageRepository.addFavorites(favoriteEntities)
            } catch (e: Exception) {
                Log.e(TAG, "批量添加收藏失败", e)
                _toastEvent.postValue(Event("批量收藏失败: ${e.message}"))
            }
        }
    }

    /**
     * 取消收藏
     */
    fun removeFavorite(favorite: FavoriteMessageEntity) {
        coroutineScope.launch {
            try {
                favoriteMessageRepository.removeFavorite(favorite)
            } catch (e: Exception) {
                Log.e(TAG, "取消收藏失败", e)
                _toastEvent.postValue(Event("取消收藏失败: ${e.message}"))
            }
        }
    }

    /**
     * 加载收藏显示模式
     */
    fun loadFavoritesDisplayMode() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(KEY_FAVORITES_DISPLAY_MODE, "list") ?: "list"
        _favoritesDisplayMode.value = mode
    }

    /**
     * 保存收藏显示模式
     */
    fun saveFavoritesDisplayMode(mode: String) {
        _favoritesDisplayMode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAVORITES_DISPLAY_MODE, mode)
            .apply()
    }
    
    /**
     * 设置筛选类型
     * @param filterType 筛选类型: "all"(全部), "chat"(聊天), "inner"(内心), "contact"(特定联系人)
     * @param contactId 当filterType为"contact"时，指定联系人ID
     */
    fun setFilter(filterType: String, contactId: String? = null) {
        _currentFilterType.value = filterType
        _currentFilterContactId.value = contactId
        allFavoriteMessages.value?.let { messages ->
            applyFilter(messages)
        }
    }
    
    /**
     * 应用筛选逻辑
     */
    private fun applyFilter(messages: List<FavoriteMessageEntity>) {
        val filterType = _currentFilterType.value ?: "all"
        val contactId = _currentFilterContactId.value
        
        val filteredMessages = when (filterType) {
            "all" -> messages
            "chat" -> messages.filter { it.source?.contains("聊天") == true }
            "inner" -> messages.filter { it.source?.contains("内心") == true }
            "contact" -> {
                if (contactId != null) {
                    messages.filter { it.contactId == contactId }
                } else {
                    messages
                }
            }
            else -> messages
        }
        
        _favoriteMessages.postValue(filteredMessages)
    }
    
    /**
     * 获取有收藏的联系人列表
     * @return 联系人信息列表，包含联系人ID、名称、头像和收藏数量
     */
    suspend fun getContactsWithFavorites(): List<ContactWithFavoriteCount> {
        return try {
            val allMessages = favoriteMessageRepository.getAllFavorites().first()
            val contactGroups = allMessages.groupBy { it.contactId }
            
            contactGroups.map { (contactId, messages) ->
                val contact = personProfileRepository.getPersonProfileById(contactId)
                val contactName = contact?.remarkName ?: contact?.realName ?: "未知联系人"
                val avatarUri = contact?.avatarUri
                
                ContactWithFavoriteCount(
                    contactId = contactId,
                    contactName = contactName,
                    avatarUri = avatarUri,
                    favoriteCount = messages.size
                )
            }.sortedByDescending { it.favoriteCount }
        } catch (e: Exception) {
            Log.e(TAG, "获取有收藏的联系人列表失败", e)
            emptyList()
        }
    }

    /**
     * 将图片文件复制到收藏夹专属的安全目录
     * @param originalPath 原始图片路径 (可以是 file:// URI 或绝对路径)
     * @return 复制成功后的新文件路径,如果失败则返回 null
     */
    private fun copyImageToFavoritesDir(originalPath: String): String? {
        return try {
            val sourceFile = File(originalPath)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source image for favorite not found at: $originalPath")
                return null
            }

            val favoritesDir = File(context.filesDir, "favorites").apply { mkdirs() }
            val destinationFile = File(favoritesDir, sourceFile.name)

            // 复制文件
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Image copied to favorites: ${destinationFile.absolutePath}")
            destinationFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to favorites dir", e)
            null
        }
    }

    // ==================== 内心活动管理功能 ====================

    /**
     * 加载联系人详情
     */
    fun loadContactDetails(contactId: String) {
        coroutineScope.launch {
            try {
                // 加载最新心声
                val latestHeartbeat = heartbeatRepository.getLatestHeartbeatForContact(contactId).first()
                _latestHeartbeat.postValue(latestHeartbeat)
                
                // 加载最新散记
                val latestJotting = jottingRepository.getLatestJottingForContact(contactId).first()
                _latestJotting.postValue(latestJotting)

                // 加载当前语义状态
                val latestSemanticState = contactSemanticStateRepository.getSemanticStateSnapshotForContact(contactId)
                _latestSemanticState.postValue(latestSemanticState)
            } catch (e: Exception) {
                // 处理错误
                _latestHeartbeat.postValue(null)
                _latestJotting.postValue(null)
                _latestSemanticState.postValue(null)
            }
        }
    }

    /**
     * 更新心声
     */
    fun updateHeartbeat(content: Any, contactId: String) {
        coroutineScope.launch {
            try {
                // TODO: 实现更新心声逻辑
                // 1. 保存心声
                // 2. 更新UI
            } catch (e: Exception) {
                Log.e(TAG, "更新心声失败", e)
                _toastEvent.postValue(Event("更新心声失败: ${e.message}"))
            }
        }
    }

    /**
     * 更新散记
     */
    fun updateJotting(content: Any, contactId: String) {
        coroutineScope.launch {
            try {
                // TODO: 实现更新散记逻辑
                // 1. 保存散记
                // 2. 更新UI
            } catch (e: Exception) {
                Log.e(TAG, "更新散记失败", e)
                _toastEvent.postValue(Event("更新散记失败: ${e.message}"))
            }
        }
    }

    /**
     * 更新语义状态。
     *
     * 用户在心声散记页的编辑会直接覆盖当前联系人状态，不做特殊优先级标记。
     */
    fun updateSemanticState(semanticState: ContactSemanticStateEntity, contactId: String) {
        coroutineScope.launch {
            try {
                val updatedSemanticState = semanticState.copy(
                    contactId = contactId,
                    updatedAt = System.currentTimeMillis(),
                    rawUpdateJson = null
                )
                contactSemanticStateRepository.updateSemanticState(updatedSemanticState)
                _latestSemanticState.postValue(updatedSemanticState)
                _toastEvent.postValue(Event("语义状态已更新"))
            } catch (e: Exception) {
                Log.e(TAG, "更新语义状态失败", e)
                _toastEvent.postValue(Event("更新语义状态失败: ${e.message}"))
            }
        }
    }

    /**
     * 切换内心活动收藏状态
     * 支持收藏心声和散记,可以单独收藏也可以组合收藏
     */
    fun toggleInnerContentFavorite(heartbeat: HeartbeatEntity?, jotting: JottingEntity?) {
        coroutineScope.launch {
            try {
                if (heartbeat == null && jotting == null) return@launch

                val contactId = heartbeat?.contactId ?: jotting?.contactId ?: return@launch
                // 使用心声和散记的时间戳组合成一个相对唯一的ID
                val combinedId = "inner_${contactId}_${heartbeat?.timestamp ?: "n"}_${jotting?.timestamp ?: "n"}"

                val existingFavorite = favoriteMessageRepository.getFavoriteByMessageId(combinedId).first()

                if (existingFavorite != null) {
                    // 如果已收藏,则取消收藏
                    favoriteMessageRepository.removeFavorite(existingFavorite)
                    heartbeat?.let { heartbeatRepository.updateHeartbeat(it.copy(isFavorited = false)) }
                    jotting?.let { jottingRepository.updateJotting(it.copy(isFavorited = false)) }
                } else {
                    // 如果未收藏,则添加收藏
                    val contact = personProfileRepository.getPersonProfileById(contactId)
                    val stringBuilder = StringBuilder()
                    var sourceTimestamp = System.currentTimeMillis()

                    heartbeat?.let {
                        stringBuilder.append("心声\n${it.content}")
                        sourceTimestamp = it.timestamp // 优先使用心声的时间戳
                    }
                    jotting?.let {
                        if (stringBuilder.isNotEmpty()) {
                            stringBuilder.append("\n\n")
                        }
                        stringBuilder.append("散记\n${it.content}")
                        if (heartbeat == null) sourceTimestamp = it.timestamp // 如果没有心声,则用散记的时间戳
                    }

                    val contactName = contact?.remarkName ?: contact?.realName ?: "未知"
                    val favoriteEntity = FavoriteMessageEntity(
                        messageId = combinedId,
                        contactId = contactId,
                        text = stringBuilder.toString(),
                        senderName = contactName,
                        senderAvatar = contact?.avatarUri,
                        source = "${contactName}的内心",
                        timestamp = sourceTimestamp,
                        type = "text"
                    )
                    favoriteMessageRepository.addFavorite(favoriteEntity)
                    heartbeat?.let { heartbeatRepository.updateHeartbeat(it.copy(isFavorited = true)) }
                    jotting?.let { jottingRepository.updateJotting(it.copy(isFavorited = true)) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换内心活动收藏状态失败", e)
                _toastEvent.postValue(Event("操作失败: ${e.message}"))
            }
        }
    }
    
    /**
     * 加载所有内心活动历史
     */
    fun loadAllInnerHistory(contactId: String) {
        coroutineScope.launch {
            try {
                // 加载所有心声
                val allHeartbeats = heartbeatRepository.getAllHeartbeatsForContact(contactId).first()
                _allHeartbeats.postValue(allHeartbeats)
                
                // 加载所有散记
                val allJottings = jottingRepository.getAllJottingsForContact(contactId).first()
                _allJottings.postValue(allJottings)
            } catch (e: Exception) {
                // 处理错误
                _allHeartbeats.postValue(emptyList())
                _allJottings.postValue(emptyList())
            }
        }
    }
}

/**
 * 联系人收藏统计数据类
 */
data class ContactWithFavoriteCount(
    val contactId: String,
    val contactName: String,
    val avatarUri: String?,
    val favoriteCount: Int
)