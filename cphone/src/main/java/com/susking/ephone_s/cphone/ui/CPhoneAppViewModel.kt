package com.susking.ephone_s.cphone.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.susking.ephone_s.cphone.domain.model.SimulatedQQConversation
import com.susking.ephone_s.cphone.domain.model.SimulatedQQMessage

/**
 * CPhone各个App的通用ViewModel
 * 管理刷新状态、数据加载和AI生成
 */
@HiltViewModel
class CPhoneAppViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ==================== 刷新状态管理 ====================
    
    private val _refreshState = MutableLiveData<RefreshState>()
    val refreshState: LiveData<RefreshState> = _refreshState
    
    /**
     * 刷新状态封装类
     */
    sealed class RefreshState {
        object Idle : RefreshState()
        object Loading : RefreshState()
        data class Success(val appType: String) : RefreshState()
        data class Error(val appType: String, val message: String) : RefreshState()
    }
    
    // ==================== 数据获取方法（从aidata模块） ====================
    
    /**
     * 获取相册数据
     * 直接返回aidata模块的AlbumPhoto，无需映射
     */
    fun getAlbumData(contactId: String): Flow<List<AlbumPhoto>> {
        return AiDataApi.getCPhoneRepository().getCPhoneData(contactId)
            .map { it?.albumPhotos ?: emptyList() }
    }
    
    /**
     * 获取浏览器历史
     */
    fun getBrowserData(contactId: String): Flow<List<BrowserRecord>> {
        return AiDataApi.getCPhoneRepository().getCPhoneData(contactId)
            .map { it?.browserHistory ?: emptyList() }
    }
    
    /**
     * 获取淘宝订单
     */
    fun getTaobaoData(contactId: String): Flow<List<TaobaoPurchase>> {
        return AiDataApi.getCPhoneRepository().getCPhoneData(contactId)
            .map { it?.taobaoData?.purchases ?: emptyList() }
    }
    
    /**
     * 获取备忘录
     */
    fun getMemoData(contactId: String): Flow<List<Memo>> {
        return AiDataApi.getCPhoneRepository().getCPhoneData(contactId)
            .map { it?.memos ?: emptyList() }
    }
    
    /**
     * 获取日记
     */
    fun getDiaryData(contactId: String): Flow<List<DiaryEntry>> {
        return AiDataApi.getCPhoneRepository().getCPhoneData(contactId)
            .map { it?.diaryEntries ?: emptyList() }
    }
    
    /**
     * 获取高德地图足迹
     */
    fun getAmapData(contactId: String): Flow<List<AmapFootprint>> {
        return AiDataApi.getCPhoneRepository().getCPhoneData(contactId)
            .map { it?.amapFootprints ?: emptyList() }
    }
    
    /**
     * 获取App使用记录
     */
    fun getAppUsageData(contactId: String): Flow<List<AppUsageRecord>> {
        return AiDataApi.getCPhoneRepository().getCPhoneData(contactId)
            .map { it?.appUsageRecords ?: emptyList() }
    }
    
    /**
     * 获取音乐歌曲
     */
    fun getMusicData(contactId: String): Flow<List<MusicTrack>> {
        return AiDataApi.getCPhoneRepository().getCPhoneData(contactId)
            .map { it?.musicTracks ?: emptyList() }
    }
    
    /**
     * 获取QQ模拟对话
     */
    fun getQQData(contactId: String): Flow<List<SimulatedQQConversation>> {
        return AiDataApi.getCPhoneRepository().getCPhoneData(contactId)
            .map { it?.qqConversations?.map { conv -> conv.toCPhoneModel() } ?: emptyList() }
    }
    
    // ==================== AI生成方法 ====================
    
    /**
     * 刷新指定App的数据
     * @param contactId 角色ID
     * @param appType App类型（album/browser/taobao/memo/diary/amap/appUsage/music/qq）
     */
    fun refreshAppData(contactId: String, appType: String) {
        viewModelScope.launch {
            if (appType == "diary") {
                _refreshState.value = RefreshState.Error(
                    appType = appType,
                    message = "手动AI生成日记已停用，请使用手动新增日记"
                )
                return@launch
            }
            _refreshState.value = RefreshState.Loading
            
            try {
                // 调用aidata模块的GenerateCPhoneDataUseCase
                val useCase = AiDataApi.getGenerateCPhoneDataUseCase()
                val result = useCase.execute(contactId, appType)
                
                result.fold(
                    onSuccess = {
                        _refreshState.value = RefreshState.Success(appType)
                    },
                    onFailure = { error ->
                        _refreshState.value = RefreshState.Error(
                            appType = appType,
                            message = error.message ?: "生成失败"
                        )
                    }
                )
            } catch (e: Exception) {
                _refreshState.value = RefreshState.Error(
                    appType = appType,
                    message = e.message ?: "未知错误"
                )
            }
        }
    }
    
    /**
     * 重置刷新状态
     */
    fun resetRefreshState() {
        _refreshState.value = RefreshState.Idle
    }

    /**
     * 新增日记
     * @param contactId 角色ID
     * @param diary 新增的日记
     * @param onCompleted 完成回调
     */
    fun addDiaryEntry(contactId: String, diary: DiaryEntry, onCompleted: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                val currentData = repository.getCPhoneDataSuspend(contactId)
                val currentEntries: List<DiaryEntry> = currentData?.diaryEntries ?: emptyList()
                val updatedEntries: List<DiaryEntry> = listOf(diary) + currentEntries
                if (currentData == null) {
                    repository.saveCPhoneData(
                        CPhoneData(
                            contactId = contactId,
                            diaryEntries = updatedEntries
                        )
                    )
                } else {
                    repository.updateDiaryEntries(contactId, updatedEntries)
                }
                onCompleted(true)
            } catch (e: Exception) {
                android.util.Log.e("CPhoneAppViewModel", "新增日记失败", e)
                onCompleted(false)
            }
        }
    }
    
    /**
     * 更新日记
     * @param contactId 角色ID
     * @param diary 更新后的日记
     * @param onCompleted 完成回调
     */
    fun updateDiaryEntry(contactId: String, diary: DiaryEntry, onCompleted: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                val currentData = repository.getCPhoneDataSuspend(contactId)
                if (currentData == null) {
                    onCompleted(false)
                    return@launch
                }
                val updatedEntries: List<DiaryEntry> = currentData.diaryEntries.map { entry: DiaryEntry ->
                    if (entry.id == diary.id) diary else entry
                }
                repository.updateDiaryEntries(contactId, updatedEntries)
                onCompleted(true)
            } catch (e: Exception) {
                android.util.Log.e("CPhoneAppViewModel", "更新日记失败", e)
                onCompleted(false)
            }
        }
    }

    /**
     * 删除日记
     * @param contactId 角色ID
     * @param diaryId 日记ID
     * @param onCompleted 完成回调
     */
    fun deleteDiaryEntry(contactId: String, diaryId: String, onCompleted: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                val currentData = repository.getCPhoneDataSuspend(contactId)
                if (currentData == null) {
                    onCompleted(false)
                    return@launch
                }
                val updatedEntries: List<DiaryEntry> = currentData.diaryEntries.filter { entry: DiaryEntry ->
                    entry.id != diaryId
                }
                repository.updateDiaryEntries(contactId, updatedEntries)
                onCompleted(true)
            } catch (e: Exception) {
                android.util.Log.e("CPhoneAppViewModel", "删除日记失败", e)
                onCompleted(false)
            }
        }
    }
    
    /**
     * 删除淘宝订单
     * @param contactId 角色ID
     * @param orderId 订单ID
     */
    fun deleteTaobaoOrder(contactId: String, orderId: String) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                // 获取当前数据
                val currentData = repository.getCPhoneDataSuspend(contactId)
                val taobaoData = currentData?.taobaoData
                
                android.util.Log.d("CPhoneAppViewModel", "准备删除订单 - contactId: $contactId, orderId: $orderId")
                android.util.Log.d("CPhoneAppViewModel", "当前订单数量: ${taobaoData?.purchases?.size}")
                android.util.Log.d("CPhoneAppViewModel", "当前订单ID列表: ${taobaoData?.purchases?.map { it.id }}")
                
                if (taobaoData != null) {
                    // 过滤掉要删除的订单
                    val updatedPurchases = taobaoData.purchases.filter { purchase ->
                        val keep = purchase.id != orderId
                        android.util.Log.d("CPhoneAppViewModel", "订单 ${purchase.id} (${purchase.itemName}): ${if (keep) "保留" else "删除"}")
                        keep
                    }
                    
                    android.util.Log.d("CPhoneAppViewModel", "删除后订单数量: ${updatedPurchases.size}")
                    
                    // 创建新的淘宝数据对象,保留totalBalance
                    val updatedTaobaoData = taobaoData.copy(
                        purchases = updatedPurchases
                    )
                    
                    // 更新淘宝数据
                    repository.updateTaobaoData(contactId, updatedTaobaoData)
                    android.util.Log.d("CPhoneAppViewModel", "淘宝数据更新完成")
                }
            } catch (e: Exception) {
                // 错误处理
                android.util.Log.e("CPhoneAppViewModel", "删除淘宝订单失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 删除高德地图足迹
     * @param contactId 角色ID
     * @param footprintId 足迹ID
     */
    fun deleteAmapFootprint(contactId: String, footprintId: String) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                // 获取当前数据
                val currentData = repository.getCPhoneDataSuspend(contactId)
                
                if (currentData != null) {
                    // 过滤掉要删除的足迹
                    val updatedFootprints = currentData.amapFootprints.filter {
                        it.id != footprintId
                    }
                    
                    // 更新足迹数据
                    repository.updateAmapFootprints(contactId, updatedFootprints)
                }
            } catch (e: Exception) {
                // 错误处理
                android.util.Log.e("CPhoneAppViewModel", "删除足迹失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 删除浏览器历史记录
     * @param contactId 角色ID
     * @param historyId 历史记录ID
     */
    fun deleteBrowserHistory(contactId: String, historyId: String) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                // 获取当前数据
                val currentData = repository.getCPhoneDataSuspend(contactId)
                
                if (currentData != null) {
                    // 过滤掉要删除的历史记录
                    val updatedHistory = currentData.browserHistory.filter {
                        it.id != historyId
                    }
                    
                    // 更新浏览器历史数据
                    repository.updateBrowserHistory(contactId, updatedHistory)
                }
            } catch (e: Exception) {
                // 错误处理
                android.util.Log.e("CPhoneAppViewModel", "删除浏览记录失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 删除App使用记录
     * @param contactId 角色ID
     * @param recordId 使用记录ID
     */
    fun deleteAppUsageRecord(contactId: String, recordId: String) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                // 获取当前数据
                val currentData = repository.getCPhoneDataSuspend(contactId)
                
                if (currentData != null) {
                    // 过滤掉要删除的使用记录
                    val updatedRecords = currentData.appUsageRecords.filter {
                        it.id != recordId
                    }
                    
                    // 更新App使用记录数据
                    repository.updateAppUsageRecords(contactId, updatedRecords)
                }
            } catch (e: Exception) {
                // 错误处理
                android.util.Log.e("CPhoneAppViewModel", "删除使用记录失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 更新相册照片
     * @param contactId 角色ID
     * @param updatedPhoto 更新后的照片数据
     */
    fun updateAlbumPhoto(contactId: String, updatedPhoto: AlbumPhoto) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                // 获取当前数据
                val currentData = repository.getCPhoneDataSuspend(contactId)
                
                android.util.Log.d("CPhoneAppViewModel", "准备更新照片 - contactId: $contactId, photoId: ${updatedPhoto.id}")
                
                if (currentData != null) {
                    // 找到并更新对应的照片
                    val updatedPhotos = currentData.albumPhotos.map { photo ->
                        if (photo.id == updatedPhoto.id) {
                            android.util.Log.d("CPhoneAppViewModel", "找到照片,更新: ${photo.id}")
                            updatedPhoto
                        } else {
                            photo
                        }
                    }
                    
                    android.util.Log.d("CPhoneAppViewModel", "更新后照片数量: ${updatedPhotos.size}")
                    
                    // 更新相册数据
                    repository.updateAlbumPhotos(contactId, updatedPhotos)
                    android.util.Log.d("CPhoneAppViewModel", "相册数据更新完成")
                }
            } catch (e: Exception) {
                // 错误处理
                android.util.Log.e("CPhoneAppViewModel", "更新相册照片失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 为淘宝订单生成图片
     * @param contactId 角色ID
     * @param purchases 需要生成图片的商品列表（如果为空则生成所有没有图片的商品）
     */
    fun generateTaobaoImages(contactId: String, purchases: List<TaobaoPurchase>? = null) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                
                // 如果未指定商品列表，则获取所有没有图片的商品
                val targetPurchases = if (purchases != null) {
                    purchases
                } else {
                    val currentData = repository.getCPhoneDataSuspend(contactId)
                    currentData?.taobaoData?.purchases?.filter {
                        it.imageUrl.isNullOrEmpty()
                    } ?: emptyList()
                }
                
                if (targetPurchases.isEmpty()) {
                    android.util.Log.d("CPhoneAppViewModel", "没有需要生成图片的淘宝商品")
                    return@launch
                }
                
                android.util.Log.d("CPhoneAppViewModel", "开始为${targetPurchases.size}个商品生成图片")
                
                // 调用ImageGenerationManager生成图片
                com.susking.ephone_s.aidata.worker.ImageGenerationManager.scheduleTaobaoImageGeneration(
                    context = context,
                    contactId = contactId,
                    purchases = targetPurchases
                )
                
                android.util.Log.d("CPhoneAppViewModel", "淘宝图片生成任务已调度")
            } catch (e: Exception) {
                android.util.Log.e("CPhoneAppViewModel", "调度淘宝图片生成失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 为相册照片生成图片
     * @param contactId 角色ID
     * @param photos 需要生成图片的照片列表（如果为空则生成所有没有图片的照片）
     */
    fun generateAlbumImages(contactId: String, photos: List<AlbumPhoto>? = null) {
        viewModelScope.launch {
            try {
                val repository = AiDataApi.getCPhoneRepository()
                
                // 如果未指定照片列表，则获取所有没有图片的照片
                val targetPhotos = if (photos != null) {
                    photos
                } else {
                    val currentData = repository.getCPhoneDataSuspend(contactId)
                    currentData?.albumPhotos?.filter {
                        it.imageUrl.isNullOrEmpty()
                    } ?: emptyList()
                }
                
                if (targetPhotos.isEmpty()) {
                    android.util.Log.d("CPhoneAppViewModel", "没有需要生成图片的相册照片")
                    return@launch
                }
                
                android.util.Log.d("CPhoneAppViewModel", "开始为${targetPhotos.size}张照片生成图片")
                
                // 调用ImageGenerationManager生成图片
                com.susking.ephone_s.aidata.worker.ImageGenerationManager.scheduleAlbumImageGeneration(
                    context = context,
                    contactId = contactId,
                    photos = targetPhotos
                )
                
                android.util.Log.d("CPhoneAppViewModel", "相册图片生成任务已调度")
            } catch (e: Exception) {
                android.util.Log.e("CPhoneAppViewModel", "调度相册图片生成失败", e)
                e.printStackTrace()
            }
        }
    }
    
    // ==================== 类型转换映射函数 ====================
    // 注意：AlbumPhoto已直接使用aidata模块，无需映射
    
    
    
    
    
    
    
    
    /**
     * aidata模块 QQConversation 转换为 cphone模块 SimulatedQQConversation
     */
    private fun QQConversation.toCPhoneModel(): SimulatedQQConversation {
        return SimulatedQQConversation(
            id = id,
            conversationType = "private",
            name = contactName,
            avatarPrompt = contactAvatar,
            messages = messages.map { it.toCPhoneModel() },
            lastMessage = lastMessage,
            timestamp = lastMessageTime.toLongOrNull() ?: 0L // lastMessageTime是String类型，转换为Long
        )
    }
    
    /**
     * aidata模块 QQMessage 转换为 cphone模块 SimulatedQQMessage
     */
    private fun QQMessage.toCPhoneModel(): SimulatedQQMessage {
        return SimulatedQQMessage(
            id = id,
            senderId = if (isSentByMe) "me" else "other", // 根据isSentByMe判断发送者
            senderName = senderName,
            content = content,
            timestamp = timestamp.toLongOrNull() ?: 0L // timestamp是String类型，转换为Long
        )
    }
}