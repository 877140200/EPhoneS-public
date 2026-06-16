package com.susking.ephone_s.aidata.domain.manager

import android.util.Log
import com.susking.ephone_s.aidata.data.local.entity.BackpackItemEntity
import com.susking.ephone_s.aidata.domain.repository.BackpackRepository
import com.susking.ephone_s.core.util.AddToBackpackEvent
import com.susking.ephone_s.core.util.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 背包事件管理器
 * 
 * 全局监听AddToBackpackEvent并自动添加到数据库
 * 确保无论UI是否打开,都能正确处理背包事件
 */
@Singleton
class BackpackEventManager @Inject constructor(
    private val backpackRepository: BackpackRepository
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "BackpackEventManager"
    }
    
    /**
     * 开始监听背包事件
     * 应在Application中调用
     */
    fun startListening() {
        Log.d(TAG, "开始监听背包事件")
        scope.launch {
            EventBus.events.collect { event ->
                when (event) {
                    is AddToBackpackEvent -> {
                        Log.d(TAG, "收到添加背包事件: ${event.productName}")
                        handleAddToBackpack(event)
                    }
                }
            }
        }
    }
    
    /**
     * 处理添加物品到背包事件
     */
    private suspend fun handleAddToBackpack(event: AddToBackpackEvent) {
        try {
            val item = BackpackItemEntity(
                productName = event.productName,
                imageUrl = event.imageUrl,
                price = event.price,
                source = event.source,
                obtainedTime = System.currentTimeMillis(),
                orderId = event.orderId,
                isDiscarded = false
            )
            
            val itemId = backpackRepository.addItem(item)
            Log.d(TAG, "成功添加物品到背包: ${event.productName}, itemId=$itemId")
        } catch (e: Exception) {
            Log.e(TAG, "添加物品到背包失败: ${e.message}", e)
        }
    }
}